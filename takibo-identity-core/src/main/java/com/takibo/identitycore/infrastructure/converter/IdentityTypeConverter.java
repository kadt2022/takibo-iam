package com.takibo.identitycore.infrastructure.converter;

import com.takibo.identitycore.domain.model.IdentityType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class IdentityTypeConverter implements AttributeConverter<IdentityType, String> {

    @Override
    public String convertToDatabaseColumn(IdentityType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public IdentityType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : IdentityType.valueOf(dbData);
    }
}
