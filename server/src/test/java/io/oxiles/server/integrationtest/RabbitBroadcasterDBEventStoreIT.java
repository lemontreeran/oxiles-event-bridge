package io.oxiles.server.integrationtest;

import io.oxiles.dto.block.BlockDetails;
import io.oxiles.dto.event.ContractEventDetails;
import io.oxiles.dto.message.EventeumMessage;
import io.oxiles.dto.transaction.TransactionDetails;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.annotation.PostConstruct;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(locations = "classpath:application-test-db-rabbit.properties")
public class RabbitBroadcasterDBEventStoreIT extends BroadcasterSmokeTest {

    public static FixedHostPortGenericContainer rabbitContainer;

    private boolean isFirstTest = true;

    @Before
    public void waitForRabbitInit() throws InterruptedException {
        //TODO Figure out how to verify when rabbitMQ has started we we don't have to sleep
        if (isFirstTest) {
            Thread.sleep(10000);
        }

        isFirstTest = false;
    }

    @RabbitListener(bindings = @QueueBinding(
            key = "contractEvents.*",
            value = @Queue("contractEvents"),
            exchange = @Exchange(value = "ThisIsAExchange", type = ExchangeTypes.TOPIC)
    ))
    public void onContractEvent(EventeumMessage message) {
        if(message.getDetails() instanceof ContractEventDetails){
            onContractEventMessageReceived((ContractEventDetails) message.getDetails());
        } else if(message.getDetails() instanceof TransactionDetails){
            onTransactionMessageReceived((TransactionDetails) message.getDetails());
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            key = "transactionEvents.*",
            value = @Queue("transactionEvents"),
            exchange = @Exchange(value = "ThisIsAExchange", type = ExchangeTypes.TOPIC)
    ))
    public void onTransactionEvent(EventeumMessage message) {
        onTransactionMessageReceived((TransactionDetails) message.getDetails());
    }

    @RabbitListener(bindings = @QueueBinding(
            key = "blockEvents",
            value = @Queue("blockEvents"),
            exchange = @Exchange(value = "ThisIsAExchange", type = ExchangeTypes.TOPIC)
    ))
    public void onBlockEvent(EventeumMessage message) {
        onBlockMessageReceived((BlockDetails) message.getDetails());
    }

    @TestConfiguration
    static class RabbitConfig {

        @PostConstruct
        void initRabbit() {
            if (RabbitBroadcasterDBEventStoreIT.rabbitContainer == null) {
                RabbitBroadcasterDBEventStoreIT.rabbitContainer = new FixedHostPortGenericContainer("rabbitmq:3.6.14-management");
                RabbitBroadcasterDBEventStoreIT.rabbitContainer.waitingFor(Wait.forListeningPort());
                RabbitBroadcasterDBEventStoreIT.rabbitContainer.withFixedExposedPort(5672, 5672);
                RabbitBroadcasterDBEventStoreIT.rabbitContainer.start();
            }

        }
    }
}
