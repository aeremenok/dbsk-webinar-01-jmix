package com.debugskills.equipment.api;

import jakarta.validation.constraints.NotBlank;

public record CreateEquipmentRequest(
        @NotBlank String employeeEmail,
        @NotBlank String equipmentType,
        @NotBlank String reason) {
}