# Research: оборудование — заявки

Всё, что нужно `/speckit.implement` знать о Jmix 2.6 для этой фичи, со `Snippet` и `Source`.
Полную документацию (`jmix-complete-docs.adoc`) на этапе implement открывать не нужно.

---

## R1. Собственный `@RestController` под `/api/**`

**Decision:** Один контроллер `EquipmentRequestController` с `@PostMapping`; DTO-`record` на вход и
выход; бизнес-логика вынесена в `@Service`. Безопасность для `/api/**` уже настроена — отдельно
ничего не настраиваем.

**Rationale:** Конституция (принцип III): только собственные `@RestController`, `security/**` и
`ApiSecurityConfiguration` не трогаем. Цепочка `ApiSecurityConfiguration` уже открывает `/api/**`
анонимно и выключает CSRF.

**Alternatives:** Generic REST (`jmix-rest-starter`, `/rest/entities`) — запрещён конституцией и новыми
зависимостями.

**Snippet:**
```java
@RestController
@RequestMapping("/api/equipment-requests")
public class EquipmentRequestController {
    private final EquipmentRequestService service;
    public EquipmentRequestController(EquipmentRequestService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<EquipmentRequestResponse> create(
            @Valid @RequestBody CreateEquipmentRequest body) {
        return ResponseEntity.ok(service.submit(body));
    }
}
```

**Source:** `docs/jmix/rest.md` (Custom Endpoints, `jmix-complete-docs.adoc` ≈ строка 73147; Public Endpoints с 73190).

---

## R2. `UnconstrainedDataManager` вместо `DataManager`

**Decision:** В контроллерах, сервисе `app` и тестах — `io.jmix.core.UnconstrainedDataManager`
(инжекция через конструктор).

**Rationale:** `/api/**` выполняется от анонимного пользователя Jmix, у которого нет прав:
обычный `DataManager` вернул бы пустой результат или бросил `AccessDeniedException`.
`UnconstrainedDataManager` — тот же fluent-API без проверки прав.

**Alternatives:** `DataManager` + назначение прав анониму — требует править `security/**` (запрещено).

**Snippet:**
```java
private final UnconstrainedDataManager dataManager;
public EquipmentRequestService(UnconstrainedDataManager dataManager) { this.dataManager = dataManager; }
```

**Source:** `docs/jmix/rest.md` («Почему UnconstrainedDataManager», `jmix-complete-docs.adoc` строка 18065 «UnconstrainedDataManager», аноним без прав — 73949).

---

## R3. Сущность `EquipmentRequest` + enum `EquipmentRequestStatus`

**Decision:** Новая сущность наследует `StandardJmixEntity`; `@JmixEntity`, `@Entity(name="equipment_EquipmentRequest")`,
`@Table(name="EQUIP_EQUIPMENT_REQUEST", indexes=...)`. Status и type хранятся строкой через enum
`EnumClass<String>` по образцу существующих `EquipmentStatus`/`EquipmentType`.

**Rationale:** Правила проекта (AGENTS 2, 3): аннотации на полях, Jakarta-импорты, id/version/аудит уже
в базовом классе, enum — строковый `id`, не `@Enumerated`.

**Alternatives:** `@Enumerated(EnumType.STRING)` — не совместимо с метаданными Jmix; отдельное поле
под хранение статуса + геттер/сеттер enum — то, что и делаем (соответствует образцу).

**Snippet:**
```java
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
    private String type;                       // EquipmentType через геттер/сеттер

    @Column(name = "REASON", nullable = false, length = 500)
    private String reason;

    @Column(name = "STATUS", nullable = false)
    private String status;                     // EquipmentRequestStatus через геттер/сеттер

    @JoinColumn(name = "EQUIPMENT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Equipment equipment;               // только RESERVED

    @Column(name = "QUEUE_POSITION")
    private Integer queuePosition;             // только QUEUED

    // getters/setters; type/status со String-полем и enum-аксессорами (см. enums.md)
}
```

Enum:
```java
public enum EquipmentRequestStatus implements EnumClass<String> {
    RESERVED("RESERVED"), QUEUED("QUEUED");
    // getId(), static fromId(String) -> null для неизвестного (как EquipmentType)
}
```

**Source:** `docs/jmix/enums.md` (Enumerations, `jmix-complete-docs.adoc` строка 21645); `docs/jmix/db-migrations.md`
(таблица обязательных колонок базового класса); образцы сущностей — `entity/Employee.java`, `entity/Equipment.java`.

---

## R4. Liquibase: новая таблица

**Decision:** Новый файл `004-equipment-request.xml` (changeSet `004-010-create-equip-equipment-request`)
плюс `<include>` в конец `changelog.xml`. В таблице — все колонки `StandardJmixEntity`.

**Rationale:** Конституция (принцип IV): только новые changeSet'ы; `001–003` не редактируем. Тип id и
ссылок — `${uuid.type}`.

**Alternatives:** `generateMigrationScripts` / правка существующих changeSet'ов — неприменимо (Studio нет,
контрольные суммы).

