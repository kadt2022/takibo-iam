package com.takibo.identitycore.infrastructure.converter;

import com.takibo.identitycore.domain.rbac.model.RoleSource;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RoleSourceConverter implements AttributeConverter<RoleSource, String> {

    @Override
    public String convertToDatabaseColumn(RoleSource attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public RoleSource convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RoleSource.valueOf(dbData);
    }
}
