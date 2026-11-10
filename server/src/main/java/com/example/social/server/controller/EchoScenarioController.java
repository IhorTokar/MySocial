package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.EchoScenarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Сценарій формування й розмикання ехо-камери для звіту. Лише для адміністратора (/api/dev/**).
 * POST /api/dev/echo-scenario/1 … /4 — кроки по черзі; DELETE /api/dev/echo-scenario — прибрати тестові групи.
 */
@RestController
@RequestMapping("/api/dev/echo-scenario")
public class EchoScenarioController {

    private final EchoScenarioService scenarioService;

    public EchoScenarioController(EchoScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @PostMapping("/{step}")
    public ResponseEntity<EchoScenarioService.StepResult> runStep(@AuthenticationPrincipal AuthenticatedUser admin,
                                                                  @PathVariable("step") int step) {
        return ResponseEntity.ok(scenarioService.runStep(step, admin.getUserId()));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Integer>> reset() {
        return ResponseEntity.ok(scenarioService.reset());
    }
}