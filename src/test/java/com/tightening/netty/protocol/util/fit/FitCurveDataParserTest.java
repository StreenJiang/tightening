package com.tightening.netty.protocol.util.fit;

import com.tightening.dto.CurveDataDTO;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FitCurveDataParserTest {

    @Test
    void parse_shouldExtractKeyFields() {
        // 按协议格式构造数据：tighteningId(4B) + 2个曲线点(各12B) = 28B
        ByteBuffer buf = ByteBuffer.allocate(4 + 2 * 12).order(ByteOrder.LITTLE_ENDIAN);

        // tighteningId = 100
        buf.putInt(100);
        // Point 1: time=0.1s, torque=5.0Nm, angle=10.0deg
        buf.putFloat(0.1f);
        buf.putFloat(5.0f);
        buf.putFloat(10.0f);
        // Point 2: time=0.2s, torque=15.0Nm, angle=90.0deg
        buf.putFloat(0.2f);
        buf.putFloat(15.0f);
        buf.putFloat(90.0f);

        CurveDataDTO dto = FitCurveDataParser.parse(buf.array());

        assertThat(dto.getTighteningId()).isEqualTo(100);
        assertThat(dto.getDataSamples()).isNotNull();
        // 2 个点的 JSON 字符串包含 2 个对象
        assertThat(dto.getDataSamples()).contains("\"torque\":5.0");
        assertThat(dto.getDataSamples()).contains("\"torque\":15.0");
    }

    @Test
    void parse_nullData_shouldThrow() {
        assertThatThrownBy(() -> FitCurveDataParser.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数据区长度不足");
    }

    @Test
    void parse_dataTooShort_shouldThrow() {
        byte[] data = new byte[2];
        assertThatThrownBy(() -> FitCurveDataParser.parse(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数据区长度不足");
    }

    @Test
    void parse_zeroPoints_shouldThrow() {
        byte[] data = new byte[4]; // only tighteningId, 0 points
        assertThatThrownBy(() -> FitCurveDataParser.parse(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有曲线点数据");
    }

    @Test
    void parse_singlePoint_shouldReturnOnePoint() {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(4 + 12).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putInt(100);
        buf.putFloat(0.1f);
        buf.putFloat(5.0f);
        buf.putFloat(10.0f);

        CurveDataDTO dto = FitCurveDataParser.parse(buf.array());
        assertThat(dto.getTighteningId()).isEqualTo(100);
        assertThat(dto.getDataSamples()).isNotNull();
        assertThat(dto.getDataSamples()).contains("\"torque\":5.0");
    }
}
