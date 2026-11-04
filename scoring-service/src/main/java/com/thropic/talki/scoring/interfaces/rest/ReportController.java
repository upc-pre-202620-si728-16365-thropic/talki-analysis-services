package com.thropic.talki.scoring.interfaces.rest;

import com.thropic.talki.scoring.domain.model.AnalysisState;
import com.thropic.talki.scoring.domain.model.ScoreResult;
import com.thropic.talki.scoring.infrastructure.persistence.ScoreResultRepository;
import com.thropic.talki.scoring.interfaces.rest.dto.ReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
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

    private final ScoreResultRepository scoreResultRepository;

    public ReportController(ScoreResultRepository scoreResultRepository) {
        this.scoreResultRepository = scoreResultRepository;
    }

    @Operation(summary = "Consultar el reporte de una práctica")
    @ApiResponse(responseCode = "200", description = "Evaluación disponible, o práctica sin evidencia suficiente (sin puntuación)")
    @ApiResponse(responseCode = "202", description = "Análisis en proceso o fallido: devuelve analysisState, nunca una puntuación")
    @ApiResponse(responseCode = "400", description = "Falta userId")
    @ApiResponse(responseCode = "404", description = "La práctica no existe o no está disponible para este usuario")
    @GetMapping("/{sessionId}")
    public ResponseEntity<?> getReport(@PathVariable String sessionId, @RequestParam String userId) {
        Optional<ScoreResult> found = scoreResultRepository.findBySessionId(sessionId)
                .filter(result -> result.getUserId().equals(userId));
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "REPORT_NOT_AVAILABLE",
                    "message", "El reporte no existe o no está disponible para este usuario",
                    "timestamp", Instant.now().toString()));
        }
        ScoreResult result = found.get();
        AnalysisState state = result.getAnalysisState();
        return switch (state) {
            case COMPLETED, INSUFFICIENT_EVIDENCE -> ResponseEntity.ok(ReportResponse.from(result));
            case PENDING -> ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "sessionId", sessionId,
                    "analysisState", state,
                    "message", "El análisis de la práctica sigue en proceso"));
            case FAILED -> ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "sessionId", sessionId,
                    "analysisState", state,
                    "message", "El análisis falló y no se generó una puntuación"));
        };
    }
}
