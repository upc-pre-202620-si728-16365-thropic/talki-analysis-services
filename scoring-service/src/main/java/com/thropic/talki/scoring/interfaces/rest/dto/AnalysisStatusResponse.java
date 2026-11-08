package com.thropic.talki.scoring.interfaces.rest.dto;

import com.thropic.talki.scoring.domain.model.AnalysisState;

/** Respuesta 202: la práctica aún no tiene evaluación que mostrar. */
public record AnalysisStatusResponse(String sessionId, AnalysisState analysisState, String message) {}
