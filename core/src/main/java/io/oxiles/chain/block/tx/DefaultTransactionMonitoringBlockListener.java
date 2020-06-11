package io.oxiles.chain.block.tx;

import io.oxiles.chain.block.tx.criteria.TransactionMatchingCriteria;
import io.oxiles.chain.factory.TransactionDetailsFactory;
import io.oxiles.dto.transaction.TransactionDetails;
import io.oxiles.dto.transaction.TransactionStatus;
import io.oxiles.integration.broadcast.blockchain.BlockchainEventBroadcaster;
import lombok.extern.slf4j.Slf4j;
import io.oxiles.chain.service.BlockCache;
import io.oxiles.chain.service.BlockchainService;
import io.oxiles.chain.service.container.ChainServicesContainer;
import io.oxiles.chain.service.domain.Block;
import io.oxiles.chain.service.domain.Transaction;
import io.oxiles.chain.service.domain.TransactionReceipt;
import io.oxiles.chain.settings.Node;
import io.oxiles.chain.settings.NodeSettings;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
public class DefaultTransactionMonitoringBlockListener implements TransactionMonitoringBlockListener {

    //Keyed by node name
    private Map<String, List<TransactionMatchingCriteria>> criteria;

    //Keyed by node name
    private Map<String, BlockchainService> blockchainServices;

    private BlockchainEventBroadcaster broadcaster;

    private TransactionDetailsFactory transactionDetailsFactory;

    private BlockCache blockCache;

    private RetryTemplate retryTemplate;

    private Lock lock = new ReentrantLock();

    private NodeSettings nodeSettings;

    public DefaultTransactionMonitoringBlockListener(ChainServicesContainer chainServicesContainer,
                                                     BlockchainEventBroadcaster broadcaster,
                                                     TransactionDetailsFactory transactionDetailsFactory,
                                                     BlockCache blockCache,
                                                     NodeSettings nodeSettings) {
        this.criteria = new ConcurrentHashMap<>();

        this.blockchainServices = new HashMap<>();

        chainServicesContainer
                .getNodeNames()
                .forEach(nodeName -> {
                    blockchainServices.put(nodeName,
                            chainServicesContainer.getNodeServices(nodeName).getBlockchainService());
                });

        this.broadcaster = broadcaster;
        this.transactionDetailsFactory = transactionDetailsFactory;
        this.blockCache = blockCache;
        this.nodeSettings = nodeSettings;
    }

    @Override
    public void onBlock(Block block) {
        lock.lock();

        try {
            processBlock(block);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void addMatchingCriteria(TransactionMatchingCriteria matchingCriteria) {

        lock.lock();

        try {
            final String nodeName = matchingCriteria.getNodeName();

            if (!criteria.containsKey(nodeName)) {
                criteria.put(nodeName, new CopyOnWriteArrayList<>());
            }

            criteria.get(nodeName).add(matchingCriteria);

            //Check if any cached blocks match
            //Note, this makes sense for tx hash but maybe doesn't for some other matchers?
            blockCache
                    .getCachedBlocks()
                    .forEach(block -> {
                        block.getTransactions().forEach(tx ->
                                broadcastIfMatched(block, tx, nodeName, Collections.singletonList(matchingCriteria)));
                    });
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeMatchingCriteria(TransactionMatchingCriteria matchingCriteria) {
        criteria.get(matchingCriteria.getNodeName()).remove(matchingCriteria);
    }

    private void processBlock(Block block) {
        block.getTransactions()
                .forEach(tx -> broadcastIfMatched(block, tx, block.getNodeName()));
    }

    private void broadcastIfMatched(Block block, Transaction tx, String nodeName, List<TransactionMatchingCriteria> criteriaToCheck) {

        final TransactionDetails txDetails = transactionDetailsFactory.createTransactionDetails(
               block, tx, TransactionStatus.CONFIRMED);

        //Only broadcast once, even if multiple matching criteria apply
        criteriaToCheck
                .stream()
                .filter(matcher -> matcher.isAMatch(txDetails))
                .findFirst()
                .ifPresent(matcher -> onTransactionMatched(txDetails, matcher));
    }

    private void broadcastIfMatched(Block block, Transaction tx, String nodeName) {
        if (criteria.containsKey(nodeName)) {
            broadcastIfMatched(block, tx, nodeName, criteria.get(nodeName));
        }
    }

    private void onTransactionMatched(TransactionDetails txDetails, TransactionMatchingCriteria matchingCriteria) {

        final Node node = nodeSettings.getNode(txDetails.getNodeName());
        final BlockchainService blockchainService = getBlockchainService(txDetails.getNodeName());

        final boolean isSuccess = isSuccessTransaction(txDetails);

        if (isSuccess && shouldWaitBeforeConfirmation(node)) {
            txDetails.setStatus(TransactionStatus.UNCONFIRMED);

            blockchainService.addBlockListener(new TransactionConfirmationBlockListener(txDetails,
                    blockchainService, broadcaster,node,
                    matchingCriteria.getStatuses(),
                    () -> onConfirmed(txDetails, matchingCriteria)));

            broadcastTransaction(txDetails, matchingCriteria);

            //Don't remove criteria if we're waiting for x blocks, as if there is a fork
            //we need to rebroadcast the unconfirmed tx in new block
        } else {
            if (!isSuccess) {
                txDetails.setStatus(TransactionStatus.FAILED);

                String reason = getRevertReason(txDetails);

                if (reason != null) {
                    txDetails.setRevertReason(reason);
                }
            }

            broadcastTransaction(txDetails, matchingCriteria);

            if (matchingCriteria.isOneTimeMatch()) {
                removeMatchingCriteria(matchingCriteria);
            }
        }
    }

    private void broadcastTransaction(TransactionDetails txDetails, TransactionMatchingCriteria matchingCriteria) {
        if (matchingCriteria.getStatuses().contains(txDetails.getStatus())) {
            broadcaster.broadcastTransaction(txDetails);
        }
    }

    private boolean isSuccessTransaction(TransactionDetails txDetails) {
        final TransactionReceipt receipt = getBlockchainService(txDetails.getNodeName())
                .getTransactionReceipt(txDetails.getHash());

        if (receipt.getStatus() == null) {
            // status is only present on Byzantium transactions onwards
            return true;
        }

        if (receipt.getStatus().equals("0x0")) {
            return false;
        }

        return true;
    }

    private boolean shouldWaitBeforeConfirmation(Node node) {
        return !node.getBlocksToWaitForConfirmation().equals(BigInteger.ZERO);
    }

    private BlockchainService getBlockchainService(String nodeName) {
        return blockchainServices.get(nodeName);
    }

    private void onConfirmed(TransactionDetails txDetails, TransactionMatchingCriteria matchingCriteria) {
        if (matchingCriteria.isOneTimeMatch()) {
            log.debug("Tx {} confirmed, removing matchingCriteria", txDetails.getHash());

            removeMatchingCriteria(matchingCriteria);
        }
    }


    private String getRevertReason(TransactionDetails txDetails) {
        Node node = nodeSettings.getNode(txDetails.getNodeName());

        if (!node.getAddTransactionRevertReason()) {
            return null;
        }

        return getBlockchainService(txDetails.getNodeName()).getRevertReason(
                txDetails.getFrom(),
                txDetails.getTo(),
                Numeric.toBigInt(txDetails.getBlockNumber()),
                txDetails.getInput()
        );
    }
}
