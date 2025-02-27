package haveno.core.btc.wallet;

import haveno.core.btc.exceptions.AddressEntryException;
import haveno.core.btc.listeners.XmrBalanceListener;
import haveno.core.btc.model.XmrAddressEntry;
import haveno.core.btc.model.XmrAddressEntryList;
import haveno.core.btc.setup.WalletsSetup;
import haveno.core.util.ParsingUtils;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;

import javax.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;

import java.io.File;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;



import monero.common.MoneroUtils;
import monero.wallet.MoneroWallet;
import monero.wallet.model.MoneroAccount;
import monero.wallet.model.MoneroOutputWallet;
import monero.wallet.model.MoneroSubaddress;
import monero.wallet.model.MoneroTransfer;
import monero.wallet.model.MoneroTxConfig;
import monero.wallet.model.MoneroTxQuery;
import monero.wallet.model.MoneroTxWallet;
import monero.wallet.model.MoneroWalletConfig;
import monero.wallet.model.MoneroWalletListener;
import monero.wallet.model.MoneroWalletListenerI;

public class XmrWalletService {
  private static final Logger log = LoggerFactory.getLogger(XmrWalletService.class);

  private WalletsSetup walletsSetup;
  private final XmrAddressEntryList addressEntryList;
  protected final CopyOnWriteArraySet<XmrBalanceListener> balanceListeners = new CopyOnWriteArraySet<>();
  protected final CopyOnWriteArraySet<MoneroWalletListenerI> walletListeners = new CopyOnWriteArraySet<>();
  private Map<String, MoneroWallet> multisigWallets;

  @Getter
  private MoneroWallet wallet;

