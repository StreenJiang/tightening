package com.tightening.lifecycle;

import com.tightening.constant.BoltState;
import com.tightening.constant.DeviceType;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.ITool;
import com.tightening.entity.*;
import com.tightening.judgment.JudgmentResult;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Builder
public class MissionContext {

    // ═══ 第一层：核心字段 ═══

    /** 不可变 — Workstation 就绪时注入 */
    private final Long productMissionId;
    private final ProductMission missionData;
    private final List<ProductBolt> boltConfigs;       // 按 boltSerialNum 排序（调用方保证）
    private final Map<Long, ITool> deviceRegistry;
    private final boolean shouldSelfLoop;

    /** 可变 — 引擎核心代码维护 */
    @Builder.Default @Setter private Stage currentStage = Stage.VALIDATION;
    @Builder.Default @Setter private SubState currentSubState = SubState.IDLE;
    @Builder.Default @Setter private BoltState[] boltStates = new BoltState[0];
    @Builder.Default @Setter private int currentBoltIndex = 0;
    @Builder.Default @Setter private int currentSideIndex = 0;
    @Builder.Default @Setter private MissionRecord missionRecord = null;
    @Builder.Default private final List<TighteningData> tighteningDataList = new ArrayList<>();
    @Builder.Default @Setter private volatile boolean interruptRequested = false;
    @Builder.Default @Setter private volatile String interruptReason = null;

    // ═══ 第二层：管道间传递数据 ═══

    @Builder.Default @Setter private TighteningData currentOperationData = null;
    @Builder.Default @Setter private DeviceType currentDeviceType = null;  // 由 handleTighteningData 从 deviceRegistry 解析
    @Builder.Default @Setter private TighteningData previousOperationData = null;
    @Builder.Default private final List<CurveData> pendingCurveData = new ArrayList<>();
    @Builder.Default @Setter private JudgmentResult judgeResult = null;
    @Builder.Default @Setter private int tighteningStatus = 0;
    @Builder.Default private final Set<LockMessage> lockMessages = new LinkedHashSet<>();

    // ═══ 第三层：Capability 间临时数据 ═══

    @Builder.Default private final Map<String, Object> extras = new HashMap<>();

    // ═══ 崩溃恢复 ═══

    @Builder.Default @Setter private ContextCheckpoint checkpoint = null;

    // ═══ 便捷方法 ═══

    public ProductBolt currentBolt() {
        if (boltConfigs == null || currentBoltIndex < 0 || currentBoltIndex >= boltConfigs.size()) {
            return null;
        }
        return boltConfigs.get(currentBoltIndex);
    }

    public boolean hasMoreBolts() {
        return boltConfigs != null && currentBoltIndex < boltConfigs.size() - 1;
    }

    public int totalBolts() {
        return boltConfigs != null ? boltConfigs.size() : 0;
    }

    public boolean allBoltsCompleted() {
        return boltStates != null && Arrays.stream(boltStates)
            .allMatch(s -> s == BoltState.JUDGED_OK || s == BoltState.JUDGED_NG);
    }
}
