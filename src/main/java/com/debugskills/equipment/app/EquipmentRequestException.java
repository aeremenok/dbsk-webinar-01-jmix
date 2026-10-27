package com.debugskills.equipment.app;

public class EquipmentRequestException extends RuntimeException {

    private final String code;

    public EquipmentRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}