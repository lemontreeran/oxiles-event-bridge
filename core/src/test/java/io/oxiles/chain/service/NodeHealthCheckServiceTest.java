package io.oxiles.chain.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.oxiles.service.SubscriptionService;
import io.oxiles.chain.service.health.NodeHealthCheckService;
import io.oxiles.chain.service.health.strategy.ReconnectionStrategy;
import io.oxiles.constant.Constants;
import io.oxiles.model.LatestBlock;
import io.oxiles.monitoring.EventeumValueMonitor;
import io.oxiles.monitoring.MicrometerValueMonitor;
import io.oxiles.service.EventStoreService;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;

public class NodeHealthCheckServiceTest {

    private static final BigInteger BLOCK_NUMBER = BigInteger.valueOf(1234);

    private static final Integer SYNCING_THRESHOLD = Integer.valueOf(60);

    private static final Long HEALTH_CHECK_INTERVAL = 1000l;

    private NodeHealthCheckService underTest;

    private BlockchainService mockBlockchainService;

    private ReconnectionStrategy mockReconnectionStrategy;

    private SubscriptionService mockSubscriptionService;

    private EventeumValueMonitor mockEventeumValueMonitor;

    private EventStoreService mockEventStoreService;

    private ScheduledThreadPoolExecutor  mockTaskScheduler;

    @Before
    public void init() throws Exception {
        mockBlockchainService = mock(BlockchainService.class);
        when(mockBlockchainService.getNodeName()).thenReturn(Constants.DEFAULT_NODE_NAME);

        mockReconnectionStrategy = mock(ReconnectionStrategy.class);
        mockSubscriptionService = mock(SubscriptionService.class);

        mockEventStoreService = mock(EventStoreService.class);
        LatestBlock latestBlock = new LatestBlock();
        latestBlock.setNumber(BLOCK_NUMBER);
        when(mockEventStoreService.getLatestBlock(any())).thenReturn(Optional.of(latestBlock));
        mockEventeumValueMonitor = new MicrometerValueMonitor(new SimpleMeterRegistry());
        mockTaskScheduler = mock(ScheduledThreadPoolExecutor.class);

        underTest = createUnderTest();

    }

    @Test
    public void testEverythingUpHappyPath() {
        wireBlockchainServiceUp(true);
        underTest.checkHealth();

        verify(mockReconnectionStrategy, never()).reconnect();
        verify(mockReconnectionStrategy, never()).resubscribe();
    }

    @Test
    public void testNodeDisconnectedReconnectSuccess() {
        wireBlockchainServiceDown(false, false);
        wireReconnectResult(true);
        underTest.checkHealth();

        verify(mockReconnectionStrategy, times(1)).reconnect();
        verify(mockReconnectionStrategy, times(1)).resubscribe();
        verify(mockSubscriptionService, times(1)).unsubscribeToAllSubscriptions(Constants.DEFAULT_NODE_NAME);
    }

    @Test
    public void testNodeDisconnectedReconnectFailure() {
        wireBlockchainServiceDown(false, false);
        wireReconnectResult(false);
        underTest.checkHealth();

        verify(mockReconnectionStrategy, times(1)).reconnect();
        verify(mockReconnectionStrategy, never()).resubscribe();
        verify(mockSubscriptionService, times(1)).unsubscribeToAllSubscriptions(Constants.DEFAULT_NODE_NAME);
    }

    @Test
    public void testNodeStaysDown() {
        wireBlockchainServiceDown(false, false);
        underTest.checkHealth();

        verify(mockReconnectionStrategy, times(1)).reconnect();
        verify(mockSubscriptionService, times(1)).unsubscribeToAllSubscriptions(Constants.DEFAULT_NODE_NAME);

        underTest.checkHealth();

        verify(mockReconnectionStrategy, times(2)).reconnect();
        verify(mockReconnectionStrategy, never()).resubscribe();
        verify(mockSubscriptionService, times(1)).unsubscribeToAllSubscriptions(Constants.DEFAULT_NODE_NAME);
    }


