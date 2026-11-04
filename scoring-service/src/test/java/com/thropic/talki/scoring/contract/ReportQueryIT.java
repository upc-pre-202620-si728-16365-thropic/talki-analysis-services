package com.thropic.talki.scoring.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thropic.talki.scoring.domain.model.ScoreCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

/** Contrato de TS04: 200 con reporte, 202 con estado y 404 si no está disponible. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReportQueryIT extends AbstractRabbitIT {

    private static final String OWNER = "user-owner";

    @Autowired
    private TestRestTemplate rest;

    @SpyBean
    private ScoreCalculator scoreCalculator;

    private final String sessionId = "session-" + UUID.randomUUID();

    @Test
    void closedSessionWithoutAnalysis_shouldAnswer202WithoutScore() throws IOException {
        publishSessionFinalized(sessionId, OWNER);

        ResponseEntity<JsonNode> response = awaitReport(202);

        assertThat(response.getBody().get("analysisState").asText()).isEqualTo("PENDING");
        assertThat(response.getBody().has("overallScore")).isFalse();
        assertThat(response.getBody().has("scores")).isFalse();
    }

    @Test
    void completedAnalysis_shouldAnswer200WithScoreEvidenceVersionsAndLimitations() throws IOException {
        publishSessionFinalized(sessionId, OWNER);
        publishFillersAnalyzed(sessionId, OWNER);

        JsonNode report = awaitReport(200).getBody();

        assertThat(report.get("sessionId").asText()).isEqualTo(sessionId);
        assertThat(report.get("analysisState").asText()).isEqualTo("COMPLETED");
        assertThat(report.get("analysisVersion").asText()).isEqualTo("filler-rules-v1");
        assertThat(report.get("rubricVersion").asText()).isEqualTo("voice-rules-v1");
        assertThat(report.get("overallScore").asInt()).isBetween(1, 100);
        assertThat(report.get("scores").get("fluency").asInt()).isBetween(0, 100);
        assertThat(report.get("evidence").get("totalFillers").asInt()).isEqualTo(3);
        assertThat(report.get("evidence").get("fillersByType").get("este").asInt()).isEqualTo(1);
        assertThat(report.get("evidence").get("wordCount").asInt()).isEqualTo(14);
        assertThat(report.get("evidence").get("durationSeconds").asInt()).isEqualTo(300);
        assertThat(report.get("limitations")).isNotEmpty();
    }

    @Test
    void analysisArrivingBeforeSessionClosure_shouldStillEndCompleted() throws IOException {
        publishFillersAnalyzed(sessionId, OWNER);
        awaitReport(200);

        publishSessionFinalized(sessionId, OWNER);

        assertThat(awaitReport(200).getBody().get("analysisState").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void sessionWithoutWords_shouldNotInventAScoreNorPublishIt() throws IOException {
        String output = bindOutputQueue("scoring.completed");
        ObjectNode event = fixture("contracts/fillers.analyzed.v1.json");
        event.put("sessionId", sessionId);
        event.put("userId", OWNER);
        event.put("wordCount", 0);
        event.put("totalFillers", 0);
        event.putObject("fillersByType");

        publish("fillers.analyzed", "com.thropic.talki.filler.domain.event.FillerAnalyzedEvent", event);

        JsonNode report = awaitReport(200).getBody();
        assertThat(report.get("analysisState").asText()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(report.has("overallScore")).isFalse();
        assertThat(report.has("scores")).isFalse();
        assertThat(rabbitTemplate.receive(output, 2_000))
                .as("sin evidencia no debe alimentar progreso ni gamificación").isNull();
    }

    @Test
    void invalidMetrics_shouldAnswer202FailedWithoutScore() throws IOException {
        doThrow(new IllegalArgumentException("métrica fuera de rango"))
                .when(scoreCalculator)
                .calculate(argThat(e -> e != null && sessionId.equals(e.getSessionId())));

        publishFillersAnalyzed(sessionId, OWNER);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ResponseEntity<JsonNode> response = report(OWNER);
            assertThat(response.getStatusCode().value()).isEqualTo(202);
            assertThat(response.getBody().get("analysisState").asText()).isEqualTo("FAILED");
            assertThat(response.getBody().has("overallScore")).isFalse();
        });
    }

    @Test
    void otherUser_shouldNotSeeTheReport() throws IOException {
        publishFillersAnalyzed(sessionId, OWNER);
        awaitReport(200);

        ResponseEntity<JsonNode> response = report("user-intruder");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().get("code").asText()).isEqualTo("REPORT_NOT_AVAILABLE");
        assertThat(response.getBody().has("scores")).isFalse();
    }

    @Test
    void unknownSession_shouldAnswer404() {
        assertThat(report(OWNER).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void missingUserId_shouldAnswer400() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/v1/reports/" + sessionId, JsonNode.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().get("code").asText()).isEqualTo("INVALID_ARGUMENT");
    }

    private ResponseEntity<JsonNode> report(String userId) {
        return rest.getForEntity("/v1/reports/{id}?userId={user}", JsonNode.class, sessionId, userId);
    }

    private ResponseEntity<JsonNode> awaitReport(int status) {
        await().atMost(Duration.ofSeconds(15)).until(() -> report(OWNER).getStatusCode().value() == status);
        return report(OWNER);
    }

    private void publishSessionFinalized(String session, String user) throws IOException {
        ObjectNode event = json.createObjectNode();
        event.put("eventType", "session.live.finalized");
        event.put("sessionId", session);
        event.put("userId", user);
        event.put("transcriptGemini", "campo que scoring-service no conserva");
        publish("session.live.finalized",
                "com.thropic.talki.livecoach.domain.event.SessionLiveFinalizedEvent", event);
    }
}
