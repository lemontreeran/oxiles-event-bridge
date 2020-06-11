package io.oxiles.chain.contract;

import io.oxiles.chain.service.container.NodeServices;
import io.oxiles.dto.event.ContractEventDetails;
import io.oxiles.dto.event.ContractEventStatus;
import io.oxiles.testutils.DummyAsyncTaskService;
import io.oxiles.chain.block.BlockListener;
import io.oxiles.chain.service.BlockchainService;
import io.oxiles.chain.service.container.ChainServicesContainer;
import io.oxiles.chain.service.domain.TransactionReceipt;
import io.oxiles.chain.settings.Node;
import io.oxiles.chain.settings.NodeSettings;
import io.oxiles.constant.Constants;
import io.oxiles.integration.broadcast.blockchain.BlockchainEventBroadcaster;
import io.oxiles.service.AsyncTaskService;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static org.mockito.Mockito.*;

public class ConfirmationCheckInitialiserTest {

     private static final String TX_HASH = "0x05ba7cdf9f35579c9e2332804a3a98bf2231572e8bfe57b3e31ed0240ae7f582";
     private static final String BLOCK_HASH = "0xb9f2b107229b1f49547a7d0d446d018adef30b83ae8a69738c2f38375b28f4dc";

     private ConfirmationCheckInitialiser underTest;

     private BlockchainService mockBlockchainService;
     private BlockListener mockBlockListener;
     private ChainServicesContainer mockChainServicesContainer;
     private NodeServices mockNodeServices;
     private NodeSettings mockNodeSettings;
     private AsyncTaskService asyncTaskService = new DummyAsyncTaskService();
     private BigInteger currentBlock = BigInteger.valueOf(2000);

     @Before
     public void init() {

         mockBlockchainService = mock(BlockchainService.class);
         mockBlockListener = mock(BlockListener.class);
         mockChainServicesContainer = mock(ChainServicesContainer.class);
         mockNodeServices = mock(NodeServices.class);
         mockNodeSettings = mock(NodeSettings.class);

         when(mockChainServicesContainer.getNodeServices(Constants.DEFAULT_NODE_NAME))
                 .thenReturn(mockNodeServices);

         when(mockNodeServices.getBlockchainService()).thenReturn(mockBlockchainService);
         when(mockBlockchainService.getCurrentBlockNumber()).thenReturn(currentBlock);
         Node node =
                 new Node();
         node.setBlocksToWaitForConfirmation(BigInteger.valueOf(10));
         node.setBlocksToWaitForMissingTx(BigInteger.valueOf(100));
         node.setBlocksToWaitBeforeInvalidating(BigInteger.valueOf(5));
         when(mockNodeSettings.getNode(any())).thenReturn(node);

         underTest = new ConfirmationCheckInitialiserForTest(mockChainServicesContainer,
                 mock(BlockchainEventBroadcaster.class), mockNodeSettings);
     }

     @Test
         public void testOnEventNotInvalidated() {
            ContractEventDetails event = createContractEventDetails(ContractEventStatus.UNCONFIRMED);
             when(event.getBlockNumber()).thenReturn(currentBlock);
             underTest.onEvent(event);

             verify(mockBlockchainService, times(1)).addBlockListener(mockBlockListener);
         }

    @Test
    public void testOnEventInvalidated() {
        underTest.onEvent(createContractEventDetails(ContractEventStatus.INVALIDATED));

        verify(mockBlockchainService, never()).addBlockListener(mockBlockListener);
    }

    @Test
    public void testOnEventWithAExpiredBlockEvent() {
        ContractEventDetails event = createContractEventDetails(ContractEventStatus.UNCONFIRMED);
        when(event.getBlockNumber()).thenReturn(BigInteger.valueOf(1));

        final TransactionReceipt mockTxReceipt = mock(TransactionReceipt.class);
        when(mockTxReceipt.getBlockHash()).thenReturn(BLOCK_HASH);

        when(mockBlockchainService.getTransactionReceipt(TX_HASH)).thenReturn(mockTxReceipt);
        underTest.onEvent(event);

        verify(mockBlockchainService, times(0)).addBlockListener(mockBlockListener);
    }

     private ContractEventDetails createContractEventDetails(ContractEventStatus status) {
         final ContractEventDetails eventDetails = mock(ContractEventDetails.class);

         when(eventDetails.getStatus()).thenReturn(status);
         when(eventDetails.getNodeName()).thenReturn(Constants.DEFAULT_NODE_NAME);
         when(eventDetails.getBlockNumber()).thenReturn(BigInteger.valueOf(0));
         when(eventDetails.getBlockHash()).thenReturn(BLOCK_HASH);
         when(eventDetails.getTransactionHash()).thenReturn(TX_HASH);

         return eventDetails;
     }

     private class ConfirmationCheckInitialiserForTest extends ConfirmationCheckInitialiser {

         public ConfirmationCheckInitialiserForTest(ChainServicesContainer chainServicesContainer,
                                                    BlockchainEventBroadcaster eventBroadcaster,
                                                    NodeSettings node) {
             super(chainServicesContainer, eventBroadcaster, node);
         }

         @Override
         protected BlockListener createEventConfirmationBlockListener(ContractEventDetails eventDetails,Node node) {
             return mockBlockListener;
         }
     }
}
