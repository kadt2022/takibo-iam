package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.SpaceId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class GroupTest {

    private static final SpaceId SPACE_ID = SpaceId.of(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"));

    @Test
    void createNew_setsNatureOnGroup() {
        Group group = Group.createNew(SPACE_ID, "GRP_FINANCE", "Finance", null, GroupNature.BUSINESS);

        assertThat(group.getNature()).isEqualTo(GroupNature.BUSINESS);
        assertThat(group.getCode()).isEqualTo("GRP_FINANCE");
        assertThat(group.getSpaceId()).isEqualTo(SPACE_ID);
    }

    @Test
    void createNew_nullNature_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> Group.createNew(SPACE_ID, "GRP_X", "X", null, null))
                .withMessage("nature");
    }
}
