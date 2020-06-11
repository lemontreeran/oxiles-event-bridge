package io.oxiles.chain.contract;

import io.oxiles.chain.block.BlockListener;
import io.oxiles.chain.block.EventConfirmationBlockListener;
import io.oxiles.chain.service.BlockchainService;
import io.oxiles.chain.settings.Node;
import io.oxiles.chain.settings.NodeSettings;
import io.oxiles.dto.event.ContractEventDetails;
import io.oxiles.dto.event.ContractEventStatus;
import io.oxiles.integration.broadcast.blockchain.BlockchainEventBroadcaster;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.oxiles.chain.service.container.ChainServicesContainer;
import io.oxiles.chain.service.domain.TransactionReceipt;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

/**
 * A contract event listener that initialises a block listener after being passed an unconfirmed event.
 *
 * This created block listener counts blocks since the event was first fired and broadcasts a CONFIRMED
 * event once the configured number of blocks have passed.
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
@Component
@AllArgsConstructor
@Slf4j
public class ConfirmationCheckInitialiser implements ContractEventListener {

    private ChainServicesContainer chainServicesContainer;
    private BlockchainEventBroadcaster eventBroadcaster;
    private NodeSettings nodeSettings;

    @Override
    public void onEvent(ContractEventDetails eventDetails) {
        if (eventDetails.getStatus() == ContractEventStatus.UNCONFIRMED) {

            final BlockchainService blockchainService = getBlockchainService(eventDetails);
            final Node node = nodeSettings.getNode(eventDetails.getNodeName());

            if (shouldInstantlyConfirm(eventDetails)) {
                eventDetails.setStatus(ContractEventStatus.CONFIRMED);
                eventBroadcaster.broadcastContractEvent(eventDetails);

                return;
            }

            log.info("Registering an EventConfirmationBlockListener for event: {}", eventDetails.getId());
            blockchainService.addBlockListener(createEventConfirmationBlockListener(eventDetails, node));
        }
    }

    protected BlockListener createEventConfirmationBlockListener(ContractEventDetails eventDetails, Node node) {
        return new EventConfirmationBlockListener(eventDetails,
                getBlockchainService(eventDetails), eventBroadcaster, node);
    }

    private BlockchainService getBlockchainService(ContractEventDetails eventDetails) {
        return chainServicesContainer.getNodeServices(
                eventDetails.getNodeName()).getBlockchainService();
    }

    private boolean shouldInstantlyConfirm(ContractEventDetails eventDetails) {
        final BlockchainService blockchainService = getBlockchainService(eventDetails);
        final Node node = nodeSettings.getNode(blockchainService.getNodeName());
        BigInteger currentBlock = blockchainService.getCurrentBlockNumber();
        BigInteger waitBlocks = node.getBlocksToWaitForConfirmation();

        return currentBlock.compareTo(eventDetails.getBlockNumber().add(waitBlocks)) >= 0
            && isTransactionStillInBlock(
                    eventDetails.getTransactionHash(), eventDetails.getBlockHash(), blockchainService);
    }

    private boolean isTransactionStillInBlock(String txHash, String blockHash, BlockchainService blockchainService) {
        final TransactionReceipt receipt = blockchainService.getTransactionReceipt(txHash);

        return receipt != null && receipt.getBlockHash().equals(blockHash);
    }
}
