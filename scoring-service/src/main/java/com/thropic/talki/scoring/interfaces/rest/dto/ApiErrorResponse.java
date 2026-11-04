package com.thropic.talki.scoring.interfaces.rest.dto;

/** Forma común de los errores del servicio. */
public record ApiErrorResponse(String code, String message, String timestamp) {}
