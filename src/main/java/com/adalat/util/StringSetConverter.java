package com.adalat.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Converter
public class StringSetConverter implements AttributeConverter<Set<String>, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Set<String> set) {
        try {
            if (set == null) {
                return null;
            }
            return objectMapper.writeValueAsString(set);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting set to JSON", e);
        }
    }

    @Override
    public Set<String> convertToEntityAttribute(String json) {
        try {
            if (json == null || json.isEmpty()) {
                return new HashSet<>();
            }
            return objectMapper.readValue(json, new TypeReference<Set<String>>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("Error converting JSON to set", e);
        }
    }
}
