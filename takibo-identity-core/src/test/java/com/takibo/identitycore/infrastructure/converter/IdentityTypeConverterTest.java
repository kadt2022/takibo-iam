package com.takibo.identitycore.infrastructure.converter;

import com.takibo.identitycore.domain.model.IdentityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityTypeConverterTest {

    private final IdentityTypeConverter converter = new IdentityTypeConverter();

    @ParameterizedTest
    @EnumSource(IdentityType.class)
    void everyType_roundTripsThroughItsName(IdentityType type) {
        String column = converter.convertToDatabaseColumn(type);

        assertThat(column).isEqualTo(type.name());
        assertThat(converter.convertToEntityAttribute(column)).isEqualTo(type);
    }

    @Test
    void null_staysNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void unknownStoredType_failsLoudly() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("ROBOT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
