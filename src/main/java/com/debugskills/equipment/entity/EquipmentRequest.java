package com.debugskills.equipment.entity;

import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@JmixEntity
@Entity(name = "equipment_EquipmentRequest")
@Table(name = "EQUIP_EQUIPMENT_REQUEST", indexes = {
        @Index(name = "IDX_EQUIP_EQUIPMENT_REQUEST_ON_EMPLOYEE", columnList = "EMPLOYEE_ID"),
        @Index(name = "IDX_EQUIP_EQUIPMENT_REQUEST_ON_EMP_TYPE", columnList = "EMPLOYEE_ID, TYPE", unique = true)
})
public class EquipmentRequest extends StandardJmixEntity {

    @JoinColumn(name = "EMPLOYEE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Employee employee;

    @Column(name = "TYPE", nullable = false)
    private String type;

    @Column(name = "REASON", nullable = false, length = 500)
    private String reason;

    @Column(name = "STATUS", nullable = false)
    private String status;

    @JoinColumn(name = "EQUIPMENT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Equipment equipment;

    @Column(name = "QUEUE_POSITION")
    private Integer queuePosition;

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public EquipmentType getType() {
        return type == null ? null : EquipmentType.fromId(type);
    }

    public void setType(EquipmentType type) {
        this.type = type == null ? null : type.getId();
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public EquipmentRequestStatus getStatus() {
        return status == null ? null : EquipmentRequestStatus.fromId(status);
    }

    public void setStatus(EquipmentRequestStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public void setEquipment(Equipment equipment) {
        this.equipment = equipment;
    }

    public Integer getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(Integer queuePosition) {
        this.queuePosition = queuePosition;
    }
}