**Snippet:**
```xml
<changeSet id="004-010-create-equip-equipment-request" author="equipment-starter">
    <createTable tableName="EQUIP_EQUIPMENT_REQUEST">
        <column name="ID" type="${uuid.type}"><constraints primaryKey="true" nullable="false"/></column>
        <column name="VERSION" type="int" defaultValueNumeric="1"><constraints nullable="false"/></column>
        <column name="CREATE_TS" type="TIMESTAMP"/>
        <column name="CREATED_BY" type="VARCHAR(50)"/>
        <column name="UPDATE_TS" type="TIMESTAMP"/>
        <column name="UPDATED_BY" type="VARCHAR(50)"/>
        <column name="DELETE_TS" type="TIMESTAMP"/>
        <column name="DELETED_BY" type="VARCHAR(50)"/>
        <column name="EMPLOYEE_ID" type="${uuid.type}"><constraints nullable="false"/></column>
        <column name="TYPE" type="VARCHAR(50)"><constraints nullable="false"/></column>
        <column name="REASON" type="VARCHAR(500)"><constraints nullable="false"/></column>
        <column name="STATUS" type="VARCHAR(50)"><constraints nullable="false"/></column>
        <column name="EQUIPMENT_ID" type="${uuid.type}"/>
        <column name="QUEUE_POSITION" type="int"/>
    </createTable>
    <createIndex tableName="EQUIP_EQUIPMENT_REQUEST" indexName="IDX_EQUIP_EQUIPMENT_REQUEST_ON_EMPLOYEE">
        <column name="EMPLOYEE_ID"/>
    </createIndex>
    <createIndex tableName="EQUIP_EQUIPMENT_REQUEST" indexName="IDX_EQUIP_EQUIPMENT_REQUEST_ON_EMP_TYPE" unique="true">
        <column name="EMPLOYEE_ID"/><column name="TYPE"/>
    </createIndex>
    <addForeignKeyConstraint baseTableName="EQUIP_EQUIPMENT_REQUEST" baseColumnNames="EMPLOYEE_ID"
                             referencedTableName="EQUIP_EMPLOYEE" referencedColumnNames="ID"
                             constraintName="FK_EQUIP_EQUIPMENT_REQUEST_ON_EMPLOYEE"/>
    <addForeignKeyConstraint baseTableName="EQUIP_EQUIPMENT_REQUEST" baseColumnNames="EQUIPMENT_ID"
                             referencedTableName="EQUIP_EQUIPMENT" referencedColumnNames="ID"
                             constraintName="FK_EQUIP_EQUIPMENT_REQUEST_ON_EQUIPMENT"/>
</changeSet>
```
Подключение в конце `changelog.xml`:
```xml
<include file="/com/debugskills/equipment/liquibase/changelog/004-equipment-request.xml"/>
```