  @Inject
  XmrWalletService(WalletsSetup walletsSetup,
                   XmrAddressEntryList addressEntryList) {
    this.walletsSetup = walletsSetup;

    this.addressEntryList = addressEntryList;
    this.multisigWallets = new HashMap<String, MoneroWallet>();

    walletsSetup.addSetupCompletedHandler(() -> {
      wallet = walletsSetup.getXmrWallet();
      wallet.addListener(new MoneroWalletListener() {
        @Override
        public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) { }

        @Override
        public void onNewBlock(long height) { }

        @Override
        public void onBalancesChanged(BigInteger newBalance, BigInteger newUnlockedBalance) {
          notifyBalanceListeners();
        }
      });
    });
  }

  // TODO (woodser): wallet has single password which is passed here?
  // TODO (woodser): test retaking failed trade.  create new multisig wallet or replace?  cannot reuse
  public MoneroWallet getOrCreateMultisigWallet(String tradeId) {
    String path = "xmr_multisig_trade_" + tradeId;
    MoneroWallet multisigWallet = null;
    if (multisigWallets.containsKey(tradeId)) return multisigWallets.get(tradeId);
    else if (MoneroUtils.walletExists(new File(walletsSetup.getWalletConfig().directory(), path).getPath())) { // TODO: use monero-wallet-rpc to determine existence?
      multisigWallet = walletsSetup.getWalletConfig().openWallet(new MoneroWalletConfig()
              .setPath(path)
              .setPassword("abctesting123"));
    } else {
      multisigWallet = walletsSetup.getWalletConfig().createWallet(new MoneroWalletConfig()
              .setPath(path)
              .setPassword("abctesting123"));
    }
    multisigWallets.put(tradeId, multisigWallet);
    multisigWallet.startSyncing(5000l);
    return multisigWallet;
  }

  public XmrAddressEntry getArbitratorAddressEntry() {
      XmrAddressEntry.Context context = XmrAddressEntry.Context.ARBITRATOR;
      Optional<XmrAddressEntry> addressEntry = getAddressEntryListAsImmutableList().stream()
              .filter(e -> context == e.getContext())
              .findAny();
      return getOrCreateAddressEntry(context, addressEntry);
  }

  public XmrAddressEntry recoverAddressEntry(String offerId, String address, XmrAddressEntry.Context context) {
      var available = findAddressEntry(address, XmrAddressEntry.Context.AVAILABLE);
      if (!available.isPresent())
          return null;
      return addressEntryList.swapAvailableToAddressEntryWithOfferId(available.get(), context, offerId);
  }

  public XmrAddressEntry getNewAddressEntry(String offerId, XmrAddressEntry.Context context) {
    if (context == XmrAddressEntry.Context.TRADE_PAYOUT) {
      XmrAddressEntry entry = new XmrAddressEntry(0, wallet.createSubaddress(0).getAddress(), context, offerId, null);
      System.out.println("Adding address entry: " + entry.getAccountIndex() + ", " + entry.getAddressString());
      addressEntryList.addAddressEntry(entry);
      return entry;
    } else {
      MoneroAccount account = wallet.createAccount();
      XmrAddressEntry entry = new XmrAddressEntry(account.getIndex(), account.getPrimaryAddress(), context, offerId, null);
      addressEntryList.addAddressEntry(entry);
      return entry;
    }
  }

  public XmrAddressEntry getOrCreateAddressEntry(String offerId, XmrAddressEntry.Context context) {
    Optional<XmrAddressEntry> addressEntry = getAddressEntryListAsImmutableList().stream()
            .filter(e -> offerId.equals(e.getOfferId()))
            .filter(e -> context == e.getContext())
            .findAny();
    if (addressEntry.isPresent()) {
        return addressEntry.get();
    } else {
        // We try to use available and not yet used entries // TODO (woodser): "available" entries is not applicable in xmr which uses account 0 for main wallet and subsequent accounts for reserved trades, refactor address association for xmr?
        Optional<XmrAddressEntry> emptyAvailableAddressEntry = getAddressEntryListAsImmutableList().stream()
                .filter(e -> XmrAddressEntry.Context.AVAILABLE == e.getContext())
                .filter(e -> isAccountUnused(e.getAccountIndex()))
                .findAny();
        if (emptyAvailableAddressEntry.isPresent()) {
            return addressEntryList.swapAvailableToAddressEntryWithOfferId(emptyAvailableAddressEntry.get(), context, offerId);
        } else {
            MoneroAccount account = wallet.createAccount();
            XmrAddressEntry entry = new XmrAddressEntry(account.getIndex(), account.getPrimaryAddress(), context, offerId, null);
            addressEntryList.addAddressEntry(entry);
            return entry;
        }
    }
  }

  private XmrAddressEntry getOrCreateAddressEntry(XmrAddressEntry.Context context, Optional<XmrAddressEntry> addressEntry) {
      if (addressEntry.isPresent()) {
        return addressEntry.get();
      } else {
        if (context == XmrAddressEntry.Context.ARBITRATOR) {
          MoneroSubaddress subaddress = wallet.createSubaddress(0);
          XmrAddressEntry entry = new XmrAddressEntry(0, subaddress.getAddress(), context);
          addressEntryList.addAddressEntry(entry);
          return entry;
        } else {
          throw new RuntimeException("XmrWalletService.getOrCreateAddressEntry(context, addressEntry) not implemented for non-arbitrator context");	// TODO (woodser): this method used with non-arbitrator context?
        }
      }
  }

  public Optional<XmrAddressEntry> getAddressEntry(String offerId, XmrAddressEntry.Context context) {
    return getAddressEntryListAsImmutableList().stream()
            .filter(e -> offerId.equals(e.getOfferId()))
            .filter(e -> context == e.getContext())
            .findAny();
  }

  public void swapTradeEntryToAvailableEntry(String offerId, XmrAddressEntry.Context context) {
    Optional<XmrAddressEntry> addressEntryOptional = getAddressEntryListAsImmutableList().stream()
            .filter(e -> offerId.equals(e.getOfferId()))
            .filter(e -> context == e.getContext())
            .findAny();
    addressEntryOptional.ifPresent(e -> {
        log.info("swap addressEntry with address {} and offerId {} from context {} to available",
                e.getAddressString(), e.getOfferId(), context);
        addressEntryList.swapToAvailable(e);
        saveAddressEntryList();
    });
}

  public void resetAddressEntriesForOpenOffer(String offerId) {
    log.info("resetAddressEntriesForOpenOffer offerId={}", offerId);
    swapTradeEntryToAvailableEntry(offerId, XmrAddressEntry.Context.OFFER_FUNDING);
    swapTradeEntryToAvailableEntry(offerId, XmrAddressEntry.Context.RESERVED_FOR_TRADE);
  }

  public void resetAddressEntriesForPendingTrade(String offerId) {
      swapTradeEntryToAvailableEntry(offerId, XmrAddressEntry.Context.MULTI_SIG);
      // We swap also TRADE_PAYOUT to be sure all is cleaned up. There might be cases where a user cannot send the funds
      // to an external wallet directly in the last step of the trade, but the funds are in the Bisq wallet anyway and
      // the dealing with the external wallet is pure UI thing. The user can move the funds to the wallet and then
      // send out the funds to the external wallet. As this cleanup is a rare situation and most users do not use
      // the feature to send out the funds we prefer that strategy (if we keep the address entry it might cause
      // complications in some edge cases after a SPV resync).
      swapTradeEntryToAvailableEntry(offerId, XmrAddressEntry.Context.TRADE_PAYOUT);
  }

  private Optional<XmrAddressEntry> findAddressEntry(String address, XmrAddressEntry.Context context) {
      return getAddressEntryListAsImmutableList().stream()
              .filter(e -> address.equals(e.getAddressString()))
              .filter(e -> context == e.getContext())
              .findAny();
  }

  public List<XmrAddressEntry> getAvailableAddressEntries() {
    return getAddressEntryListAsImmutableList().stream()
            .filter(addressEntry -> XmrAddressEntry.Context.AVAILABLE == addressEntry.getContext())
            .collect(Collectors.toList());
}

  public List<XmrAddressEntry> getAddressEntriesForTrade() {
      return getAddressEntryListAsImmutableList().stream()
            .filter(addressEntry -> XmrAddressEntry.Context.MULTI_SIG == addressEntry.getContext() ||
                    XmrAddressEntry.Context.TRADE_PAYOUT == addressEntry.getContext())
            .collect(Collectors.toList());
  }

  public List<XmrAddressEntry> getAddressEntries(XmrAddressEntry.Context context) {
      return getAddressEntryListAsImmutableList().stream()
              .filter(addressEntry -> context == addressEntry.getContext())
              .collect(Collectors.toList());
  }

  public List<XmrAddressEntry> getFundedAvailableAddressEntries() {
      return getAvailableAddressEntries().stream()
              .filter(addressEntry -> getBalanceForAccount(addressEntry.getAccountIndex()).isPositive())
              .collect(Collectors.toList());
  }

  public List<XmrAddressEntry> getAddressEntryListAsImmutableList() {
    return addressEntryList.getAddressEntriesAsListImmutable();
  }

  public boolean isAccountUnused(int accountIndex) {
    return accountIndex != 0 && getBalanceForAccount(accountIndex).value == 0;
    //return !wallet.getSubaddress(accountIndex, 0).isUsed(); // TODO: isUsed() does not include unconfirmed funds
  }

  public Coin getBalanceForAccount(int accountIndex) {

    // get wallet balance
    BigInteger balance = wallet.getBalance(accountIndex);

    // balance from xmr wallet does not include unconfirmed funds, so add them  // TODO: support lower in stack?
    for (MoneroTxWallet unconfirmedTx : wallet.getTxs(new MoneroTxQuery().setIsConfirmed(false))) {
      for (MoneroTransfer transfer : unconfirmedTx.getTransfers()) {
        if (transfer.getAccountIndex() == accountIndex) {
          balance = transfer.isIncoming() ? balance.add(transfer.getAmount()) : balance.subtract(transfer.getAmount());
        }
      }
    }

    System.out.println("Returning balance for account " + accountIndex + ": " + balance.longValueExact());

    return Coin.valueOf(balance.longValueExact());
  }


  public Coin getAvailableConfirmedBalance() {
    return wallet != null ? Coin.valueOf(wallet.getUnlockedBalance(0).longValueExact()) : Coin.ZERO;
  }

  public Coin getSavingWalletBalance() {
    return wallet != null ? Coin.valueOf(wallet.getBalance(0).longValueExact()) : Coin.ZERO;
  }

  public Stream<XmrAddressEntry> getAddressEntriesForAvailableBalanceStream() {
    Stream<XmrAddressEntry> availableAndPayout = Stream.concat(getAddressEntries(XmrAddressEntry.Context.TRADE_PAYOUT).stream(), getFundedAvailableAddressEntries().stream());
    Stream<XmrAddressEntry> available = Stream.concat(availableAndPayout, getAddressEntries(XmrAddressEntry.Context.ARBITRATOR).stream());
    available = Stream.concat(available, getAddressEntries(XmrAddressEntry.Context.OFFER_FUNDING).stream());
    return available.filter(addressEntry -> getBalanceForAccount(addressEntry.getAccountIndex()).isPositive());
  }

  public void addBalanceListener(XmrBalanceListener listener) {
    balanceListeners.add(listener);
  }

  public void removeBalanceListener(XmrBalanceListener listener) {
    balanceListeners.remove(listener);
  }

  public void saveAddressEntryList() {
    addressEntryList.requestPersistence();
  }

  public List<MoneroTxWallet> getTransactions(boolean includeDead) {
      return wallet.getTxs(new MoneroTxQuery().setIsFailed(includeDead ? null : false));
  }

  public void shutDown() {
    System.out.println("XmrWalletService.shutDown()");

    // collect wallets to shutdown
    List<MoneroWallet> openWallets = new ArrayList<MoneroWallet>();
    if (wallet != null) openWallets.add(wallet);
    for (String multisigWalletKey : multisigWallets.keySet()) {
      openWallets.add(multisigWallets.get(multisigWalletKey));
    }

    // create shutdown threads
    List<Thread> threads = new ArrayList<Thread>();
    for (MoneroWallet openWallet : openWallets) {
      threads.add(new Thread(new Runnable() {
        @Override
        public void run() {
          try { walletsSetup.getWalletConfig().closeWallet(openWallet); }
          catch (Exception e) {
            e.printStackTrace(); // exception expected on shutdown when run as daemon TODO (woodser): detect if running as daemon
          }
        }
      }));
    }

    // run shutdown threads in parallel
    for (Thread thread : threads) thread.start();

    // wait for all threads
    System.out.println("Joining threads");
    for (Thread thread : threads) {
      try { thread.join(); }
      catch (InterruptedException e) { e.printStackTrace(); }
    }
    System.out.println("Done joining threads");
  }

  ///////////////////////////////////////////////////////////////////////////////////////////
  // Withdrawal Send
  ///////////////////////////////////////////////////////////////////////////////////////////

  public String sendFunds(int fromAccountIndex,
                          String toAddress,
                          Coin receiverAmount,
                          @SuppressWarnings("SameParameterValue") XmrAddressEntry.Context context,
                          FutureCallback<MoneroTxWallet> callback) throws AddressFormatException,
          AddressEntryException, InsufficientMoneyException {

    try {
      MoneroTxWallet tx = wallet.createTx(new MoneroTxConfig()
          .setAccountIndex(fromAccountIndex)
          .setAddress(toAddress)
          .setAmount(ParsingUtils.satoshisToXmrAtomicUnits(receiverAmount.value))
          .setRelay(true));
      callback.onSuccess(tx);
      printTxs("sendFunds", tx);
      return tx.getHash();
    } catch (Exception e) {
      callback.onFailure(e);
      throw e;
    }
  }

