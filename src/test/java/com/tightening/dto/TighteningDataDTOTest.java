package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TighteningDataDTOTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        TighteningDataDTO original = new TighteningDataDTO();
        original.setId(1L);
        original.setDeleted(0);
        original.setCreatorId(100L);
        original.setModifierId(200L);

        original.setTaskRecordId(1L);
        original.setWorkstationName("Station-01");
        original.setToolName("Tool-A");
        original.setToolTypeName("PF6000");
        original.setProductSideName("Left");
        original.setBoltSerialNum(1);
        original.setArmLocation("Arm-1");
        original.setParameterSet(5);
        original.setParameterSetName("PSET-005");

        original.setTighteningId(1234567890L);
        original.setTighteningStatus(1);
        original.setResultType(1);
        original.setTorqueStatus(1);
        original.setAngleStatus(1);
        original.setRundownAngleStatus(1);
        original.setTorqueValuesUnit(1);

        original.setTorqueMinLimit(10.0);
        original.setTorqueMaxLimit(50.0);
        original.setTorqueFinalTarget(30.0);
        original.setTorque(29.5);
        original.setAngleMinLimit(90.0);
        original.setAngleMaxLimit(180.0);
        original.setAngleFinalTarget(135.0);
        original.setAngle(135.2);
        original.setRundownAngleMinLimit(0.0);
        original.setRundownAngleMaxLimit(360.0);
        original.setRundownAngle(45.0);

        original.setTimestamp("2024-01-15 10:30:00");

        original.setCellId(1);
        original.setChannelId(2);
        original.setControllerName("Controller-01");
        original.setVin("VIN1234567890");
        original.setJobId(10);
        original.setBatchSize(100);
        original.setBatchCounter(1);
        original.setBatchStatus(0);

        original.setRevision(1);
        original.setExtraData("{\"key\":\"value\"}");

        String json = mapper.writeValueAsString(original);
        TighteningDataDTO restored = mapper.readValue(json, TighteningDataDTO.class);

        assertThat(restored.getVin()).isEqualTo("VIN1234567890");
        assertThat(restored.getTorque()).isCloseTo(29.5, within(0.001));
        assertThat(restored.getAngle()).isCloseTo(135.2, within(0.001));
        assertThat(restored.getParameterSet()).isEqualTo(5);
        assertThat(restored.getTighteningId()).isEqualTo(1234567890L);
        assertThat(restored.getTimestamp()).isEqualTo("2024-01-15 10:30:00");
        assertThat(restored.getRevision()).isEqualTo(1);
        assertThat(restored.getExtraData()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new TighteningDataDTO());
        TighteningDataDTO restored = mapper.readValue(json, TighteningDataDTO.class);
        assertThat(restored).isNotNull();
        assertThat(restored.getId()).isNull();
    }
}
