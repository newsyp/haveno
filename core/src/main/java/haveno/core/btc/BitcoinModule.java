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

import haveno.core.btc.model.AddressEntryList;
import haveno.core.btc.model.XmrAddressEntryList;
import haveno.core.btc.nodes.BtcNodes;
import haveno.core.btc.setup.RegTestHost;
import haveno.core.btc.setup.WalletsSetup;
import haveno.core.btc.wallet.BsqCoinSelector;
import haveno.core.btc.wallet.BsqWalletService;
import haveno.core.btc.wallet.BtcWalletService;
import havenocore.btc.wallet.NonBsqCoinSelector;
import haveno.core.btc.wallet.TradeWalletService;
import haveno.core.btc.wallet.XmrWalletService;
import haveno.core.provider.ProvidersRepository;
import haveno.core.provider.fee.FeeProvider;
import haveno.core.provider.fee.FeeService;
import haveno.core.provider.price.PriceFeedService;

import haveno.common.app.AppModule;
import haveno.common.config.Config;

import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import java.io.File;

import java.util.Arrays;
import java.util.List;

import static haveno.common.config.Config.PROVIDERS;
import static haveno.common.config.Config.WALLET_DIR;
import static com.google.inject.name.Names.named;

public class BitcoinModule extends AppModule {

    public BitcoinModule(Config config) {
        super(config);
    }

    @Override
    protected void configure() {
        // If we have selected BTC_DAO_REGTEST or BTC_DAO_TESTNET we use our master regtest node,
        // otherwise the specified host or default (localhost)
        String regTestHost = config.bitcoinRegtestHost;
        if (regTestHost.isEmpty()) {
            regTestHost = config.baseCurrencyNetwork.isDaoTestNet() ?
                    "104.248.31.39" :
                    config.baseCurrencyNetwork.isDaoRegTest() ?
                            "134.209.242.206" :
                            Config.DEFAULT_REGTEST_HOST;
        }

        RegTestHost.HOST = regTestHost;
        if (Arrays.asList("localhost", "127.0.0.1").contains(regTestHost)) {
            bind(RegTestHost.class).toInstance(RegTestHost.LOCALHOST);
        } else if ("none".equals(regTestHost)) {
            bind(RegTestHost.class).toInstance(RegTestHost.NONE);
        } else {
            bind(RegTestHost.class).toInstance(RegTestHost.REMOTE_HOST);
        }

        bind(File.class).annotatedWith(named(WALLET_DIR)).toInstance(config.walletDir);

        bindConstant().annotatedWith(named(Config.BTC_NODES)).to(config.btcNodes);
        bindConstant().annotatedWith(named(Config.USER_AGENT)).to(config.userAgent);
        bindConstant().annotatedWith(named(Config.NUM_CONNECTIONS_FOR_BTC)).to(config.numConnectionsForBtc);
        bindConstant().annotatedWith(named(Config.USE_ALL_PROVIDED_NODES)).to(config.useAllProvidedNodes);
        bindConstant().annotatedWith(named(Config.IGNORE_LOCAL_BTC_NODE)).to(config.ignoreLocalBtcNode);
        bindConstant().annotatedWith(named(Config.SOCKS5_DISCOVER_MODE)).to(config.socks5DiscoverMode);
        bind(new TypeLiteral<List<String>>(){}).annotatedWith(named(PROVIDERS)).toInstance(config.providers);

        bind(AddressEntryList.class).in(Singleton.class);
        bind(XmrAddressEntryList.class).in(Singleton.class);
        bind(WalletsSetup.class).in(Singleton.class);
        bind(XmrWalletService.class).in(Singleton.class);
        bind(BtcWalletService.class).in(Singleton.class);
        bind(BsqWalletService.class).in(Singleton.class);
        bind(TradeWalletService.class).in(Singleton.class);
        bind(BsqCoinSelector.class).in(Singleton.class);
        bind(NonBsqCoinSelector.class).in(Singleton.class);
        bind(BtcNodes.class).in(Singleton.class);
        bind(Balances.class).in(Singleton.class);

        bind(ProvidersRepository.class).in(Singleton.class);
        bind(FeeProvider.class).in(Singleton.class);
        bind(PriceFeedService.class).in(Singleton.class);
        bind(FeeService.class).in(Singleton.class);
        bind(TxFeeEstimationService.class).in(Singleton.class);
    }
}