//  public String sendFunds(String fromAddress, String toAddress, Coin receiverAmount, Coin fee, @Nullable KeyParameter aesKey, @SuppressWarnings("SameParameterValue") AddressEntry.Context context,
//      FutureCallback<Transaction> callback) throws AddressFormatException, AddressEntryException, InsufficientMoneyException {
//    SendRequest sendRequest = getSendRequest(fromAddress, toAddress, receiverAmount, fee, aesKey, context);
//    Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
//    Futures.addCallback(sendResult.broadcastComplete, callback, MoreExecutors.directExecutor());
//
//    printTx("sendFunds", sendResult.tx);
//    return sendResult.tx.getTxId().toString();
//  }

  ///////////////////////////////////////////////////////////////////////////////////////////
  // Util
  ///////////////////////////////////////////////////////////////////////////////////////////

  public static void printTxs(String tracePrefix, MoneroTxWallet... txs) {
    StringBuilder sb = new StringBuilder();
    for (MoneroTxWallet tx : txs) sb.append('\n' + tx.toString());
    log.info("\n" + tracePrefix + ":" + sb.toString());
  }

  private void notifyBalanceListeners() {
    for (XmrBalanceListener balanceListener : balanceListeners) {
      Coin balance;
      if (balanceListener.getAccountIndex() != null && balanceListener.getAccountIndex() != 0) {
        balance = getBalanceForAccount(balanceListener.getAccountIndex());
      } else {
        balance = getAvailableConfirmedBalance();
      }
      balanceListener.onBalanceChanged(BigInteger.valueOf(balance.value));
    }
  }

  /**
   * Wraps a MoneroWalletListener to notify the Haveno application.
   */
  public class HavenoWalletListener extends MoneroWalletListener {

    private MoneroWalletListener listener;

    public HavenoWalletListener(MoneroWalletListener listener) {
      this.listener = listener;
    }

    @Override
    public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
        listener.onSyncProgress(height, startHeight, endHeight, percentDone, message);
    }

    @Override
    public void onNewBlock(long height) {
        listener.onNewBlock(height);
    }

    @Override
    public void onBalancesChanged(BigInteger newBalance, BigInteger newUnlockedBalance) {
        listener.onBalancesChanged(newBalance, newUnlockedBalance);
    }

    @Override
    public void onOutputReceived(MoneroOutputWallet output) {
        listener.onOutputReceived(output);
    }

    @Override
    public void onOutputSpent(MoneroOutputWallet output) {
        listener.onOutputSpent(output);
    }
  }
}
