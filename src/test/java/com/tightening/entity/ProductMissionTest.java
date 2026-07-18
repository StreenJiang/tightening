package com.tightening.entity;

import com.tightening.constant.InspectionScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMissionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        ProductMission original = new ProductMission();
        original.setId(1L);
        original.setName("EngineAssembly");
        original.setMaxNgCount(5);
        original.setPasswordRequiredNgCount(1);
        original.setEnabled(1);
        original.setMultiDeviceIndependent(0);
        original.setSkipScrew(0);
        original.setIsInspection(0);
        original.setInspectionScope(InspectionScope.ALL);

        String json = mapper.writeValueAsString(original);
        ProductMission restored = mapper.readValue(json, ProductMission.class);

        assertThat(restored.getName()).isEqualTo("EngineAssembly");
        assertThat(restored.getMaxNgCount()).isEqualTo(5);
        assertThat(restored.getPasswordRequiredNgCount()).isEqualTo(1);
        assertThat(restored.getEnabled()).isEqualTo(1);
        assertThat(restored.getMultiDeviceIndependent()).isEqualTo(0);
        assertThat(restored.getSkipScrew()).isEqualTo(0);
        assertThat(restored.getIsInspection()).isEqualTo(0);
        assertThat(restored.getInspectionScope()).isEqualTo(InspectionScope.ALL);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductMission());
        ProductMission restored = mapper.readValue(json, ProductMission.class);
        assertThat(restored).isNotNull();
    }
}
