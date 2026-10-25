package com.debugskills.equipment.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

public enum EquipmentStatus implements EnumClass<String> {

    AVAILABLE("AVAILABLE"),
    BOOKED("BOOKED"),
    ISSUED("ISSUED"),
    MAINTENANCE("MAINTENANCE");

    private final String id;

    EquipmentStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static EquipmentStatus fromId(String id) {
        for (EquipmentStatus at : EquipmentStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
