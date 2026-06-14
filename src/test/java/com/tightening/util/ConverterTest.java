package com.tightening.util;

import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.TighteningData;
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

    @Test
    void testEntity2Dto_single() {
        TighteningData entity = new TighteningData();
        entity.setVin("VIN123");
        entity.setTorque(45.5);

        TighteningDataDTO dto = Converter.entity2Dto(entity, TighteningDataDTO::new);

        assertNotNull(dto);
        assertEquals("VIN123", dto.getVin());
        assertEquals(45.5, dto.getTorque(), 0.001);
    }

    @Test
    void testEntity2Dto_list() {
        TighteningData e1 = new TighteningData().setVin("VIN1").setTorque(10.0);
        TighteningData e2 = new TighteningData().setVin("VIN2").setTorque(20.0);
        List<TighteningData> entities = List.of(e1, e2);

        List<TighteningDataDTO> dtos = Converter.entity2Dto(entities, TighteningDataDTO::new);

        assertEquals(2, dtos.size());
        assertEquals("VIN1", dtos.get(0).getVin());
        assertEquals(10.0, dtos.get(0).getTorque(), 0.001);
        assertEquals("VIN2", dtos.get(1).getVin());
        assertEquals(20.0, dtos.get(1).getTorque(), 0.001);
    }

    @Test
    void testDto2Entity_single() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setVin("DTO_VIN");
        dto.setTorque(99.9);

        TighteningData entity = Converter.dto2Entity(dto, TighteningData::new);

        assertNotNull(entity);
        assertEquals("DTO_VIN", entity.getVin());
        assertEquals(99.9, entity.getTorque(), 0.001);
    }

    @Test
    void testDto2Entity_list() {
        TighteningDataDTO d1 = new TighteningDataDTO();
        d1.setVin("D1").setTorque(1.0);
        TighteningDataDTO d2 = new TighteningDataDTO();
        d2.setVin("D2").setTorque(2.0);
        List<TighteningDataDTO> dtos = List.of(d1, d2);

        List<TighteningData> entities = Converter.dto2Entity(dtos, TighteningData::new);

        assertEquals(2, entities.size());
        assertEquals("D1", entities.get(0).getVin());
        assertEquals(1.0, entities.get(0).getTorque(), 0.001);
        assertEquals("D2", entities.get(1).getVin());
        assertEquals(2.0, entities.get(1).getTorque(), 0.001);
    }
}