package com.takibo.adp.spring.adapter;

import com.takibo.adp.api.DecisionRequest;
import com.takibo.adp.api.Thresholds;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La politique lit l'heure dans le fuseau de la JVM : les instants sont donc construits
 * dans ce même fuseau pour que le test reste déterministe quelle que soit la machine.
 */
class DefaultThresholdPolicyTest {

    private static final Instant WEDNESDAY_2PM = local(2026, 10, 21, 14, 0);
    private static final Instant WEDNESDAY_9PM = local(2026, 10, 21, 21, 0);

    private final DefaultThresholdPolicy policy = new DefaultThresholdPolicy();

    private static Instant local(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant();
    }

    private Thresholds thresholds(String path, Set<String> roles, Instant timestamp) {
        return policy.calculate(new DecisionRequest("alice", "org", "space", roles, Set.of(), path, "GET",
                timestamp, null, null, null, null, null, null, null, null, null, null, null, 15, "1.0", Map.of()));
    }

    @Test
    void ordinaryResourceDuringBusinessHours_usesBaseline() {
        assertThat(thresholds("/api/orders", Set.of("USER"), WEDNESDAY_2PM))
                .isEqualTo(new Thresholds(75.0, 50.0, "baseline"));
        assertThat(thresholds(null, Set.of(), WEDNESDAY_2PM))
                .isEqualTo(new Thresholds(75.0, 50.0, "baseline"));
    }

    @Test
    void adminAndPlatformResources_lowerBothThresholds() {
        assertThat(thresholds("/api/admin/users", Set.of(), WEDNESDAY_2PM))
                .isEqualTo(new Thresholds(55.0, 35.0, "admin resource"));
        assertThat(thresholds("/api/platform/spaces", Set.of(), WEDNESDAY_2PM))
                .isEqualTo(new Thresholds(55.0, 35.0, "admin resource"));
    }

    @Test
    void publicResources_raiseBothThresholds() {
        assertThat(thresholds("/public/health", Set.of(), WEDNESDAY_2PM))
                .isEqualTo(new Thresholds(85.0, 60.0, "public resource"));
    }

    @Test
    void adminPathWinsOverPublicPath() {
        assertThat(thresholds("/admin/public", Set.of(), WEDNESDAY_2PM).reason()).isEqualTo("admin resource");
    }

    @Test
    void platformAdminRole_lowersDenyThresholdOnly() {
        assertThat(thresholds("/api/orders", Set.of("R_TAKIBO_PLATFORM_ADMIN"), WEDNESDAY_2PM))
                .isEqualTo(new Thresholds(65.0, 50.0, "admin role"));
    }

    @Test
    void outsideBusinessHours_lowersBothThresholds() {
        assertThat(thresholds("/api/orders", Set.of(), WEDNESDAY_9PM))
                .isEqualTo(new Thresholds(60.0, 40.0, "outside business hours"));
        assertThat(thresholds("/api/orders", Set.of(), local(2026, 10, 24, 14, 0)))
                .isEqualTo(new Thresholds(60.0, 40.0, "outside business hours"));
    }

    @Test
    void businessHourBoundaries_areInclusiveFrom8To18() {
        assertThat(thresholds(null, Set.of(), local(2026, 10, 21, 7, 59)).reason()).isEqualTo("outside business hours");
        assertThat(thresholds(null, Set.of(), local(2026, 10, 21, 8, 0)).reason()).isEqualTo("baseline");
        assertThat(thresholds(null, Set.of(), local(2026, 10, 21, 18, 59)).reason()).isEqualTo("baseline");
        assertThat(thresholds(null, Set.of(), local(2026, 10, 21, 19, 0)).reason()).isEqualTo("outside business hours");
    }

    @Test
    void cumulativeAdjustments_areClampedToSafetyFloors() {
        // 75 - 20 - 10 - 15 = 30 -> plancher 50 ; 50 - 15 - 10 = 25 (plancher atteint exactement)
        assertThat(thresholds("/api/admin", Set.of("R_TAKIBO_PLATFORM_ADMIN"), WEDNESDAY_9PM))
                .isEqualTo(new Thresholds(50.0, 25.0, "admin resource, admin role, outside business hours"));
    }

    @Test
    void publicResourceOutsideBusinessHours_combinesAdjustments() {
        assertThat(thresholds("/public/docs", Set.of(), WEDNESDAY_9PM))
                .isEqualTo(new Thresholds(70.0, 50.0, "public resource, outside business hours"));
    }

    @Test
    void challengeThresholdStaysBelowDenyThreshold_forEveryCombination() {
        String[] paths = {null, "/api/orders", "/api/admin", "/api/platform", "/public"};
        Set<?>[] roleSets = {Set.of(), Set.of("R_TAKIBO_PLATFORM_ADMIN")};
        Instant[] times = {WEDNESDAY_2PM, WEDNESDAY_9PM};

        for (String path : paths) {
            for (Set<?> roles : roleSets) {
                for (Instant time : times) {
                    @SuppressWarnings("unchecked")
                    Thresholds t = thresholds(path, (Set<String>) roles, time);
                    assertThat(t.challengeThreshold()).as("%s %s %s", path, roles, time).isLessThan(t.denyThreshold());
                    assertThat(t.denyThreshold()).isBetween(50.0, 95.0);
                    assertThat(t.challengeThreshold()).isBetween(25.0, 70.0);
                }
            }
        }
    }
}
