package com.thropic.talki.scoring.domain.model;

import com.thropic.talki.scoring.domain.event.FillerAnalyzedEvent;
import com.thropic.talki.scoring.domain.event.ScoringCompletedEvent;
import org.springframework.stereotype.Component;

@Component
public class ScoreCalculator {

    public ScoringCompletedEvent calculate(FillerAnalyzedEvent event) {
        // Las métricas acústicas y el WPM provienen del live-coach-service
        // (calculados en memoria durante la sesión sobre el audio de Gemini Live)
        // y se propagan por el bus de eventos. Si no llegaron (eventos legacy o
        // fallback), recurrimos a una estimación derivada del wordCount.
        int wpm = event.getWordsPerMinute() > 0
                ? event.getWordsPerMinute()
                : estimateWpm(event.getWordCount(), event.getDurationSeconds());
        double silenceRatio = event.getSilenceRatio();
        double volumeRmsAvg = event.getVolumeRmsAvg();

        int fluency    = fluencyScore(wpm, event.getTotalFillers());
        int clarity    = clarityScore(wpm);
        int volume     = volumeScore(volumeRmsAvg);
        int vocabulary = vocabularyScore(event.getTotalFillers());
        int confidence = confidenceScore(silenceRatio);

        VoiceScore scores = new VoiceScore(fluency, clarity, volume, vocabulary, confidence);

        return new ScoringCompletedEvent(
                event.getSessionId(),
                event.getUserId(),
                scores,
                event.getTotalFillers(),
                silenceRatio,
                wpm,
                event.getDurationSeconds(),
                event.getTraceId()
        );
    }

    private int estimateWpm(int wordCount, int durationSeconds) {
        if (durationSeconds <= 0) return 0;
        return (wordCount * 60) / durationSeconds;
    }

    private int fluencyScore(int wpm, int fillerCount) {
        int base = (wpm >= 120 && wpm <= 160) ? 100 :
                   (wpm >= 100) ? 80 : 60;
        return Math.max(0, base - fillerCount * 2);
    }

    private int clarityScore(int wpm) {
        if (wpm >= 100 && wpm <= 150) return 85;
        if (wpm >= 80) return 65;
        return 50;
    }

    /**
     * Score de volumen derivado del RMS promedio del audio (rango típico 0.0-1.0
     * tras normalización). Se considera óptimo entre 0.5 y 0.8: niveles más
     * bajos sugieren timidez y más altos saturación o griterío.
     */
    private int volumeScore(double rmsAvg) {
        if (rmsAvg <= 0.0) return 0;
        if (rmsAvg >= 0.5 && rmsAvg <= 0.8) return 90;
        if (rmsAvg >= 0.3 && rmsAvg < 0.5) return 70;
        if (rmsAvg > 0.8) return 60;
        return 40;
    }

    private int vocabularyScore(int fillerCount) {
        return Math.max(0, 100 - fillerCount * 3);
    }

    private int confidenceScore(double silenceRatio) {
        if (silenceRatio < 0.05) return 90;
        if (silenceRatio < 0.10) return 75;
        if (silenceRatio < 0.20) return 55;
        return 30;
    }
}
