package com.tightening.constant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeTest {

    @Test
    void fromValue_shouldReturnCorrectValue() {
        assertThat(ImageType.fromValue("original")).contains(ImageType.ORIGINAL);
        assertThat(ImageType.fromValue("rendered")).contains(ImageType.RENDERED);
        assertThat(ImageType.fromValue("thumbnail")).contains(ImageType.THUMBNAIL);
    }

    @Test
    void fromValue_shouldReturnEmptyForInvalidValue() {
        assertThat(ImageType.fromValue("invalid")).isEmpty();
        assertThat(ImageType.fromValue("")).isEmpty();
        assertThat(ImageType.fromValue(null)).isEmpty();
    }

    @Test
    void values_shouldBeUnique() {
        var values = java.util.Arrays.stream(ImageType.values())
                .map(ImageType::getValue).distinct().count();
        assertThat(values).isEqualTo(ImageType.values().length);
    }
}
