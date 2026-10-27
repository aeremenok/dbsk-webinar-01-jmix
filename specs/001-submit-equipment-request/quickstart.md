# Quickstart: проверка «заявок на оборудование»

Как убедиться, что фича работает end-to-end. См. также `contracts/equipment-requests-api.md`,
`data-model.md`, `research.md` (полная реализация — в `tasks.md`).

## Prerequisites

- JDK 21 (`JAVA_HOME`), Gradle wrapper уже в проекте.
- НЕ запускать `./gradlew bootRun` внутри сессии агента (процесс не завершится). Для ручной проверки
  наружу запускать в отдельном терминале.

## 1. Прогнать тесты (основная проверка)

```bash
./gradlew test
```

Ожидается: зелёный результат. Каждый сценарий Given/When/Then из `spec.md` (A1–A9) покрыт тестом:

| Тест-класс | Что проверяет |
|---|---|
| `EquipmentRequestControllerTest` | A1–A9 через MockMvc (резерв, очередь, дубликаты, отказы, регистр email) |
| `EquipmentRequestServiceTest` | логика сервиса + конкурентность резервирования последней единицы |

## 2. Бизнес-приёмка (скрипт, на запущенном приложении)

Приложение поднимается наружу (отдельный терминал):

```bash
./gradlew bootRun          # дождаться «Started EquipmentRequestsApplication»
```

На свежей БД прогон приёмки:

```bash
scripts/accept.sh          # A1–A9, последняя строка — «9/9»
```

`accept.sh` проверяет класс ответа (2xx/4xx) и поля тела, а не конкретные коды HTTP (конкретные коды —
в `contracts/`). Прогон жирно зависит от «свежей» БД: проверки идут по порядку и опираются на состояние
после предыдущих. При странных провалах — перезапустить `bootRun` и повторить.

## 3. Ручной smoke (curl)

```bash
curl -s localhost:8080/api/ping                      # {"status":"ok","employees":5}

curl -s -X POST localhost:8080/api/equipment-requests \
  -H 'Content-Type: application/json' \
  -d '{"employeeEmail":"ivan.petrov@company.com","equipmentType":"LAPTOP","reason":"проект"}'
# → {"id":"...","status":"RESERVED","inventoryNumber":"LAP-001"}

curl -s -X POST localhost:8080/api/equipment-requests \
  -H 'Content-Type: application/json' \
  -d '{"employeeEmail":"petr.kuznetsov@company.com","equipmentType":"LAPTOP","reason":"работа"}'
# → HTTP 400 {"error":"employee_inactive","message":"..."}
```

## 4. Ожидаемая финальная картина

- `./gradlew test` зелёный, число тестов не меньше, чем было в стартере (+тесты фичи).
- `scripts/accept.sh` → `9/9`.
- `scripts/check.sh` → PASS (конституция соблюдена: `security/**`, `ApiSecurityConfiguration`,
  `build.gradle` не изменены, `[NEEDS CLARIFICATION]` в spec не осталось).

## 5. Что НЕ проверяем (вне scope)

Выдача/возврат, отмена, согласование, уведомления, вход по паролю, админка, ведение справочников,
интеграции — вне scope лабы (BR-6).