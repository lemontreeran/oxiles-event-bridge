package io.oxiles.server.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.oxiles.dto.block.BlockDetails;
import io.oxiles.dto.event.ContractEventDetails;
import io.oxiles.dto.event.filter.ContractEventFilter;
import io.oxiles.dto.message.ContractEventFilterAdded;
import io.oxiles.dto.message.ContractEventFilterRemoved;
import io.oxiles.dto.message.EventeumMessage;
import io.oxiles.dto.transaction.TransactionDetails;
import io.oxiles.integration.KafkaSettings;
import io.oxiles.model.TransactionMonitoringSpec;
import io.oxiles.utils.JSON;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BaseKafkaIntegrationTest extends BaseIntegrationTest {

    private static final String KAFKA_LISTENER_CONTAINER_ID = "org.springframework.kafka.KafkaListenerEndpointContainer#0";

    private ObjectMapper objectMapper = new ObjectMapper();

    private List<EventeumMessage<ContractEventFilter>> broadcastFiltersEventMessages = new ArrayList<>();

    private List<EventeumMessage<TransactionMonitoringSpec>> broadcastTransactionEventMessages = new ArrayList<>();

    @Autowired
    private KafkaSettings kafkaSettings;

    private KafkaMessageListenerContainer springMessageListener;

    @ClassRule
    public static KafkaEmbedded embeddedKafka = new KafkaEmbedded(1, true, 1);

    private KafkaMessageListenerContainer<String, String> testContainer;

    @Autowired
    public KafkaListenerEndpointRegistry registry;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        // set up the Kafka consumer properties
        final Map<String, Object> consumerProperties =
                KafkaTestUtils.consumerProps(generateTestGroupId(), "false", embeddedKafka);

        // create a Kafka consumer factory
        DefaultKafkaConsumerFactory<String, String> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProperties, new StringDeserializer(), new StringDeserializer());

        // set the topic that needs to be consumed
        ContainerProperties containerProperties = new ContainerProperties(kafkaSettings.getContractEventsTopic(),
                kafkaSettings.getEventeumEventsTopic(), kafkaSettings.getBlockEventsTopic(), kafkaSettings.getTransactionEventsTopic());

        // create a Kafka MessageListenerContainer
        testContainer = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);

        // setup a Kafka message listener
        testContainer.setupMessageListener(new MessageListener<String, String>() {
            @Override
            public void onMessage(ConsumerRecord<String, String> record) {
                System.out.println("Received message: " + JSON.stringify(record.value()));
                try {
                    if (record.topic().equals(kafkaSettings.getContractEventsTopic())) {
                        final EventeumMessage<ContractEventDetails> message =
                                objectMapper.readValue(record.value(), EventeumMessage.class);

                        getBroadcastContractEvents().add(message.getDetails());
                    }

                    if (record.topic().equals(kafkaSettings.getEventeumEventsTopic())) {
                        final EventeumMessage message =
                                objectMapper.readValue(record.value(), EventeumMessage.class);

                        if (message.getType().equals(ContractEventFilterAdded.TYPE)
                            || message.getType().equals(ContractEventFilterRemoved.TYPE)) {
                            final EventeumMessage<ContractEventFilter> filterMessge = message;
                            getBroadcastFilterEventMessages().add(filterMessge);
                        } else {
                            final EventeumMessage<TransactionMonitoringSpec> txMessge = message;
                            getBroadcastTransactionEventMessages().add(txMessge);
                        }

                    }

                    if (record.topic().equals(kafkaSettings.getBlockEventsTopic())) {
                        final EventeumMessage<BlockDetails> message =
                                objectMapper.readValue(record.value(), EventeumMessage.class);

                        getBroadcastBlockMessages().add(message.getDetails());
                    }

                    if (record.topic().equals(kafkaSettings.getTransactionEventsTopic())) {
                        final EventeumMessage<TransactionDetails> message =
                                objectMapper.readValue(record.value(), EventeumMessage.class);

                        getBroadcastTransactionMessages().add(message.getDetails());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // start the container and underlying message listener
        testContainer.start();

        ContainerTestUtils.waitForAssignment(testContainer,
                embeddedKafka.getPartitionsPerTopic() * testContainer.getContainerProperties().getTopics().length);

        final MessageListenerContainer defaultContainer = registry.getListenerContainer(KAFKA_LISTENER_CONTAINER_ID);

        //Container won't exist in non multi-instance mode
        if (defaultContainer != null) {
            ContainerTestUtils.waitForAssignment(defaultContainer, embeddedKafka.getPartitionsPerTopic());
        }

        registry
                .getListenerContainers()
                .forEach(container -> {
                    try {
                        if (container != defaultContainer) {
                            ContainerTestUtils.waitForAssignment(container, 3);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

        clearMessages();
    }

    @After
    public void tearDown() {
        // stop the container
        testContainer.stop();
    }

    public List<EventeumMessage<ContractEventFilter>> getBroadcastFilterEventMessages() {
        return broadcastFiltersEventMessages;
    }

    public List<EventeumMessage<TransactionMonitoringSpec>> getBroadcastTransactionEventMessages() {
        return broadcastTransactionEventMessages;
    }

    protected void clearMessages() {
        super.clearMessages();
        broadcastFiltersEventMessages.clear();
    }

    private String generateTestGroupId() {
        return "testGroup-" + UUID.randomUUID().toString();
    }

}
