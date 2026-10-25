package com.debugskills.equipment.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

public enum EquipmentType implements EnumClass<String> {

    LAPTOP("LAPTOP"),
    MONITOR("MONITOR"),
    HEADSET("HEADSET");

    private final String id;

    EquipmentType(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static EquipmentType fromId(String id) {
        for (EquipmentType at : EquipmentType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
