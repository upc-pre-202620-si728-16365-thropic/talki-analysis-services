package com.thropic.talki.scoring.domain.model;

/** Estado de la evaluación de una práctica, visible para quien consulta el reporte. */
public enum AnalysisState {
    /** La práctica se cerró y su análisis aún no termina. */
    PENDING,
    /** Hay evidencia suficiente y puntuación calculada. */
    COMPLETED,
    /** La práctica no tiene evidencia para evaluar: no se calcula puntuación. */
    INSUFFICIENT_EVIDENCE,
    /** El cálculo falló: no se presenta ninguna puntuación. */
    FAILED
}
