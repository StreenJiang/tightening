package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurveDataDTOTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        CurveDataDTO original = new CurveDataDTO();
        original.setId(1L);
        original.setDeleted(0);
        original.setCreatorId(100L);
        original.setModifierId(200L);

        original.setTaskRecordId(1L);
        original.setWorkstationName("Station-01");
        original.setProductSideName("Left");
        original.setBoltSerialNum(1);
        original.setParameterSet(5);
        original.setTighteningId(1234567890);
        original.setTimestamp("2024-01-15 10:30:00");
        original.setDataType(1);
        original.setDataSamples("1.0,2.0,3.0,4.0");

        String json = mapper.writeValueAsString(original);
        CurveDataDTO restored = mapper.readValue(json, CurveDataDTO.class);

        assertThat(restored.getTaskRecordId()).isEqualTo(1L);
        assertThat(restored.getTighteningId()).isEqualTo(1234567890);
        assertThat(restored.getTimestamp()).isEqualTo("2024-01-15 10:30:00");
        assertThat(restored.getDataType()).isEqualTo(1);
        assertThat(restored.getDataSamples()).isEqualTo("1.0,2.0,3.0,4.0");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new CurveDataDTO());
        CurveDataDTO restored = mapper.readValue(json, CurveDataDTO.class);
        assertThat(restored).isNotNull();
        assertThat(restored.getId()).isNull();
    }
}
