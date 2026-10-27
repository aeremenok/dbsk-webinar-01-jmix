---

description: "Task list for feature implementation — Заявки на оборудование: подача и статус"

---

# Tasks: Заявки на оборудование — подача и статус

**Input**: Design documents from `/specs/001-submit-equipment-request/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: включены по требованию конституции (принцип VII) и AGENTS (правило 10): каждый сценарий
Given/When/Then из `spec.md` — автоматический тест.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Основной код: `src/main/java/com/debugskills/equipment/…`
- Тесты: `src/test/java/com/debugskills/equipment/…`
- Liquibase: `src/main/resources/com/debugskills/equipment/liquibase/…`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Проверка исходного состояния — фундамент неизменяемого кода.

- [ ] T001 Verify baseline is green: run `./gradlew compileJava` and `./gradlew test` from repo root
    before any changes; both must pass. (Do NOT run `bootRun`.)

**Checkpoint**: старт — компиляция и тесты зелёные.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Схема БД, сущность, enum, DTO, обработчик ошибок и каркас контроллера/сервиса —
то, без чего не работает ни один сценарий.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Schema & entity

- [ ] T002 [P] Create Liquibase changelog
    `src/main/resources/com/debugskills/equipment/liquibase/changelog/004-equipment-request.xml`
    (changeSet `004-010-create-equip-equipment-request`, author `equipment-starter`) per `data-model.md`:
    table `EQUIP_EQUIPMENT_REQUEST` with all `StandardJmixEntity` columns (`ID` `${uuid.type}` PK
    NOT NULL, `VERSION` int NOT NULL default 1, `CREATE_TS`, `CREATED_BY` VARCHAR(50), `UPDATE_TS`,
    `UPDATED_BY` VARCHAR(50), `DELETE_TS`, `DELETED_BY`) plus `EMPLOYEE_ID` `${uuid.type}` NOT NULL,
    `TYPE` VARCHAR(50) NOT NULL, `REASON` VARCHAR(500) NOT NULL, `STATUS` VARCHAR(50) NOT NULL,
    `EQUIPMENT_ID` `${uuid.type}` (nullable), `QUEUE_POSITION` int (nullable); indexes
    `IDX_EQUIP_EQUIPMENT_REQUEST_ON_EMPLOYEE` (EMPLOYEE_ID) and unique
    `IDX_EQUIP_EQUIPMENT_REQUEST_ON_EMP_TYPE` (EMPLOYEE_ID, TYPE); FKs
    `FK_EQUIP_EQUIPMENT_REQUEST_ON_EMPLOYEE` → EQUIP_EMPLOYEE(ID),
    `FK_EQUIP_EQUIPMENT_REQUEST_ON_EQUIPMENT` → EQUIP_EQUIPMENT(ID).
- [ ] T003 Add `<include file="/com/debugskills/equipment/liquibase/changelog/004-equipment-request.xml"/>`
    to the END of `src/main/resources/com/debugskills/equipment/liquibase/changelog.xml`
    (only add the include — do not touch existing includes/changeSets).
- [ ] T004 [P] Create enum `EquipmentRequestStatus` in
    `src/main/java/com/debugskills/equipment/entity/EquipmentRequestStatus.java` per `research.md` R3
    and `data-model.md`: `EnumClass<String>`, constants `RESERVED("RESERVED")`, `QUEUED("QUEUED")`,
    `getId()`, static `fromId(String)` returning `null` for unknown values; copy pattern from
    `EquipmentStatus.java`.
- [ ] T005 Create entity `EquipmentRequest` in
    `src/main/java/com/debugskills/equipment/entity/EquipmentRequest.java` per `data-model.md` and
    `research.md` R3: `@JmixEntity`, `@Entity(name = "equipment_EquipmentRequest")`,
    `@Table(name = "EQUIP_EQUIPMENT_REQUEST", indexes = { @Index(UNIQUE) on EMPLOYEE_ID,TYPE })`,
    extends `StandardJmixEntity`; fields — `employee` (`@ManyToOne(LAZY)` + `@JoinColumn(name =
    "EMPLOYEE_ID", nullable = false)`, `optional = false`), `type` String with `EquipmentType`
    accessors (nullable = false), `reason` String (nullable = false, length = 500), `status` String
    with `EquipmentRequestStatus` accessors (nullable = false), `equipment` (`@ManyToOne(LAZY)` +
    `@JoinColumn(name = "EQUIPMENT_ID")`, nullable), `queuePosition` Integer; Jakarta imports only;
    no fields from `StandardJmixEntity` re-declared.

### API scaffolding

- [ ] T006 [P] Create DTO records in `src/main/java/com/debugskills/equipment/api/` per
    `contracts/equipment-requests-api.md`:
    `CreateEquipmentRequest(String employeeEmail, String equipmentType, String reason)` — each field
    `@NotBlank` (jakarta.validation.constraints);
    `EquipmentRequestResponse(UUID id, String status, String inventoryNumber, Integer queuePosition)`
    annotated `@JsonInclude(JsonInclude.Include.NON_NULL)` with static `from(EquipmentRequest r)`;
    `ApiError(String error, String message)`. NOTE: `ApiError` несёт одно строковое `message` по
    FR-010/`contracts/` — НЕ `List<String> details` из примера `docs/jmix/rest.md`.
- [ ] T007 Create business exception `EquipmentRequestException` (RuntimeException holding `code`
    + `message`) in `src/main/java/com/debugskills/equipment/app/EquipmentRequestException.java`;
    then create `@RestControllerAdvice` `ApiExceptionHandler` in
    `src/main/java/com/debugskills/equipment/api/ApiExceptionHandler.java` per `research.md` R1 and
    `contracts/equipment-requests-api.md`: map `MethodArgumentNotValidException` → 400
    `ApiError("validation_failed", …)`, `HttpMessageNotReadableException` → 400
    `ApiError("malformed_request", …)`, and `EquipmentRequestException` → 400
    `ApiError(code, message)`.
- [ ] T008 Create `@RestController` `EquipmentRequestController` in
    `src/main/java/com/debugskills/equipment/api/EquipmentRequestController.java`: `@PostMapping`
    on `@RequestMapping("/api/equipment-requests")`, signature
    `ResponseEntity<EquipmentRequestResponse> create(@Valid @RequestBody CreateEquipmentRequest body)`
    delegating to `EquipmentRequestService.submit(...)`; return `ResponseEntity.ok(...)`.
    (depends on T009 — create together with the service skeleton so the class compiles)
- [ ] T009 Create `@Service` `EquipmentRequestService` in
    `src/main/java/com/debugskills/equipment/app/EquipmentRequestService.java` with
    constructor-injected `UnconstrainedDataManager` and placeholder
    `@Transactional public EquipmentRequestResponse submit(CreateEquipmentRequest body)` returning
    nothing yet (body filled in US1–US4).

**Checkpoint**: foundation ready — `./gradlew compileJava` compiles; Liquibase `004` applies on
`./gradlew test` context startup (DB in-memory builds master changelog).

---

## Phase 3: User Story 1 - Резервирование свободной единицы (Priority: P1) 🎯 MVP

**Goal**: Действующий сотрудник подаёт заявку на тип со свободной единицей; единица резервируется
(статус оборудования → `BOOKED`), ответ — 200 `RESERVED` + `inventoryNumber`.

**Independent Test**: MockMvc-тест A1: POST валидной заявки `ivan.petrov@company.com`/`LAPTOP` →
200, `status=RESERVED`, `inventoryNumber` с префиксом `LAP-`, `id` присутствует, `queuePosition`
отсутствует.

### Tests for User Story 1

- [ ] T010 [US1] Add test-data isolation infra per `research.md` R8 in
    `src/test/java/com/debugskills/equipment/api/EquipmentRequestControllerTest.java`:
    `@Autowired UnconstrainedDataManager dataManager` + `SystemAuthenticator`; static
    `SEED_STATUS` map (`"LAP-001"→AVAILABLE, "LAP-002"→AVAILABLE, "LAP-003"→ISSUED,
    "MON-001"→ISSUED, "MON-002"→ISSUED, "HDS-001"→AVAILABLE`); `@AfterEach` inside
    `systemAuthenticator.runWithSystem(...)` deletes ALL `EquipmentRequest` rows then restores seed
    `Equipment` statuses; NEVER create/delete seed `Employee` rows.
- [ ] T011 [P] [US1] Write MockMvc test
    `given_ivan_laptop_when_post_then_reserved` for scenario A1 in
    `src/test/java/com/debugskills/equipment/api/EquipmentRequestControllerTest.java`: expect 200,
    `$.status == "RESERVED"`, `$.inventoryNumber` prefix `LAP-`, `$.id` present, `$.queuePosition`
    doesNotExist. Must FAIL now (service not implemented).

### Implementation for User Story 1

- [ ] T012 [US1] Implement employee lookup as the first step of `EquipmentRequestService.submit(...)`
    (`src/main/java/com/debugskills/equipment/app/EquipmentRequestService.java`): JPQL
    `lower(e.email) = :email` with parameter `body.employeeEmail().trim().toLowerCase()` (FR-002);
    if not found, throw the `equipment_request` business exception with code `employee_not_found`
    (message per `contracts/equipment-requests-api.md`).
- [ ] T013 [US1] Implement equipment-type validation in `submit(...)`: `EquipmentType.fromId(body
    .equipmentType())`; if `null` (or raw string not decoded), throw business exception with code
    `invalid_equipment_type` (FR-003, 400 per FR-010).
- [ ] T014 [US1] Implement reservation branch in `submit(...)`: load available unit with
    `.query("select e from equipment_Equipment e where e.type = :type and e.status = :st order by
    e.inventoryNumber").parameter("type", type).parameter("st", EquipmentStatus.AVAILABLE)
    .lockMode(LockModeType.PESSIMISTIC_WRITE).maxResults(1).optional()` (research.md R5/R6); if
    present — `unit.setStatus(EquipmentStatus.BOOKED)`, create `EquipmentRequest` with
    `status=RESERVED`, `equipment=unit`, `reason`, `employee`, `type`, `dataManager.save(request, unit)`;
    map to `EquipmentRequestResponse.from(request)` (FR-006, FR-010).

**Checkpoint**: US1 fully functional and testable independently — A1 green.

---

## Phase 4: User Story 2 - Постановка в очередь (Priority: P1)

**Goal**: Свободных единиц типа нет — заявка встаёт в очередь, ответ — 200 `QUEUED` +
`queuePosition`. Позиция считается per тип: число уже стоящих `QUEUED` заявок того же типа + 1
(FR-007, FR-008).

**Independent Test**: MockMvc-тесты A3-Alexey, A4, A5: при отсутствии свободных ноутбуков первая
заявка получает `queuePosition=1`, вторая — `2`; заявка Ольги на монитор — `queuePosition=1`
(очередь отдельна по типу).

### Tests for User Story 2

- [ ] T015 [P] [US2] Write MockMvc tests for scenarios A3 (Алексей, после резерва Марии),
    A4 (Ольга), A5 (Ольга → монитор `QUEUED` `queuePosition=1`) in
    `src/test/java/com/debugskills/equipment/api/EquipmentRequestControllerTest.java` — expect 200,
    `$.status == "QUEUED"`, exact `$.queuePosition`. Must FAIL until T016.

### Implementation for User Story 2

- [ ] T016 [US2] Implement queue branch in `submit(...)`: when no free unit — compute
    `Long queued = dataManager.loadValue("select count(r) from equipment_EquipmentRequest r where
    r.type = :type and r.status = :st", Long.class).parameter("type", type).parameter("st",
    EquipmentRequestStatus.QUEUED).one();` then `queuePosition = queued.intValue() + 1`; create
    `EquipmentRequest` with `status=QUEUED`, `queuePosition`, `equipment=null`; save (FR-007,
    FR-008, FR-009 — очередь уже стоящих не сдвигается).

**Checkpoint**: US1 + US2 работают: резерв и очередь покрыты тестами.

---

## Phase 5: User Story 3 - Повторная заявка на тот же тип (Priority: P2)

**Goal**: У сотрудника уже есть активная заявка на тип — новая не создаётся, возвращается
существующая как есть (причина из повторной заявки игнорируется), никаких сдвигов очереди/резервов
(FR-005, BR-3, US3-Acceptance 1–2, A2/A8/A9).

**Independent Test**: MockMvc-тесты A2 (тот же `id`, что A1), A8 (email `" IVAN.Petrov@Company.com "`
→ тот же `id`, что A1), A9 (Алексей повторно с другой причиной → тот же `id` из A3 и
`queuePosition=1`).

### Tests for User Story 3

- [ ] T017 [P] [US3] Write MockMvc tests for A2, A8, A9 in
    `src/test/java/com/debugskills/equipment/api/EquipmentRequestControllerTest.java`: reuse the `id`
    captured from A1/A3 within the same test method (each test builds its own Given-state); A8 — body
    `"IVAN.Petrov@Company.com"` with leading/trailing spaces; A9 — assert `$.queuePosition` unchanged.

### Implementation for User Story 3

- [ ] T018 [US3] Implement dedup in `submit(...)` BEFORE reservation: query
    `.query("select r from equipment_EquipmentRequest r where r.employee = :emp and r.type = :type")
    .parameter("emp", employee).parameter("type", type).optional()`; if present — return
    `EquipmentRequestResponse.from(existing)` immediately (reason NOT stored/updated). The unique
    index `IDX_EQUIP_EQUIPMENT_REQUEST_ON_EMP_TYPE` (T002) is the DB-level backstop for BR-3
    (FR-005, FR-009).

**Checkpoint**: US1–US3 work: дубликаты не создаются, регистр/пробелы email не ломают поиск.

---

## Phase 6: User Story 4 - Отклонение некорректных заявок (Priority: P2)

**Goal**: Заявки от уволенных/неизвестных сотрудников, с пустой причиной и неизвестным типом
отклоняются HTTP 400 с телом `{"error": ..., "message": ...}` и понятным объяснением (FR-002,
FR-003, FR-004, FR-010).

**Independent Test**: MockMvc-тесты A6 (Пётр-уволен → 400, `employee_inactive`; неизвестный email →
400, `employee_not_found`), A7 (пустая `reason` → 400, `validation_failed`; `equipmentType=PRINTER` →
400, `invalid_equipment_type`).

### Tests for User Story 4

- [ ] T019 [P] [US4] Write MockMvc tests for A6 and A7 in
    `src/test/java/com/debugskills/equipment/api/EquipmentRequestControllerTest.java`: expect 400 and
    exactly the `error` codes listed in the Goal; whitespace-only `reason` (`"   "`) also rejected.

### Implementation for User Story 4

- [ ] T020 [US4] Add inactive-employee check in `submit(...)` after T012 lookup:
    if `employee.getActive() == false` throw business exception with code `employee_inactive`
    (FR-002); ensure `ApiExceptionHandler` maps both business exceptions to 400 `ApiError`.
- [ ] T021 [US4] Confirm bean-validation path: blank/missing `employeeEmail`/`equipmentType`/`reason`
    (incl. whitespace-only `reason`) fail `@NotBlank` on `CreateEquipmentRequest` and produce 400
    `validation_failed` via `ApiExceptionHandler#onInvalid`; malformed JSON → 400
    `malformed_request` (FR-001, FR-004, contracts table).

**Checkpoint**: US1–US4 complete — все 9 сценариев A1–A9 покрыты тестами.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Конкурентность (Q3=A), полный прогон и проверка охраны конституции.

- [ ] T022 [P] Write concurrency/atomicity test in
    `src/test/java/com/debugskills/equipment/app/EquipmentRequestServiceTest.java` (@SpringBootTest):
    Given — two OWN `Employee` rows (unique emails, distinct from seed) via `UnconstrainedDataManager`
    inside `systemAuthenticator.runWithSystem(...)`; ensure EXACTLY ONE `Equipment` of the requested
    type with `AVAILABLE` status exists (e.g., temporarily set seeded LAP-001/LAP-002 to `BOOKED`
    in the test setup, or an equivalent Given), and restore seed statuses in `@AfterEach`. When — two
    threads (each a separate transaction, started by `CountDownLatch`) invoke `service.submit(...)`
    for the two employees against that type. Then — assert exactly one `RESERVED` and one `QUEUED`
    with `queuePosition=1`, and that the single unit ended `BOOKED` (never double-reserved; spec Edge
    Case, SC-005). Clean up created rows in `@AfterEach`.
- [ ] T023 Run `./gradlew test` from repo root; all tests (existing `PingControllerTest`,
    `EquipmentRequestsApplicationTests` and new feature tests) must be green.
- [ ] T024 Run `./gradlew compileJava` and `scripts/check.sh` from repo root; expect PASS lines —
    guards `security/**`, `ApiSecurityConfiguration.java`, `build.gradle` untouched, no
    `[NEEDS CLARIFICATION]` left in `spec.md`, test count not decreased.

**Checkpoint**: `./gradlew test` зелёный, `check.sh` PASS. (Ручная бизнес-приёмка `scripts/accept.sh`
выполняется человеком на свежем `bootRun` — отдельным терминалом, вне сессии агента.)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - В этой фиче один эндпоинт и одна транзакция в `EquipmentRequestService`, поэтому сюжетные фазы
    надстраивают метод `submit(...)` последовательно: **US2 зависит от US1**, **US3 зависит от US1**
    (нужен загрузка сотрудника + сохранившаяся заявка), **US4 зависит от US1** (тот же lookup).
- **Polish (Final Phase)**: Depends on all user stories complete

### User Story Dependencies

- **US1 (P1)**: after Foundational; no other-story dependency
- **US2 (P1)**: after US1 (same `submit` method, queue branch)
- **US3 (P2)**: after US1; uses employee lookup + existing request (edges A8/A9)
- **US4 (P2)**: after US1 (lookup/type) + US2/US3 not required but recommended

### Within Each User Story

- Tests принадлежат сюжету и пишутся на свои сценарии рядом с реализацией; тест обязан быть красным
  до реализации и зелёным после.
- Entities/models before service branches, service before controller wiring (контроллер — T008 уже в
  Foundational).
- Story complete before moving to next priority.

### Parallel Opportunities

- Phase 2: T002, T004, T006 — независимые файлы, можно параллельно (T003 за T002, T005 за T004,
  T007 за T006; T008 вместе с T009 — см. dependency note).
- Tests внутри сюжета: T011, T015, T017, T019 — разные тест-методы, но в общем файле
  `EquipmentRequestControllerTest.java`; конфликт правок одного файла — писать последовательно либо
  добавлять в разные классы.
- T022 (конкурентность) и T024 (check.sh) независимы от остальных polish-задач.

---

## Parallel Example: User Story 1

```bash
# Тесты US1:
Task: "Add test-data isolation infra (@AfterEach) in EquipmentRequestControllerTest.java"
Task: "Write MockMvc test for A1 (reserved) in EquipmentRequestControllerTest.java"

# Реализация US1 (последовательно, все в EquipmentRequestService.submit):
Task: "Employee lookup (normalized email)"
Task: "Equipment type validation"
Task: "Reservation branch with lockMode(PESSIMISTIC_WRITE) + DTO mapping"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001 baseline).
2. Complete Phase 2: Foundational (T002–T009).
3. Complete Phase 3: US1 (T010–T014).
4. **STOP and VALIDATE**: `./gradlew test` — A1 test green; deploy/demo if ready.

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. + US1 (резерв) → test independently → MVP
3. + US2 (очередь) → test independently
4. + US3 (дедупликация) → test independently
5. + US4 (отказы + email-нормализация A8) → test independently
6. Polish (конкурентность Q3, check.sh)

### Parallel Team Strategy

Один разработчик/агент (сессия implement ведёт `submit(...)` последовательно). Если параллельно —
Foundational вместе, затем US1 одним, US4-validation-prep другим; но файл `EquipmentRequestService.java`
один, поэтому безопаснее последовательный порядок сюжетов.

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to a user story for traceability
- `EquipmentRequestService.submit(...)` — единственная точка бизнес-логики; сюжеты надстраивают её ветви
- Каждый сценарий Given/When/Then из spec.md — тест (конституция VII), тесты не удалять
- Никогда не запускать `./gradlew bootRun` в сессии агента
- `security/**`, `ApiSecurityConfiguration.java`, `build.gradle` и существующие changeSet'ы `001–003`
  не трогать