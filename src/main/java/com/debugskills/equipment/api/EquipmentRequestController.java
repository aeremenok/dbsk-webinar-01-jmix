package com.debugskills.equipment.api;

import com.debugskills.equipment.app.EquipmentRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/equipment-requests")
public class EquipmentRequestController {

    private final EquipmentRequestService service;

    public EquipmentRequestController(EquipmentRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EquipmentRequestResponse> create(
            @Valid @RequestBody CreateEquipmentRequest body) {
        return ResponseEntity.ok(service.submit(body));
    }
}