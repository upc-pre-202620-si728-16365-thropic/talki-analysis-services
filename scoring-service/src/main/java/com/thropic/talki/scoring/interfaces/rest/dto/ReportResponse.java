package com.thropic.talki.scoring.interfaces.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.thropic.talki.scoring.domain.model.AnalysisState;
import com.thropic.talki.scoring.domain.model.ScoreResult;
import com.thropic.talki.scoring.domain.model.VoiceScore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reporte de una práctica. {@code overallScore} y {@code scores} solo existen en
 * estado COMPLETED: los demás estados nunca muestran una puntuación.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportResponse(
        Long reportId,
        String sessionId,
        AnalysisState analysisState,
        String analysisVersion,
        String rubricVersion,
        Integer overallScore,
        VoiceScore scores,
        Evidence evidence,
        List<String> limitations,
        LocalDateTime computedAt) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Evidence(
            int totalFillers,
            Map<String, Integer> fillersByType,
            Integer wordCount,
            int wordsPerMinute,
            Integer durationSeconds,
            double silenceRatio,
            Double volumeRmsAvg) {}

    public static ReportResponse from(ScoreResult result) {
        boolean completed = result.getAnalysisState() == AnalysisState.COMPLETED;
        return new ReportResponse(
                result.getId(),
                result.getSessionId(),
                result.getAnalysisState(),
                result.getAnalysisVersion(),
                result.getRubricVersion(),
                completed ? result.getOverallScore() : null,
                completed ? result.asVoiceScore() : null,
                new Evidence(
                        result.getTotalFillers(),
                        result.getFillersByType(),
                        result.getWordCount(),
                        result.getWordsPerMinute(),
                        result.getDurationSeconds(),
                        result.getSilenceRatio(),
                        result.getVolumeRmsAvg()),
                limitationsOf(result),
                result.getComputedAt());
    }

    private static List<String> limitationsOf(ScoreResult result) {
        List<String> limitations = new ArrayList<>();
        if (result.getAnalysisState() == AnalysisState.INSUFFICIENT_EVIDENCE) {
            limitations.add("La práctica no tiene palabras transcritas: no se calcula puntuación.");
            return limitations;
        }
        limitations.add("Puntuación calculada con reglas sobre métricas del discurso; "
                + "no evalúa el contenido ni la argumentación.");
        if (result.getVolumeRmsAvg() == null || result.getVolumeRmsAvg() <= 0) {
            limitations.add("Sin métrica de volumen: la dimensión de volumen no refleja audio medido.");
        }
        if (result.getSilenceRatio() <= 0) {
            limitations.add("Sin métrica de silencios: la confianza estimada no refleja pausas medidas.");
        }
        return limitations;
    }
}
