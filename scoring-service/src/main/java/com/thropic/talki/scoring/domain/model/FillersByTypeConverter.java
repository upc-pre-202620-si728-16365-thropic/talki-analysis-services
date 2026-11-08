package com.thropic.talki.scoring.domain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

/** Guarda el conteo de muletillas por tipo como JSON, igual en H2 y PostgreSQL. */
@Converter
public class FillersByTypeConverter implements AttributeConverter<Map<String, Integer>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Integer> fillers) {
        if (fillers == null) return null;
        try {
            return MAPPER.writeValueAsString(fillers);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar fillersByType", e);
        }
    }

    @Override
    public Map<String, Integer> convertToEntityAttribute(String json) {
        if (json == null) return null;
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("fillers_by_type inválido", e);
        }
    }
}
