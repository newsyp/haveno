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

package haveno.core.app.misc;

import haveno.core.account.sign.SignedWitnessService;
import haveno.core.account.witness.AccountAgeWitnessService;
import haveno.core.dao.DaoSetup;
import haveno.core.dao.governance.ballot.BallotListService;
import haveno.core.dao.governance.blindvote.MyBlindVoteListService;
import haveno.core.dao.governance.bond.reputation.MyReputationListService;
import haveno.core.dao.governance.myvote.MyVoteListService;
import haveno.core.dao.governance.proofofburn.MyProofOfBurnListService;
import haveno.core.dao.governance.proposal.MyProposalListService;
import haveno.core.filter.FilterManager;
import haveno.core.trade.statistics.TradeStatisticsManager;

import haveno.network.p2p.P2PService;
import haveno.network.p2p.peers.PeerManager;
import haveno.network.p2p.storage.P2PDataStorage;

import haveno.common.config.Config;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AppSetupWithP2PAndDAO extends AppSetupWithP2P {
    private final DaoSetup daoSetup;

    @Inject
    public AppSetupWithP2PAndDAO(P2PService p2PService,
                                 P2PDataStorage p2PDataStorage,
                                 PeerManager peerManager,
                                 TradeStatisticsManager tradeStatisticsManager,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 SignedWitnessService signedWitnessService,
                                 FilterManager filterManager,
                                 DaoSetup daoSetup,
                                 MyVoteListService myVoteListService,
                                 BallotListService ballotListService,
                                 MyBlindVoteListService myBlindVoteListService,
                                 MyProposalListService myProposalListService,
                                 MyReputationListService myReputationListService,
                                 MyProofOfBurnListService myProofOfBurnListService,
                                 Config config) {
        super(p2PService,
                p2PDataStorage,
                peerManager,
                tradeStatisticsManager,
                accountAgeWitnessService,
                signedWitnessService,
                filterManager,
                config);

        this.daoSetup = daoSetup;

        // TODO Should be refactored/removed. In the meantime keep in sync with CorePersistedDataHost
        if (config.daoActivated) {
            persistedDataHosts.add(myVoteListService);
            persistedDataHosts.add(ballotListService);
            persistedDataHosts.add(myBlindVoteListService);
            persistedDataHosts.add(myProposalListService);
            persistedDataHosts.add(myReputationListService);
            persistedDataHosts.add(myProofOfBurnListService);
        }
    }

    @Override
    protected void onBasicServicesInitialized() {
        super.onBasicServicesInitialized();

        daoSetup.onAllServicesInitialized(log::error, log::warn);
    }
}
