package com.tightening.controller;

import com.tightening.constant.ImageType;
import com.tightening.dto.ProductSideDTO;
import com.tightening.entity.ProductSide;
import com.tightening.service.ProductSideService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSideControllerTest {

    @Mock private ProductSideService sideService;
    @InjectMocks private ProductSideController controller;

    @Test
    void list_shouldReturnOk() {
        when(sideService.listByMissionId(1L)).thenReturn(List.of());
        assertThat(controller.list(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void create_shouldReturnOk() {
        ProductSideDTO dto = new ProductSideDTO();
        dto.setName("Side A");
        dto.setProductMissionId(1L);
        assertThat(controller.create(dto).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void update_shouldReturnOk() {
        ProductSideDTO dto = new ProductSideDTO();
        dto.setName("Side A Updated");
        dto.setProductMissionId(1L);
        assertThat(controller.update(1L, dto).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void delete_shouldReturnOk() {
        assertThat(controller.delete(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void getImage_notFound_shouldReturn404() {
        when(sideService.getImageData(999L, ImageType.RENDERED)).thenReturn(null);
        assertThat(controller.getImage(999L, "rendered").getStatusCode().is4xxClientError()).isTrue();
    }
}
