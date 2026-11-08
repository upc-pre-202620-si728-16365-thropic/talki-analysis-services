package com.thropic.talki.scoring.infrastructure.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración de OpenAPI 3 para el scoring-service.
 *
 * Publica la especificación en /v3/api-docs y la UI en /swagger-ui.html.
 * La URL relativa permite usar "Try it out" en cualquier puerto; la URL del
 * ambiente publicado se agrega cuando exista.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI scoringServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Talki — Scoring Service API")
                        .description("Microservicio del bounded context Scoring & Feedback del producto Talki. "
                                + "Consume fillers.analyzed del exchange talki.events, calcula el Voice Coach "
                                + "Score con 5 dimensiones de 0 a 100 (fluency, clarity, volume, vocabulary, "
                                + "confidence) y publica scoring.completed sin duplicar evaluaciones. "
                                + "GET /v1/reports/{sessionId} entrega la evaluación con su evidencia, versiones "
                                + "y limitaciones, o el estado del análisis sin puntuación.")
                        .version("v1")
                        .contact(new Contact()
                                .name("Thropic — Equipo Talki")
                                .url("https://github.com/upc-pre-202620-si728-16365-thropic"))
                        .license(new License()
                                .name("Académico — UPC 1ASI0728 2026-20")))
                .servers(List.of(
                        new Server().url("/").description("Servidor actual"),
                        new Server().url("http://localhost:8088").description("Local, puerto por defecto")
                ));
    }
}
