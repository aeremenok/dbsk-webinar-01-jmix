package com.debugskills.equipment.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

public enum EquipmentRequestStatus implements EnumClass<String> {

    RESERVED("RESERVED"),
    QUEUED("QUEUED");

    private final String id;

    EquipmentRequestStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static EquipmentRequestStatus fromId(String id) {
        for (EquipmentRequestStatus at : EquipmentRequestStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}