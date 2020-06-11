package io.oxiles.chain.service;

import io.oxiles.chain.service.container.NodeServices;
import io.oxiles.dto.event.ContractEventDetails;
import io.oxiles.chain.service.container.ChainServicesContainer;
import io.oxiles.chain.settings.Node;
import io.oxiles.chain.settings.NodeSettings;
import io.oxiles.constant.Constants;
import io.oxiles.dto.event.filter.ContractEventFilter;
import io.oxiles.dto.event.filter.ContractEventSpecification;
import io.oxiles.dto.event.filter.ParameterDefinition;
import io.oxiles.dto.event.filter.ParameterType;
import io.oxiles.service.EventStoreService;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultEventBlockManagementServiceTest {

    private static final String EVENT_SPEC_HASH = "0x4d44a3f7bbdc3ab16cf28ad5234f38b7464ff912da473754ab39f0f97692eded";

    private static final String CONTRACT_ADDRESS = "0xd94a9d6733a64cecdcc8ca01da72554b4d883a47";

    private static final ContractEventSpecification EVENT_SPEC;

    private static final ContractEventFilter EVENT_FILTER;

    private DefaultEventBlockManagementService underTest;

    private BlockchainService mockBlockchainService;

    private EventStoreService mockEventStoreService;

    private NodeServices mockNodeServices;

    private ChainServicesContainer mockChainServicesContainer;

    private NodeSettings mockNodeSettings;

    static {
        EVENT_SPEC = new ContractEventSpecification();
        EVENT_SPEC.setEventName("AnEvent");
        EVENT_SPEC.setIndexedParameterDefinitions(Arrays.asList(
                new ParameterDefinition(0, ParameterType.build("ADDRESS")),
                new ParameterDefinition(1, ParameterType.build("UINT256"))));

        EVENT_FILTER = new ContractEventFilter();
        EVENT_FILTER.setNode(Constants.DEFAULT_NODE_NAME);
        EVENT_FILTER.setEventSpecification(EVENT_SPEC);
        EVENT_FILTER.setContractAddress(CONTRACT_ADDRESS);

        EVENT_SPEC.setNonIndexedParameterDefinitions(
                Arrays.asList(new ParameterDefinition(2, ParameterType.build("BYTES32"))));
    }

    @Before
    public void init() {
        mockNodeServices = mock(NodeServices.class);
        mockChainServicesContainer = mock(ChainServicesContainer.class);
        mockBlockchainService = mock(BlockchainService.class);
        mockEventStoreService = mock(EventStoreService.class);
        mockNodeSettings = mock(NodeSettings.class);

        when(mockChainServicesContainer.getNodeServices(Constants.DEFAULT_NODE_NAME))
                .thenReturn(mockNodeServices);
        when(mockNodeServices.getBlockchainService()).thenReturn(mockBlockchainService);
        when(mockBlockchainService.getCurrentBlockNumber()).thenReturn(BigInteger.valueOf(1000));

        Node node = new Node();

        node.setSyncStartBlock(BigInteger.valueOf(Long.valueOf(NodeSettings.DEFAULT_SYNC_START_BLOCK)));
        node.setMaxUnsyncedBlocksForFilter(BigInteger.valueOf(7200));

        when(mockNodeSettings.getNode(Constants.DEFAULT_NODE_NAME)).thenReturn(node);

        underTest = new DefaultEventBlockManagementService(mockChainServicesContainer, mockEventStoreService, mockNodeSettings);
    }

    @Test
    public void testUpdateAndGetNoMatch() {
        underTest.updateLatestBlock(EVENT_SPEC_HASH, BigInteger.TEN, CONTRACT_ADDRESS);
        final BigInteger result = underTest.getLatestBlockForEvent(EVENT_FILTER);

        assertEquals(BigInteger.TEN.add(BigInteger.ONE), result);
    }

    @Test
    public void testUpdateAndGetLowerMatch() {
        underTest.updateLatestBlock(EVENT_SPEC_HASH, BigInteger.ONE, CONTRACT_ADDRESS);
        underTest.updateLatestBlock(EVENT_SPEC_HASH, BigInteger.TEN, CONTRACT_ADDRESS);
        final BigInteger result = underTest.getLatestBlockForEvent(EVENT_FILTER);

        assertEquals(BigInteger.TEN.add(BigInteger.ONE), result);
    }

    @Test
    public void testUpdateAndGetHigherMatch() {
        underTest.updateLatestBlock(EVENT_SPEC_HASH, BigInteger.TEN, CONTRACT_ADDRESS);
        underTest.updateLatestBlock(EVENT_SPEC_HASH, BigInteger.ONE, CONTRACT_ADDRESS);
        final BigInteger result = underTest.getLatestBlockForEvent(EVENT_FILTER);

        assertEquals(BigInteger.TEN.add(BigInteger.ONE), result);
    }

    @Test
    public void testGetNoLocalMatchButHitInEventStore() {
        final ContractEventDetails mockEventDetails = mock(ContractEventDetails.class);
        when(mockEventDetails.getBlockNumber()).thenReturn(BigInteger.ONE);
        when(mockEventStoreService.getLatestContractEvent(EVENT_SPEC_HASH, CONTRACT_ADDRESS)).thenReturn(Optional.of(mockEventDetails));

        final BigInteger result = underTest.getLatestBlockForEvent(EVENT_FILTER);

        assertEquals(BigInteger.ONE.add(BigInteger.ONE), result);
    }

    @Test
    public void testGetNoLocalMatchAndNoHitInEventStore() {
        when(mockEventStoreService.getLatestContractEvent(EVENT_SPEC_HASH, CONTRACT_ADDRESS)).thenReturn(null);
        when(mockBlockchainService.getCurrentBlockNumber()).thenReturn(BigInteger.valueOf(20));
        when(mockEventStoreService.getLatestContractEvent(EVENT_SPEC_HASH, CONTRACT_ADDRESS)).thenReturn(Optional.empty());

        final BigInteger result = underTest.getLatestBlockForEvent(EVENT_FILTER);

        assertEquals(BigInteger.valueOf(20), result);
    }
}
