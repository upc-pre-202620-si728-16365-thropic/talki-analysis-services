package com.thropic.talki.filler.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato real sobre RabbitMQ: el mensaje que entra tiene la forma exacta que
 * publica live-coach-service (incluido su __TypeId__) y el que sale debe coincidir
 * con el fixture que consume scoring-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SessionFinalizedPipelineIT {

    private static final String EXCHANGE = "talki.events";
    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:3.12-management");

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    private String outputQueue;

    @BeforeEach
    void bindOutputQueue() {
        outputQueue = "test.fillers-analyzed." + UUID.randomUUID();
        amqpAdmin.declareQueue(new Queue(outputQueue, false, false, false));
        amqpAdmin.declareBinding(new Binding(outputQueue, Binding.DestinationType.QUEUE,
                EXCHANGE, "fillers.analyzed", null));
    }

    @Test
    void sessionFinalized_shouldPublishFillersAnalyzedWithSameCorrelationAndContract() throws IOException {
        ObjectNode input = fixture("contracts/session.live.finalized.v1.json");

        publishAsLiveCoach(input);

        Message output = rabbitTemplate.receive(outputQueue, 15_000);
        assertThat(output).as("fillers.analyzed publicado").isNotNull();
        JsonNode published = json.readTree(output.getBody());

        assertThat(published.get("sessionId").asText()).isEqualTo(input.get("sessionId").asText());
        assertThat(published.get("userId").asText()).isEqualTo(input.get("userId").asText());
        assertThat(published.get("traceId").asText()).isEqualTo(input.get("traceId").asText());
        assertThat(published.get("totalFillers").asInt()).isEqualTo(3);
        assertThat(published.get("wordCount").asInt()).isEqualTo(14);
        assertThat(published.get("durationSeconds").asInt()).isEqualTo(300);
        assertThat(published.get("wordsPerMinute").asInt()).isEqualTo(130);
        assertThat(fieldNames(published))
                .as("los campos publicados deben coincidir con el contrato fillers.analyzed v1")
                .containsExactlyInAnyOrderElementsOf(
                        fieldNames(fixture("contracts/fillers.analyzed.v1.json")));
    }

    private void publishAsLiveCoach(ObjectNode event) throws IOException {
        Message message = MessageBuilder.withBody(json.writeValueAsBytes(event))
                .setContentType("application/json")
                .setHeader("__TypeId__", "com.thropic.talki.livecoach.domain.event.SessionLiveFinalizedEvent")
                .build();
        rabbitTemplate.send(EXCHANGE, "session.live.finalized", message);
    }

    private ObjectNode fixture(String path) throws IOException {
        return (ObjectNode) json.readTree(new ClassPathResource(path).getInputStream());
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
