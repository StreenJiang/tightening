package com.tightening.lifecycle;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ContextCheckpoint {
    long missionId;
    long missionRecordId;
    Stage stage;
    SubState subState;
    int currentBoltIndex;
    int currentSideIndex;
    int completedBolts;
    boolean dataStored;
    String snapshotReason;
    long timestamp;
}
