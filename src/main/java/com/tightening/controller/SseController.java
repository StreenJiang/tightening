package com.tightening.controller;

import com.tightening.service.SseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class SseController {

    private final SseService sseService;

    public SseController(SseService sseService) {
        this.sseService = sseService;
    }

    @GetMapping("/events")
    public SseEmitter deviceEvents() {
        return sseService.createDeviceEmitter();
    }

    @GetMapping("/workplace/events")
    public SseEmitter workplaceEvents() {
        return sseService.createWorkplaceEmitter();
    }
}
