package com.tightening.service;

import com.tightening.constant.LockReason;
import com.tightening.constant.SseEventType;
import com.tightening.constant.WorkplaceStatus;
import com.tightening.dto.SseEvent;
import com.tightening.dto.WorkplaceStatusPayload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WorkplaceStatusService {

    private final SseService sseService;
    private volatile WorkplaceStatus status = WorkplaceStatus.UNACTIVATED;
    private volatile Set<LockReason> lockReasons = Set.of();

    public WorkplaceStatusService(SseService sseService) {
        this.sseService = sseService;
    }

    public WorkplaceStatus current() {
        return status;
    }

    public void transitionTo(WorkplaceStatus newStatus, Set<LockReason> reasons) {
        this.status = newStatus;
        this.lockReasons = reasons;
        sseService.emit(new SseEvent(
            SseEventType.WORKPLACE_STATUS,
            new WorkplaceStatusPayload(newStatus, toKeySet(reasons)),
            LocalDateTime.now()));
    }

    public void reset() {
        transitionTo(WorkplaceStatus.UNACTIVATED, Set.of());
    }

    private Set<String> toKeySet(Set<LockReason> reasons) {
        return reasons.stream()
            .map(LockReason::getKey)
            .collect(Collectors.toSet());
    }
}
