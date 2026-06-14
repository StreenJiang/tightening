package com.tightening.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JsonUtilsTest {

    static class TestData {
        public String name;
        public int value;

        public TestData() {
        }

        public TestData(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    @Test
    void toJson_validObject() {
        TestData data = new TestData("test", 42);
        String json = JsonUtils.toJson(data);
        assertThat(json).contains("\"name\":\"test\"");
        assertThat(json).contains("\"value\":42");
    }

    @Test
    void parse_validJson() {
        String json = "{\"name\":\"hello\",\"value\":99}";
        TestData data = JsonUtils.parse(json, TestData.class);
        assertThat(data.name).isEqualTo("hello");
        assertThat(data.value).isEqualTo(99);
    }

    @Test
    void parse_invalidJson_throwsRuntimeException() {
        String badJson = "{bad json}";
        assertThatThrownBy(() -> JsonUtils.parse(badJson, TestData.class))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void objectMapper_shouldBeAvailable() {
        assertThat(JsonUtils.OBJECT_MAPPER).isNotNull();
    }
}
