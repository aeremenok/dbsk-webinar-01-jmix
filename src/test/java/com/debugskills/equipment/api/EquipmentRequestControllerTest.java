package com.debugskills.equipment.api;

import com.debugskills.equipment.entity.Equipment;
import com.debugskills.equipment.entity.EquipmentRequest;
import com.debugskills.equipment.entity.EquipmentStatus;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.SystemAuthenticator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EquipmentRequestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UnconstrainedDataManager dataManager;

    @Autowired
    SystemAuthenticator systemAuthenticator;

    private static final Map<String, EquipmentStatus> SEED_STATUS = Map.of(
            "LAP-001", EquipmentStatus.AVAILABLE,
            "LAP-002", EquipmentStatus.AVAILABLE,
            "LAP-003", EquipmentStatus.ISSUED,
            "MON-001", EquipmentStatus.ISSUED,
            "MON-002", EquipmentStatus.ISSUED,
            "HDS-001", EquipmentStatus.AVAILABLE);

    @AfterEach
    void cleanUp() {
        systemAuthenticator.runWithSystem(() -> {
            dataManager.remove(dataManager.load(EquipmentRequest.class).all().list());
            for (Equipment eq : dataManager.load(Equipment.class).all().list()) {
                EquipmentStatus seed = SEED_STATUS.get(eq.getInventoryNumber());
                if (seed != null && eq.getStatus() != seed) {
                    eq.setStatus(seed);
                    dataManager.save(eq);
                }
            }
        });
    }

    @Test
    void given_ivan_laptop_when_post_then_reserved() throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"ivan.petrov@company.com","equipmentType":"LAPTOP","reason":"Новый проект"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.inventoryNumber").value(
                        org.hamcrest.Matchers.startsWith("LAP-")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.queuePosition").doesNotExist());
    }

    @Test
    void given_alexey_after_maria_reserved_when_post_then_queued_1() throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"ivan.petrov@company.com","equipmentType":"LAPTOP","reason":"для работы"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"maria.sidorova@company.com","equipmentType":"LAPTOP","reason":"для проекта"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));

        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"aleksey.ivanov@company.com","equipmentType":"LAPTOP","reason":"для разработки"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.queuePosition").value(1));
    }

    @Test
    void given_olga_after_queue_of_one_when_post_laptop_then_queued_2() throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"ivan.petrov@company.com","equipmentType":"LAPTOP","reason":"для работы"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"maria.sidorova@company.com","equipmentType":"LAPTOP","reason":"для проекта"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"aleksey.ivanov@company.com","equipmentType":"LAPTOP","reason":"для разработки"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.queuePosition").value(1));

        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"olga.smirnova@company.com","equipmentType":"LAPTOP","reason":"для аналитики"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.queuePosition").value(2));
    }

    @Test
    void given_olga_when_post_monitor_then_queued_1() throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"olga.smirnova@company.com","equipmentType":"MONITOR","reason":"Нужен второй монитор"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.queuePosition").value(1));
    }

    @Test
    void given_ivan_reserved_when_post_same_then_same_id() throws Exception {
        MvcResult first = mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"ivan.petrov@company.com","equipmentType":"LAPTOP","reason":"Новый проект"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andReturn();
        String firstId = JsonPath.read(
                first.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"ivan.petrov@company.com","equipmentType":"LAPTOP","reason":"Новый проект"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));
    }

    @Test
    void given_ivan_reserved_when_post_upper_spaces_then_same_id() throws Exception {
        MvcResult first = mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"ivan.petrov@company.com","equipmentType":"LAPTOP","reason":"Новый проект"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andReturn();
        String firstId = JsonPath.read(
                first.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":" IVAN.Petrov@Company.com ","equipmentType":"LAPTOP","reason":"Новый проект"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));
    }

    @Test
    void given_alexey_queued_when_post_other_reason_then_same_position() throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"ivan.petrov@company.com","equipmentType":"LAPTOP","reason":"для работы"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"maria.sidorova@company.com","equipmentType":"LAPTOP","reason":"для проекта"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
        MvcResult alexey = mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"aleksey.ivanov@company.com","equipmentType":"LAPTOP","reason":"для разработки"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.queuePosition").value(1))
                .andReturn();
        String alexeyId = JsonPath.read(
                alexey.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"aleksey.ivanov@company.com","equipmentType":"LAPTOP","reason":"Совсем другая причина"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alexeyId))
                .andExpect(jsonPath("$.queuePosition").value(1));
    }

    @Test
    void given_inactive_employee_when_post_then_400_inactive() throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"petr.kuznetsov@company.com","equipmentType":"LAPTOP","reason":"Нужен ноутбук для работы"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("employee_inactive"));
    }

    @Test
    void given_unknown_email_when_post_then_400_not_found() throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"nobody@company.com","equipmentType":"LAPTOP","reason":"Нужен ноутбук для работы"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("employee_not_found"));
    }

    @Test
    void given_blank_reason_when_post_then_400_validation_failed() throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"ivan.petrov@company.com","equipmentType":"LAPTOP","reason":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    @Test
    void given_whitespace_reason_when_post_then_400_validation_failed() throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"ivan.petrov@company.com","equipmentType":"LAPTOP","reason":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    @Test
    void given_unknown_type_when_post_then_400_invalid_equipment_type() throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType("application/json")
                        .content("""
                                {"employeeEmail":"ivan.petrov@company.com","equipmentType":"PRINTER","reason":"Нужен принтер для печати"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_equipment_type"));
    }
}