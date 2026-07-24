package com.tightening.service;

import com.tightening.constant.LockReason;
import com.tightening.constant.SseEvents;
import com.tightening.constant.WorkplaceStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
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
        sseService.emitWorkplace(SseEvents.WORKPLACE_STATUS, Map.of(
            "status", newStatus.name(),
            "lockReasons", toKeySet(reasons),
            "ts", LocalDateTime.now().toString()
        ));
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
