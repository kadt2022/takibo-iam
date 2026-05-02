package com.takibo.identitycore.infrastructure.converter;

import com.takibo.identitycore.domain.rbac.model.GroupSource;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GroupSourceConverter implements AttributeConverter<GroupSource, String> {

    @Override
    public String convertToDatabaseColumn(GroupSource attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public GroupSource convertToEntityAttribute(String dbData) {
        return dbData == null ? null : GroupSource.valueOf(dbData);
    }
}
