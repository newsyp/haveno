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

package haveno.cli.opts;


import joptsimple.OptionSpec;

import static haveno.cli.opts.OptLabel.OPT_NEW_WALLET_PASSWORD;
import static haveno.cli.opts.OptLabel.OPT_WALLET_PASSWORD;
import static joptsimple.internal.Strings.EMPTY;

public class SetWalletPasswordOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> passwordOpt = parser.accepts(OPT_WALLET_PASSWORD, "bisq wallet password")
            .withRequiredArg();

    final OptionSpec<String> newPasswordOpt = parser.accepts(OPT_NEW_WALLET_PASSWORD, "new bisq wallet password")
            .withOptionalArg()
            .defaultsTo(EMPTY);

    public SetWalletPasswordOptionParser(String[] args) {
        super(args);
    }

    public SetWalletPasswordOptionParser parse() {
        super.parse();

        // Short circuit opt validation if user just wants help.
        if (options.has(helpOpt))
            return this;

        if (!options.has(passwordOpt) || options.valueOf(passwordOpt).isEmpty())
            throw new IllegalArgumentException("no password specified");

        return this;
    }

    public String getPassword() {
        return options.valueOf(passwordOpt);
    }

    public String getNewPassword() {
        return options.has(newPasswordOpt) ? options.valueOf(newPasswordOpt) : "";
    }
}
