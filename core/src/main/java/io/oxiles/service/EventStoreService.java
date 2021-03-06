package io.oxiles.service;

import io.oxiles.dto.event.ContractEventDetails;
import io.oxiles.dto.hcs.HCSMessageTransactionDetails;
import io.oxiles.model.LatestBlock;

import java.util.Optional;

/**
 * A service that interacts with the event store in order to retrieve data required by Eventeum.
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
public interface EventStoreService {

    /**
     * Returns the contract event with the latest block, that matches the event signature.
     *
     * @param eventSignature The event signature
     * @param contractAddress The event contract address
     * @return The event details
     */
    Optional<ContractEventDetails> getLatestContractEvent(String eventSignature, String contractAddress);

    /**
     * Returns the latest block, for the specified node.
     *
     * @param nodeName The nodename
     * @return The block details
     */
    Optional<LatestBlock> getLatestBlock(String nodeName);

    Optional<HCSMessageTransactionDetails> getLatestMessageFromTopic(String mirrorNode, String topicId);
}
