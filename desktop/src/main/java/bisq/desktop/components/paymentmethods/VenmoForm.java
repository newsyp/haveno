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

package bisq.desktop.components.paymentmethods;

import bisq.desktop.components.InputTextField;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.VenmoValidator;

import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.VenmoAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.VenmoAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import static bisq.desktop.util.FormBuilder.addLabelTextField;
import static bisq.desktop.util.FormBuilder.addLabelTextFieldWithCopyIcon;

// Removed due too high chargeback risk
@Deprecated
public class VenmoForm extends PaymentMethodForm {
    private final VenmoAccount account;
    private final VenmoValidator validator;
    private InputTextField accountIdInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                ((VenmoAccountPayload) paymentAccountPayload).getHolderName());
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.venmo.venmoUserName"), ((VenmoAccountPayload) paymentAccountPayload).getVenmoUserName());
        return gridRow;
    }

    public VenmoForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, VenmoValidator venmoValidator, InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.account = (VenmoAccount) paymentAccount;
        this.validator = venmoValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow,
                Res.getWithCol("payment.account.owner"));
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            account.setHolderName(newValue);
            updateFromInputs();
        });

        accountIdInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.venmo.venmoUserName"));
        accountIdInputTextField.setValidator(validator);
        accountIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            account.setVenmoUserName(newValue);
            updateFromInputs();
        });

        final TradeCurrency singleTradeCurrency = account.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameToggleButton != null && !useCustomAccountNameToggleButton.isSelected()) {
            String accountNr = accountIdInputTextField.getText();
            accountNr = StringUtils.abbreviate(accountNr, 9);
            String method = Res.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(accountNr));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), account.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"), Res.get(account.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                account.getHolderName());
        TextField field = addLabelTextField(gridPane, ++gridRow, Res.get("payment.venmo.venmoUserName"), account.getVenmoUserName()).second;
        field.setMouseTransparent(false);
        final TradeCurrency singleTradeCurrency = account.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && inputValidator.validate(account.getHolderName()).isValid
                && validator.validate(account.getVenmoUserName()).isValid
                && account.getTradeCurrencies().size() > 0);
    }
}
