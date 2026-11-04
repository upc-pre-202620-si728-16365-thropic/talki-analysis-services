package com.thropic.talki.scoring.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entidad JPA que persiste el resultado del Voice Coach Score calculado
 * por el scoring-service tras consumir fillers.analyzed. Vive en el
 * schema del scoring-service (database-per-service); su correlación
 * con la Session del bounded context Practice Session se realiza por
 * sessionId vía eventos, no por JPA cross-BC.
 *
 * <p>Las puntuaciones solo tienen significado en estado COMPLETED; en los demás
 * estados valen 0 y el reporte no las expone.
 */
@Entity
@Table(name = "score_results")
public class ScoreResult {

    public static final String ANALYSIS_VERSION = "filler-rules-v1";
    public static final String RUBRIC_VERSION = "voice-rules-v1";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private int fluency;

    @Column(nullable = false)
    private int clarity;

    @Column(name = "volume_score", nullable = false)
    private int volume;

    @Column(nullable = false)
    private int vocabulary;

    @Column(nullable = false)
    private int confidence;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Column(name = "total_fillers", nullable = false)
    private int totalFillers;

    @Column(name = "words_per_minute", nullable = false)
    private int wordsPerMinute;

    @Column(name = "silence_ratio", nullable = false)
    private double silenceRatio;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Columnas nuevas nullables para que ddl-auto=update funcione sobre filas previas.
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_state", length = 32)
    private AnalysisState analysisState;

    @Column(name = "analysis_version", length = 32)
    private String analysisVersion;

    @Column(name = "rubric_version", length = 32)
    private String rubricVersion;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "volume_rms_avg")
    private Double volumeRmsAvg;

    @Convert(converter = FillersByTypeConverter.class)
    @Column(name = "fillers_by_type", length = 2000)
    private Map<String, Integer> fillersByType;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;

    protected ScoreResult() {}

    public ScoreResult(String sessionId, String userId, VoiceScore scores,
                        int totalFillers, int wordsPerMinute, double silenceRatio) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
        this.totalFillers = totalFillers;
        complete(scores, wordsPerMinute, silenceRatio);
    }

    /** Práctica cerrada cuyo análisis aún no termina. */
    public static ScoreResult pending(String sessionId, String userId) {
        ScoreResult result = new ScoreResult();
        result.sessionId = sessionId;
        result.userId = userId;
        result.createdAt = LocalDateTime.now();
        result.analysisState = AnalysisState.PENDING;
        return result;
    }

    public void recordEvidence(int totalFillers, Map<String, Integer> fillersByType, int wordCount,
                               int wordsPerMinute, int durationSeconds,
                               double silenceRatio, double volumeRmsAvg) {
        this.totalFillers = totalFillers;
        this.fillersByType = fillersByType;
        this.wordCount = wordCount;
        this.wordsPerMinute = wordsPerMinute;
        this.durationSeconds = durationSeconds;
        this.silenceRatio = silenceRatio;
        this.volumeRmsAvg = volumeRmsAvg;
    }

    public void complete(VoiceScore scores, int wordsPerMinute, double silenceRatio) {
        this.fluency = scores.getFluency();
        this.clarity = scores.getClarity();
        this.volume = scores.getVolume();
        this.vocabulary = scores.getVocabulary();
        this.confidence = scores.getConfidence();
        this.overallScore = scores.overall();
        this.wordsPerMinute = wordsPerMinute;
        this.silenceRatio = silenceRatio;
        finish(AnalysisState.COMPLETED);
    }

    public void markInsufficientEvidence() {
        finish(AnalysisState.INSUFFICIENT_EVIDENCE);
    }

    public void markFailed() {
        finish(AnalysisState.FAILED);
    }

    private void finish(AnalysisState state) {
        this.analysisState = state;
        this.analysisVersion = ANALYSIS_VERSION;
        this.rubricVersion = RUBRIC_VERSION;
        this.computedAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return getAnalysisState() == AnalysisState.PENDING;
    }

    public VoiceScore asVoiceScore() {
        return new VoiceScore(fluency, clarity, volume, vocabulary, confidence);
    }

    public Long getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public int getFluency() { return fluency; }
    public int getClarity() { return clarity; }
    public int getVolume() { return volume; }
    public int getVocabulary() { return vocabulary; }
    public int getConfidence() { return confidence; }
    public int getOverallScore() { return overallScore; }
    public int getTotalFillers() { return totalFillers; }
    public int getWordsPerMinute() { return wordsPerMinute; }
    public double getSilenceRatio() { return silenceRatio; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** Las filas anteriores a esta columna siempre fueron evaluaciones calculadas. */
    public AnalysisState getAnalysisState() {
        return analysisState == null ? AnalysisState.COMPLETED : analysisState;
    }

    public String getAnalysisVersion() { return analysisVersion; }
    public String getRubricVersion() { return rubricVersion; }
    public Integer getWordCount() { return wordCount; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public Double getVolumeRmsAvg() { return volumeRmsAvg; }
    public Map<String, Integer> getFillersByType() { return fillersByType; }
    public LocalDateTime getComputedAt() { return computedAt; }
}
