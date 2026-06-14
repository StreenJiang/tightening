package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountInfoDTOTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        UserAccountInfoDTO original = new UserAccountInfoDTO();
        original.setId(1L);
        original.setDeleted(0);
        original.setCreatorId(100L);
        original.setModifierId(200L);

        original.setStaffId("S001");
        original.setName("Zhang San");
        original.setPosition("Operator");
        original.setAccount("zhangsan");
        original.setPassword("password123");
        original.setOperationPassword("op456");

        String json = mapper.writeValueAsString(original);
        UserAccountInfoDTO restored = mapper.readValue(json, UserAccountInfoDTO.class);

        assertThat(restored.getStaffId()).isEqualTo("S001");
        assertThat(restored.getName()).isEqualTo("Zhang San");
        assertThat(restored.getPosition()).isEqualTo("Operator");
        assertThat(restored.getAccount()).isEqualTo("zhangsan");
        assertThat(restored.getPassword()).isEqualTo("password123");
        assertThat(restored.getOperationPassword()).isEqualTo("op456");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new UserAccountInfoDTO());
        UserAccountInfoDTO restored = mapper.readValue(json, UserAccountInfoDTO.class);
        assertThat(restored).isNotNull();
        assertThat(restored.getId()).isNull();
    }
}
