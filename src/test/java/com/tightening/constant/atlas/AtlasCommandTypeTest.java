package com.tightening.constant.atlas;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AtlasCommandTypeTest {

    @Test
    void fromMid_known() {
        assertThat(AtlasCommandType.fromMid(1)).isEqualTo(AtlasCommandType.CONNECT);
        assertThat(AtlasCommandType.fromMid(61)).isEqualTo(AtlasCommandType.TIGHTEN_DATA);
        assertThat(AtlasCommandType.fromMid(9999)).isEqualTo(AtlasCommandType.HEARTBEAT);
    }

    @Test
    void fromMid_unknown() {
        assertThat(AtlasCommandType.fromMid(-1)).isNull();
        assertThat(AtlasCommandType.fromMid(0)).isNull();
        assertThat(AtlasCommandType.fromMid(99999)).isNull();
    }

    @Test
    void getMid_getName() {
        assertThat(AtlasCommandType.CONNECT.getMid()).isEqualTo(1);
        assertThat(AtlasCommandType.CONNECT.getName()).isEqualTo("连接设备");
    }

    @Test
    void toString_containsMidAndName() {
        assertThat(AtlasCommandType.CONNECT).hasToString("0001 (连接设备)");
        assertThat(AtlasCommandType.TIGHTEN_DATA).hasToString("0061 (拧紧数据)");
    }

    @Test
    void codes_unique() {
        long distinctCount = java.util.Arrays.stream(AtlasCommandType.values())
                .mapToInt(AtlasCommandType::getMid)
                .distinct()
                .count();
        assertThat(distinctCount).isEqualTo(AtlasCommandType.values().length);
    }
}
