package com.tightening.lifecycle;

import com.tightening.constant.DeviceType;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.judgment.JudgmentStrategy;
import com.tightening.service.MissionRecordService;
import com.tightening.service.TighteningDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("LifecycleEngineFactory")
class LifecycleEngineFactoryTest {

    @Mock private MissionRecordService missionRecordService;
    @Mock private TighteningDataService tighteningDataService;
    @Mock private JudgmentStrategy judgmentStrategy;

    private LifecycleEngineFactory factory;

    @BeforeEach
    void setUp() {
        factory = new LifecycleEngineFactory(
            missionRecordService, tighteningDataService,
            Map.of(DeviceType.ATLAS_PF4000, judgmentStrategy));
    }

    @Test
    @DisplayName("createEngine 返回已组装的引擎")
    void shouldCreateEngineWithContext() {
        var mission = new ProductMission();
        mission.setId(1L);
        var engine = factory.createEngine(mission, List.of(), Map.of(), false);

        assertThat(engine).isNotNull();
        assertThat(engine.getContext().getProductMissionId()).isEqualTo(1L);
        assertThat(engine.isAlive()).isFalse();
    }
}
