package com.thropic.talki.scoring.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/scores")
public class ScoreController {

    @Operation(summary = "Comprobar que el servicio responde",
            description = "No equivale a disponibilidad de las evaluaciones. Para consultarlas, usar /v1/reports/{sessionId}.")
    @ApiResponse(responseCode = "200", description = "Servicio en ejecución",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject("""
                    {
                      "service": "scoring-service",
                      "status": "UP",
                      "description": "Consumes fillers.analyzed → calcula VoiceScore → publica scoring.completed"
                    }""")))
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "service", "scoring-service",
                "status", "UP",
                "description", "Consumes fillers.analyzed → calcula VoiceScore → publica scoring.completed"
        ));
    }
}
