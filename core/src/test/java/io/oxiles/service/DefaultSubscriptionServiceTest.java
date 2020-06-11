package io.oxiles.service;

import java.util.Collections;

import io.oxiles.chain.service.container.NodeServices;
import io.oxiles.testutils.DummyAsyncTaskService;
import io.reactivex.disposables.Disposable;
import io.oxiles.chain.contract.ContractEventListener;
import io.oxiles.chain.service.container.ChainServicesContainer;
import io.oxiles.chain.settings.NodeType;
import io.oxiles.constant.Constants;
import io.oxiles.dto.event.filter.ContractEventFilter;
import io.oxiles.dto.event.filter.ContractEventSpecification;
import io.oxiles.dto.event.filter.ParameterDefinition;
import io.oxiles.dto.event.filter.ParameterType;
import io.oxiles.integration.broadcast.internal.EventeumEventBroadcaster;
import io.oxiles.model.FilterSubscription;
import io.oxiles.repository.ContractEventFilterRepository;
import io.oxiles.service.exception.NotFoundException;
import io.oxiles.chain.block.BlockListener;
import io.oxiles.chain.service.BlockchainService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.retry.support.RetryTemplate;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DefaultSubscriptionServiceTest {
    private static final String FILTER_ID = "123-456";

    private static final String EVENT_NAME = "DummyEvent";

    private static final String CONTRACT_ADDRESS = "0x7a55a28856d43bba3c6a7e36f2cee9a82923e99b";

    private static ContractEventSpecification eventSpec;

    private DefaultSubscriptionService underTest;

    @Mock
    private ChainServicesContainer mockChainServicesContainer;
    @Mock
    private NodeServices mockNodeServices;
    @Mock
    private BlockchainService mockBlockchainService;
    @Mock
    private ContractEventFilterRepository mockRepo;
    @Mock
    private EventeumEventBroadcaster mockFilterBroadcaster;
    @Mock
    private BlockListener mockBlockListener1;
    @Mock
    private BlockListener mockBlockListener2;
    @Mock
    private ContractEventListener mockEventListener1;
    @Mock
    private ContractEventListener mockEventListener2;

    private RetryTemplate mockRetryTemplate;

    static {
        eventSpec = new ContractEventSpecification();
        eventSpec.setEventName(EVENT_NAME);

        eventSpec.setIndexedParameterDefinitions(Arrays.asList(
                new ParameterDefinition(0, ParameterType.build("UINT256"))));

        eventSpec.setNonIndexedParameterDefinitions(
                Arrays.asList(new ParameterDefinition(1, ParameterType.build("UINT256")),
                        new ParameterDefinition(2, ParameterType.build("ADDRESS"))));
    }

    @Before
    public void init() {
        when(mockChainServicesContainer.getNodeServices(
                Constants.DEFAULT_NODE_NAME)).thenReturn(mockNodeServices);
        when(mockChainServicesContainer.getNodeNames()).thenReturn(
                Collections.singletonList(Constants.DEFAULT_NODE_NAME));
        when(mockNodeServices.getBlockchainService()).thenReturn(mockBlockchainService);
        when(mockNodeServices.getNodeType()).thenReturn(NodeType.NORMAL);

        mockRetryTemplate = new RetryTemplate();

        underTest = new DefaultSubscriptionService(mockChainServicesContainer,
                mockRepo, mockFilterBroadcaster, new DummyAsyncTaskService(),
                Arrays.asList(mockBlockListener1, mockBlockListener2),
                Arrays.asList(mockEventListener1, mockEventListener2),mockRetryTemplate);
    }

    @Test
    public void testSubscribeToNewBlocksOnInit() {

        underTest.init();

        verify(mockBlockchainService, times(1)).addBlockListener(mockBlockListener1);
        verify(mockBlockchainService, times(1)).addBlockListener(mockBlockListener2);
    }

    @Test
    public void testRegisterNewContractEventFilter() {
        final ContractEventFilter filter = createEventFilter();
        underTest.registerContractEventFilter(filter, true);

        verifyContractEventFilterRegistration(filter,true, true);
        assertEquals(1, underTest.listContractEventFilters().size());
    }

    @Test
    public void testRegisterNewContractEventFilterBroadcastFalse() {
        final ContractEventFilter filter = createEventFilter();

        underTest.registerContractEventFilter(filter, false);

        verifyContractEventFilterRegistration(filter,true, false);
        assertEquals(1, underTest.listContractEventFilters().size());
    }

    @Test
    public void testRegisterNewContractEventFilterAlreadyRegistered() {
        final ContractEventFilter filter = createEventFilter();
        underTest.registerContractEventFilter(filter, true);
        underTest.registerContractEventFilter(filter, true);

        verifyContractEventFilterRegistration(filter,true, true);
        assertEquals(1, underTest.listContractEventFilters().size());
    }

    @Test
    public void testRegisterNewContractEventFilterAutoGenerateId() {
        final ContractEventFilter filter = createEventFilter(null, Constants.DEFAULT_NODE_NAME);

        when(mockBlockchainService.registerEventListener(any(ContractEventFilter.class), any(ContractEventListener.class)))
                .thenReturn(new FilterSubscription(filter, mock(Disposable.class)));

        underTest.registerContractEventFilter(filter, true);

        assertTrue(!filter.getId().isEmpty());
        assertEquals(1, underTest.listContractEventFilters().size());
    }

    @Test
    public void testListContractEventFilterAlreadyRegistered() {
        final ContractEventFilter filter1 = createEventFilter(null, Constants.DEFAULT_NODE_NAME);
        when(mockBlockchainService.registerEventListener(any(ContractEventFilter.class), any(ContractEventListener.class)))
	   .thenReturn(new FilterSubscription(filter1, mock(Disposable.class)));

        underTest.registerContractEventFilter(filter1, true);
        underTest.registerContractEventFilter(filter1, true);

        assertEquals(1, underTest.listContractEventFilters().size());
    }

    @Test
    public void testResubscribeToAllSubscriptions() {
        final ContractEventFilter filter1 = createEventFilter(FILTER_ID, Constants.DEFAULT_NODE_NAME);
        final ContractEventFilter filter2 = createEventFilter("AnotherId", Constants.DEFAULT_NODE_NAME);
        final Disposable sub1 = mock(Disposable.class);
        final Disposable sub2 = mock(Disposable.class);

        when(mockBlockchainService.registerEventListener(
                eq(filter1), any(ContractEventListener.class))).thenReturn(new FilterSubscription(filter1, sub1));
        when(mockBlockchainService.registerEventListener(
                eq(filter2), any(ContractEventListener.class))).thenReturn(new FilterSubscription(filter2, sub2));

        //Add 2 filters
        underTest.registerContractEventFilter(filter1, true);
        underTest.registerContractEventFilter(filter2, true);

        reset(mockBlockchainService, mockRepo, mockFilterBroadcaster);

        when(mockBlockchainService.registerEventListener(
                eq(filter1), any(ContractEventListener.class))).thenReturn(new FilterSubscription(filter1, sub1));
        when(mockBlockchainService.registerEventListener(
                eq(filter2), any(ContractEventListener.class))).thenReturn(new FilterSubscription(filter2, sub2));

        underTest.resubscribeToAllSubscriptions(Constants.DEFAULT_NODE_NAME);

        verifyContractEventFilterRegistration(filter1, false, false);
        verifyContractEventFilterRegistration(filter2, false, false);
    }

    @Test
    public void testUnnregisterContractEventFilter() throws NotFoundException {
        final ContractEventFilter filter = createEventFilter();
        final Disposable sub1 = mock(Disposable.class);

        when(mockBlockchainService.registerEventListener(
                eq(filter), any(ContractEventListener.class))).thenReturn(new FilterSubscription(filter, sub1));

        underTest.registerContractEventFilter(filter, false);

        underTest.unregisterContractEventFilter(FILTER_ID);

        verify(sub1, times(1)).dispose();
        verify(mockRepo, times(1)).deleteById(FILTER_ID);
        verify(mockFilterBroadcaster, times(1)).broadcastEventFilterRemoved(filter);
        assertEquals(0, underTest.listContractEventFilters().size());

        boolean exceptionThrown = false;
        //This will test that the filter has been deleted from memory
        try {
            underTest.unregisterContractEventFilter(FILTER_ID);
        } catch (NotFoundException e) {
            //Expected
            exceptionThrown = true;
        }

        assertEquals(true, exceptionThrown);
    }

    @Test
    public void testUnsubscribeToAllSubscriptions() {
        final ContractEventFilter filter1 = createEventFilter("filter1", Constants.DEFAULT_NODE_NAME);
        final Disposable sub1 = mock(Disposable.class);

        final ContractEventFilter filter2 = createEventFilter();
        final Disposable sub2 = mock(Disposable.class);

        when(mockBlockchainService.registerEventListener(
                eq(filter1), any(ContractEventListener.class))).thenReturn(new FilterSubscription(filter1, sub1));
        when(mockBlockchainService.registerEventListener(
                eq(filter2), any(ContractEventListener.class))).thenReturn(new FilterSubscription(filter2, sub2));

        underTest.registerContractEventFilter(filter1, false);
        underTest.registerContractEventFilter(filter2, false);
        underTest.unsubscribeToAllSubscriptions(Constants.DEFAULT_NODE_NAME);

        verify(sub1, times(1)).dispose();
        verify(sub2, times(1)).dispose();
    }

    private void verifyContractEventFilterRegistration(ContractEventFilter filter, boolean save, boolean broadcast) {
        verify(mockBlockchainService, times(1)).registerEventListener(eq(filter), any(ContractEventListener.class));

        int expectedSaveInvocations = save ? 1 : 0;
        verify(mockRepo, times(expectedSaveInvocations)).save(filter);

        int expectedBroadcastInvocations = broadcast ? 1 : 0;
        verify(mockFilterBroadcaster, times(expectedBroadcastInvocations)).broadcastEventFilterAdded(filter);
    }

    private ContractEventFilter createEventFilter(String id, String nodeName) {
        final ContractEventFilter filter = new ContractEventFilter();

        filter.setId(id);
        filter.setContractAddress(CONTRACT_ADDRESS);
        filter.setEventSpecification(eventSpec);
        filter.setNode(nodeName);

        return filter;
    }

    private ContractEventFilter createEventFilter() {
        final ContractEventFilter filter = createEventFilter(FILTER_ID, Constants.DEFAULT_NODE_NAME);

        when(mockBlockchainService.registerEventListener(eq(filter), any(ContractEventListener.class)))
                .thenReturn(new FilterSubscription(filter, mock(Disposable.class)));

        return filter;
    }

}
