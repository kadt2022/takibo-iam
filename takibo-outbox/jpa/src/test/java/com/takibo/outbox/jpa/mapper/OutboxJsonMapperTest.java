package com.takibo.outbox.jpa.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxJsonMapperTest {

    private final OutboxJsonMapper mapper = new OutboxJsonMapper(new ObjectMapper());

    @Test
    void parsesPayloadJson() {
        assertThat(mapper.stringToJsonNode("{\"orgId\":\"o-1\",\"n\":2}").get("n").asInt()).isEqualTo(2);
    }

    @Test
    void missingOrBlankPayload_becomesJsonNull() {
        assertThat(mapper.stringToJsonNode(null).isNull()).isTrue();
        assertThat(mapper.stringToJsonNode("  ").isNull()).isTrue();
    }

    @Test
    void invalidPayload_isRejected() {
        assertThatThrownBy(() -> mapper.stringToJsonNode("{not json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid outbox payload JSON");
    }

    @Test
    void serializesNodes_andKeepsNull() {
        assertThat(mapper.jsonNodeToString(mapper.stringToJsonNode("{\"a\":1}"))).isEqualTo("{\"a\":1}");
        assertThat(mapper.jsonNodeToString(null)).isNull();
    }
}
