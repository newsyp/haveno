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

import haveno.proto.grpc.TxInfo;

import com.google.common.annotations.VisibleForTesting;

import static haveno.cli.ColumnHeaderConstants.*;
import static haveno.cli.CurrencyFormat.formatSatoshis;
import static com.google.common.base.Strings.padEnd;

@VisibleForTesting
public class TransactionFormat {

    public static String format(TxInfo txInfo) {
        String headerLine = padEnd(COL_HEADER_TX_ID, txInfo.getTxId().length(), ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_TX_IS_CONFIRMED + COL_HEADER_DELIMITER
                + COL_HEADER_TX_INPUT_SUM + COL_HEADER_DELIMITER
                + COL_HEADER_TX_OUTPUT_SUM + COL_HEADER_DELIMITER
                + COL_HEADER_TX_FEE + COL_HEADER_DELIMITER
                + COL_HEADER_TX_SIZE + COL_HEADER_DELIMITER
                + (txInfo.getMemo().isEmpty() ? "" : COL_HEADER_TX_MEMO + COL_HEADER_DELIMITER)
                + "\n";

        String colDataFormat = "%-" + txInfo.getTxId().length() + "s"
                + "  %" + COL_HEADER_TX_IS_CONFIRMED.length() + "s"
                + "  %" + COL_HEADER_TX_INPUT_SUM.length() + "s"
                + "  %" + COL_HEADER_TX_OUTPUT_SUM.length() + "s"
                + "  %" + COL_HEADER_TX_FEE.length() + "s"
                + "  %" + COL_HEADER_TX_SIZE.length() + "s"
                + "  %s";

        return headerLine
                + String.format(colDataFormat,
                txInfo.getTxId(),
                txInfo.getIsPending() ? "NO" : "YES", // pending=true means not confirmed
                formatSatoshis(txInfo.getInputSum()),
                formatSatoshis(txInfo.getOutputSum()),
                formatSatoshis(txInfo.getFee()),
                txInfo.getSize(),
                txInfo.getMemo().isEmpty() ? "" : txInfo.getMemo());
    }
}
