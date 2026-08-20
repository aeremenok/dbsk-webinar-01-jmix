package com.debugskills.equipment.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Assignment of a Jmix resource role (by its {@code CODE}) to a {@link User}.
 * Kept as an explicit entity so the starter is self-contained and easy to seed
 * from Liquibase. Loaded in {@code DatabaseUserRepository.loadUserByUsername}.
 */
@JmixEntity
@Entity(name = "equipment_UserRole")
@Table(name = "EQUIP_USER_ROLE",
        indexes = {
                @Index(name = "IDX_EQUIP_USER_ROLE_USER", columnList = "USER_ID")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_EQUIP_USER_ROLE", columnNames = {"USER_ID", "ROLE_CODE"})
        })
public class UserRole {

    @Id
    @Column(name = "ID", nullable = false)
    @JmixGeneratedValue
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @NotNull
    @JoinColumn(name = "USER_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @InstanceName
    @NotNull
    @Column(name = "ROLE_CODE", nullable = false, length = 50)
    private String roleCode;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }
}
