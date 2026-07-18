package com.tightening.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tightening.dto.ApiResponse;
import com.tightening.dto.PageResult;
import com.tightening.dto.ProductMissionDTO;
import com.tightening.entity.ProductMission;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.InspectionMissionBindingService;
import com.tightening.service.MissionConfigValidator;
import com.tightening.service.MissionPrerequisiteService;
import com.tightening.service.ProductMissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductMissionControllerTest {

    @Mock private ProductMissionService missionService;
    @Mock private MissionPrerequisiteService prerequisiteService;
    @Mock private InspectionMissionBindingService bindingService;
    @Mock private BarCodeMatchingRuleService barcodeRuleService;
    @Mock private MissionConfigValidator missionValidator;
    @InjectMocks private ProductMissionController controller;

    @Test
    void list_shouldReturnOk() {
        when(missionService.listByPage(eq(1), eq(100), isNull())).thenReturn(new Page<>(1, 100));

        ResponseEntity<ApiResponse<PageResult<ProductMissionDTO>>> response = controller.list(1, 100, null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(200);
        assertThat(response.getBody().data().total()).isEqualTo(0);
        assertThat(response.getBody().data().size()).isEqualTo(100);
        assertThat(response.getBody().data().current()).isEqualTo(1);
    }

    @Test
    void list_shouldPassNameToService_whenNameProvided() {
        when(missionService.listByPage(1, 100, "Test")).thenReturn(new Page<>(1, 100));

        ResponseEntity<ApiResponse<PageResult<ProductMissionDTO>>> response = controller.list(1, 100, "Test");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(200);
        assertThat(response.getBody().data().total()).isEqualTo(0);
        assertThat(response.getBody().data().size()).isEqualTo(100);
        assertThat(response.getBody().data().current()).isEqualTo(1);
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
    void create_shouldReturnOk() {
        ProductMissionDTO dto = new ProductMissionDTO();
        dto.setName("Test");
        dto.setMaxNgCount(3);
        assertThat(controller.create(dto).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void create_shouldReturnFail_whenDuplicateKeyException() {
        ProductMissionDTO dto = new ProductMissionDTO();
        dto.setName("Duplicate");

        doThrow(new DuplicateKeyException("UNIQUE constraint")).when(missionService).saveOrUpdate(any());

        ResponseEntity<ApiResponse<String>> response = controller.create(dto);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("任务名称已存在");
    }

    @Test
    void update_shouldReturnOk() {
        ProductMissionDTO dto = new ProductMissionDTO();
        dto.setName("Test Updated");
        dto.setMaxNgCount(5);
        assertThat(controller.update(1L, dto).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void update_shouldReturnFail_whenDuplicateKeyException() {
        ProductMissionDTO dto = new ProductMissionDTO();
        dto.setName("Duplicate");

        doThrow(new DuplicateKeyException("UNIQUE constraint")).when(missionService).saveOrUpdate(any());

        ResponseEntity<ApiResponse<String>> response = controller.update(1L, dto);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("任务名称已存在");
    }

    @Test
    void delete_shouldReturnOk() {
        assertThat(controller.delete(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void addPrerequisite_shouldReturnOk() {
        var request = new ProductMissionController.PrerequisiteRequest(2L, 1);
        assertThat(controller.addPrerequisite(1L, request).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void addInspectionBinding_shouldReturnOk() {
        var request = new ProductMissionController.InspectionBindingRequest(2L);
        assertThat(controller.addInspectionBinding(1L, request).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void get_shouldReturnOk() {
        when(missionService.getById(1L)).thenReturn(new ProductMission());
        assertThat(controller.get(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void deletePrerequisite_shouldReturnOk() {
        assertThat(controller.deletePrerequisite(1L, 1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void listPrerequisites_shouldReturnOk() {
        when(prerequisiteService.listByMissionId(1L)).thenReturn(List.of());

        assertThat(controller.listPrerequisites(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void deleteInspectionBinding_shouldReturnOk() {
        assertThat(controller.deleteInspectionBinding(1L, 1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void listInspectionBindings_shouldReturnOk() {
        when(bindingService.listByInspectionMissionId(1L)).thenReturn(List.of());

        assertThat(controller.listInspectionBindings(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void deleteBarcodeRule_shouldReturnOk() {
        assertThat(controller.deleteBarcodeRule(1L, 1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void listBarcodeRules_shouldReturnOk() {
        when(barcodeRuleService.listByMissionId(1L)).thenReturn(List.of());

        assertThat(controller.listBarcodeRules(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }
}
