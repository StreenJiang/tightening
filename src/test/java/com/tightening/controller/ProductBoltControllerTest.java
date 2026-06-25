package com.tightening.controller;

import com.tightening.dto.ProductBoltDTO;
import com.tightening.entity.ProductBolt;
import com.tightening.service.ProductBoltService;
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
class ProductBoltControllerTest {

    @Mock private ProductBoltService boltService;
    @InjectMocks private ProductBoltController controller;

    @Test
    void list_shouldReturnOk() {
        when(boltService.listBySideId(1L)).thenReturn(List.of());
        assertThat(controller.list(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void create_shouldReturnOk() {
        ProductBoltDTO dto = new ProductBoltDTO();
        dto.setProductSideId(1L);
        dto.setBoltSerialNum(1);
        dto.setTorqueMin(5.0);
        dto.setTorqueMax(25.0);
        assertThat(controller.create(dto, 1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void update_shouldReturnOk() {
        ProductBoltDTO dto = new ProductBoltDTO();
        dto.setProductSideId(1L);
        dto.setBoltSerialNum(1);
        dto.setTorqueMin(5.0);
        dto.setTorqueMax(25.0);
        assertThat(controller.update(1L, dto, 1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void delete_shouldReturnOk() {
        assertThat(controller.delete(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }
}
