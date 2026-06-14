package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountInfoTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        UserAccountInfo original = new UserAccountInfo();
        original.setId(1L);
        original.setAccount("testuser");
        original.setName("Test User");
        original.setPassword("encrypted123");
        original.setStaffId("STAFF001");
        original.setPosition("Operator");

        String json = mapper.writeValueAsString(original);
        UserAccountInfo restored = mapper.readValue(json, UserAccountInfo.class);

        assertThat(restored.getAccount()).isEqualTo("testuser");
        assertThat(restored.getName()).isEqualTo("Test User");
        assertThat(restored.getPassword()).isEqualTo("encrypted123");
        assertThat(restored.getStaffId()).isEqualTo("STAFF001");
        assertThat(restored.getPosition()).isEqualTo("Operator");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new UserAccountInfo());
        UserAccountInfo restored = mapper.readValue(json, UserAccountInfo.class);
        assertThat(restored).isNotNull();
    }
}
