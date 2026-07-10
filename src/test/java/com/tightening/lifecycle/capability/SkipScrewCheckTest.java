package com.tightening.lifecycle.capability;

import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tightening.lifecycle.capability.CapabilityResult.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkipScrewCheck")
class SkipScrewCheckTest {

    SkipScrewCheck cap;

    @BeforeEach
    void setUp() { cap = new SkipScrewCheck(); }

    @Test
    @DisplayName("id")
    void id() { assertThat(cap.id()).isEqualTo("SkipScrewCheck"); }

    @Test
    @DisplayName("skipScrew=false → precondition false")
    void notEnabled() {
        var ctx = MissionContext.builder()
                .missionData(new ProductMission().setSkipScrew(0)).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("skipScrew=true → precondition true, execute returns Interrupt")
    void enabled() {
        var ctx = MissionContext.builder()
                .missionData(new ProductMission().setSkipScrew(1)).build();
        assertThat(cap.precondition(ctx)).isTrue();
        assertThat(cap.execute(ctx)).isEqualTo(Interrupt);
    }
}
