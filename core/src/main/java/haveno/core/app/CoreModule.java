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

package haveno.core.app;

import haveno.core.alert.AlertModule;
import haveno.core.btc.BitcoinModule;
import haveno.core.dao.DaoModule;
import haveno.core.filter.FilterModule;
import haveno.core.network.CoreNetworkFilter;
import haveno.core.network.p2p.seed.DefaultSeedNodeRepository;
import haveno.core.offer.OfferModule;
import haveno.core.presentation.CorePresentationModule;
import haveno.core.proto.network.CoreNetworkProtoResolver;
import haveno.core.proto.persistable.CorePersistenceProtoResolver;
import haveno.core.trade.TradeModule;
import haveno.core.user.Preferences;
import haveno.core.util.FormattingUtils;
import haveno.core.util.coin.CoinFormatter;
import haveno.core.util.coin.ImmutableCoinFormatter;

import haveno.network.crypto.EncryptionServiceModule;
import haveno.network.p2p.P2PModule;
import haveno.network.p2p.network.BridgeAddressProvider;
import haveno.network.p2p.network.NetworkFilter;
import haveno.network.p2p.seed.SeedNodeRepository;

import haveno.common.app.AppModule;
import haveno.common.config.Config;
import haveno.common.crypto.PubKeyRing;
import haveno.common.crypto.PubKeyRingProvider;
import haveno.common.proto.network.NetworkProtoResolver;
import haveno.common.proto.persistable.PersistenceProtoResolver;

import com.google.inject.Singleton;

import java.io.File;

import static haveno.common.config.Config.*;
import static com.google.inject.name.Names.named;

public class CoreModule extends AppModule {

    public CoreModule(Config config) {
        super(config);
    }

    @Override
    protected void configure() {
        bind(Config.class).toInstance(config);

        bind(BridgeAddressProvider.class).to(Preferences.class);

        bind(SeedNodeRepository.class).to(DefaultSeedNodeRepository.class);
        bind(NetworkFilter.class).to(CoreNetworkFilter.class).in(Singleton.class);

        bind(File.class).annotatedWith(named(STORAGE_DIR)).toInstance(config.storageDir);

        CoinFormatter btcFormatter = new ImmutableCoinFormatter(config.networkParameters.getMonetaryFormat());
        bind(CoinFormatter.class).annotatedWith(named(FormattingUtils.BTC_FORMATTER_KEY)).toInstance(btcFormatter);

        bind(File.class).annotatedWith(named(KEY_STORAGE_DIR)).toInstance(config.keyStorageDir);

        bind(NetworkProtoResolver.class).to(CoreNetworkProtoResolver.class);
        bind(PersistenceProtoResolver.class).to(CorePersistenceProtoResolver.class);

        bindConstant().annotatedWith(named(USE_DEV_PRIVILEGE_KEYS)).to(config.useDevPrivilegeKeys);
        bindConstant().annotatedWith(named(USE_DEV_MODE)).to(config.useDevMode);
        bindConstant().annotatedWith(named(USE_DEV_MODE_HEADER)).to(config.useDevModeHeader);
        bindConstant().annotatedWith(named(REFERRAL_ID)).to(config.referralId);

        // ordering is used for shut down sequence
        install(new TradeModule(config));
        install(new EncryptionServiceModule(config));
        install(new OfferModule(config));
        install(new P2PModule(config));
        install(new BitcoinModule(config));
        install(new DaoModule(config));
        install(new AlertModule(config));
        install(new FilterModule(config));
        install(new CorePresentationModule(config));
        bind(PubKeyRing.class).toProvider(PubKeyRingProvider.class);
    }
}
