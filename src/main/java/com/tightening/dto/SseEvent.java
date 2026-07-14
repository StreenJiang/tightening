package com.tightening.dto;

import com.tightening.constant.SseEventType;
import java.time.LocalDateTime;

public record SseEvent(SseEventType type, Object payload, LocalDateTime timestamp) {}
