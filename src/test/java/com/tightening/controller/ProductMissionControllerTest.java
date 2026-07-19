package com.tightening.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tightening.dto.ApiResponse;
import com.tightening.dto.PageResult;
import com.tightening.dto.ProductMissionDTO;
import com.tightening.dto.ProductMissionDetailDTO;
import com.tightening.service.ProductMissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductMissionControllerTest {

    @Mock private ProductMissionService missionService;
    @InjectMocks private ProductMissionController controller;

    @Test
    void list_shouldReturnOk() {
        when(missionService.listByPage(eq(1), eq(100), isNull())).thenReturn(PageResult.of(new Page<>(1, 100), List.of()));

        ResponseEntity<ApiResponse<PageResult<ProductMissionDTO>>> response = controller.list(1, 100, null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(200);
    }

    @Test
    void list_shouldPassNameToService_whenNameProvided() {
        when(missionService.listByPage(1, 100, "Test")).thenReturn(PageResult.of(new Page<>(1, 100), List.of()));

        ResponseEntity<ApiResponse<PageResult<ProductMissionDTO>>> response = controller.list(1, 100, "Test");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(200);
    }

    @Test
    void checkName_shouldReturnTrue_whenDuplicateExists() {
        when(missionService.isNameDuplicate("Test", null)).thenReturn(true);

        ResponseEntity<ApiResponse<Boolean>> response = controller.checkName("Test", null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isTrue();
    }

    @Test
    void checkName_shouldReturnFalse_whenNameAvailable() {
        when(missionService.isNameDuplicate("NewMission", null)).thenReturn(false);

        ResponseEntity<ApiResponse<Boolean>> response = controller.checkName("NewMission", null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isFalse();
    }

    @Test
    void checkName_shouldExcludeSelf_whenEditing() {
        when(missionService.isNameDuplicate("Test", 1L)).thenReturn(false);

        ResponseEntity<ApiResponse<Boolean>> response = controller.checkName("Test", 1L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isFalse();
    }

    @Test
    void get_shouldReturnOk() {
        when(missionService.getDetail(1L)).thenReturn(new ProductMissionDetailDTO());
        assertThat(controller.get(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void delete_shouldReturnOk() {
        assertThat(controller.delete(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }
}
