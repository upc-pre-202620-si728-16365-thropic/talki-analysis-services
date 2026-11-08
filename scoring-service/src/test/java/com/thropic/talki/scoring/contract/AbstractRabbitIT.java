package com.thropic.talki.scoring.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Base de las pruebas *IT: un único RabbitMQ real para toda la ejecución. */
public abstract class AbstractRabbitIT {

    protected static final String EXCHANGE = "talki.events";
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

    protected final ObjectMapper json = new ObjectMapper();

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    protected AmqpAdmin amqpAdmin;

    /** Cola temporal enlazada al exchange para observar lo que publica el servicio. */
    protected String bindOutputQueue(String routingKey) {
        String queue = "test." + routingKey + "." + UUID.randomUUID();
        amqpAdmin.declareQueue(new Queue(queue, false, false, false));
        amqpAdmin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE,
                EXCHANGE, routingKey, null));
        return queue;
    }

    /** Publica un fixture con la forma real del productor, para una sesión concreta. */
    protected void publishFillersAnalyzed(String sessionId, String userId) throws IOException {
        ObjectNode event = fixture("contracts/fillers.analyzed.v1.json");
        event.put("sessionId", sessionId);
        event.put("userId", userId);
        publish("fillers.analyzed",
                "com.thropic.talki.filler.domain.event.FillerAnalyzedEvent", event);
    }

    protected void publish(String routingKey, String producerType, JsonNode event) throws IOException {
        Message message = MessageBuilder.withBody(json.writeValueAsBytes(event))
                .setContentType("application/json")
                .setHeader("__TypeId__", producerType)
                .build();
        rabbitTemplate.send(EXCHANGE, routingKey, message);
    }

    protected ObjectNode fixture(String path) throws IOException {
        return (ObjectNode) json.readTree(new ClassPathResource(path).getInputStream());
    }

    protected List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
