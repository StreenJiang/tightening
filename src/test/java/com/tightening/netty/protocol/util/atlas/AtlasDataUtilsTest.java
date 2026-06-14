package com.tightening.netty.protocol.util.atlas;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class AtlasDataUtilsTest {

    // ---- parseAsciiInt(byte[], int, int) ----

    @Test
    void parseAsciiInt_offsetLength_valid() {
        byte[] data = "   42".getBytes(StandardCharsets.US_ASCII);
        assertThat(AtlasDataUtils.parseAsciiInt(data, 0, data.length)).isEqualTo(42);
    }

    @Test
    void parseAsciiInt_offsetLength_empty() {
        byte[] data = "    ".getBytes(StandardCharsets.US_ASCII);
        assertThat(AtlasDataUtils.parseAsciiInt(data, 0, data.length)).isNull();
    }

    // ---- parseAsciiInt(byte[]) ----

    @Test
    void parseAsciiInt_valid() {
        byte[] data = "123".getBytes(StandardCharsets.US_ASCII);
        assertThat(AtlasDataUtils.parseAsciiInt(data)).isEqualTo(123);
    }

    @Test
    void parseAsciiInt_empty() {
        byte[] data = "   ".getBytes(StandardCharsets.US_ASCII);
        assertThat(AtlasDataUtils.parseAsciiInt(data)).isZero();
    }

    // ---- parseAsciiInt(ByteBuf, int) ----

    @Test
    void parseAsciiInt_byteBuf_valid() {
        ByteBuf buf = Unpooled.wrappedBuffer("456".getBytes(StandardCharsets.US_ASCII));
        assertThat(AtlasDataUtils.parseAsciiInt(buf, 3)).isEqualTo(456);
    }

    @Test
    void parseAsciiInt_byteBuf_empty() {
        ByteBuf buf = Unpooled.wrappedBuffer("   ".getBytes(StandardCharsets.US_ASCII));
        assertThat(AtlasDataUtils.parseAsciiInt(buf, 3)).isNull();
    }

    // ---- encodeIntField ----

    @Test
    void encodeIntField_valid() {
        assertThat(AtlasDataUtils.encodeIntField(123, 6)).isEqualTo("000123");
    }

    @Test
    void encodeIntField_null() {
        assertThat(AtlasDataUtils.encodeIntField(null, 3)).isEqualTo("   ");
    }

    // ---- encodeStringField ----

    @Test
    void encodeStringField_valid() {
        assertThat(AtlasDataUtils.encodeStringField("ABC", 6)).isEqualTo("ABC   ");
    }

    @Test
    void encodeStringField_null() {
        assertThat(AtlasDataUtils.encodeStringField(null, 3)).isEqualTo("   ");
    }

    @Test
    void encodeStringField_truncate() {
        assertThat(AtlasDataUtils.encodeStringField("TOO_LONG", 3)).isEqualTo("TOO");
    }

    // ---- encodeDoubleField ----

    @Test
    void encodeDoubleField_valid() {
        assertThat(AtlasDataUtils.encodeDoubleField(12.34, 7, 2)).isEqualTo("  12.34");
    }

    @Test
    void encodeDoubleField_null() {
        assertThat(AtlasDataUtils.encodeDoubleField(null, 5, 2)).isEqualTo("     ");
    }

    // ---- formatAscii ----

    @Test
    void formatAscii_valid() {
        byte[] result = AtlasDataUtils.formatAscii(5, 3);
        assertThat(new String(result, StandardCharsets.US_ASCII)).isEqualTo("005");
    }

    @Test
    void formatAscii_null() {
        byte[] result = AtlasDataUtils.formatAscii(null, 3);
        assertThat(new String(result, StandardCharsets.US_ASCII)).isEqualTo("   ");
    }

    @Test
    void formatAscii_tooLong() {
        assertThatThrownBy(() -> AtlasDataUtils.formatAscii(1234, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
