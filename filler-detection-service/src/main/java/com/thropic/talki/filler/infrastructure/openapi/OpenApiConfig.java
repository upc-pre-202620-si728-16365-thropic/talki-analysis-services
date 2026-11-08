package com.thropic.talki.filler.infrastructure.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración de OpenAPI 3 para el filler-detection-service.
 *
 * Publica la especificación en /v3/api-docs y la UI en /swagger-ui.html.
 * El servicio trabaja por eventos y no expone rutas REST de negocio.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fillerDetectionServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Talki — Filler Detection Service API")
                        .description("Microservicio del bounded context Speech Analysis del producto Talki. "
                                + "Trabaja por eventos: consume session.live.finalized del exchange talki.events, "
                                + "detecta muletillas en la transcripción y publica fillers.analyzed con las "
                                + "métricas recibidas. No expone rutas REST de negocio.")
                        .version("v1")
                        .contact(new Contact()
                                .name("Thropic — Equipo Talki")
                                .url("https://github.com/upc-pre-202620-si728-16365-thropic"))
                        .license(new License()
                                .name("Académico — UPC 1ASI0728 2026-20")))
                .servers(List.of(
                        new Server().url("/").description("Servidor actual"),
                        new Server().url("http://localhost:8087").description("Local, puerto por defecto")
                ));
    }
}
