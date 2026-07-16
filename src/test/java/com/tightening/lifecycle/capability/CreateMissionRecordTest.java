package com.tightening.lifecycle.capability;

import com.tightening.entity.MissionRecord;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.MissionRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateMissionRecord Capability")
class CreateMissionRecordTest {

    @Mock private MissionRecordService missionRecordService;
    private CreateMissionRecord cap;

    @BeforeEach
    void setUp() {
        cap = new CreateMissionRecord(missionRecordService);
    }

    @Test
    @DisplayName("创建 MissionRecord 并回写 Context")
    void shouldCreateRecordAndSetOnContext() {
        MissionRecord record = new MissionRecord();
        record.setId(42L);
        when(missionRecordService.createRecord(1L, null, 0)).thenReturn(record);

        MissionContext ctx = minimalContext();
        CapabilityResult result = cap.execute(ctx);

        assertThat(result).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getMissionRecord().getId()).isEqualTo(42L);
        verify(missionRecordService).createRecord(1L, null, 0);
    }

    private static MissionContext minimalContext() {
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .build();
    }
}
