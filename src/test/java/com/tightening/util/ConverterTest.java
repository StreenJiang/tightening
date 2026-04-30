package com.tightening.util;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConverterTest {
    // 模拟你的业务对象（Jackson 要求有无参构造）
    static class CurvePoint {
        public double x;
        public double y;

        public CurvePoint() { }

        public CurvePoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    @Test
    void testFromList_ValidList_ShouldReturnJsonArray() {
        // Given
        List<CurvePoint> points = List.of(
                new CurvePoint(1.5, 2.5),
                new CurvePoint(3.0, 4.0)
        );

        // When
        String json = Converter.fromList(points);

        // Then
        assertNotNull(json);
        assertTrue(json.startsWith("["));
        assertTrue(json.endsWith("]"));
        assertTrue(json.contains("\"x\":1.5"));
        assertTrue(json.contains("\"y\":2.5"));
    }

    @Test
    void testFromJsonToList_ValidJson_ShouldReturnStronglyTypedList() {
        // Given
        String json = "[{\"x\":1.5,\"y\":2.5},{\"x\":3.0,\"y\":4.0}]";

        // When
        List<CurvePoint> result = Converter.fromJsonToList(json, CurvePoint.class);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        // 验证数值精度
        assertEquals(1.5, result.getFirst().x, 0.001);
        assertEquals(2.5, result.getFirst().y, 0.001);
        // 核心断言：验证类型确实是 CurvePoint，而非 LinkedHashMap
        assertInstanceOf(CurvePoint.class, result.getFirst());
    }

    @Test
    void testFromJsonToList_NullOrBlank_ShouldReturnEmptyList() {
        assertEquals(0, Converter.fromJsonToList(null, CurvePoint.class).size());
        assertEquals(0, Converter.fromJsonToList("   ", CurvePoint.class).size());
        assertEquals(0, Converter.fromJsonToList("", CurvePoint.class).size());
    }

    @Test
    void testFromJsonToList_InvalidJson_ShouldThrowRuntimeException() {
        // Given
        String badJson = "{this is not json}";

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                Converter.fromJsonToList(badJson, CurvePoint.class)
        );
        assertTrue(ex.getMessage().contains("Convert from json to list failed..."));
        // 验证底层异常被正确包装
        assertNotNull(ex.getCause());
    }
}