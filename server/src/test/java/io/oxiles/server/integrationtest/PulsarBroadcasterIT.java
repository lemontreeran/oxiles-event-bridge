package io.oxiles.server.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.oxiles.server.integrationtest.container.PulsarContainer;
import io.oxiles.dto.block.BlockDetails;
import io.oxiles.dto.event.ContractEventDetails;
import io.oxiles.dto.transaction.TransactionDetails;
import io.oxiles.integration.PulsarSettings;
import org.apache.pulsar.client.api.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(locations = "classpath:application-test-db-pulsar.properties")
public class PulsarBroadcasterIT extends BroadcasterSmokeTest {

    private static PulsarContainer pulsarContainer;

    private PulsarClient client;

    @Autowired
    private PulsarSettings settings;

    private BackgroundPulsarConsumer<BlockDetails> blockBackgroundConsumer;

    private BackgroundPulsarConsumer<ContractEventDetails> eventBackgroundConsumer;

    private BackgroundPulsarConsumer<TransactionDetails> transactionBackgroundConsumer;

    @BeforeClass
    public static void setup() {
        pulsarContainer = new PulsarContainer();
        pulsarContainer.start();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.setProperty("PULSAR_URL", pulsarContainer.getPlainTextServiceUrl());
    }

    @AfterClass
    public static void tearDown() {
        pulsarContainer.stop();

        System.clearProperty("PULSAR_URL");
    }

    @Before
    public void configureConsumer() throws PulsarClientException {
        client = PulsarClient.builder()
                .serviceUrl(pulsarContainer.getPlainTextServiceUrl())
                .build();

        blockBackgroundConsumer = new BackgroundPulsarConsumer<>(
                createConsumer(settings.getTopic().getBlockEvents()), BlockDetails.class);
        blockBackgroundConsumer.start(block -> onBlockMessageReceived(block));

        eventBackgroundConsumer = new BackgroundPulsarConsumer<>(
                createConsumer(settings.getTopic().getContractEvents()), ContractEventDetails.class);
        eventBackgroundConsumer.start(event -> onContractEventMessageReceived(event));

        transactionBackgroundConsumer = new BackgroundPulsarConsumer<>(
                createConsumer(settings.getTopic().getTransactionEvents()), TransactionDetails.class);
        transactionBackgroundConsumer.start(event -> onTransactionMessageReceived(event));
    }

    @After
    public void teardownConsumers() throws PulsarClientException {
        blockBackgroundConsumer.stop();
        eventBackgroundConsumer.stop();
        client.close();
    }

    private Consumer<byte[]> createConsumer(String topic) throws PulsarClientException {
        return client.newConsumer()
                .topic(topic)
                .subscriptionName("test-" + topic)
                .ackTimeout(10, TimeUnit.SECONDS)
                .subscriptionType(SubscriptionType.Exclusive)
                .subscribe();
    }

    private class BackgroundPulsarConsumer<T> {
        private Consumer<byte[]> pulsarConsumer;

        private Class<T> entityClass;

        private ExecutorService executerService;

        private boolean stopped;

        private ObjectMapper objectMapper = new ObjectMapper();

        private BackgroundPulsarConsumer(Consumer<byte[]> pulsarConsumer, Class<T> entityClass) {
            this.pulsarConsumer = pulsarConsumer;
            this.entityClass = entityClass;

            executerService = Executors.newCachedThreadPool();
        }

        public void start(java.util.function.Consumer<T> consumer) {

            executerService.execute(() -> {
                do {
                    try {
                        // Wait until a message is available
                        Message<byte[]> msg = pulsarConsumer.receive();

                        consumer.accept(objectMapper.readValue(msg.getValue(), entityClass));

                        // Acknowledge processing of the message so that it can be deleted
                        pulsarConsumer.acknowledge(msg);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } while (!stopped);
            });
        }

        public void stop() throws PulsarClientException {
            stopped = true;

            pulsarConsumer.close();
        }

    }
}
