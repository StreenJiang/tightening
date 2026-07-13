package com.tightening.judgment;

import com.tightening.constant.TighteningStatus;
import com.tightening.dto.TighteningDataDTO;

/**
 * SudongX7 quality judgment: torque ∧ angle ∧ controller tighteningStatus.
 * rundownAngle does NOT participate in the final verdict.
 */
public class SudongJudgment implements JudgmentStrategy {

    @Override
    public JudgmentResult judge(TighteningDataDTO dto) {
        boolean torqueOk = inRange(dto.getTorque(), dto.getTorqueMinLimit(), dto.getTorqueMaxLimit());
        boolean angleOk = inRange(dto.getAngle(), dto.getAngleMinLimit(), dto.getAngleMaxLimit());
        Integer s = dto.getTighteningStatus();
        boolean statusOk = s != null && s.equals(TighteningStatus.OK.getCode());

        StringBuilder reason = new StringBuilder();
        if (!torqueOk) reason.append("torque=").append(dto.getTorque())
                .append(" not in [").append(dto.getTorqueMinLimit()).append(",").append(dto.getTorqueMaxLimit()).append("]; ");
        if (!angleOk) reason.append("angle=").append(dto.getAngle())
                .append(" not in [").append(dto.getAngleMinLimit()).append(",").append(dto.getAngleMaxLimit()).append("]; ");
        if (!statusOk) reason.append("tighteningStatus=").append(dto.getTighteningStatus()).append("; ");

        if (torqueOk && angleOk && statusOk) {
            return JudgmentResult.ok();
        }
        return JudgmentResult.ng(reason.toString());
    }

    private static boolean inRange(double value, double min, double max) {
        if (min == 0 && max == 0) return true; // no limits configured
        return value >= min && value <= max;
    }
}
