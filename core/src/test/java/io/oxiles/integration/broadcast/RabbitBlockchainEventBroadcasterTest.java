package io.oxiles.integration.broadcast;

import io.oxiles.dto.event.ContractEventDetails;
import io.oxiles.dto.event.ContractEventStatus;
import io.oxiles.dto.event.parameter.NumberParameter;
import io.oxiles.dto.event.parameter.StringParameter;
import io.oxiles.dto.transaction.TransactionDetails;
import io.oxiles.dto.transaction.TransactionStatus;
import io.oxiles.dto.block.BlockDetails;
import io.oxiles.dto.message.BlockEvent;
import io.oxiles.dto.message.ContractEvent;
import io.oxiles.dto.message.EventeumMessage;
import io.oxiles.dto.message.TransactionEvent;
import io.oxiles.integration.RabbitSettings;
import io.oxiles.integration.broadcast.blockchain.RabbitBlockChainEventBroadcaster;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

public class RabbitBlockchainEventBroadcasterTest {

    private static final String EVENT_EXCHANGE = "ThisIsAExchangeName";
    private static final String NEW_BLOCK_ROUTING_KEY = "newBlock";
    private static final String CONTRACT_EVENT_ROUTING_KEY = "contractEvent";
    private static final String TRANSACTION_EVENT_ROUTING_KEY = "transactionEvent";


    private RabbitBlockChainEventBroadcaster underTest;

    private RabbitTemplate rabbitTemplate;
    private RabbitSettings rabbitSettings;

    @Before
    public void init() {
        rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        rabbitSettings = Mockito.mock(RabbitSettings.class);

        Mockito.when(rabbitSettings.getExchange()).thenReturn(EVENT_EXCHANGE);
        Mockito.when(rabbitSettings.getBlockEventsRoutingKey()).thenReturn(NEW_BLOCK_ROUTING_KEY);
        Mockito.when(rabbitSettings.getContractEventsRoutingKey()).thenReturn(CONTRACT_EVENT_ROUTING_KEY);
        Mockito.when(rabbitSettings.getTransactionEventsRoutingKey()).thenReturn(TRANSACTION_EVENT_ROUTING_KEY);

        underTest = new RabbitBlockChainEventBroadcaster(rabbitTemplate, rabbitSettings);
    }

    @Test
    public void testBroadcastNewBlock() {

        final BlockDetails block = new BlockDetails();
        block.setHash("0xc2141b870536473fdea321893bc084eb3244cc56ea8d4b77de240dfeac6604d2");
        block.setNumber(BigInteger.TEN);

        underTest.broadcastNewBlock(block);

        final ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<EventeumMessage> messageCaptor = ArgumentCaptor.forClass(EventeumMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(EVENT_EXCHANGE), routingKeyCaptor.capture(), messageCaptor.capture());

        assertEquals(String.format("%s", NEW_BLOCK_ROUTING_KEY),routingKeyCaptor.getValue());
        assertEquals(BlockEvent.TYPE, messageCaptor.getValue().getType());
        assertEquals(block, messageCaptor.getValue().getDetails());
    }

    @Test
    public void testBroadcastContractEvent() {

        final ContractEventDetails event = createContractEventDetails();

        underTest.broadcastContractEvent(event);

        final ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<EventeumMessage> messageCaptor = ArgumentCaptor.forClass(EventeumMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(EVENT_EXCHANGE), routingKeyCaptor.capture(), messageCaptor.capture());

        assertEquals(String.format("%s.%s", CONTRACT_EVENT_ROUTING_KEY, event.getFilterId()),routingKeyCaptor.getValue());
        assertEquals(ContractEvent.TYPE, messageCaptor.getValue().getType());
        assertEquals(event, messageCaptor.getValue().getDetails());
    }

    @Test
    public void testBroadcastTransactionEvent() {

        final TransactionDetails event = createTransactionEvent();

        underTest.broadcastTransaction(event);

        final ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<EventeumMessage> messageCaptor = ArgumentCaptor.forClass(EventeumMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(EVENT_EXCHANGE), routingKeyCaptor.capture(), messageCaptor.capture());

        assertEquals(String.format("%s.%s", TRANSACTION_EVENT_ROUTING_KEY, event.getHash()),routingKeyCaptor.getValue());
        assertEquals(TransactionEvent.TYPE, messageCaptor.getValue().getType());
        assertEquals(event, messageCaptor.getValue().getDetails());
    }

    private TransactionDetails createTransactionEvent() {
        final TransactionDetails transactionEvent = new TransactionDetails();
        transactionEvent.setBlockNumber("10");
        transactionEvent.setStatus(TransactionStatus.FAILED);
        transactionEvent.setBlockHash("0xc2141b870536473fdea321893bc084eb3244cc56ea8d4b77de240dfeac6604d2");
        transactionEvent.setHash("0xc2141b870536473fdea321893bc084eb3244cc56ea8d4b77de240dfeac6604d2");
        transactionEvent.setTransactionIndex("0x4744d9c8c368be18d010832bf19cc5f35fe0d3f5f800fec20f9f1ca10a1820f7");
        transactionEvent.setFrom("0xf0a6c84894ed7312a75ff0e621cde2f8a1c62d6f");
        transactionEvent.setTo("0xf0a6c84894ed7312a75ff0e621cde2f8a1c62d6f");

        return transactionEvent;
    }

    private ContractEventDetails createContractEventDetails() {
        final ContractEventDetails contractEvent = new ContractEventDetails();
        contractEvent.setBlockNumber(BigInteger.TEN);
        contractEvent.setStatus(ContractEventStatus.CONFIRMED);
        contractEvent.setFilterId(UUID.randomUUID().toString());
        contractEvent.setBlockHash("0xc2141b870536473fdea321893bc084eb3244cc56ea8d4b77de240dfeac6604d2");
        contractEvent.setLogIndex(BigInteger.ONE);
        contractEvent.setTransactionHash("0x4744d9c8c368be18d010832bf19cc5f35fe0d3f5f800fec20f9f1ca10a1820f7");
        contractEvent.setName("AnEvent");
        contractEvent.setAddress("0xf0a6c84894ed7312a75ff0e621cde2f8a1c62d6f");
        contractEvent.setEventSpecificationSignature("somesig");
        contractEvent.setIndexedParameters(Arrays.asList
                (new StringParameter("bytes32", "1234"), new NumberParameter("uint256", BigInteger.valueOf(123))));
        contractEvent.setNonIndexedParameters(Arrays.asList
                (new StringParameter("string", "5678"), new NumberParameter("uint256", BigInteger.valueOf(456))));

        return contractEvent;
    }
}
