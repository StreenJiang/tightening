package com.tightening.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tightening.constant.EnabledStatus;
import com.tightening.dto.ApiResponse;
import com.tightening.dto.PageResult;
import com.tightening.dto.ProductTaskDTO;
import com.tightening.dto.ProductTaskDetailDTO;
import com.tightening.service.ProductTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductTaskControllerTest {

    @Mock private ProductTaskService taskService;
    @InjectMocks private ProductTaskController controller;

    @Test
    void list_shouldReturnOk() {
        when(taskService.listByPage(eq(1), eq(100), isNull())).thenReturn(PageResult.of(new Page<>(1, 100), List.of()));

        ResponseEntity<ApiResponse<PageResult<ProductTaskDTO>>> response = controller.list(1, 100, null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(200);
    }

    @Test
    void list_shouldPassNameToService_whenNameProvided() {
        when(taskService.listByPage(1, 100, "Test")).thenReturn(PageResult.of(new Page<>(1, 100), List.of()));

        ResponseEntity<ApiResponse<PageResult<ProductTaskDTO>>> response = controller.list(1, 100, "Test");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(200);
    }

    @Test
    void checkName_shouldReturnTrue_whenDuplicateExists() {
        when(taskService.isNameDuplicate("Test", null)).thenReturn(true);

        ResponseEntity<ApiResponse<Boolean>> response = controller.checkName("Test", null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isTrue();
    }

    @Test
    void checkName_shouldReturnFalse_whenNameAvailable() {
        when(taskService.isNameDuplicate("NewTask", null)).thenReturn(false);

        ResponseEntity<ApiResponse<Boolean>> response = controller.checkName("NewTask", null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isFalse();
    }

    @Test
    void checkName_shouldExcludeSelf_whenEditing() {
        when(taskService.isNameDuplicate("Test", 1L)).thenReturn(false);

        ResponseEntity<ApiResponse<Boolean>> response = controller.checkName("Test", 1L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isFalse();
    }

    @Test
    void get_shouldReturnOk() {
        when(taskService.getDetail(1L)).thenReturn(new ProductTaskDetailDTO());
        assertThat(controller.get(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void delete_shouldReturnOk() {
        assertThat(controller.delete(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(EnabledStatus.class)
    void setEnabled_shouldReturnOk(EnabledStatus status) {
        ResponseEntity<ApiResponse<String>> response = controller.setEnabled(1L, status);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(200);
        verify(taskService).updateEnabled(1L, status.getCode());
    }
}
