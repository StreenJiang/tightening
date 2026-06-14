package com.tightening.constant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExtraDataKeysTest {
    @Test
    void barcode_key_shouldBeCorrect() { assertThat(ExtraDataKeys.BARCODE).isEqualTo("barcode"); }

    @Test
    void constructor_exists() {
        assertThat(ExtraDataKeys.class.getDeclaredConstructors()).hasSize(1);
    }
}
