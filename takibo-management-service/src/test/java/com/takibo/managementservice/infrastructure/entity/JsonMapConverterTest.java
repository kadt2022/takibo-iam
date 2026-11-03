package com.takibo.managementservice.infrastructure.entity;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonMapConverterTest {

    private final JsonMapConverter converter = new JsonMapConverter();

    @Test
    void additionalSettingsSurviveARoundTripThroughTheColumn() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("theme", "dark");
        settings.put("maxSessions", 3);
        settings.put("features", List.of("a", "b"));
        settings.put("nested", Map.of("enabled", true));

        String column = converter.convertToDatabaseColumn(settings);

        assertThat(column).isEqualTo(
                "{\"theme\":\"dark\",\"maxSessions\":3,\"features\":[\"a\",\"b\"],\"nested\":{\"enabled\":true}}"
        );
        assertThat(converter.convertToEntityAttribute(column)).isEqualTo(settings);
    }

    @Test
    void nullStaysNullInBothDirections() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void anEmptyMapIsStoredAsAnEmptyObject() {
        assertThat(converter.convertToDatabaseColumn(Map.of())).isEqualTo("{}");
        assertThat(converter.convertToEntityAttribute("{}")).isEmpty();
    }

    @Test
    void aCorruptedColumnIsReportedInsteadOfSilentlyIgnored() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("{not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot deserialize JSON");
        assertThatThrownBy(() -> converter.convertToEntityAttribute("[1,2]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot deserialize JSON");
    }

    @Test
    void aValueThatCannotBeSerializedIsReported() {
        Map<String, Object> settings = Map.of("unserializable", new Object());

        assertThatThrownBy(() -> converter.convertToDatabaseColumn(settings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot serialize JSON");
    }
}
