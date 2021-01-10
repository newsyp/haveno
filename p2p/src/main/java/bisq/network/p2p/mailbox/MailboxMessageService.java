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

package bisq.network.p2p.mailbox;

import bisq.network.crypto.EncryptionService;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NetworkNotReadyException;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.PrefixedSealedAndSignedMessage;
import bisq.network.p2p.SendMailboxMessageListener;
import bisq.network.p2p.messaging.DecryptedMailboxListener;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.SetupListener;
import bisq.network.p2p.peers.BroadcastHandler;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.getdata.RequestDataManager;
import bisq.network.p2p.seed.SeedNodeRepository;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.messages.AddDataMessage;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.UserThread;
import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.SealedAndSigned;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
@Slf4j
public class MailboxMessageService implements SetupListener, RequestDataManager.Listener, HashMapChangedListener,
        PersistedDataHost {
    private final SeedNodeRepository seedNodeRepository;
    private final EncryptionService encryptionService;
    private final IgnoredMailboxService ignoredMailboxService;
    private final PersistenceManager<MailboxMessageList> persistenceManager;
    private final KeyRing keyRing;
    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final P2PDataStorage p2PDataStorage;
    private final RequestDataManager requestDataManager;
    private final Set<DecryptedMailboxListener> decryptedMailboxListeners = new CopyOnWriteArraySet<>();
    private final MailboxMessageList mailboxMessageList = new MailboxMessageList();
    private final Map<String, List<MailboxItem>> mailboxItemsByUid = new HashMap<>();
    private boolean isBootstrapped;

    @Inject
    public MailboxMessageService(NetworkNode networkNode,
                                 PeerManager peerManager,
                                 P2PDataStorage p2PDataStorage,
                                 RequestDataManager requestDataManager,
                                 SeedNodeRepository seedNodeRepository,
                                 EncryptionService encryptionService,
                                 IgnoredMailboxService ignoredMailboxService,
                                 PersistenceManager<MailboxMessageList> persistenceManager,
                                 KeyRing keyRing) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.p2PDataStorage = p2PDataStorage;
        this.requestDataManager = requestDataManager;
        this.seedNodeRepository = seedNodeRepository;
        this.encryptionService = encryptionService;
        this.ignoredMailboxService = ignoredMailboxService;
        this.persistenceManager = persistenceManager;
        this.keyRing = keyRing;

        this.requestDataManager.addListener(this);

        this.persistenceManager.initialize(mailboxMessageList, PersistenceManager.Source.PRIVATE);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted(Runnable completeHandler) {
        persistenceManager.readPersisted(persisted -> {
                    persisted.forEach(mailboxItem -> {
                        DecryptedMessageWithPubKey decryptedMessageWithPubKey = mailboxItem.getDecryptedMessageWithPubKey();
                        MailboxMessage mailboxMessage = (MailboxMessage) decryptedMessageWithPubKey.getNetworkEnvelope();
                        String uid = mailboxMessage.getUid();
                        mailboxItemsByUid.putIfAbsent(uid, new ArrayList<>());
                        mailboxItemsByUid.get(uid).add(mailboxItem);
                        mailboxMessageList.add(mailboxItem);
                    });

                    completeHandler.run();
                },
                completeHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendEncryptedMailboxMessage(NodeAddress peer,
                                            PubKeyRing peersPubKeyRing,
                                            NetworkEnvelope message,
                                            SendMailboxMessageListener sendMailboxMessageListener) {
        if (peersPubKeyRing == null) {
            log.error("sendEncryptedMailboxMessage: peersPubKeyRing is null. We ignore the call.");
            return;
        }

        checkNotNull(peer, "PeerAddress must not be null (sendEncryptedMailboxMessage)");
        checkNotNull(networkNode.getNodeAddress(),
                "My node address must not be null at sendEncryptedMailboxMessage");
        checkArgument(!keyRing.getPubKeyRing().equals(peersPubKeyRing), "We got own keyring instead of that from peer");

        if (!isBootstrapped)
            throw new NetworkNotReadyException();

        if (networkNode.getAllConnections().isEmpty()) {
            sendMailboxMessageListener.onFault("There are no P2P network nodes connected. " +
                    "Please check your internet connection.");
            return;
        }

        if (capabilityRequiredAndCapabilityNotSupported(peer, message)) {
            sendMailboxMessageListener.onFault("We did not send the EncryptedMailboxMessage " +
                    "because the peer does not support the capability.");
            return;
        }

        try {
            log.debug("\n\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n" +
                    "Encrypt message:\nmessage={}"
                    + "\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n", message);

            PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = getPrefixedSealedAndSignedMessage(peer,
                    peersPubKeyRing, message);

            log.debug("sendEncryptedMailboxMessage msg={},  peersNodeAddress={}", message, peer);
            SettableFuture<Connection> future = networkNode.sendMessage(peer, prefixedSealedAndSignedMessage);
            Futures.addCallback(future, new FutureCallback<>() {
                @Override
                public void onSuccess(@Nullable Connection connection) {
                    sendMailboxMessageListener.onArrived();
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    PublicKey receiverStoragePublicKey = peersPubKeyRing.getSignaturePubKey();
                    addMailboxData(new MailboxStoragePayload(prefixedSealedAndSignedMessage,
                                    keyRing.getSignatureKeyPair().getPublic(),
                                    receiverStoragePublicKey),
                            receiverStoragePublicKey,
                            sendMailboxMessageListener);
                }
            }, MoreExecutors.directExecutor());
        } catch (CryptoException e) {
            log.error("sendEncryptedMessage failed");
            e.printStackTrace();
            sendMailboxMessageListener.onFault("sendEncryptedMailboxMessage failed " + e);
        }
    }

    public void removeMailboxMsg(DecryptedMessageWithPubKey decryptedMessageWithPubKey) {
        if (isBootstrapped) {
            // We need to delay a bit to not get a ConcurrentModificationException as we might iterate over
            // mailboxItemsByUid while getting called.
            UserThread.execute(() -> {
                MailboxMessage mailboxMessage = (MailboxMessage) decryptedMessageWithPubKey.getNetworkEnvelope();
                String uid = mailboxMessage.getUid();
                if (mailboxItemsByUid.containsKey(uid)) {
                    List<MailboxItem> list = mailboxItemsByUid.get(uid);
                    // In case we have not been bootstrapped when we tried to remove the message at the time when we
                    // received the message, we remove it now.
                    list.forEach(mailboxItem -> {
                        removeMailboxEntryFromNetwork(mailboxItem.getProtectedMailboxStorageEntry());
                        mailboxMessageList.remove(mailboxItem);
                    });
                    mailboxItemsByUid.remove(uid);
                    requestPersistence();
                }
            });
        } else {
            // In case the network was not ready yet we try again later
            UserThread.runAfter(() -> removeMailboxMsg(decryptedMessageWithPubKey), 30);
        }
    }

    public Set<DecryptedMessageWithPubKey> getMyMailBoxMessages() {
        return mailboxItemsByUid.values().stream()
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .map(MailboxItem::getDecryptedMessageWithPubKey)
                .collect(Collectors.toSet());
    }

    public void addDecryptedMailboxListener(DecryptedMailboxListener listener) {
        decryptedMailboxListeners.add(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SetupListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onTorNodeReady() {
        boolean seedNodesAvailable = requestDataManager.requestPreliminaryData();
        if (!seedNodesAvailable) {
            isBootstrapped = true;
            // As we do not expect a updated data request response we start here with addHashMapChangedListenerAndApply
            addHashMapChangedListenerAndApply();
        }
    }

    @Override
    public void onHiddenServicePublished() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // RequestDataManager.Listener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPreliminaryDataReceived() {
    }

    @Override
    public void onUpdatedDataReceived() {
        if (!isBootstrapped) {
            isBootstrapped = true;
            // Only now we start listening and processing. The p2PDataStorage is our cache for data we have received
            // after the hidden service was ready.
            addHashMapChangedListenerAndApply();
        }
    }

    @Override
    public void onDataReceived() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener implementation for ProtectedStorageEntry items
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(Collection<ProtectedStorageEntry> protectedStorageEntries) {
        Collection<ProtectedMailboxStorageEntry> entries = protectedStorageEntries.stream()
                .filter(e -> e instanceof ProtectedMailboxStorageEntry)
                .map(e -> (ProtectedMailboxStorageEntry) e)
                .filter(e -> networkNode.getNodeAddress() != null)
                .filter(e -> !seedNodeRepository.isSeedNode(networkNode.getNodeAddress())) // Seed nodes don't expect mailbox messages
                .collect(Collectors.toSet());
        if (entries.size() > 1) {
            threadedBatchProcessMailboxEntries(entries);
        } else if (entries.size() == 1) {
            processSingleMailboxEntry(entries);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addHashMapChangedListenerAndApply() {
        p2PDataStorage.addHashMapChangedListener(this);
        onAdded(p2PDataStorage.getMap().values());
    }

    private void processSingleMailboxEntry(Collection<ProtectedMailboxStorageEntry> protectedMailboxStorageEntries) {
        checkArgument(protectedMailboxStorageEntries.size() == 1);
        var decryptedEntries = new ArrayList<>(getDecryptedEntries(protectedMailboxStorageEntries));
        if (decryptedEntries.size() == 1) {
            processMailboxItem(decryptedEntries.get(0));
        }
    }

    // We run the batch processing of all mailbox messages we have received at startup in a thread to not block the UI.
    // For about 1000 messages decryption takes about 1 sec.
    private void threadedBatchProcessMailboxEntries(Collection<ProtectedMailboxStorageEntry> protectedMailboxStorageEntries) {
        ListeningExecutorService executor = Utilities.getSingleThreadListeningExecutor("processMailboxEntry-" + new Random().nextInt(1000));
        long ts = System.currentTimeMillis();
        ListenableFuture<Set<MailboxItem>> future = executor.submit(() -> {
            var decryptedEntries = getDecryptedEntries(protectedMailboxStorageEntries);
            log.info("Batch processing of {} mailbox entries took {} ms",
                    protectedMailboxStorageEntries.size(),
                    System.currentTimeMillis() - ts);
            return decryptedEntries;
        });

        Futures.addCallback(future, new FutureCallback<>() {
            public void onSuccess(Set<MailboxItem> decryptedMailboxMessageWithEntries) {
                UserThread.execute(() -> decryptedMailboxMessageWithEntries.forEach(e -> processMailboxItem(e)));
            }

            public void onFailure(@NotNull Throwable throwable) {
                log.error(throwable.toString());
            }
        }, MoreExecutors.directExecutor());
    }

    private Set<MailboxItem> getDecryptedEntries(Collection<ProtectedMailboxStorageEntry> protectedMailboxStorageEntries) {
        Set<MailboxItem> decryptedMailboxMessageWithEntries = new HashSet<>();
        protectedMailboxStorageEntries.stream()
                .map(this::decryptProtectedMailboxStorageEntry)
                .filter(Objects::nonNull)
                .forEach(decryptedMailboxMessageWithEntries::add);
        return decryptedMailboxMessageWithEntries;
    }

    @Nullable
    private MailboxItem decryptProtectedMailboxStorageEntry(ProtectedMailboxStorageEntry protectedMailboxStorageEntry) {
        PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = protectedMailboxStorageEntry
                .getMailboxStoragePayload()
                .getPrefixedSealedAndSignedMessage();
        SealedAndSigned sealedAndSigned = prefixedSealedAndSignedMessage.getSealedAndSigned();
        String uid = prefixedSealedAndSignedMessage.getUid();
        if (ignoredMailboxService.isIgnored(uid)) {
            // We had persisted a past failed decryption attempt on that message so we don't try again and return early
            return null;
        }
        try {
            DecryptedMessageWithPubKey decryptedMessageWithPubKey = encryptionService.decryptAndVerify(sealedAndSigned);
            checkArgument(decryptedMessageWithPubKey.getNetworkEnvelope() instanceof MailboxMessage);
            return new MailboxItem(protectedMailboxStorageEntry, decryptedMessageWithPubKey);
        } catch (CryptoException ignore) {
            // Expected if message was not intended for us
            // We persist those entries so at the next startup we do not need to try to decrypt it anymore
            ignoredMailboxService.ignore(uid, protectedMailboxStorageEntry.getCreationTimeStamp());
        } catch (ProtobufferException e) {
            log.error(e.toString());
            e.getStackTrace();
        }
        return null;
    }

    private void processMailboxItem(MailboxItem mailboxItem) {
        DecryptedMessageWithPubKey decryptedMessageWithPubKey = mailboxItem.getDecryptedMessageWithPubKey();
        MailboxMessage mailboxMessage = (MailboxMessage) decryptedMessageWithPubKey.getNetworkEnvelope();
        String uid = mailboxMessage.getUid();
        mailboxItemsByUid.putIfAbsent(uid, new ArrayList<>());
        mailboxItemsByUid.get(uid).add(mailboxItem);
        mailboxMessageList.add(mailboxItem);
        requestPersistence();

        NodeAddress sender = mailboxMessage.getSenderNodeAddress();
        log.info("Received a {} mailbox message with uid {} and senderAddress {}",
                mailboxMessage.getClass().getSimpleName(), uid, sender);
        decryptedMailboxListeners.forEach(e -> e.onMailboxMessageAdded(decryptedMessageWithPubKey, sender));

        if (isBootstrapped) {
            // After we notified our listeners we remove the data immediately from the network.
            // In case the client has not been ready it need to take it via getMailBoxMessages.
            removeMailboxEntryFromNetwork(mailboxItem.getProtectedMailboxStorageEntry());
        } else {
            log.info("We are not bootstrapped yet, so we remove later once the message got processed.");
        }
    }

    private void addMailboxData(MailboxStoragePayload expirableMailboxStoragePayload,
                                PublicKey receiversPublicKey,
                                SendMailboxMessageListener sendMailboxMessageListener) {
        if (isBootstrapped) {
            if (!networkNode.getAllConnections().isEmpty()) {
                try {
                    ProtectedMailboxStorageEntry protectedMailboxStorageEntry = p2PDataStorage.getMailboxDataWithSignedSeqNr(
                            expirableMailboxStoragePayload,
                            keyRing.getSignatureKeyPair(),
                            receiversPublicKey);

                    BroadcastHandler.Listener listener = new BroadcastHandler.Listener() {
                        @Override
                        public void onSufficientlyBroadcast(List<Broadcaster.BroadcastRequest> broadcastRequests) {
                            broadcastRequests.stream()
                                    .filter(broadcastRequest -> broadcastRequest.getMessage() instanceof AddDataMessage)
                                    .filter(broadcastRequest -> {
                                        AddDataMessage addDataMessage = (AddDataMessage) broadcastRequest.getMessage();
                                        return addDataMessage.getProtectedStorageEntry().equals(protectedMailboxStorageEntry);
                                    })
                                    .forEach(e -> sendMailboxMessageListener.onStoredInMailbox());
                        }

                        @Override
                        public void onNotSufficientlyBroadcast(int numOfCompletedBroadcasts, int numOfFailedBroadcast) {
                            sendMailboxMessageListener.onFault("Message was not sufficiently broadcast.\n" +
                                    "numOfCompletedBroadcasts: " + numOfCompletedBroadcasts + ".\n" +
                                    "numOfFailedBroadcast=" + numOfFailedBroadcast);
                        }
                    };
                    boolean result = p2PDataStorage.addProtectedStorageEntry(protectedMailboxStorageEntry, networkNode.getNodeAddress(), listener);
                    if (!result) {
                        sendMailboxMessageListener.onFault("Data already exists in our local database");

                        // This should only fail if there are concurrent calls to addProtectedStorageEntry with the
                        // same ProtectedMailboxStorageEntry. This is an unexpected use case so if it happens we
                        // want to see it, but it is not worth throwing an exception.
                        log.error("Unexpected state: adding mailbox message that already exists.");
                    }
                } catch (CryptoException e) {
                    log.error("Signing at getMailboxDataWithSignedSeqNr failed.");
                }
            } else {
                sendMailboxMessageListener.onFault("There are no P2P network nodes connected. " +
                        "Please check your internet connection.");
            }
        } else {
            throw new NetworkNotReadyException();
        }
    }

    private void removeMailboxEntryFromNetwork(ProtectedMailboxStorageEntry protectedMailboxStorageEntry) {
        MailboxStoragePayload mailboxStoragePayload = (MailboxStoragePayload) protectedMailboxStorageEntry.getProtectedStoragePayload();
        PublicKey receiversPubKey = protectedMailboxStorageEntry.getReceiversPubKey();
        try {
            ProtectedMailboxStorageEntry updatedEntry = p2PDataStorage.getMailboxDataWithSignedSeqNr(
                    mailboxStoragePayload,
                    keyRing.getSignatureKeyPair(),
                    receiversPubKey);

            P2PDataStorage.ByteArray hashOfPayload = P2PDataStorage.get32ByteHashAsByteArray(mailboxStoragePayload);
            if (p2PDataStorage.getMap().containsKey(hashOfPayload)) {
                boolean result = p2PDataStorage.remove(updatedEntry, networkNode.getNodeAddress());
                if (result) {
                    log.info("Removed mailboxEntry from network");
                } else {
                    log.warn("Removing mailboxEntry from network failed");
                }
            } else {
                log.info("The mailboxEntry was already removed earlier.");
            }
        } catch (CryptoException e) {
            e.printStackTrace();
            log.error("Could not remove ProtectedMailboxStorageEntry from network. Error: {}", e.toString());
        }
    }

    private void requestPersistence() {
        persistenceManager.requestPersistence();
    }

    // TODO avoid code duplication
    private PrefixedSealedAndSignedMessage getPrefixedSealedAndSignedMessage(NodeAddress peersNodeAddress,
                                                                             PubKeyRing pubKeyRing,
                                                                             NetworkEnvelope message) throws CryptoException {
        byte[] addressPrefixHash;
        if (peerManager.peerHasCapability(peersNodeAddress, Capability.NO_ADDRESS_PRE_FIX)) {
            // The peer has an updated version so we do not need to send the prefix.
            // We cannot use null as not updated nodes would get a nullPointer at protobuf serialisation.
            addressPrefixHash = new byte[0];
        } else {
            addressPrefixHash = peersNodeAddress.getAddressPrefixHash();
        }
        return new PrefixedSealedAndSignedMessage(
                networkNode.getNodeAddress(),
                encryptionService.encryptAndSign(pubKeyRing, message),
                addressPrefixHash,
                UUID.randomUUID().toString());
    }

    // TODO avoid code duplication
    private boolean capabilityRequiredAndCapabilityNotSupported(NodeAddress peersNodeAddress, NetworkEnvelope message) {
        if (!(message instanceof CapabilityRequiringPayload))
            return false;

        // We might have multiple entries of the same peer without the supportedCapabilities field set if we received
        // it from old versions, so we filter those.
        Optional<Capabilities> optionalCapabilities = peerManager.findPeersCapabilities(peersNodeAddress);
        if (optionalCapabilities.isPresent()) {
            boolean result = optionalCapabilities.get().containsAll(((CapabilityRequiringPayload) message).getRequiredCapabilities());

            if (!result)
                log.warn("We don't send the message because the peer does not support the required capability. " +
                        "peersNodeAddress={}", peersNodeAddress);

            return !result;
        }

        log.warn("We don't have the peer in our persisted peers so we don't know their capabilities. " +
                "We decide to not sent the msg. peersNodeAddress={}", peersNodeAddress);
        return true;

    }
}