    @Test
    public void testNodeComesBackUpNotSubscribed() {
        wireBlockchainServiceDown(false, false);
        underTest.checkHealth();

        verify(mockReconnectionStrategy, times(1)).reconnect();
        verify(mockReconnectionStrategy, never()).resubscribe();
        verify(mockSubscriptionService, times(1)).unsubscribeToAllSubscriptions(Constants.DEFAULT_NODE_NAME);

        reset(mockBlockchainService);
        wireBlockchainServiceUp(false);
        underTest.checkHealth();

        verify(mockReconnectionStrategy, times(1)).reconnect();
        verify(mockReconnectionStrategy, times(1)).resubscribe();
    }

    @Test
    public void testNodeComesBackUpAndStaysUp() {
        wireBlockchainServiceDown(false, false);
        underTest.checkHealth();

        verify(mockReconnectionStrategy, times(1)).reconnect();
        verify(mockReconnectionStrategy, never()).resubscribe();
        verify(mockSubscriptionService, times(1)).unsubscribeToAllSubscriptions(Constants.DEFAULT_NODE_NAME);

        reset(mockBlockchainService);
        reset(mockSubscriptionService);
        wireBlockchainServiceUp(true);
        underTest.checkHealth();

        verify(mockReconnectionStrategy, times(1)).reconnect();
        verify(mockReconnectionStrategy, times(1)).resubscribe();
        verify(mockSubscriptionService, never()).unsubscribeToAllSubscriptions(Constants.DEFAULT_NODE_NAME);

        underTest.checkHealth();

        verify(mockReconnectionStrategy, times(1)).reconnect();
        verify(mockReconnectionStrategy, times(1)).resubscribe();
        verify(mockSubscriptionService, never()).unsubscribeToAllSubscriptions(Constants.DEFAULT_NODE_NAME);
    }

    @Test
    public void testUnsubscribeOnlyOccursFirsTime() {
        wireBlockchainServiceDown(false, false);

        underTest.checkHealth();
        verify(mockSubscriptionService, times(1)).unsubscribeToAllSubscriptions(Constants.DEFAULT_NODE_NAME);

        reset(mockSubscriptionService);

        underTest.checkHealth();
        verify(mockSubscriptionService, never()).unsubscribeToAllSubscriptions(Constants.DEFAULT_NODE_NAME);
    }

    private void wireBlockchainServiceUp(boolean isSubscribed) {
        when(mockBlockchainService.getCurrentBlockNumber()).thenReturn(BLOCK_NUMBER);
        when(mockBlockchainService.isConnected()).thenReturn(isSubscribed);
        when(mockBlockchainService.getNodeName()).thenReturn(Constants.DEFAULT_NODE_NAME);
        when(mockSubscriptionService.isFullySubscribed(Constants.DEFAULT_NODE_NAME)).thenReturn(isSubscribed);
    }

    private void wireBlockchainServiceDown(boolean isConnected, boolean isSubscribed) {

        when(mockBlockchainService.isConnected()).thenReturn(isSubscribed);
        if (isConnected) {
            when(mockBlockchainService.getCurrentBlockNumber()).thenReturn(BLOCK_NUMBER);
        } else {
            when(mockBlockchainService.getCurrentBlockNumber()).thenThrow(
                    new BlockchainException("Error!", new IOException("")));
        }
    }

    private AtomicBoolean isConnected = new AtomicBoolean(false);

    private void wireReconnectResult(boolean reconnectSuccess) {
        isConnected.set(false);

        doAnswer((invocation) -> {
            if (reconnectSuccess) {
                isConnected.set(true);
            } else {
                isConnected.set(false);
            }
            return null;
        }).when(mockReconnectionStrategy).reconnect();

        doAnswer((invocation) -> {
            if (isConnected.get()) {
                return BLOCK_NUMBER;
            } else {
                throw new BlockchainException("Error!", new IOException(""));
            }
        }).when(mockBlockchainService).getCurrentBlockNumber();
    }

    private NodeHealthCheckService createUnderTest() throws Exception {
        final NodeHealthCheckService healthCheckService =
                new NodeHealthCheckService(
                        mockBlockchainService,
                        mockReconnectionStrategy,
                        mockSubscriptionService,
                        mockEventeumValueMonitor,
                        mockEventStoreService,
                        SYNCING_THRESHOLD,
                        mockTaskScheduler,
                        HEALTH_CHECK_INTERVAL
                );

        Field initiallySubscribed = NodeHealthCheckService.class.getDeclaredField("initiallySubscribed");
        initiallySubscribed.setAccessible(true);
        initiallySubscribed.set(healthCheckService, true);

        return healthCheckService;
    }
}
