# Тестирование (Jmix 2.6, JUnit 5, Spring Boot Test)

Запуск: `./gradlew test`. Тест должен быть зелёным до закрытия задачи. Каждый сценарий Given/When/Then из `spec.md` — отдельный тест; имя в стиле `given_..._when_..._then_...`. Тесты не удаляем и не ослабляем, чтобы «позеленеть».

Источник в полной документации: «Testing» (строки 78454–79588), интеграционные тесты — с 78466. MockMvc в документации Jmix не описан, шаблон ниже — стендовый.

## Окружение тестов

`src/test/resources/application.properties` задаёт отдельную БД в памяти: `jdbc:hsqldb:mem:equipment_test`. При старте контекста Liquibase прогоняет весь мастер-файл (в том числе сид из `003-seed.xml`). Образцы в проекте: `EquipmentRequestsApplicationTests` (контекст поднимается) и `api/PingControllerTest` (MockMvc).

## Тест REST-эндпоинта через MockMvc

`@SpringBootTest` + `@AutoConfigureMockMvc`: поднимается весь контекст, запрос идёт через цепочку фильтров безопасности (в том числе `ApiSecurityConfiguration`) и настоящий контроллер, но без сетевого порта.

```java
package com.debugskills.equipment.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EquipmentRequestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void given_validBody_when_post_then_successWithContractFields() throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeEmail": "ivan.petrov@company.com",
                                 "equipmentType": "LAPTOP",
                                 "reason": "Новый проект"}
                                """))
                .andExpect(status().is2xxSuccessful())          // точный код — по contracts/
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.inventoryNumber").isNotEmpty())
                .andExpect(jsonPath("$.queuePosition").doesNotExist());   // нет поля или null
    }

    @Test
    void given_emptyReason_when_post_then_clientError() throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeEmail": "ivan.petrov@company.com",
                                 "equipmentType": "LAPTOP",
                                 "reason": ""}
                                """))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error").isNotEmpty());   // формат ошибки — по contracts/
    }
}
```

Первый тест меняет состояние (резервирует единицу оборудования и создаёт запись): без уборки из раздела «Изоляция данных» он испортит следующие тесты.

Что важно:

- Для GET: `get("/api/...")`, для заголовков — `.header(...)`. Тело ответа целиком: `.andReturn().getResponse().getContentAsString()`.
- Статус проверяй так, как зафиксировано в `contracts/` (`isCreated()`, `isOk()`, `isBadRequest()`); `is2xxSuccessful()` / `is4xxClientError()` — только когда контракт не фиксирует код.
- `jsonPath("$.queuePosition").doesNotExist()` проходит и когда поля нет, и когда оно `null`.
- `status().is4xxClientError()` в MockMvc не покажет разницу между своим обработчиком ошибок и стандартным `/error` (error dispatch в MockMvc не выполняется): финальную проверку формата ошибок делай `scripts/accept.sh`.
- Класс с `@AutoConfigureMockMvc` получает отдельный контекст Spring, но БД в памяти общая, см. ниже.

## Изоляция данных между тестами

БД `equipment_test` в памяти создаётся один раз на JVM тестов. Spring кэширует контекст между тестовыми классами с одинаковой конфигурацией, и даже при создании нового контекста (другой набор аннотаций, `@DirtiesContext`) БД с тем же именем остаётся: в памяти она живёт, пока жива JVM, и Liquibase второй раз ничего не создаёт. Поэтому:

- данные, сохранённые одним тестом, видят **все следующие тесты любого класса**;
- порядок выполнения тестов не гарантирован: тест не должен зависеть от того, что выполнилось до него;
- `@DirtiesContext` данные **не сбрасывает**.

Сид справочников (5 сотрудников, оборудование из `003-seed.xml`) общий для всех тестов. `PingControllerTest` проверяет, что сотрудников ровно 5: лишний `Employee`, оставленный другим тестом, его сломает.

Два рабочих приёма (можно сочетать):

1. **Свои данные.** Тест создаёт собственные `Employee` / `Equipment` с уникальными значениями (`"t-" + UUID.randomUUID() + "@test.local"`) и удаляет их.
2. **Уборка за собой.** `@AfterEach` удаляет строки, которые создал тестируемый код (заявки), и возвращает строки сида, которые код изменил.

Сами строки сида (`Employee`, `Equipment`) не удаляем.

```java
@Autowired
UnconstrainedDataManager dataManager;
@Autowired
SystemAuthenticator systemAuthenticator;

// статусы оборудования в сиде (003-seed.xml)
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
        // 1. сначала зависимые строки: заявки (имя класса сущности — из вашей модели)
        dataManager.remove(dataManager.load(EquipmentRequest.class).all().list());

        // 2. затем вернуть изменённые строки сида
        for (Equipment eq : dataManager.load(Equipment.class).all().list()) {
            EquipmentStatus seed = SEED_STATUS.get(eq.getInventoryNumber());
            if (seed != null && eq.getStatus() != seed) {
                eq.setStatus(seed);
                dataManager.save(eq);
            }
        }
    });
}
```

Замечания:

- Данные из теста пишем через `UnconstrainedDataManager` (права не проверяются), внутри `SystemAuthenticator.runWithSystem(...)` или `withSystem(...)`: в потоке теста нет аутентифицированного пользователя, а поля аудита (`createdBy`, `updatedBy`) заполняются от текущего пользователя.
- Порядок уборки: сначала записи со ссылками, потом то, на что они ссылаются (иначе внешний ключ не даст удалить).
- Вместо загрузки и удаления сущностей можно очистить таблицу SQL (раздел Test Data Cleanup, строка 78554): `JdbcTestUtils.deleteFromTables(new JdbcTemplate(dataSource), "EQUIP_EQUIPMENT_REQUEST")`; имя таблицы — `EQUIP_` + имя сущности в `UPPER_SNAKE_CASE`. Удобно, когда ссылку на созданную записью строку из теста не получить.
- Если сценарию нужно особое состояние (например, «свободных ноутбуков нет»), его готовит сам тест в блоке Given, а не рассчитывает на результат другого теста.

## Интеграционный тест сервиса (без HTTP)

Годится, когда проверяется логика сервиса, которая ходит в БД через `DataManager`.

```java
@SpringBootTest
class MyServiceTest {

    @Autowired
    MyService service;
    @Autowired
    UnconstrainedDataManager dataManager;
    @Autowired
    SystemAuthenticator systemAuthenticator;

    @Test
    void given_seededEmployee_when_service_then_resultSaved() {
        systemAuthenticator.runWithSystem(() -> {
            Employee emp = dataManager.load(Employee.class)
                    .query("select e from equipment_Employee e where e.email = :email")
                    .parameter("email", "ivan.petrov@company.com")
                    .one();

            // when: вызов сервиса; then: assertEquals / assertNotNull ...
        });
    }
}
```

На тестовый класс `@Transactional` не ставь: сервис тогда выполняется в транзакции теста, и проверить его собственные границы транзакции (коммит, откат при ошибке) нельзя. Данные чисти вручную, как выше.

## Unit-тест без контекста

Только для чистой логики без `DataManager` (расчёты, преобразование в DTO): обычный JUnit 5 без `@SpringBootTest`, при необходимости Mockito (идёт в составе `spring-boot-starter-test`, новых зависимостей не нужно). Цепочки fluent-загрузчика (`load().query().parameter().list()`) мокать неудобно: для кода, который ходит в БД, пиши интеграционный тест. Раздел документации: «Unit Tests», строка 79362.
