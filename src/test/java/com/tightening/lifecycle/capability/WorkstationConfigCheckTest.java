package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.ITool;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductTask;
import com.tightening.lifecycle.TaskContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("WorkstationConfigCheck")
class WorkstationConfigCheckTest {

    private final WorkstationConfigCheck cap = new WorkstationConfigCheck();

    @Test
    @DisplayName("配置完整时返回 Pass")
    void shouldPassWhenConfigurationValid() {
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L)
                .taskData(new ProductTask())
                .boltConfigs(List.of(new ProductBolt()))
                .deviceRegistry(Map.of(1L, mock(ITool.class)))
                
                .build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
    }

    @Test
    @DisplayName("螺栓列表为空时返回 Fail")
    void shouldFailWhenNoBolts() {
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L)
                .taskData(new ProductTask())
                .boltConfigs(List.of())
                .deviceRegistry(Map.of(1L, mock(ITool.class)))
                
                .build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("boltConfigs 为 null 时返回 Fail")
    void shouldFailWhenBoltConfigsNull() {
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L)
                .taskData(new ProductTask())
                .boltConfigs(null)
                .deviceRegistry(Map.of(1L, mock(ITool.class)))
                
                .build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("设备注册表为空时返回 Fail")
    void shouldFailWhenNoDevices() {
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L)
                .taskData(new ProductTask())
                .boltConfigs(List.of(new ProductBolt()))
                .deviceRegistry(Map.of())
                
                .build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("deviceRegistry 为 null 时返回 Fail")
    void shouldFailWhenDeviceRegistryNull() {
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L)
                .taskData(new ProductTask())
                .boltConfigs(List.of(new ProductBolt()))
                .deviceRegistry(null)
                
                .build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("taskData 为 null 时返回 Fail")
    void shouldFailWhenTaskDataNull() {
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L)
                .taskData(null)
                .boltConfigs(List.of(new ProductBolt()))
                .deviceRegistry(Map.of(1L, mock(ITool.class)))
                
                .build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("返回正确的 identity")
    void shouldReturnCorrectIdentity() {
        assertThat(cap.id()).isEqualTo("WorkstationConfigCheck");
        assertThat(cap.stage()).isEqualTo(Stage.VALIDATION);
        assertThat(cap.subState()).isEqualTo(SubState.VALIDATING);
        assertThat(cap.priority()).isEqualTo(0);
    }
}
