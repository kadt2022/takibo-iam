package com.takibo.outbox.jpa.mapper;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class OutboxJsonMapper {

    private final ObjectMapper objectMapper;

    public OutboxJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Named("stringToJsonNode")
    public JsonNode stringToJsonNode(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid outbox payload JSON", e);
        }
    }

    @Named("jsonNodeToString")
    public String jsonNodeToString(JsonNode node) {
        return node == null ? null : node.toString();
    }
}
