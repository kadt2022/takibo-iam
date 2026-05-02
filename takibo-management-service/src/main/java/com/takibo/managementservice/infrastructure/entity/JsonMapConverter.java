package com.takibo.managementservice.infrastructure.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;

@Converter(autoApply = false)
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  @Override public String convertToDatabaseColumn(Map<String, Object> attribute) {
    try { return attribute == null ? null : MAPPER.writeValueAsString(attribute); }
    catch (Exception e) { throw new IllegalArgumentException("Cannot serialize JSON", e); }
  }
  @Override public Map<String, Object> convertToEntityAttribute(String dbData) {
    try { return dbData == null ? null : MAPPER.readValue(dbData, new TypeReference<>(){}); }
    catch (Exception e) { throw new IllegalArgumentException("Cannot deserialize JSON", e); }
  }
}
