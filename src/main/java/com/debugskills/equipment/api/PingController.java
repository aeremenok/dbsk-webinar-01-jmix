package com.debugskills.equipment.api;

import io.jmix.core.UnconstrainedDataManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke-check endpoint: proves that the {@code /api/**} security chain, anonymous access
 * and the seed data work. Counts the rows of the Employee reference table.
 */
@RestController
@RequestMapping("/api")
public class PingController {

    private final UnconstrainedDataManager dataManager;

    public PingController(UnconstrainedDataManager dataManager) {
        this.dataManager = dataManager;
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        Long employees = dataManager
                .loadValue("select count(e) from equipment_Employee e", Long.class)
                .one();
        return new PingResponse("ok", employees);
    }

    public record PingResponse(String status, long employees) {
    }
}
