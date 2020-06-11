package io.oxiles.chain.service.strategy;

import io.oxiles.testutils.DummyAsyncTaskService;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.PublishProcessor;
import io.oxiles.chain.block.BlockListener;
import io.oxiles.chain.service.domain.Block;
import io.oxiles.service.EventStoreService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.websocket.events.NewHead;
import org.web3j.protocol.websocket.events.NewHeadsNotification;
import org.web3j.protocol.websocket.events.NotificationParams;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class PubSubBlockchainSubscriptionStrategyTest {

    private static final String BLOCK_HASH = "0xc0e07697167c58f2a173df45f5c9b2c46ca0941cdf0bf79616d53dc92f62aebd";

    private static final BigInteger BLOCK_NUMBER = BigInteger.valueOf(123);

    private static final String BLOCK_NUMBER_HEX = "0x7B";

    //12345678
    private static final String BLOCK_TIMESTAMP = "0xbc614e";

    private static final String NODE_NAME = "mainnet";

    private PubSubBlockSubscriptionStrategy underTest;

    private PublishProcessor<NewHeadsNotification> blockPublishProcessor;

    private Web3j mockWeb3j;

    private NewHeadsNotification mockNewHeadsNotification;

    private NewHead mockNewHead;

    private EthBlock mockEthBlock;

    private BlockListener mockBlockListener;

    private EventStoreService mockEventStoreService;

    @Before
    public void init() throws IOException {
        this.mockWeb3j = mock(Web3j.class);

        mockNewHeadsNotification = mock(NewHeadsNotification.class);
        mockEventStoreService = mock(EventStoreService.class);
        when(mockNewHeadsNotification.getParams()).thenReturn(new NewHeadNotificationParameter());

        mockNewHead = mock(NewHead.class);
        when(mockNewHead.getHash()).thenReturn(BLOCK_HASH);

        blockPublishProcessor = PublishProcessor.create();
        when(mockWeb3j.newHeadsNotifications()).thenReturn(blockPublishProcessor);

        mockEthBlock = mock(EthBlock.class);
        final EthBlock.Block mockBlock = mock(EthBlock.Block.class);

        when(mockBlock.getNumber()).thenReturn(BLOCK_NUMBER);
        when(mockBlock.getHash()).thenReturn(BLOCK_HASH);
        when(mockBlock.getTimestamp()).thenReturn(Numeric.toBigInt(BLOCK_TIMESTAMP));
        when(mockEthBlock.getBlock()).thenReturn(mockBlock);

        final Request<?, EthBlock> mockRequest = mock(Request.class);
        doReturn(mockRequest).when(mockWeb3j).ethGetBlockByHash(BLOCK_HASH, true);

        when(mockRequest.send()).thenReturn(mockEthBlock);

        underTest = new PubSubBlockSubscriptionStrategy(mockWeb3j, NODE_NAME,
                mockEventStoreService, BigInteger.valueOf(0), new DummyAsyncTaskService());
    }

    @Test
    public void testSubscribe() {
        final Disposable returnedSubscription = underTest.subscribe();

        assertEquals(false, returnedSubscription.isDisposed());
    }

    @Test
    public void testUnsubscribe() {
        final Disposable returnedSubscription = underTest.subscribe();

        assertEquals(false, returnedSubscription.isDisposed());

        underTest.unsubscribe();

        assertEquals(true, returnedSubscription.isDisposed());
    }

    @Test
    public void testAddBlockListener() {
        underTest.subscribe();
        final Block block = doRegisterBlockListenerAndTrigger();
        assertNotNull(block);
    }

    @Test
    public void testRemoveBlockListener() {
        underTest.subscribe();
        final Block block = doRegisterBlockListenerAndTrigger();
        assertNotNull(block);

        reset(mockBlockListener);
        underTest.removeBlockListener(mockBlockListener);

        blockPublishProcessor.onNext(mockNewHeadsNotification);

        verify(mockBlockListener, never()).onBlock(any());
    }

    @Test
    public void testBlockHashPassedToListenerIsCorrect() {
        underTest.subscribe();
        final Block block = doRegisterBlockListenerAndTrigger();

        assertEquals(BLOCK_HASH, block.getHash());
    }

    @Test
    public void testBlockNumberPassedToListenerIsCorrect() {
        underTest.subscribe();
        final Block block = doRegisterBlockListenerAndTrigger();

        assertEquals(BLOCK_NUMBER, block.getNumber());
    }

    @Test
    public void testBlockTimestampPassedToListenerIsCorrect() {
        underTest.subscribe();
        final Block block = doRegisterBlockListenerAndTrigger();

        assertEquals(BigInteger.valueOf(12345678), block.getTimestamp());
    }

    @Test
    public void testBlockNodeNamePassedToListenerIsCorrect() {
        underTest.subscribe();
        final Block block = doRegisterBlockListenerAndTrigger();

        assertEquals(NODE_NAME, block.getNodeName());
    }

    private Block doRegisterBlockListenerAndTrigger() {

        mockBlockListener = mock(BlockListener.class);
        underTest.addBlockListener(mockBlockListener);

        blockPublishProcessor.onNext(mockNewHeadsNotification);

        final ArgumentCaptor<Block> captor = ArgumentCaptor.forClass(Block.class);
        verify(mockBlockListener).onBlock(captor.capture());

        return captor.getValue();
    }

    private class NewHeadNotificationParameter extends NotificationParams<NewHead> {
        @Override
        public NewHead getResult() {
            return mockNewHead;
        }
    }
}