**Source:** `docs/jmix/db-migrations.md` (Database Schema Migration, `jmix-complete-docs.adoc` строка 20655; ручные changelog'и с 20719); шаблон таблицы со ссылкой — там же.

---

## R5. JPQL через DataManager: поиск сотрудника, дубликат, свободная единица, размер очереди

**Decision:** Все запросы — fluent-API `dataManager.load(...)` / `loadValue(...)`; в JPQL имя сущности
`equipment_X`; параметры-enum — значением enum.

**Rationale:** Правила AGENTS 3, 5. `EntityManager` напрямую — только при явной необходимости.

**Alternatives:** `EntityManager`/нативные SQL — не нужны (всё покрыто JPQL).

**Snippet:**
```java
// сотрудник: email без регистра + без пробелов (A8 в accept.sh)
Employee employee = dataManager.load(Employee.class)
        .query("select e from equipment_Employee e where lower(e.email) = :email")
        .parameter("email", body.employeeEmail().trim().toLowerCase())
        .optional().orElse(null);

// дубликат: активная заявка того же сотрудника на тот же тип (BR-3 / FR-005)
Optional<EquipmentRequest> existing = dataManager.load(EquipmentRequest.class)
        .query("select r from equipment_EquipmentRequest r where r.employee = :emp and r.type = :type")
        .parameter("emp", employee)
        .parameter("type", type)
        .optional();

// свободная единица типа (первая по инвентарному номеру), с пессимистической блокировкой (R6)
Optional<Equipment> free = dataManager.load(Equipment.class)
        .query("select e from equipment_Equipment e where e.type = :type and e.status = :st order by e.inventoryNumber")
        .parameter("type", type)
        .parameter("st", EquipmentStatus.AVAILABLE)
        .lockMode(LockModeType.PESSIMISTIC_WRITE)
        .maxResults(1)
        .optional();

// размер очереди на тип (FR-008)
Long queued = dataManager.loadValue(
        "select count(r) from equipment_EquipmentRequest r where r.type = :type and r.status = :st", Long.class)
        .parameter("type", type)
        .parameter("st", EquipmentRequestStatus.QUEUED)
        .one();
```

**Source:** `docs/jmix/data-manager.md` (Using DataManager, `jmix-complete-docs.adoc` строка 17545; Loading Entities by Query 17634; Loading Scalar and Aggregate Values 17727).

---

## R6. Атомарное резервирование последней свободной единицы

**Decision:** Всё выполняется в одном `@Transactional`-методе сервиса. Кандидата в свободные единицы
читаем с `.lockMode(LockModeType.PESSIMISTIC_WRITE)` (SELECT … FOR UPDATE), потом помечаем `BOOKED`
и сохраняем заявку. Одновременные запросы на последнюю единицу сериализуются на строке: второй
транзакции достаётся либо другая свободная единица, либо очередь.

**Rationale:** Требование Q3=A (строгая атомарность, без «перепродажи»). Пессимистическая блокировка —
стандартный способ атомарного «забери свободную единицу».

**Alternatives:** (а) никакая блокировка — гонка, два сотрудника увидят один резерв; (б) оптимистичная
блокировка (`@Version`) — ретраи при конфликте, сложнее; (в) условный bulk UPDATE — `DataManager`
умеет только `select`, пришлось бы доставать `EntityManager`.

**Snippet:**
```java
@Service
public class EquipmentRequestService {
    private final UnconstrainedDataManager dataManager;

    @Transactional
    public EquipmentRequestResponse submit(CreateEquipmentRequest body) {
        // 1) тип, 2) сотрудник (R5), 3) дубликат (R5)
        EquipmentRequest request = dataManager.create(EquipmentRequest.class);
        request.setEmployee(employee);
        request.setType(type);
        request.setReason(body.reason());

        Optional<Equipment> free = /* R5, с lockMode(PESSIMISTIC_WRITE) */;
        if (free.isPresent()) {
            Equipment unit = free.get();
            unit.setStatus(EquipmentStatus.BOOKED);
            request.setStatus(EquipmentRequestStatus.RESERVED);
            request.setEquipment(unit);
            dataManager.save(request, unit);
        } else {
            long pos = /* R5: count(QUEUED) */ + 1;
            request.setStatus(EquipmentRequestStatus.QUEUED);
            request.setQueuePosition((int) pos);
            dataManager.save(request);
        }
        return EquipmentRequestResponse.from(request);
    }
}
```
Примечание для тестов конкурентности (реализуется в `tasks`): два потока вызывают `service.submit(...)`
через инжектированный бин (каждый в своей транзакции), используя двух разных сотрудников и тип с одной
свободной единицей; ожидается ровно один `RESERVED` и один `QUEUED` позиции 1.

**Source:** Transaction Management — `jmix-complete-docs.adoc` строка 19528; Using Locks (`lockMode`,
`jakarta.persistence.LockModeType`) — строка 17839; `docs/jmix/data-manager.md` («Транзакции»).

---

## R7. Нормализация email (A8: регистр и пробелы)

**Decision:** Перед поиском входной email обрезается (`trim()`) и приводится к нижнему регистру;
сравнение в JPQL — `lower(e.email) = :email`.

**Rationale:** `scripts/accept.sh`, шаг A8 (`" IVAN.Petrov@Company.com "` должен дать ту же заявку, что
и `ivan.petrov@company.com` в A1). В `spec.md` это зафиксировано в FR-002 и edge case.

**Alternatives:** точное сравнение `e.email = :email` — провалит A8; `upper(...)` — то же, что `lower`,
вопрос вкуса.

**Snippet:** см. R5 («сотрудник»).

**Source:** без Jmix-документации — требование бизнес-приёмки (`scripts/accept.sh` A8, `spec.md` FR-002).

---

## R8. Тесты и изоляция данных

**Decision:** `@SpringBootTest` + `@AutoConfigureMockMvc` для сценариев A1–A9; `@SpringBootTest` для
сервиса и конкурентности. Логика: `UnconstrainedDataManager` + `systemAuthenticator.runWithSystem(...)`
для подготовки/уборки. `@AfterEach` удаляет созданные заявки и возвращает сид-статусы оборудования.

**Rationale:** БД в памяти общая для всех тестов одного прогона; порядок тестов не гарантирован
(раздел тестирования). `PingControllerTest` считает ровно 5 сотрудников — создавать/удалять сид-строки
Employee нельзя.

**Snippet:**
```java
@Autowired
UnconstrainedDataManager dataManager;
@Autowired
SystemAuthenticator systemAuthenticator;

private static final Map<String, EquipmentStatus> SEED_STATUS = Map.of(
        "LAP-001", EquipmentStatus.AVAILABLE, "LAP-002", EquipmentStatus.AVAILABLE,
        "LAP-003", EquipmentStatus.ISSUED, "MON-001", EquipmentStatus.ISSUED,
        "MON-002", EquipmentStatus.ISSUED, "HDS-001", EquipmentStatus.AVAILABLE);

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
```

**Source:** `docs/jmix/testing.md` (Integration Tests — `jmix-complete-docs.adoc` строка 78466; Test Data Cleanup — 78554); примеры — `src/test/java/com/debugskills/equipment/api/PingControllerTest.java`.

---

## Открытые вопросы (NEEDS CLARIFICATION)

Нет. Все технические неизвестности сняты; `GAP` не требуется.