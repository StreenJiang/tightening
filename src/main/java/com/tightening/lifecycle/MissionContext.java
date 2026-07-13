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
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Builder
public class MissionContext {

    // ═══ 第一层：核心字段 ═══

    /** 不可变 — Workstation 就绪时注入 */
    private final Long productMissionId;
    private final ProductMission missionData;
    private final List<ProductBolt> boltConfigs;       // 按 boltSerialNum 排序（调用方保证）
    private final Map<Long, ITool> deviceRegistry;
    /** 可变 — 引擎在运行时可能修改（例如 SkipScrew 快速路径禁用自循环） */
    @Builder.Default @Setter private boolean shouldSelfLoop;

    /** 可变 — 引擎核心代码维护 (volatile 确保 HTTP 线程读取可见性) */
    @Builder.Default @Setter private volatile Stage currentStage = Stage.VALIDATION;
    @Builder.Default @Setter private volatile SubState currentSubState = SubState.IDLE;
    @Builder.Default @Setter private BoltState[] boltStates = new BoltState[0];
    @Builder.Default @Setter private volatile int currentBoltIndex = 0;
    @Builder.Default @Setter private int currentSideIndex = 0;
    @Builder.Default @Setter private volatile MissionRecord missionRecord = null;
    @Builder.Default private final List<TighteningData> tighteningDataList = new ArrayList<>();
    /** 产品追溯码（触发阶段写入，生命周期内不可变） */
    @Builder.Default @Setter
    private String productCode = null;
    /** 物料码（触发阶段写入，生命周期内不可变） */
    @Builder.Default @Setter
    private String partsCode = null;
    @Builder.Default @Setter private volatile boolean interruptRequested = false;
    @Builder.Default @Setter private volatile String interruptReason = null;

    // ═══ 第二层：管道间传递数据 ═══

    @Builder.Default @Setter private TighteningData currentOperationData = null;
    @Builder.Default @Setter private DeviceType currentDeviceType = null;  // 由 handleTighteningData 从 deviceRegistry 解析
    @Builder.Default @Setter private volatile Integer currentPSet = null;  // SendPSet 写入，仅 SudongX7 等不回传协议使用
    @Builder.Default @Setter private JudgmentResult judgeResult = null;
    @Builder.Default private final Set<LockMessage> lockMessages = new LinkedHashSet<>();

    // ═══ 第三层：Capability 间临时数据 ═══

    @Builder.Default private final Map<String, Object> extras = new ConcurrentHashMap<>();

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
