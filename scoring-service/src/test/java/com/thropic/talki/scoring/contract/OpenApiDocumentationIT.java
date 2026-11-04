package com.thropic.talki.scoring.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** La documentación publicada describe el contrato real, con ejemplos y sin datos de otro curso. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiDocumentationIT extends AbstractRabbitIT {

    private static final String JSON = "application/json";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void reportEndpoint_shouldDocumentEveryResponseWithSchemaAndExamples() {
        JsonNode responses = apiDocs().at("/paths/~1v1~1reports~1{sessionId}/get/responses");

        assertThat(responses.at("/200/content/" + encoded(JSON) + "/schema/$ref").asText())
                .endsWith("ReportResponse");
        assertThat(responses.at("/200/content/" + encoded(JSON) + "/examples")).hasSize(2);
        assertThat(responses.at("/202/content/" + encoded(JSON) + "/schema/$ref").asText())
                .endsWith("AnalysisStatusResponse");
        assertThat(responses.at("/202/content/" + encoded(JSON) + "/examples")).hasSize(2);
        assertThat(responses.at("/400/content/" + encoded(JSON) + "/schema/$ref").asText())
                .endsWith("ApiErrorResponse");
        assertThat(responses.at("/404/content/" + encoded(JSON) + "/schema/$ref").asText())
                .endsWith("ApiErrorResponse");
    }

    @Test
    void healthEndpoint_shouldShowARealExample() {
        JsonNode content = apiDocs().at("/paths/~1v1~1scores~1health/get/responses/200/content/" + encoded(JSON));

        assertThat(content.toString()).contains("scoring-service").doesNotContain("additionalProp");
    }

    @Test
    void documentInformation_shouldBelongToTheCurrentCourseAndContext() {
        JsonNode docs = apiDocs();

        assertThat(docs.at("/info/license/name").asText()).contains("1ASI0728");
        assertThat(docs.at("/info/description").asText()).contains("Scoring & Feedback");
        assertThat(docs.at("/servers/0/url").asText()).isEqualTo("/");
        assertThat(docs.get("servers").toString()).doesNotContain("api.talki.lat");
    }

    private JsonNode apiDocs() {
        return rest.getForObject("/v3/api-docs", JsonNode.class);
    }

    private String encoded(String mediaType) {
        return mediaType.replace("/", "~1");
    }
}
