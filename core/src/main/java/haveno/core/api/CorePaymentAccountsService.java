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

package haveno.core.api;

import haveno.core.account.witness.AccountAgeWitnessService;
import haveno.core.api.model.PaymentAccountForm;
import haveno.core.locale.CryptoCurrency;
import haveno.core.payment.CryptoCurrencyAccount;
import haveno.core.payment.InstantCryptoCurrencyAccount;
import haveno.core.payment.PaymentAccount;
import haveno.core.payment.PaymentAccountFactory;
import haveno.core.payment.payload.PaymentMethod;
import haveno.core.user.User;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.File;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static haveno.core.locale.CurrencyUtil.getCryptoCurrency;
import static java.lang.String.format;

@Singleton
@Slf4j
class CorePaymentAccountsService {

    private final CoreWalletsService coreWalletsService;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final PaymentAccountForm paymentAccountForm;
    private final User user;

    @Inject
    public CorePaymentAccountsService(CoreWalletsService coreWalletsService,
                                      AccountAgeWitnessService accountAgeWitnessService,
                                      PaymentAccountForm paymentAccountForm,
                                      User user) {
        this.coreWalletsService = coreWalletsService;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.paymentAccountForm = paymentAccountForm;
        this.user = user;
    }

    // Fiat Currency Accounts

    PaymentAccount createPaymentAccount(String jsonString) {
        PaymentAccount paymentAccount = paymentAccountForm.toPaymentAccount(jsonString);
        verifyPaymentAccountHasRequiredFields(paymentAccount);
        user.addPaymentAccountIfNotExists(paymentAccount);
        accountAgeWitnessService.publishMyAccountAgeWitness(paymentAccount.getPaymentAccountPayload());
        log.info("Saved payment account with id {} and payment method {}.",
                paymentAccount.getId(),
                paymentAccount.getPaymentAccountPayload().getPaymentMethodId());
        return paymentAccount;
    }

    Set<PaymentAccount> getPaymentAccounts() {
        return user.getPaymentAccounts();
    }

    List<PaymentMethod> getFiatPaymentMethods() {
        return PaymentMethod.getPaymentMethods().stream()
                .filter(paymentMethod -> !paymentMethod.isAsset())
                .sorted(Comparator.comparing(PaymentMethod::getId))
                .collect(Collectors.toList());
    }

    String getPaymentAccountFormAsString(String paymentMethodId) {
        File jsonForm = getPaymentAccountForm(paymentMethodId);
        jsonForm.deleteOnExit(); // If just asking for a string, delete the form file.
        return paymentAccountForm.toJsonString(jsonForm);
    }

    File getPaymentAccountForm(String paymentMethodId) {
        return paymentAccountForm.getPaymentAccountForm(paymentMethodId);
    }

    // Crypto Currency Accounts

    PaymentAccount createCryptoCurrencyPaymentAccount(String accountName,
                                                      String currencyCode,
                                                      String address,
                                                      boolean tradeInstant) {
        String bsqCode = currencyCode.toUpperCase();
        if (!bsqCode.equals("BSQ"))
            throw new IllegalArgumentException("api does not currently support " + currencyCode + " accounts");

        // Validate the BSQ address string but ignore the return value.
        coreWalletsService.getValidBsqLegacyAddress(address);

        var cryptoCurrencyAccount = tradeInstant
                ? (InstantCryptoCurrencyAccount) PaymentAccountFactory.getPaymentAccount(PaymentMethod.BLOCK_CHAINS_INSTANT)
                : (CryptoCurrencyAccount) PaymentAccountFactory.getPaymentAccount(PaymentMethod.BLOCK_CHAINS);
        cryptoCurrencyAccount.init();
        cryptoCurrencyAccount.setAccountName(accountName);
        cryptoCurrencyAccount.setAddress(address);
        Optional<CryptoCurrency> cryptoCurrency = getCryptoCurrency(bsqCode);
        cryptoCurrency.ifPresent(cryptoCurrencyAccount::setSingleTradeCurrency);
        user.addPaymentAccount(cryptoCurrencyAccount);
        accountAgeWitnessService.publishMyAccountAgeWitness(cryptoCurrencyAccount.getPaymentAccountPayload());
        log.info("Saved crypto payment account with id {} and payment method {}.",
                cryptoCurrencyAccount.getId(),
                cryptoCurrencyAccount.getPaymentAccountPayload().getPaymentMethodId());
        return cryptoCurrencyAccount;
    }

    // TODO Support all alt coin payment methods supported by UI.
    //  The getCryptoCurrencyPaymentMethods method below will be
    //  callable from the CLI when more are supported.

    List<PaymentMethod> getCryptoCurrencyPaymentMethods() {
        return PaymentMethod.getPaymentMethods().stream()
                .filter(PaymentMethod::isAsset)
                .sorted(Comparator.comparing(PaymentMethod::getId))
                .collect(Collectors.toList());
    }

    private void verifyPaymentAccountHasRequiredFields(PaymentAccount paymentAccount) {
        // Do checks here to make sure required fields are populated.
        if (paymentAccount.isTransferwiseAccount() && paymentAccount.getTradeCurrencies().isEmpty())
            throw new IllegalArgumentException(format("no trade currencies defined for %s payment account",
                    paymentAccount.getPaymentMethod().getDisplayString().toLowerCase()));
    }
}
