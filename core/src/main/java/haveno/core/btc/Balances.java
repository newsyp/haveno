/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package haveno.core.btc;

import haveno.core.btc.wallet.XmrWalletService;
import haveno.core.offer.OpenOffer;
import haveno.core.offer.OpenOfferManager;
import haveno.core.support.dispute.Dispute;
import haveno.core.support.dispute.refund.RefundManager;
import haveno.core.trade.Trade;
import haveno.core.trade.TradeManager;
import haveno.core.trade.closed.ClosedTradableManager;
import haveno.core.trade.failed.FailedTradesManager;

import haveno.common.UserThread;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import javafx.collections.ListChangeListener;

import java.math.BigInteger;

import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;



import monero.wallet.model.MoneroAccount;
import monero.wallet.model.MoneroOutputWallet;
import monero.wallet.model.MoneroWalletListener;

@Slf4j
public class Balances {
    private final TradeManager tradeManager;
    private final XmrWalletService xmrWalletService;
    private final OpenOfferManager openOfferManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final RefundManager refundManager;

    @Getter
    private final ObjectProperty<Coin> availableBalance = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<Coin> reservedBalance = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<Coin> lockedBalance = new SimpleObjectProperty<>();

    @Inject
    public Balances(TradeManager tradeManager,
                    XmrWalletService xmrWalletService,
                    OpenOfferManager openOfferManager,
                    ClosedTradableManager closedTradableManager,
                    FailedTradesManager failedTradesManager,
                    RefundManager refundManager) {
        this.tradeManager = tradeManager;
        this.xmrWalletService = xmrWalletService;
        this.openOfferManager = openOfferManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.refundManager = refundManager;
    }

    public void onAllServicesInitialized() {
        openOfferManager.getObservableList().addListener((ListChangeListener<OpenOffer>) c -> updateBalance());
        tradeManager.getObservableList().addListener((ListChangeListener<Trade>) change -> updateBalance());
        refundManager.getDisputesAsObservableList().addListener((ListChangeListener<Dispute>) c -> updateBalance());
        xmrWalletService.getWallet().addListener(new MoneroWalletListener() {
          @Override public void onBalancesChanged(BigInteger newBalance, BigInteger newUnlockedBalance) { updateBalance(); }
          @Override public void onOutputReceived(MoneroOutputWallet output) { updateBalance(); }
          @Override public void onOutputSpent(MoneroOutputWallet output) { updateBalance(); }
        });
        updateBalance();
    }

    private void updateBalance() {
        // Need to delay a bit to get the balances correct
        UserThread.execute(() -> {
            updateAvailableBalance();
            updateReservedBalance();
            updateLockedBalance();
        });
    }

    // TODO (woodser): currently reserved balance = reserved for trade (excluding multisig) and locked balance = txs with < 10 confirmations

    private void updateAvailableBalance() {
        availableBalance.set(Coin.valueOf(xmrWalletService.getWallet().getUnlockedBalance(0).longValue()));
    }

    private void updateReservedBalance() {
        BigInteger sum = new BigInteger("0");
        List<MoneroAccount> accounts = xmrWalletService.getWallet().getAccounts();
        for (MoneroAccount account : accounts) {
          if (account.getIndex() != 0) sum = sum.add(account.getBalance());
        }
        reservedBalance.set(Coin.valueOf(sum.longValue()));
    }

    private void updateLockedBalance() {
        BigInteger balance = xmrWalletService.getWallet().getBalance(0);
        BigInteger unlockedBalance = xmrWalletService.getWallet().getUnlockedBalance(0);
        lockedBalance.set(Coin.valueOf(balance.subtract(unlockedBalance).longValue()));
    }
}
