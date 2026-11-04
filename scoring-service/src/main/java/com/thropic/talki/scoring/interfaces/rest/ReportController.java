package com.thropic.talki.scoring.interfaces.rest;

import com.thropic.talki.scoring.domain.model.AnalysisState;
import com.thropic.talki.scoring.domain.model.ScoreResult;
import com.thropic.talki.scoring.infrastructure.persistence.ScoreResultRepository;
import com.thropic.talki.scoring.interfaces.rest.dto.AnalysisStatusResponse;
import com.thropic.talki.scoring.interfaces.rest.dto.ApiErrorResponse;
import com.thropic.talki.scoring.interfaces.rest.dto.ReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;

/**
 * Consulta del reporte de una práctica (TS04).
 *
 * <p>La propiedad se compara con el {@code userId} recibido. Ese valor lo declara quien
 * llama, igual que en /v1/progress/dashboard: hasta que el servicio valide el JWT no es
 * una autorización real.
 */
@RestController
@RequestMapping("/v1/reports")
public class ReportController {

    private static final String COMPLETED_EXAMPLE = """
            {
              "reportId": 2,
              "sessionId": "session-123",
              "analysisState": "COMPLETED",
              "analysisVersion": "filler-rules-v1",
              "rubricVersion": "voice-rules-v1",
              "overallScore": 87,
              "scores": { "fluency": 94, "clarity": 85, "volume": 90, "vocabulary": 91, "confidence": 75 },
              "evidence": {
                "totalFillers": 3,
                "fillersByType": { "este": 1, "o sea": 1, "eh": 1 },
                "wordCount": 25,
                "wordsPerMinute": 130,
                "durationSeconds": 300,
                "silenceRatio": 0.08,
                "volumeRmsAvg": 0.6
              },
              "limitations": ["Puntuación calculada con reglas sobre métricas del discurso; no evalúa el contenido ni la argumentación."],
              "computedAt": "2026-11-03T21:37:13.33202"
            }""";

    private static final String INSUFFICIENT_EXAMPLE = """
            {
              "reportId": 3,
              "sessionId": "session-124",
              "analysisState": "INSUFFICIENT_EVIDENCE",
              "analysisVersion": "filler-rules-v1",
              "rubricVersion": "voice-rules-v1",
              "evidence": {
                "totalFillers": 0,
                "fillersByType": {},
                "wordCount": 0,
                "wordsPerMinute": 0,
                "durationSeconds": 30,
                "silenceRatio": 0.0,
                "volumeRmsAvg": 0.0
              },
              "limitations": ["La práctica no tiene palabras transcritas: no se calcula puntuación."],
              "computedAt": "2026-11-03T21:37:20.610629"
            }""";

    private static final String PENDING_MESSAGE = "El análisis de la práctica sigue en proceso";
    private static final String FAILED_MESSAGE = "El análisis falló y no se generó una puntuación";

    private final ScoreResultRepository scoreResultRepository;

    public ReportController(ScoreResultRepository scoreResultRepository) {
        this.scoreResultRepository = scoreResultRepository;
    }

    @Operation(summary = "Consultar el reporte de una práctica",
            description = "Solo los estados COMPLETED incluyen puntuación. El userId lo declara quien llama: "
                    + "el servicio aún no valida el JWT, así que esta comparación no es una autorización real.")
    @ApiResponse(responseCode = "200", description = "Evaluación disponible, o práctica sin evidencia suficiente (sin puntuación)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ReportResponse.class),
                    examples = {
                            @ExampleObject(name = "Evaluación disponible", value = COMPLETED_EXAMPLE),
                            @ExampleObject(name = "Práctica sin palabras", value = INSUFFICIENT_EXAMPLE)
                    }))
    @ApiResponse(responseCode = "202", description = "Análisis en proceso o fallido: devuelve analysisState, nunca una puntuación",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AnalysisStatusResponse.class),
                    examples = {
                            @ExampleObject(name = "En proceso", value = """
                                    { "sessionId": "session-123", "analysisState": "PENDING", "message": "El análisis de la práctica sigue en proceso" }"""),
                            @ExampleObject(name = "Fallido", value = """
                                    { "sessionId": "session-123", "analysisState": "FAILED", "message": "El análisis falló y no se generó una puntuación" }""")
                    }))
    @ApiResponse(responseCode = "400", description = "Falta userId",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject("""
                            { "code": "INVALID_ARGUMENT", "message": "Falta el parámetro userId", "timestamp": "2026-11-04T02:37:20Z" }""")))
    @ApiResponse(responseCode = "404", description = "La práctica no existe o no está disponible para este usuario",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject("""
                            { "code": "REPORT_NOT_AVAILABLE", "message": "El reporte no existe o no está disponible para este usuario", "timestamp": "2026-11-04T02:37:20Z" }""")))
    @GetMapping(value = "/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getReport(
            @Parameter(description = "Identificador de la práctica", example = "session-123") @PathVariable String sessionId,
            @Parameter(description = "Usuario que consulta", example = "42") @RequestParam String userId) {
        Optional<ScoreResult> found = scoreResultRepository.findBySessionId(sessionId)
                .filter(result -> result.getUserId().equals(userId));
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(
                    "REPORT_NOT_AVAILABLE",
                    "El reporte no existe o no está disponible para este usuario",
                    Instant.now().toString()));
        }
        ScoreResult result = found.get();
        AnalysisState state = result.getAnalysisState();
        return switch (state) {
            case COMPLETED, INSUFFICIENT_EVIDENCE -> ResponseEntity.ok(ReportResponse.from(result));
            case PENDING -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new AnalysisStatusResponse(sessionId, state, PENDING_MESSAGE));
            case FAILED -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new AnalysisStatusResponse(sessionId, state, FAILED_MESSAGE));
        };
    }
}
