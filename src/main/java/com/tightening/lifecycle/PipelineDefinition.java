package com.tightening.lifecycle;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.capability.Capability;

import java.util.*;

public class PipelineDefinition {

    private final Map<Stage, List<StageEntry>> orderedStages = new LinkedHashMap<>();
    private final Map<Stage, Map<SubState, List<Capability>>> caps = new HashMap<>();
    private final Map<Stage, Map<SubState, Transition>> transitions = new HashMap<>();

    public static class StageEntry {
        private final Stage stage;
        private final SubState subState;
        private final boolean waitingPoint;

        public StageEntry(Stage stage, SubState subState, boolean waitingPoint) {
            this.stage = stage;
            this.subState = subState;
            this.waitingPoint = waitingPoint;
        }

        public Stage stage() { return stage; }
        public SubState subState() { return subState; }
        public boolean waitingPoint() { return waitingPoint; }
    }

    public record Transition(Stage nextStage, SubState nextSubState) {}

    public PipelineDefinition registerCapability(Capability cap) {
        caps.computeIfAbsent(cap.stage(), k -> new HashMap<>())
            .computeIfAbsent(cap.subState(), k -> new ArrayList<>())
            .add(cap);
        return this;
    }

    public PipelineDefinition registerCapabilities(Collection<Capability> capabilities) {
        capabilities.forEach(this::registerCapability);
        return this;
    }

    public PipelineDefinition sortByPriority() {
        caps.values().forEach(stageMap ->
            stageMap.values().forEach(list ->
                list.sort(Comparator.comparingInt(Capability::priority))
            )
        );
        return this;
    }

    public PipelineDefinition registerStage(Stage stage, List<StageEntry> entries) {
        orderedStages.put(stage, entries);
        return this;
    }

    public PipelineDefinition registerTransition(Stage fromStage, SubState fromSubState,
                                                  Stage toStage, SubState toSubState) {
        transitions.computeIfAbsent(fromStage, k -> new HashMap<>())
            .put(fromSubState, new Transition(toStage, toSubState));
        return this;
    }

    public List<Capability> getCapabilities(Stage stage, SubState subState) {
        return caps.getOrDefault(stage, Map.of())
            .getOrDefault(subState, List.of());
    }

    public Transition getNext(Stage stage, SubState subState) {
        Map<SubState, Transition> stageTransitions = transitions.get(stage);
        if (stageTransitions != null) {
            Transition t = stageTransitions.get(subState);
            if (t != null) return t;
        }
        List<StageEntry> entries = orderedStages.get(stage);
        if (entries != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).subState() == subState && i + 1 < entries.size()) {
                    StageEntry next = entries.get(i + 1);
                    return new Transition(stage, next.subState());
                }
            }
        }
        Stage[] stageOrder = Stage.values();
        int currentIdx = Arrays.asList(stageOrder).indexOf(stage);
        if (currentIdx >= 0 && currentIdx + 1 < stageOrder.length) {
            Stage nextStage = stageOrder[currentIdx + 1];
            List<StageEntry> nextEntries = orderedStages.get(nextStage);
            if (nextEntries != null && !nextEntries.isEmpty()) {
                return new Transition(nextStage, nextEntries.get(0).subState());
            }
        }
        return new Transition(stage, subState);  // terminal
    }

    public boolean isWaitingPoint(Stage stage, SubState subState) {
        List<StageEntry> entries = orderedStages.get(stage);
        if (entries != null) {
            return entries.stream()
                .filter(e -> e.subState() == subState)
                .anyMatch(e -> e.waitingPoint);
        }
        return false;
    }

    public static PipelineDefinition createDefault() {
        PipelineDefinition pd = new PipelineDefinition();

        pd.registerStage(Stage.VALIDATION, List.of(
            new StageEntry(Stage.VALIDATION, SubState.VALIDATING, false)
        ));
        pd.registerStage(Stage.ACTIVATION, List.of(
            new StageEntry(Stage.ACTIVATION, SubState.PREPARING, false),
            new StageEntry(Stage.ACTIVATION, SubState.ACTIVATING, false)
        ));
        pd.registerStage(Stage.OPERATION, List.of(
            new StageEntry(Stage.OPERATION, SubState.SWITCH_BOLT, false),
            new StageEntry(Stage.OPERATION, SubState.TIGHTENING_RECEIVED, true),
            new StageEntry(Stage.OPERATION, SubState.JUDGING, false),
            new StageEntry(Stage.OPERATION, SubState.STORING, false),
            new StageEntry(Stage.OPERATION, SubState.ADVANCING, false)
        ));
        pd.registerStage(Stage.FINALIZATION, List.of(
            new StageEntry(Stage.FINALIZATION, SubState.CLEANING_TASKS, false),
            new StageEntry(Stage.FINALIZATION, SubState.LOCKING_TOOLS, false),
            new StageEntry(Stage.FINALIZATION, SubState.RESETTING_STATE, false),
            new StageEntry(Stage.FINALIZATION, SubState.EXPORTING, false)
        ));

        pd.registerTransition(Stage.VALIDATION, SubState.VALIDATING,
            Stage.ACTIVATION, SubState.PREPARING);
        pd.registerTransition(Stage.ACTIVATION, SubState.ACTIVATING,
            Stage.OPERATION, SubState.SWITCH_BOLT);
        pd.registerTransition(Stage.OPERATION, SubState.ADVANCING,
            Stage.OPERATION, SubState.SWITCH_BOLT);

        return pd;
    }
}
