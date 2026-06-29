package com.tightening.lifecycle.capability;

import com.tightening.constant.DeviceType;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.TighteningData;
import com.tightening.judgment.JudgmentStrategy;
import com.tightening.lifecycle.MissionContext;
import com.tightening.util.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ExecuteJudgment implements Capability {

    private final Map<DeviceType, JudgmentStrategy> judgmentStrategies;

    @Override public String id() { return "ExecuteJudgment"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.JUDGING; }
    @Override public int priority() { return 3; }

    @Override
    public boolean precondition(MissionContext ctx) {
        return ctx.getCurrentOperationData() != null;
    }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        TighteningData data = ctx.getCurrentOperationData();
        TighteningDataDTO dto = Converter.entity2Dto(data, TighteningDataDTO::new);

        // 根据设备类型选择 JudgmentStrategy（由 handleTighteningData 解析）
        DeviceType deviceType = ctx.getCurrentDeviceType();
        JudgmentStrategy strategy = deviceType != null ? judgmentStrategies.get(deviceType) : null;
        if (strategy == null) {
            log.warn("No JudgmentStrategy available");
            return CapabilityResult.Fail;
        }
        ctx.setJudgeResult(strategy.judge(dto));
        log.debug("ExecuteJudgment: ok={}", ctx.getJudgeResult().isOk());
        return CapabilityResult.Pass;
    }
}
