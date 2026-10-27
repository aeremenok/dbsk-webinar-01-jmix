# Data Model: Заявки на оборудование

Модель для фичи «подача заявки на оборудование». Источник требований — `spec.md`; подтверждение
Jmix-механик — `research.md`.

## Обзор

Добавляется одна сущность `EquipmentRequest` и один enum `EquipmentRequestStatus`. Справочники
`Employee`, `Equipment` и enum `EquipmentType`, `EquipmentStatus` уже существуют и не меняются.

```text
Employee (существующая) 1 ──< EquipmentRequest >── 0..1 Equipment (существующая)
                                  │
                                  └─ type: EquipmentType (enum, String)
                                  └─ status: EquipmentRequestStatus (enum, String)
```

## Сущности

### EquipmentRequest (новая)

Таблица `EQUIP_EQUIPMENT_REQUEST`, имя сущности `equipment_EquipmentRequest`. Наследует
`StandardJmixEntity` (поля `id`, `version`, `createTs`, `createdBy`, `updateTs`, `updatedBy`,
`deleteTs`, `deletedBy` присутствуют и в колонках таблицы).

| Атрибут | Колонка | Тип | Nullable | Примечание |
|---|---|---|---|---|
| `id` / audit… | `ID`, `VERSION`, `CREATE_TS`, … | см. базовый класс | — | из `StandardJmixEntity` |
| `employee` | `EMPLOYEE_ID` | `${uuid.type}` (FK → EQUIP_EMPLOYEE) | no | `@ManyToOne(LAZY)`, обязателен (BR-1) |
| `type` | `TYPE` | `VARCHAR(50)` | no | `EquipmentType` через `String`-поле |
| `reason` | `REASON` | `VARCHAR(500)` | no | причина заявки (BR-1) |
| `status` | `STATUS` | `VARCHAR(50)` | no | `EquipmentRequestStatus` через `String`-поле |
| `equipment` | `EQUIPMENT_ID` | `${uuid.type}` (FK → EQUIP_EQUIPMENT) | yes | заполнен только если `RESERVED` |
| `queuePosition` | `QUEUE_POSITION` | `int` | yes | заполнен только если `QUEUED` |

Индексы:
- `IDX_EQUIP_EQUIPMENT_REQUEST_ON_EMPLOYEE` (не уникальный) — по `EMPLOYEE_ID`.
- `IDX_EQUIP_EQUIPMENT_REQUEST_ON_EMP_TYPE` (**unique**) — по (`EMPLOYEE_ID`, `TYPE`): гарантия BR-3
  («не больше одной активной заявки сотрудника на тип») на уровне БД.

Ограничения ссылок:
- `FK_EQUIP_EQUIPMENT_REQUEST_ON_EMPLOYEE` → `EQUIP_EMPLOYEE(ID)`;
- `FK_EQUIP_EQUIPMENT_REQUEST_ON_EQUIPMENT` → `EQUIP_EQUIPMENT(ID)`.

### EquipmentRequestStatus (новый enum)

`EnumClass<String>`, хранится как `id` (`String`).

| Константа | id | Смысл |
|---|---|---|
| `RESERVED` | `RESERVED` | единица зарезервирована (см. BR-4) |
| `QUEUED` | `QUEUED` | заявка в очереди (см. BR-5) |

### Employee (существующая, без изменений)

`EQUIP_EMPLOYEE`: `fullName`, `email` (уникальный), `department`, `active` (`Boolean`, «действующий»).
`active = false` — уволенный сотрудник.

### Equipment (существующая, без изменений)

`EQUIP_EQUIPMENT`: `inventoryNumber` (уникальный), `name`, `type` (`EquipmentType`), `status`
(`EquipmentStatus`: `AVAILABLE`, `BOOKED`, `ISSUED`, `MAINTENANCE`). Для резервирования используется
переход `AVAILABLE → BOOKED`.

## Валидация (маппинг на FR)

| Правило | FR | Где проверяется |
|---|---|---|
| `employeeEmail`, `equipmentType`, `reason` обязательны и непусты | FR-001, FR-004 | Bean Validation `@NotBlank` на DTO → `validation_failed` (400) |
| `reason` из пробелов считается пустым | FR-004 | `@NotBlank` (trim) |
| `equipmentType` ∈ {LAPTOP, MONITOR, HEADSET} | FR-003 | сервис: `EquipmentType.fromId(...) == null` → `invalid_equipment_type` (400) |
| сотрудник найден и действующий | FR-002 | сервис: не найден → `employee_not_found`; `active=false` → `employee_inactive` (400) |
| email без регистра и пробелов | FR-002 (A8) | сервис: `trim().toLowerCase()` + `lower(e.email)` |
| одна активная заявка сотрудника на тип | FR-005 | сервис (запрос дубликата) + уникальный индекс БД |
| свободная единица → резерв | FR-006 | сервис: `EQUIPMENT.status=AVAILABLE` → `BOOKED`, заявка `RESERVED` |
| нет свободной → очередь | FR-007, FR-008 | сервис: `count(QUEUED типа) + 1`, заявка `QUEUED` |

## Переходы состояний

- `Equipment.status`: `AVAILABLE → BOOKED` (при резервировании). Обратных переходов в scope нет
  (выдача/возврат вне scope); в тестах статус возвращает `@AfterEach`.
- `EquipmentRequest`: создаётся сразу в конечном состоянии `RESERVED` (с `equipment`) или `QUEUED`
  (с `queuePosition`). Переходов состояния после создания в scope нет.

## Инварианты

1. `RESERVED` ⇒ `equipment != null` и `queuePosition == null`; `QUEUED` ⇒ наоборот.
2. Не более одной записи per (`EMPLOYEE_ID`, `TYPE`) (уникальный индекс).
3. Зарезервированная единица имеет `Equipment.status = BOOKED` (не `AVAILABLE`), поэтому не может быть
   отдана другому (FR-006/SC-005).
4. `queuePosition` у заявки равно числу `QUEUED`-заявок того же типа, созданных раньше, + 1 (FR-008).