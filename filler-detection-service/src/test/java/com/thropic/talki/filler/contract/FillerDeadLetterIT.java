package com.thropic.talki.filler.contract;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** TS03: un mensaje que no se puede procesar queda visible en la cola de fallos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FillerDeadLetterIT {

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

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void malformedMessage_shouldEndInDeadLetterQueue() {
        Message malformed = MessageBuilder.withBody("{not-json".getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .build();

        rabbitTemplate.send("talki.events", "session.live.finalized", malformed);

        assertThat(rabbitTemplate.receive("filler.session-finalized.dlq", 15_000))
                .as("el mensaje inválido debe quedar en la cola de fallos")
                .isNotNull();
    }
}
