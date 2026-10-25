package com.debugskills.equipment.entity;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@JmixEntity
@Entity(name = "equipment_Equipment")
@Table(name = "EQUIP_EQUIPMENT", indexes = {
        @Index(name = "IDX_EQUIP_EQUIPMENT_ON_INV_NUM", columnList = "INVENTORY_NUMBER", unique = true)
})
public class Equipment extends StandardJmixEntity {

    @Column(name = "INVENTORY_NUMBER", nullable = false, unique = true)
    private String inventoryNumber;

    @InstanceName
    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "TYPE", nullable = false)
    private String type;

    @Column(name = "STATUS", nullable = false)
    private String status = EquipmentStatus.AVAILABLE.getId();

    public String getInventoryNumber() {
        return inventoryNumber;
    }

    public void setInventoryNumber(String inventoryNumber) {
        this.inventoryNumber = inventoryNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EquipmentType getType() {
        return type == null ? null : EquipmentType.fromId(type);
    }

    public void setType(EquipmentType type) {
        this.type = type == null ? null : type.getId();
    }

    public EquipmentStatus getStatus() {
        return status == null ? null : EquipmentStatus.fromId(status);
    }

    public void setStatus(EquipmentStatus status) {
        this.status = status == null ? null : status.getId();
    }
}
