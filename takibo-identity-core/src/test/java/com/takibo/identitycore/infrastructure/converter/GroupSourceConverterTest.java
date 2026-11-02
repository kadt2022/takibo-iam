package com.takibo.identitycore.infrastructure.converter;

import com.takibo.identitycore.domain.rbac.model.GroupSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupSourceConverterTest {

    private final GroupSourceConverter converter = new GroupSourceConverter();

    @ParameterizedTest
    @EnumSource(GroupSource.class)
    void everySource_roundTripsThroughItsName(GroupSource source) {
        String column = converter.convertToDatabaseColumn(source);

        assertThat(column).isEqualTo(source.name());
        assertThat(converter.convertToEntityAttribute(column)).isEqualTo(source);
    }

    @Test
    void null_staysNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void unknownStoredSource_failsLoudly_insteadOfBeingReinterpreted() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("ROOT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
