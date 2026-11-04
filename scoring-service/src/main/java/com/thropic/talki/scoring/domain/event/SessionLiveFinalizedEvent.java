package com.thropic.talki.scoring.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lo que scoring-service necesita de session.live.finalized: saber que la práctica
 * se cerró y de quién es. No conserva la transcripción ni las métricas del evento.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionLiveFinalizedEvent {

    private String sessionId;
    private String userId;

    public SessionLiveFinalizedEvent() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
