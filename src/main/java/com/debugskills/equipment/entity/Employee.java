package com.debugskills.equipment.entity;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@JmixEntity
@Entity(name = "equipment_Employee")
@Table(name = "EQUIP_EMPLOYEE", indexes = {
        @Index(name = "IDX_EQUIP_EMPLOYEE_ON_EMAIL", columnList = "EMAIL", unique = true)
})
public class Employee extends StandardJmixEntity {

    @InstanceName
    @Column(name = "FULL_NAME", nullable = false)
    private String fullName;

    @Column(name = "EMAIL", nullable = false)
    private String email;

    @Column(name = "DEPARTMENT")
    private String department;

    /**
     * {@code true} for a current employee, {@code false} for a dismissed one.
     */
    @Column(name = "ACTIVE", nullable = false)
    private Boolean active = true;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
