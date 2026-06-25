package com.tightening.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.tightening.dto.ProductMissionDTO;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.InspectionMissionBinding;
import com.tightening.entity.MissionPrerequisite;
import com.tightening.entity.ProductMission;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.InspectionMissionBindingService;
import com.tightening.service.MissionPrerequisiteService;
import com.tightening.service.ProductMissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductMissionControllerTest {

    @Mock private ProductMissionService missionService;
    @Mock private MissionPrerequisiteService prerequisiteService;
    @Mock private InspectionMissionBindingService bindingService;
    @Mock private BarCodeMatchingRuleService barcodeRuleService;
    @InjectMocks private ProductMissionController controller;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> LambdaQueryChainWrapper<T> mockLambdaChain(Object service, Class<T> entityClass) {
        LambdaQueryChainWrapper<T> queryChain = org.mockito.Mockito.mock(LambdaQueryChainWrapper.class);
        when(((com.baomidou.mybatisplus.extension.service.IService<T>) service).lambdaQuery()).thenReturn(queryChain);
        lenient().when(queryChain.eq(any(), any())).thenReturn(queryChain);
        lenient().when((LambdaQueryChainWrapper) queryChain.orderByDesc((com.baomidou.mybatisplus.core.toolkit.support.SFunction) any())).thenReturn(queryChain);
        lenient().when(queryChain.last(any())).thenReturn(queryChain);
        return queryChain;
    }

    @Test
    void list_shouldReturnOk() {
        LambdaQueryChainWrapper<ProductMission> queryChain = mockLambdaChain(missionService, ProductMission.class);
        when(queryChain.list()).thenReturn(List.of());

        assertThat(controller.list(1, 100).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void create_shouldReturnOk() {
        ProductMissionDTO dto = new ProductMissionDTO();
        dto.setName("Test");
        dto.setMaxNgCount(3);
        assertThat(controller.create(dto).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void update_shouldReturnOk() {
        ProductMissionDTO dto = new ProductMissionDTO();
        dto.setName("Test Updated");
        dto.setMaxNgCount(5);
        assertThat(controller.update(1L, dto).getStatusCode().is2xxSuccessful()).isTrue();
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
        LambdaQueryChainWrapper<MissionPrerequisite> queryChain = mockLambdaChain(prerequisiteService, MissionPrerequisite.class);
        when(queryChain.list()).thenReturn(List.of());

        assertThat(controller.listPrerequisites(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void deleteInspectionBinding_shouldReturnOk() {
        assertThat(controller.deleteInspectionBinding(1L, 1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void listInspectionBindings_shouldReturnOk() {
        LambdaQueryChainWrapper<InspectionMissionBinding> queryChain = mockLambdaChain(bindingService, InspectionMissionBinding.class);
        when(queryChain.list()).thenReturn(List.of());

        assertThat(controller.listInspectionBindings(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void deleteBarcodeRule_shouldReturnOk() {
        assertThat(controller.deleteBarcodeRule(1L, 1L).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void listBarcodeRules_shouldReturnOk() {
        LambdaQueryChainWrapper<BarCodeMatchingRule> queryChain = mockLambdaChain(barcodeRuleService, BarCodeMatchingRule.class);
        when(queryChain.list()).thenReturn(List.of());

        assertThat(controller.listBarcodeRules(1L).getStatusCode().is2xxSuccessful()).isTrue();
    }
}
