package com.tightening.lifecycle.capability;

import com.tightening.entity.MissionRecord;
import com.tightening.entity.ProductMission;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.MissionContext;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreData Capability")
class StoreDataTest {

    @Mock private TighteningDataService tighteningDataService;
    private StoreData cap;

    @BeforeEach
    void setUp() {
        cap = new StoreData(tighteningDataService);
    }

    @Test
    @DisplayName("存储数据并关联 MissionRecord")
    void shouldStoreDataWithMissionRecordId() {
        var data = new TighteningData().setTighteningId(100L);
        var record = new MissionRecord();
        record.setId(42L);
        MissionContext ctx = ctxWith(data, record);

        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(data.getMissionRecordId()).isEqualTo(42L);
        verify(tighteningDataService).save(data);
        assertThat(ctx.getTighteningDataList()).contains(data);
        assertThat(ctx.getCurrentOperationData()).isNull();
    }

    @Test
    @DisplayName("无 data 时 precondition 返回 false")
    void shouldSkipWhenNoData() {
        MissionContext ctx = ctxWith(null, new MissionRecord());
        ctx.getMissionRecord().setId(1L);
        assertThat(cap.precondition(ctx)).isFalse();
    }

    private static MissionContext ctxWith(TighteningData data, MissionRecord record) {
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).currentOperationData(data)
            .missionRecord(record).build();
    }
}
