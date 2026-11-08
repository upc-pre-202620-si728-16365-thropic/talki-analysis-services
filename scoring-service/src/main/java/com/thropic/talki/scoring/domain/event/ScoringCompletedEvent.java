package com.thropic.talki.scoring.domain.event;

import com.thropic.talki.scoring.domain.model.VoiceScore;

import java.time.Instant;
import java.util.UUID;

public class ScoringCompletedEvent {
    private String eventId;
    private String eventType = "scoring.completed";
    private Instant occurredAt;
    private String traceId;
    private String version = "1.0";
    private String sessionId;
    private String userId;
    private VoiceScore scores;
    private int overallScore;
    private int fillersCount;
    private double silenceRatio;
    private int wordsPerMinute;
    private int durationSeconds;

    public ScoringCompletedEvent() {}

    public ScoringCompletedEvent(String sessionId, String userId, VoiceScore scores,
                                  int fillersCount, double silenceRatio,
                                  int wordsPerMinute, int durationSeconds, String traceId) {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.scores = scores;
        this.overallScore = scores.overall();
        this.fillersCount = fillersCount;
        this.silenceRatio = silenceRatio;
        this.wordsPerMinute = wordsPerMinute;
        this.durationSeconds = durationSeconds;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public VoiceScore getScores() { return scores; }
    public void setScores(VoiceScore scores) { this.scores = scores; }
    public int getOverallScore() { return overallScore; }
    public void setOverallScore(int overallScore) { this.overallScore = overallScore; }
    public int getFillersCount() { return fillersCount; }
    public void setFillersCount(int fillersCount) { this.fillersCount = fillersCount; }
    public double getSilenceRatio() { return silenceRatio; }
    public void setSilenceRatio(double silenceRatio) { this.silenceRatio = silenceRatio; }
    public int getWordsPerMinute() { return wordsPerMinute; }
    public void setWordsPerMinute(int wordsPerMinute) { this.wordsPerMinute = wordsPerMinute; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
}
