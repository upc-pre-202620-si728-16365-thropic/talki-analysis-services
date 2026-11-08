package com.thropic.talki.scoring.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thropic.talki.scoring.domain.model.ScoreResult;
import com.thropic.talki.scoring.infrastructure.persistence.ScoreResultRepository;
import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class FeedbackReportSteps {

    private static final String EXCHANGE = "talki.events";

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ScoreResultRepository scoreResultRepository;

    private String sessionId;
    private String ownerId;
    private String requesterId;
    private String evaluationsQueue;
    private ObjectNode lastAnalysis;
    private ResponseEntity<JsonNode> response;

    @Before
    public void newPractice() {
        sessionId = "session-" + UUID.randomUUID();
        evaluationsQueue = "test.scoring.completed." + UUID.randomUUID();
        amqpAdmin.declareQueue(new Queue(evaluationsQueue, false, false, false));
        amqpAdmin.declareBinding(new Binding(evaluationsQueue, Binding.DestinationType.QUEUE,
                EXCHANGE, "scoring.completed", null));
    }

    @Dado("que {string} cerró una práctica")
    public void closedPractice(String user) throws IOException {
        ownerId = user;
        ObjectNode event = json.createObjectNode();
        event.put("eventType", "session.live.finalized");
        event.put("sessionId", sessionId);
        event.put("userId", user);
        publish("session.live.finalized",
                "com.thropic.talki.livecoach.domain.event.SessionLiveFinalizedEvent", event);
        await().atMost(Duration.ofSeconds(15))
                .until(() -> scoreResultRepository.findBySessionId(sessionId).isPresent());
    }

    @Dado("el análisis de esa práctica encontró {int} muletillas en {int} palabras")
    public void analysisFound(int fillers, int words) throws IOException {
        lastAnalysis = (ObjectNode) json.readTree(
                new ClassPathResource("contracts/fillers.analyzed.v1.json").getInputStream());
        lastAnalysis.put("sessionId", sessionId);
        lastAnalysis.put("userId", ownerId);
        lastAnalysis.put("totalFillers", fillers);
        lastAnalysis.put("wordCount", words);
        if (fillers == 0) {
            lastAnalysis.putObject("fillersByType");
        }
        publishAnalysis();
        await().atMost(Duration.ofSeconds(15)).until(() -> scoreResultRepository.findBySessionId(sessionId)
                .filter(result -> !result.isPending()).isPresent());
    }

    @Dado("el mismo análisis vuelve a llegar")
    public void sameAnalysisAgain() throws IOException {
        publishAnalysis();
    }

    @Cuando("{string} consulta el reporte de esa práctica")
    public void requestReport(String user) {
        requesterId = user;
        response = report();
    }

    @Entonces("recibe la respuesta {int}")
    public void receivesStatus(int status) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
    }

    @Entonces("el estado del análisis es {string}")
    public void analysisStateIs(String state) {
        assertThat(response.getBody().get("analysisState").asText()).isEqualTo(state);
    }

    @Entonces("el reporte incluye puntuación, muletillas, versiones y limitaciones")
    public void reportIsComplete() {
        JsonNode body = response.getBody();
        assertThat(body.get("overallScore").asInt()).isBetween(1, 100);
        assertThat(body.get("scores").get("fluency").asInt()).isBetween(0, 100);
        assertThat(body.get("evidence").get("totalFillers").asInt()).isEqualTo(3);
        assertThat(body.get("evidence").get("fillersByType")).isNotEmpty();
        assertThat(body.get("analysisVersion").asText()).isNotBlank();
        assertThat(body.get("rubricVersion").asText()).isNotBlank();
        assertThat(body.get("limitations")).isNotEmpty();
    }

    @Entonces("el reporte no incluye puntuación")
    public void reportHasNoScore() {
        assertThat(response.getBody().has("overallScore")).isFalse();
        assertThat(response.getBody().has("scores")).isFalse();
    }

    @Entonces("se publicó {int} evaluación/evaluaciones")
    public void publishedEvaluations(int expected) {
        int published = 0;
        while (rabbitTemplate.receive(evaluationsQueue, 2_000) != null) {
            published++;
        }
        assertThat(published).isEqualTo(expected);
        assertThat(scoreResultRepository.findAll().stream()
                .map(ScoreResult::getSessionId)
                .filter(sessionId::equals))
                .as("una sola fila por práctica")
                .hasSize(1);
    }

    private ResponseEntity<JsonNode> report() {
        return rest.getForEntity("/v1/reports/{id}?userId={user}", JsonNode.class, sessionId, requesterId);
    }

    private void publishAnalysis() throws IOException {
        publish("fillers.analyzed", "com.thropic.talki.filler.domain.event.FillerAnalyzedEvent", lastAnalysis);
    }

    private void publish(String routingKey, String producerType, JsonNode event) throws IOException {
        Message message = MessageBuilder.withBody(json.writeValueAsBytes(event))
                .setContentType("application/json")
                .setHeader("__TypeId__", producerType)
                .build();
        rabbitTemplate.send(EXCHANGE, routingKey, message);
    }
}
