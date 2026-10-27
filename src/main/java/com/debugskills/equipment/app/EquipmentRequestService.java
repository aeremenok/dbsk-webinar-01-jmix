package com.debugskills.equipment.app;

import com.debugskills.equipment.api.CreateEquipmentRequest;
import com.debugskills.equipment.api.EquipmentRequestResponse;
import com.debugskills.equipment.entity.Employee;
import com.debugskills.equipment.entity.Equipment;
import com.debugskills.equipment.entity.EquipmentRequest;
import com.debugskills.equipment.entity.EquipmentRequestStatus;
import com.debugskills.equipment.entity.EquipmentStatus;
import com.debugskills.equipment.entity.EquipmentType;
import io.jmix.core.UnconstrainedDataManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class EquipmentRequestService {

    private final UnconstrainedDataManager dataManager;

    @Autowired
    public EquipmentRequestService(UnconstrainedDataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Transactional
    public EquipmentRequestResponse submit(CreateEquipmentRequest body) {
        EquipmentType type = EquipmentType.fromId(body.equipmentType());
        if (type == null) {
            throw new EquipmentRequestException("invalid_equipment_type", "Неизвестный тип оборудования");
        }

        Employee employee = dataManager.load(Employee.class)
                .query("select e from equipment_Employee e where lower(e.email) = :email")
                .parameter("email", body.employeeEmail().trim().toLowerCase())
                .optional().orElse(null);
        if (employee == null) {
            throw new EquipmentRequestException("employee_not_found", "Сотрудник не найден");
        }
        if (!Boolean.TRUE.equals(employee.getActive())) {
            throw new EquipmentRequestException("employee_inactive", "Сотрудник не является действующим");
        }

        Optional<EquipmentRequest> existing = dataManager.load(EquipmentRequest.class)
                .query("select r from equipment_EquipmentRequest r where r.employee = :emp and r.type = :type")
                .parameter("emp", employee)
                .parameter("type", type)
                .optional();
        if (existing.isPresent()) {
            return EquipmentRequestResponse.from(existing.get());
        }

        EquipmentRequest request = dataManager.create(EquipmentRequest.class);
        request.setEmployee(employee);
        request.setType(type);
        request.setReason(body.reason());

        Optional<Equipment> candidate = dataManager.load(Equipment.class)
                .query("select e from equipment_Equipment e where e.type = :type and e.status = :st order by e.inventoryNumber")
                .parameter("type", type)
                .parameter("st", EquipmentStatus.AVAILABLE)
                .maxResults(1)
                .optional();

        if (candidate.isEmpty()) {
            Long queued = dataManager.loadValue(
                    "select count(r) from equipment_EquipmentRequest r where r.type = :type and r.status = :st",
                    Long.class)
                    .parameter("type", type)
                    .parameter("st", EquipmentRequestStatus.QUEUED)
                    .one();
            request.setStatus(EquipmentRequestStatus.QUEUED);
            request.setQueuePosition(queued.intValue() + 1);
            dataManager.save(request);
            return EquipmentRequestResponse.from(request);
        }

        Equipment unit = dataManager.load(Equipment.class)
                .id(candidate.get().getId())
                .lockMode(LockModeType.PESSIMISTIC_WRITE)
                .optional()
                .orElse(null);
        if (unit == null || unit.getStatus() != EquipmentStatus.AVAILABLE) {
            Long queued = dataManager.loadValue(
                    "select count(r) from equipment_EquipmentRequest r where r.type = :type and r.status = :st",
                    Long.class)
                    .parameter("type", type)
                    .parameter("st", EquipmentRequestStatus.QUEUED)
                    .one();
            request.setStatus(EquipmentRequestStatus.QUEUED);
            request.setQueuePosition(queued.intValue() + 1);
            dataManager.save(request);
            return EquipmentRequestResponse.from(request);
        }

        unit.setStatus(EquipmentStatus.BOOKED);
        request.setStatus(EquipmentRequestStatus.RESERVED);
        request.setEquipment(unit);
        dataManager.save(request, unit);
        return EquipmentRequestResponse.from(request);
    }
}