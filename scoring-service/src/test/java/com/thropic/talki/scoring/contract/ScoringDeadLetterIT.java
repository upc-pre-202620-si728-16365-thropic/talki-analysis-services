package com.thropic.talki.scoring.contract;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thropic.talki.scoring.domain.model.ScoreCalculator;
import com.thropic.talki.scoring.infrastructure.persistence.ScoreResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

/** TS03: un fallo transitorio se reintenta y uno persistente queda en la cola de fallos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ScoringDeadLetterIT extends AbstractRabbitIT {

    private static final String DLQ = "scoring.fillers-ready.dlq";

    @SpyBean
    private ScoreCalculator scoreCalculator;

    @Autowired
    private ScoreResultRepository scoreResultRepository;

    @Test
    void malformedMessage_shouldEndInDeadLetterQueue() {
        Message malformed = MessageBuilder.withBody("{not-json".getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .build();

        rabbitTemplate.send(EXCHANGE, "fillers.analyzed", malformed);

        assertThat(rabbitTemplate.receive(DLQ, 15_000))
                .as("el mensaje inválido debe quedar en la cola de fallos")
                .isNotNull();
    }

    @Test
    void eventWithoutSessionId_shouldEndInDeadLetterQueueWithoutPersisting() throws IOException {
        long before = scoreResultRepository.count();
        ObjectNode event = fixture("contracts/fillers.analyzed.v1.json");
        event.remove("sessionId");

        publish("fillers.analyzed", "com.thropic.talki.filler.domain.event.FillerAnalyzedEvent", event);

        assertThat(rabbitTemplate.receive(DLQ, 15_000))
                .as("sin sessionId no se puede correlacionar: debe quedar en la cola de fallos")
                .isNotNull();
        assertThat(scoreResultRepository.count()).isEqualTo(before);
    }

    @Test
    void transientFailure_shouldBeRetriedAndCompleted() throws IOException {
        String sessionId = "session-" + UUID.randomUUID();
        doThrow(new RuntimeException("fallo transitorio simulado"))
                .doCallRealMethod()
                .when(scoreCalculator)
                .calculate(argThat(e -> e != null && sessionId.equals(e.getSessionId())));

        publishFillersAnalyzed(sessionId, "user-retry");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(scoreResultRepository.findBySessionId(sessionId)).isPresent());
    }
}
