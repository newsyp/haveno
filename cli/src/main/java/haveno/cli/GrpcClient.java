/*
 * This file is part of Haveno.
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

package haveno.cli;

import haveno.proto.grpc.AddressBalanceInfo;
import haveno.proto.grpc.BalancesInfo;
import haveno.proto.grpc.BsqBalanceInfo;
import haveno.proto.grpc.BtcBalanceInfo;
import haveno.proto.grpc.CancelOfferRequest;
import haveno.proto.grpc.ConfirmPaymentReceivedRequest;
import haveno.proto.grpc.ConfirmPaymentStartedRequest;
import haveno.proto.grpc.CreateCryptoCurrencyPaymentAccountRequest;
import haveno.proto.grpc.CreateOfferRequest;
import haveno.proto.grpc.CreatePaymentAccountRequest;
import haveno.proto.grpc.GetAddressBalanceRequest;
import haveno.proto.grpc.GetBalancesRequest;
import haveno.proto.grpc.GetCryptoCurrencyPaymentMethodsRequest;
import haveno.proto.grpc.GetFundingAddressesRequest;
import haveno.proto.grpc.GetMethodHelpRequest;
import haveno.proto.grpc.GetMyOfferRequest;
import haveno.proto.grpc.GetMyOffersRequest;
import haveno.proto.grpc.GetOfferRequest;
import haveno.proto.grpc.GetOffersRequest;
import haveno.proto.grpc.GetPaymentAccountFormRequest;
import haveno.proto.grpc.GetPaymentAccountsRequest;
import haveno.proto.grpc.GetPaymentMethodsRequest;
import haveno.proto.grpc.GetTradeRequest;
import haveno.proto.grpc.GetTransactionRequest;
import haveno.proto.grpc.GetTxFeeRateRequest;
import haveno.proto.grpc.GetUnusedBsqAddressRequest;
import haveno.proto.grpc.GetVersionRequest;
import haveno.proto.grpc.KeepFundsRequest;
import haveno.proto.grpc.LockWalletRequest;
import haveno.proto.grpc.MarketPriceRequest;
import haveno.proto.grpc.OfferInfo;
import haveno.proto.grpc.RegisterDisputeAgentRequest;
import haveno.proto.grpc.RemoveWalletPasswordRequest;
import haveno.proto.grpc.SendBsqRequest;
import haveno.proto.grpc.SendBtcRequest;
import haveno.proto.grpc.SetTxFeeRatePreferenceRequest;
import haveno.proto.grpc.SetWalletPasswordRequest;
import haveno.proto.grpc.StopRequest;
import haveno.proto.grpc.TakeOfferReply;
import haveno.proto.grpc.TakeOfferRequest;
import haveno.proto.grpc.TradeInfo;
import haveno.proto.grpc.TxFeeRateInfo;
import haveno.proto.grpc.TxInfo;
import haveno.proto.grpc.UnlockWalletRequest;
import haveno.proto.grpc.UnsetTxFeeRatePreferenceRequest;
import haveno.proto.grpc.VerifyBsqSentToAddressRequest;
import haveno.proto.grpc.WithdrawFundsRequest;
import haveno.proto.grpc.XmrBalanceInfo;

import protobuf.PaymentAccount;
import protobuf.PaymentMethod;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static haveno.cli.CryptoCurrencyUtil.isSupportedCryptoCurrency;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;


@SuppressWarnings("ResultOfMethodCallIgnored")
@Slf4j
public final class GrpcClient {

    private final GrpcStubs grpcStubs;

    public GrpcClient(String apiHost, int apiPort, String apiPassword) {
        this.grpcStubs = new GrpcStubs(apiHost, apiPort, apiPassword);
    }

    public String getVersion() {
        var request = GetVersionRequest.newBuilder().build();
        return grpcStubs.versionService.getVersion(request).getVersion();
    }

    public BalancesInfo getBalances() {
        return getBalances("");
    }

    public BsqBalanceInfo getBsqBalances() {
        return getBalances("BSQ").getBsq();
    }

    public BtcBalanceInfo getBtcBalances() {
        return getBalances("BTC").getBtc();
    }

    public XmrBalanceInfo getXmrBalances() {
        return getBalances("XMR").getXmr();
    }

    public BalancesInfo getBalances(String currencyCode) {
        var request = GetBalancesRequest.newBuilder()
                .setCurrencyCode(currencyCode)
                .build();
        return grpcStubs.walletsService.getBalances(request).getBalances();
    }

    public AddressBalanceInfo getAddressBalance(String address) {
        var request = GetAddressBalanceRequest.newBuilder()
                .setAddress(address).build();
        return grpcStubs.walletsService.getAddressBalance(request).getAddressBalanceInfo();
    }

    public double getBtcPrice(String currencyCode) {
        var request = MarketPriceRequest.newBuilder()
                .setCurrencyCode(currencyCode)
                .build();
        return grpcStubs.priceService.getMarketPrice(request).getPrice();
    }

    public List<AddressBalanceInfo> getFundingAddresses() {
        var request = GetFundingAddressesRequest.newBuilder().build();
        return grpcStubs.walletsService.getFundingAddresses(request).getAddressBalanceInfoList();
    }

    public String getUnusedBsqAddress() {
        var request = GetUnusedBsqAddressRequest.newBuilder().build();
        return grpcStubs.walletsService.getUnusedBsqAddress(request).getAddress();
    }

    public String getUnusedBtcAddress() {
        var request = GetFundingAddressesRequest.newBuilder().build();
        var addressBalances = grpcStubs.walletsService.getFundingAddresses(request)
                .getAddressBalanceInfoList();
        //noinspection OptionalGetWithoutIsPresent
        return addressBalances.stream()
                .filter(AddressBalanceInfo::getIsAddressUnused)
                .findFirst()
                .get()
                .getAddress();
    }

    public TxInfo sendBsq(String address, String amount, String txFeeRate) {
        var request = SendBsqRequest.newBuilder()
                .setAddress(address)
                .setAmount(amount)
                .setTxFeeRate(txFeeRate)
                .build();
        return grpcStubs.walletsService.sendBsq(request).getTxInfo();
    }

    public TxInfo sendBtc(String address, String amount, String txFeeRate, String memo) {
        var request = SendBtcRequest.newBuilder()
                .setAddress(address)
                .setAmount(amount)
                .setTxFeeRate(txFeeRate)
                .setMemo(memo)
                .build();
        return grpcStubs.walletsService.sendBtc(request).getTxInfo();
    }

    public boolean verifyBsqSentToAddress(String address, String amount) {
        var request = VerifyBsqSentToAddressRequest.newBuilder()
                .setAddress(address)
                .setAmount(amount)
                .build();
        return grpcStubs.walletsService.verifyBsqSentToAddress(request).getIsAmountReceived();
    }

    public TxFeeRateInfo getTxFeeRate() {
        var request = GetTxFeeRateRequest.newBuilder().build();
        return grpcStubs.walletsService.getTxFeeRate(request).getTxFeeRateInfo();
    }

    public TxFeeRateInfo setTxFeeRate(long txFeeRate) {
        var request = SetTxFeeRatePreferenceRequest.newBuilder()
                .setTxFeeRatePreference(txFeeRate)
                .build();
        return grpcStubs.walletsService.setTxFeeRatePreference(request).getTxFeeRateInfo();
    }

    public TxFeeRateInfo unsetTxFeeRate() {
        var request = UnsetTxFeeRatePreferenceRequest.newBuilder().build();
        return grpcStubs.walletsService.unsetTxFeeRatePreference(request).getTxFeeRateInfo();
    }

    public TxInfo getTransaction(String txId) {
        var request = GetTransactionRequest.newBuilder()
                .setTxId(txId)
                .build();
        return grpcStubs.walletsService.getTransaction(request).getTxInfo();
    }

    public OfferInfo createFixedPricedOffer(String direction,
                                            String currencyCode,
                                            long amount,
                                            long minAmount,
                                            String fixedPrice,
                                            double securityDeposit,
                                            String paymentAcctId,
                                            String makerFeeCurrencyCode) {
        return createOffer(direction,
                currencyCode,
                amount,
                minAmount,
                false,
                fixedPrice,
                0.00,
                securityDeposit,
                paymentAcctId,
                makerFeeCurrencyCode);
    }

    public OfferInfo createMarketBasedPricedOffer(String direction,
                                                  String currencyCode,
                                                  long amount,
                                                  long minAmount,
                                                  double marketPriceMargin,
                                                  double securityDeposit,
                                                  String paymentAcctId,
                                                  String makerFeeCurrencyCode) {
        return createOffer(direction,
                currencyCode,
                amount,
                minAmount,
                true,
                "0",
                marketPriceMargin,
                securityDeposit,
                paymentAcctId,
                makerFeeCurrencyCode);
    }

    public OfferInfo createOffer(String direction,
                                 String currencyCode,
                                 long amount,
                                 long minAmount,
                                 boolean useMarketBasedPrice,
                                 String fixedPrice,
                                 double marketPriceMargin,
                                 double securityDeposit,
                                 String paymentAcctId,
                                 String makerFeeCurrencyCode) {
        var request = CreateOfferRequest.newBuilder()
                .setDirection(direction)
                .setCurrencyCode(currencyCode)
                .setAmount(amount)
                .setMinAmount(minAmount)
                .setUseMarketBasedPrice(useMarketBasedPrice)
                .setPrice(fixedPrice)
                .setMarketPriceMargin(marketPriceMargin)
                .setBuyerSecurityDeposit(securityDeposit)
                .setPaymentAccountId(paymentAcctId)
                .setMakerFeeCurrencyCode(makerFeeCurrencyCode)
                .build();
        return grpcStubs.offersService.createOffer(request).getOffer();
    }

    public void cancelOffer(String offerId) {
        var request = CancelOfferRequest.newBuilder()
                .setId(offerId)
                .build();
        grpcStubs.offersService.cancelOffer(request);
    }

    public OfferInfo getOffer(String offerId) {
        var request = GetOfferRequest.newBuilder()
                .setId(offerId)
                .build();
        return grpcStubs.offersService.getOffer(request).getOffer();
    }

    public OfferInfo getMyOffer(String offerId) {
        var request = GetMyOfferRequest.newBuilder()
                .setId(offerId)
                .build();
        return grpcStubs.offersService.getMyOffer(request).getOffer();
    }

    public List<OfferInfo> getOffers(String direction, String currencyCode) {
        if (isSupportedCryptoCurrency(currencyCode)) {
            return getCryptoCurrencyOffers(direction, currencyCode);
        } else {
            var request = GetOffersRequest.newBuilder()
                    .setDirection(direction)
                    .setCurrencyCode(currencyCode)
                    .build();
            return grpcStubs.offersService.getOffers(request).getOffersList();
        }
    }

    public List<OfferInfo> getCryptoCurrencyOffers(String direction, String currencyCode) {
        return getOffers(direction, "XMR").stream()
                .filter(o -> o.getBaseCurrencyCode().equalsIgnoreCase(currencyCode))
                .collect(toList());
    }

    public List<OfferInfo> getOffersSortedByDate(String currencyCode) {
        ArrayList<OfferInfo> offers = new ArrayList<>();
        offers.addAll(getOffers(BUY.name(), currencyCode));
        offers.addAll(getOffers(SELL.name(), currencyCode));
        return sortOffersByDate(offers);
    }

    public List<OfferInfo> getOffersSortedByDate(String direction, String currencyCode) {
        var offers = getOffers(direction, currencyCode);
        return offers.isEmpty() ? offers : sortOffersByDate(offers);
    }

    public List<OfferInfo> getBsqOffersSortedByDate() {
        ArrayList<OfferInfo> offers = new ArrayList<>();
        offers.addAll(getCryptoCurrencyOffers(BUY.name(), "BSQ"));
        offers.addAll(getCryptoCurrencyOffers(SELL.name(), "BSQ"));
        return sortOffersByDate(offers);
    }

    public List<OfferInfo> getMyOffers(String direction, String currencyCode) {
        if (isSupportedCryptoCurrency(currencyCode)) {
            return getMyCryptoCurrencyOffers(direction, currencyCode);
        } else {
            var request = GetMyOffersRequest.newBuilder()
                    .setDirection(direction)
                    .setCurrencyCode(currencyCode)
                    .build();
            return grpcStubs.offersService.getMyOffers(request).getOffersList();
        }
    }

    public List<OfferInfo> getMyCryptoCurrencyOffers(String direction, String currencyCode) {
        return getMyOffers(direction, "BTC").stream()
                .filter(o -> o.getBaseCurrencyCode().equalsIgnoreCase(currencyCode))
                .collect(toList());
    }

    public List<OfferInfo> getMyOffersSortedByDate(String direction, String currencyCode) {
        var offers = getMyOffers(direction, currencyCode);
        return offers.isEmpty() ? offers : sortOffersByDate(offers);
    }

    public List<OfferInfo> getMyOffersSortedByDate(String currencyCode) {
        ArrayList<OfferInfo> offers = new ArrayList<>();
        offers.addAll(getMyOffers(BUY.name(), currencyCode));
        offers.addAll(getMyOffers(SELL.name(), currencyCode));
        return sortOffersByDate(offers);
    }

    public List<OfferInfo> getMyBsqOffersSortedByDate() {
        ArrayList<OfferInfo> offers = new ArrayList<>();
        offers.addAll(getMyCryptoCurrencyOffers(BUY.name(), "BSQ"));
        offers.addAll(getMyCryptoCurrencyOffers(SELL.name(), "BSQ"));
        return sortOffersByDate(offers);
    }

    public OfferInfo getMostRecentOffer(String direction, String currencyCode) {
        List<OfferInfo> offers = getOffersSortedByDate(direction, currencyCode);
        return offers.isEmpty() ? null : offers.get(offers.size() - 1);
    }

    public List<OfferInfo> sortOffersByDate(List<OfferInfo> offerInfoList) {
        return offerInfoList.stream()
                .sorted(comparing(OfferInfo::getDate))
                .collect(toList());
    }

    public TakeOfferReply getTakeOfferReply(String offerId, String paymentAccountId, String takerFeeCurrencyCode) {
        var request = TakeOfferRequest.newBuilder()
                .setOfferId(offerId)
                .setPaymentAccountId(paymentAccountId)
                .setTakerFeeCurrencyCode(takerFeeCurrencyCode)
                .build();
        return grpcStubs.tradesService.takeOffer(request);
    }

    public TradeInfo takeOffer(String offerId, String paymentAccountId, String takerFeeCurrencyCode) {
        var reply = getTakeOfferReply(offerId, paymentAccountId, takerFeeCurrencyCode);
        if (reply.hasTrade())
            return reply.getTrade();
        else
            throw new IllegalStateException(reply.getFailureReason().getDescription());
    }

    public TradeInfo getTrade(String tradeId) {
        var request = GetTradeRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        return grpcStubs.tradesService.getTrade(request).getTrade();
    }

    public void confirmPaymentStarted(String tradeId) {
        var request = ConfirmPaymentStartedRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        grpcStubs.tradesService.confirmPaymentStarted(request);
    }

    public void confirmPaymentReceived(String tradeId) {
        var request = ConfirmPaymentReceivedRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        grpcStubs.tradesService.confirmPaymentReceived(request);
    }

    public void keepFunds(String tradeId) {
        var request = KeepFundsRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        grpcStubs.tradesService.keepFunds(request);
    }

    public void withdrawFunds(String tradeId, String address, String memo) {
        var request = WithdrawFundsRequest.newBuilder()
                .setTradeId(tradeId)
                .setAddress(address)
                .setMemo(memo)
                .build();
        grpcStubs.tradesService.withdrawFunds(request);
    }

    public List<PaymentMethod> getPaymentMethods() {
        var request = GetPaymentMethodsRequest.newBuilder().build();
        return grpcStubs.paymentAccountsService.getPaymentMethods(request).getPaymentMethodsList();
    }

    public String getPaymentAcctFormAsJson(String paymentMethodId) {
        var request = GetPaymentAccountFormRequest.newBuilder()
                .setPaymentMethodId(paymentMethodId)
                .build();
        return grpcStubs.paymentAccountsService.getPaymentAccountForm(request).getPaymentAccountFormJson();
    }

    public PaymentAccount createPaymentAccount(String json) {
        var request = CreatePaymentAccountRequest.newBuilder()
                .setPaymentAccountForm(json)
                .build();
        return grpcStubs.paymentAccountsService.createPaymentAccount(request).getPaymentAccount();
    }

    public List<PaymentAccount> getPaymentAccounts() {
        var request = GetPaymentAccountsRequest.newBuilder().build();
        return grpcStubs.paymentAccountsService.getPaymentAccounts(request).getPaymentAccountsList();
    }

    public PaymentAccount createCryptoCurrencyPaymentAccount(String accountName,
                                                             String currencyCode,
                                                             String address,
                                                             boolean tradeInstant) {
        var request = CreateCryptoCurrencyPaymentAccountRequest.newBuilder()
                .setAccountName(accountName)
                .setCurrencyCode(currencyCode)
                .setAddress(address)
                .setTradeInstant(tradeInstant)
                .build();
        return grpcStubs.paymentAccountsService.createCryptoCurrencyPaymentAccount(request).getPaymentAccount();
    }

    public List<PaymentMethod> getCryptoPaymentMethods() {
        var request = GetCryptoCurrencyPaymentMethodsRequest.newBuilder().build();
        return grpcStubs.paymentAccountsService.getCryptoCurrencyPaymentMethods(request).getPaymentMethodsList();
    }

    public void lockWallet() {
        var request = LockWalletRequest.newBuilder().build();
        grpcStubs.walletsService.lockWallet(request);
    }

    public void unlockWallet(String walletPassword, long timeout) {
        var request = UnlockWalletRequest.newBuilder()
                .setPassword(walletPassword)
                .setTimeout(timeout).build();
        grpcStubs.walletsService.unlockWallet(request);
    }

    public void removeWalletPassword(String walletPassword) {
        var request = RemoveWalletPasswordRequest.newBuilder()
                .setPassword(walletPassword).build();
        grpcStubs.walletsService.removeWalletPassword(request);
    }

    public void setWalletPassword(String walletPassword) {
        var request = SetWalletPasswordRequest.newBuilder()
                .setPassword(walletPassword).build();
        grpcStubs.walletsService.setWalletPassword(request);
    }

    public void setWalletPassword(String oldWalletPassword, String newWalletPassword) {
        var request = SetWalletPasswordRequest.newBuilder()
                .setPassword(oldWalletPassword)
                .setNewPassword(newWalletPassword).build();
        grpcStubs.walletsService.setWalletPassword(request);
    }

    public void registerDisputeAgent(String disputeAgentType, String registrationKey) {
        var request = RegisterDisputeAgentRequest.newBuilder()
                .setDisputeAgentType(disputeAgentType).setRegistrationKey(registrationKey).build();
        grpcStubs.disputeAgentsService.registerDisputeAgent(request);
    }

    public void stopServer() {
        var request = StopRequest.newBuilder().build();
        grpcStubs.shutdownService.stop(request);
    }

    public String getMethodHelp(Method method) {
        var request = GetMethodHelpRequest.newBuilder().setMethodName(method.name()).build();
        return grpcStubs.helpService.getMethodHelp(request).getMethodHelp();
    }
}
