package com.thropic.talki.scoring.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.thropic.talki.scoring.domain.model.ScoreResult;
import com.thropic.talki.scoring.infrastructure.persistence.ScoreResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato real fillers.analyzed → scoring.completed sobre RabbitMQ y H2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FillersAnalyzedPipelineIT extends AbstractRabbitIT {

    @Autowired
    private ScoreResultRepository scoreResultRepository;

    @Test
    void fillersAnalyzed_shouldPersistScoreAndPublishWithSameCorrelation() throws IOException {
        String sessionId = "session-" + UUID.randomUUID();
        String output = bindOutputQueue("scoring.completed");

        publishFillersAnalyzed(sessionId, "user-contract-1");

        Message message = rabbitTemplate.receive(output, 15_000);
        assertThat(message).as("scoring.completed publicado").isNotNull();
        JsonNode published = json.readTree(message.getBody());

        assertThat(published.get("sessionId").asText()).isEqualTo(sessionId);
        assertThat(published.get("userId").asText()).isEqualTo("user-contract-1");
        assertThat(published.get("traceId").asText()).isEqualTo("trace-contract-1");
        assertThat(published.get("overallScore").asInt()).isBetween(0, 100);
        assertThat(published.get("fillersCount").asInt()).isEqualTo(3);

        ScoreResult persisted = scoreResultRepository.findBySessionId(sessionId).orElseThrow();
        assertThat(persisted.getUserId()).isEqualTo("user-contract-1");
        assertThat(persisted.getOverallScore()).isEqualTo(published.get("overallScore").asInt());
    }

    @Test
    void duplicatedFillersAnalyzed_shouldKeepOneScoreAndPublishOnce() throws IOException {
        String sessionId = "session-" + UUID.randomUUID();
        String output = bindOutputQueue("scoring.completed");

        publishFillersAnalyzed(sessionId, "user-contract-1");
        assertThat(rabbitTemplate.receive(output, 15_000)).as("primer scoring.completed").isNotNull();

        publishFillersAnalyzed(sessionId, "user-contract-1");

        assertThat(rabbitTemplate.receive(output, 3_000))
                .as("un duplicado no debe publicar otro scoring.completed").isNull();
        assertThat(scoreResultRepository.findAll().stream()
                .filter(r -> r.getSessionId().equals(sessionId)))
                .hasSize(1);
    }
}
