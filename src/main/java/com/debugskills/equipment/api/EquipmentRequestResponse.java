package com.debugskills.equipment.api;

import com.debugskills.equipment.entity.EquipmentRequest;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EquipmentRequestResponse(
        UUID id,
        String status,
        String inventoryNumber,
        Integer queuePosition) {

    public static EquipmentRequestResponse from(EquipmentRequest r) {
        return new EquipmentRequestResponse(
                r.getId(),
                r.getStatus() == null ? null : r.getStatus().getId(),
                r.getEquipment() == null ? null : r.getEquipment().getInventoryNumber(),
                r.getQueuePosition());
    }
}