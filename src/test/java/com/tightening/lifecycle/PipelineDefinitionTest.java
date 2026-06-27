package com.tightening.lifecycle;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.capability.Capability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PipelineDefinition 管道路由表")
class PipelineDefinitionTest {

    private PipelineDefinition pd;

    @BeforeEach
    void setUp() {
        pd = PipelineDefinition.createDefault();
    }

    @Test
    @DisplayName("未注册 (Stage,SubState) 返回空 Capability 列表")
    void shouldReturnEmptyCapabilitiesForUnregisteredState() {
        assertThat(pd.getCapabilities(Stage.VALIDATION, SubState.VALIDATING)).isEmpty();
    }

    @Test
    @DisplayName("VALIDATION 推进到 ACTIVATION")
    void shouldTransitionFromValidationToActivation() {
        PipelineDefinition.Transition t = pd.getNext(Stage.VALIDATION, SubState.VALIDATING);
        assertThat(t.nextStage()).isEqualTo(Stage.ACTIVATION);
        assertThat(t.nextSubState()).isEqualTo(SubState.PREPARING);
    }

    @Test
    @DisplayName("ACTIVATION/ACTIVATING 推进到 OPERATION/SWITCH_BOLT")
    void shouldTransitionFromActivationToOperation() {
        PipelineDefinition.Transition t = pd.getNext(Stage.ACTIVATION, SubState.ACTIVATING);
        assertThat(t.nextStage()).isEqualTo(Stage.OPERATION);
        assertThat(t.nextSubState()).isEqualTo(SubState.SWITCH_BOLT);
    }

    @Test
    @DisplayName("OPERATION 内 TIGHTENING_RECEIVED → JUDGING")
    void shouldTransitionWithinOperation() {
        PipelineDefinition.Transition t = pd.getNext(Stage.OPERATION, SubState.TIGHTENING_RECEIVED);
        assertThat(t.nextSubState()).isEqualTo(SubState.JUDGING);
    }

    @Test
    @DisplayName("ADVANCING 循环回 SWITCH_BOLT")
    void advancingShouldLoopToSwitchBolt() {
        PipelineDefinition.Transition t = pd.getNext(Stage.OPERATION, SubState.ADVANCING);
        assertThat(t.nextStage()).isEqualTo(Stage.OPERATION);
        assertThat(t.nextSubState()).isEqualTo(SubState.SWITCH_BOLT);
    }

    @Test
    @DisplayName("SWITCH_BOLT 不是等待点")
    void switchBoltShouldNotBeWaitingPoint() {
        assertThat(pd.isWaitingPoint(Stage.OPERATION, SubState.SWITCH_BOLT)).isFalse();
    }

    @Test
    @DisplayName("TIGHTENING_RECEIVED 是等待点")
    void tighteningReceivedShouldBeWaitingPoint() {
        assertThat(pd.isWaitingPoint(Stage.OPERATION, SubState.TIGHTENING_RECEIVED)).isTrue();
    }

    @Test
    @DisplayName("ADVANCING 不是等待点")
    void advancingShouldNotBeWaitingPoint() {
        assertThat(pd.isWaitingPoint(Stage.OPERATION, SubState.ADVANCING)).isFalse();
    }

    @Test
    @DisplayName("JUDGING 不是等待点")
    void judgingShouldNotBeWaitingPoint() {
        assertThat(pd.isWaitingPoint(Stage.OPERATION, SubState.JUDGING)).isFalse();
    }

    @Test
    @DisplayName("到达终点后 getNext 返回自身")
    void shouldReturnSameWhenAtTerminal() {
        PipelineDefinition.Transition t = pd.getNext(Stage.FINALIZATION, SubState.EXPORTING);
        assertThat(t.nextStage()).isEqualTo(Stage.FINALIZATION);
        assertThat(t.nextSubState()).isEqualTo(SubState.EXPORTING);
    }

    @Test
    @DisplayName("registerCapability 注册后可查询")
    void shouldRegisterCapability() {
        Capability mockCap = mock(Capability.class);
        when(mockCap.stage()).thenReturn(Stage.OPERATION);
        when(mockCap.subState()).thenReturn(SubState.JUDGING);
        when(mockCap.id()).thenReturn("test");
        when(mockCap.priority()).thenReturn(0);

        PipelineDefinition custom = new PipelineDefinition();
        custom.registerCapability(mockCap);
        custom.sortByPriority();

        assertThat(custom.getCapabilities(Stage.OPERATION, SubState.JUDGING))
            .containsExactly(mockCap);
    }

    @Test
    @DisplayName("registerCapabilities 批量注册")
    void shouldRegisterCapabilitiesInBatch() {
        Capability c1 = mock(Capability.class);
        when(c1.stage()).thenReturn(Stage.OPERATION);
        when(c1.subState()).thenReturn(SubState.JUDGING);
        when(c1.id()).thenReturn("c1");
        when(c1.priority()).thenReturn(1);

        Capability c2 = mock(Capability.class);
        when(c2.stage()).thenReturn(Stage.OPERATION);
        when(c2.subState()).thenReturn(SubState.JUDGING);
        when(c2.id()).thenReturn("c2");
        when(c2.priority()).thenReturn(0);

        PipelineDefinition custom = new PipelineDefinition();
        custom.registerCapabilities(List.of(c1, c2));
        custom.sortByPriority();

        assertThat(custom.getCapabilities(Stage.OPERATION, SubState.JUDGING))
            .containsExactly(c2, c1);  // sorted by priority
    }
}
