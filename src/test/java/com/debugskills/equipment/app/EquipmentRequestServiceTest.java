package com.debugskills.equipment.app;

import com.debugskills.equipment.api.CreateEquipmentRequest;
import com.debugskills.equipment.api.EquipmentRequestResponse;
import com.debugskills.equipment.entity.Employee;
import com.debugskills.equipment.entity.Equipment;
import com.debugskills.equipment.entity.EquipmentRequest;
import com.debugskills.equipment.entity.EquipmentStatus;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.SystemAuthenticator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class EquipmentRequestServiceTest {

    private static final Map<String, EquipmentStatus> SEED_STATUS = Map.of(
            "LAP-001", EquipmentStatus.AVAILABLE,
            "LAP-002", EquipmentStatus.AVAILABLE,
            "LAP-003", EquipmentStatus.ISSUED,
            "MON-001", EquipmentStatus.ISSUED,
            "MON-002", EquipmentStatus.ISSUED,
            "HDS-001", EquipmentStatus.AVAILABLE);

    @Autowired
    EquipmentRequestService service;

    @Autowired
    UnconstrainedDataManager dataManager;

    @Autowired
    SystemAuthenticator systemAuthenticator;

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
            for (Employee emp : dataManager.load(Employee.class)
                    .query("select e from equipment_Employee e where e.email like 't-%@test.local'")
                    .list()) {
                dataManager.remove(emp);
            }
        });
    }

    @Test
    void twoConcurrentSubmitsOnSingleUnit_oneReserved_oneQueued() throws Exception {
        List<Employee> employees = systemAuthenticator.withSystem(() -> {
            Employee emp1 = dataManager.create(Employee.class);
            emp1.setFullName("Test One");
            emp1.setEmail("t-" + java.util.UUID.randomUUID() + "@test.local");
            emp1.setActive(true);
            Employee emp2 = dataManager.create(Employee.class);
            emp2.setFullName("Test Two");
            emp2.setEmail("t-" + java.util.UUID.randomUUID() + "@test.local");
            emp2.setActive(true);
            dataManager.save(emp1, emp2);
            // Exactly one AVAILABLE laptop: BOOK both seeded laptops? No — we want exactly one free.
            // LAP-001 stays AVAILABLE; LAP-002 goes BOOKED.
            Equipment lap2 = dataManager.load(Equipment.class)
                    .query("select e from equipment_Equipment e where e.inventoryNumber = :n")
                    .parameter("n", "LAP-002")
                    .one();
            lap2.setStatus(EquipmentStatus.BOOKED);
            dataManager.save(lap2);
            return List.of(emp1, emp2);
        });

        Employee emp1 = employees.get(0);
        Employee emp2 = employees.get(1);

        // Keep exactly one AVAILABLE unit: LAP-001. LAP-003 is ISSUED by seed.
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<EquipmentRequestResponse> result1 = new AtomicReference<>();
        AtomicReference<EquipmentRequestResponse> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread t1 = new Thread(() -> runSubmit(start, ready, emp1, result1, error));
        Thread t2 = new Thread(() -> runSubmit(start, ready, emp2, result2, error));
        t1.start();
        t2.start();
        ready.await();
        start.countDown();
        t1.join(30000);
        t2.join(30000);

        assertNotNull(result1.get(), "thread1 threw: " + error.get());
        assertNotNull(result2.get(), "thread2 threw: " + error.get());

        String s1 = result1.get().status();
        String s2 = result2.get().status();
        assertTrue(s1.equals("RESERVED") || s1.equals("QUEUED"));
        assertTrue(s2.equals("RESERVED") || s2.equals("QUEUED"));
        assertEquals(1, (s1.equals("RESERVED") ? 1 : 0) + (s2.equals("RESERVED") ? 1 : 0));
        assertEquals(1, (s1.equals("QUEUED") ? 1 : 0) + (s2.equals("QUEUED") ? 1 : 0));

        EquipmentRequestResponse queued = s1.equals("QUEUED") ? result1.get() : result2.get();
        assertEquals(1, queued.queuePosition());

        Equipment unit = systemAuthenticator.withSystem(() ->
                dataManager.load(Equipment.class)
                        .query("select e from equipment_Equipment e where e.inventoryNumber = :n")
                        .parameter("n", "LAP-001")
                        .one());
        assertEquals(EquipmentStatus.BOOKED, unit.getStatus());
    }

    private void runSubmit(CountDownLatch start, CountDownLatch ready, Employee emp,
                           AtomicReference<EquipmentRequestResponse> target,
                           AtomicReference<Throwable> error) {
        try {
            ready.countDown();
            start.await();
            target.set(service.submit(new CreateEquipmentRequest(
                    emp.getEmail(), "LAPTOP", "concurrent")));
        } catch (Throwable t) {
            error.set(t);
        }
    }
}