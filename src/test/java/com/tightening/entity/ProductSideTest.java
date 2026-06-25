package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSideTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        byte[] imageData = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        byte[] renderedImageData = "rendered-preview".getBytes();
        byte[] thumbnailData = "thumb".getBytes();

        ProductSide original = new ProductSide();
        original.setId(1L);
        original.setProductMissionId(100L);
        original.setName("Side A");
        original.setImageData(imageData);
        original.setRenderedImageData(renderedImageData);
        original.setThumbnailData(thumbnailData);

        String json = mapper.writeValueAsString(original);
        ProductSide restored = mapper.readValue(json, ProductSide.class);

        assertThat(restored.getProductMissionId()).isEqualTo(100L);
        assertThat(restored.getName()).isEqualTo("Side A");
        assertThat(restored.getImageData()).containsExactly(imageData);
        assertThat(restored.getRenderedImageData()).containsExactly(renderedImageData);
        assertThat(restored.getThumbnailData()).containsExactly(thumbnailData);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductSide());
        ProductSide restored = mapper.readValue(json, ProductSide.class);
        assertThat(restored).isNotNull();
    }
}
