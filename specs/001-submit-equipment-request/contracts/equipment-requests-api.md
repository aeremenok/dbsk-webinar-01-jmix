# Contract: POST /api/equipment-requests

Потребитель — корпоративный портал (`docs/brief/portal-requirement.md`). Коды HTTP и формат ошибок
выбраны командой (зафиксированы в `spec.md` FR-010).

## Запрос

`POST /api/equipment-requests`, `Content-Type: application/json`.

| Поле | Тип | Обязательное | Значение |
|---|---|---|---|
| `employeeEmail` | string | да (непустое, `@NotBlank`) | email сотрудника |
| `equipmentType` | string | да (непустое, `@NotBlank`) | `LAPTOP`, `MONITOR`, `HEADSET` |
| `reason` | string | да (непустое, `@NotBlank`) | причина заявки |

Пример тела:
```json
{ "employeeEmail": "ivan.petrov@company.com", "equipmentType": "LAPTOP", "reason": "Новый проект" }
```

## Успешный ответ (HTTP 200)

| Поле | Когда присутствует | Тип | Значение |
|---|---|---|---|
| `id` | всегда | string (UUID) | идентификатор заявки |
| `status` | всегда | string | `RESERVED` или `QUEUED` |
| `inventoryNumber` | если `RESERVED` | string | инвентарный номер зарезервированной единицы |
| `queuePosition` | если `QUEUED` | integer | позиция в очереди (с 1) |

Поля `inventoryNumber`/`queuePosition` отсутствуют в JSON, когда неприменимы (`@JsonInclude(NON_NULL)`).

Пример (зарезервировано):
```json
{ "id": "...", "status": "RESERVED", "inventoryNumber": "LAP-001" }
```

Пример (в очереди):
```json
{ "id": "...", "status": "QUEUED", "queuePosition": 2 }
```

## Ответ с ошибкой (HTTP 400)

Одна и та же форма для всех отказов:

| Поле | Тип | Значение |
|---|---|---|
| `error` | string | машинный код ошибки |
| `message` | string | понятное человеку объяснение |

```json
{ "error": "employee_not_found", "message": "Сотрудник не найден" }
```

### Коды `error`

| Код | Условие | Сообщение (`message`, ориентировочно) |
|---|---|---|
| `validation_failed` | отсутствует/пустое обязательное поле | перечисляет невалидные поля |
| `malformed_request` | тело не читаемо (битый JSON) | «Некорректный формат запроса» |
| `employee_not_found` | email не найден в справочнике | «Сотрудник не найден» |
| `employee_inactive` | сотрудник уволен (`active=false`) | «Сотрудник не является действующим» |
| `invalid_equipment_type` | тип не из LAPTOP/MONITOR/HEADSET | «Неизвестный тип оборудования» |

## Правила соответствия сценариям приёмки

| Сценарий | Ответ |
|---|---|
| A1 Иван → ноутбук | 200, `RESERVED`, `inventoryNumber` = один из LAP-001/LAP-002 |
| A2 Иван повторно → ноутбук | 200, `id` тот же, что в A1 |
| A3 Мария → ноутбук / Алексей → ноутбук | Мария 200 `RESERVED`; Алексей 200 `QUEUED` `queuePosition=1` |
| A4 Ольга → ноутбук | 200 `QUEUED` `queuePosition=2` |
| A5 Ольга → монитор | 200 `QUEUED` `queuePosition=1` (очередь per тип) |
| A6 Пётр (уволен) / неизвестный | 400 `employee_inactive` / `employee_not_found` |
| A7 пустая причина / `PRINTER` | 400 `validation_failed` / `invalid_equipment_type` |
| A8 email в другом регистре/с пробелами | 200, `id` тот же, что в A1 |
| A9 Алексей повторно, другая причина | 200, `id` из A3, `queuePosition=1` (причина не меняет заявку) |