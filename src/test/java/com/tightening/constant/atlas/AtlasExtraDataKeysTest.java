package com.tightening.constant.atlas;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AtlasExtraDataKeysTest {
    @Test
    void allKeys_shouldBeNonEmpty() {
        assertThat(AtlasExtraDataKeys.STRATEGY).isNotBlank();
        assertThat(AtlasExtraDataKeys.STRATEGY_OPTIONS).isNotBlank();
        assertThat(AtlasExtraDataKeys.TIGHTENING_ERROR_STATUS).isNotBlank();
        assertThat(AtlasExtraDataKeys.COMPENSATED_ANGLE).isNotBlank();
        assertThat(AtlasExtraDataKeys.STAGE_RESULTS).isNotBlank();
        assertThat(AtlasExtraDataKeys.TOOL_SERIAL_NUMBER).isNotBlank();
        assertThat(AtlasExtraDataKeys.IDENTIFIER_RESULT_PART_2).isNotBlank();
        assertThat(AtlasExtraDataKeys.IDENTIFIER_RESULT_PART_3).isNotBlank();
        assertThat(AtlasExtraDataKeys.IDENTIFIER_RESULT_PART_4).isNotBlank();
        assertThat(AtlasExtraDataKeys.CUSTOMER_TIGHTENING_ERROR_CODE).isNotBlank();
        assertThat(AtlasExtraDataKeys.PV_COMPENSATE_VALUE).isNotBlank();
        assertThat(AtlasExtraDataKeys.TIGHTENING_ERROR_STATUS_2).isNotBlank();
        assertThat(AtlasExtraDataKeys.FINAL_ANGLE_DECIMAL).isNotBlank();
        assertThat(AtlasExtraDataKeys.TOTAL_STAGES).isNotBlank();
        assertThat(AtlasExtraDataKeys.COMPLETED_STAGES).isNotBlank();
    }

    @Test
    void constructor_exists() {
        assertThat(AtlasExtraDataKeys.class.getDeclaredConstructors()).hasSize(1);
    }
}
