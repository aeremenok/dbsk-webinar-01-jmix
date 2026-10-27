# Отчёт /speckit.analyze (прогон 27.10)

Сохранён из сессии opencode. Отчёт описывает артефакты **до** правок: после него агенту сказали «fix all issues», и в этом чекпоинте уже исправленные spec, plan и tasks.

## Specification Analysis Report

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| I1 | Inconsistency | MEDIUM | spec.md:158 (SC-002) vs tasks.md T017, contracts A8 | SC-002 перечисляет «A1–A7 и A9», но приёмка `scripts/accept.sh` содержит **A8** (email с пробелами/регистром); A8 покрыт FR-002, edge case и T017, но выпал из критерия успеха | Дополнить SC-002 до «A1–A9» |
| I2 | Inconsistency | MEDIUM | contracts/equipment-requests-api.md (таблица кодов) vs tasks.md T007 | В таблице кодов ошибок нет `malformed_request`, хотя T007 мапит `HttpMessageNotReadableException` на него | Добавить `malformed_request` в таблицу кодов контракта |
| U1 | Underspecification | MEDIUM | tasks.md T007, T012, T013, T020 | Бизнес-исключение `EquipmentRequestException` упомянуто только в скобках T007 («defined here or in app») — отдельной задачи на его создание нет, а T012/T013/T020 бросают именно его | Сделать отдельную задачу на `EquipmentRequestException` или явно закрепить создание в T007 |
| U2 | Underspecification | MEDIUM | tasks.md T022 | «type with EXACTLY ONE available Equipment» недостижимо «на чистом» сиде: LAP-001/LAP-002/HDS-001 уже свободны. Given-состояние теста не описано | Задать Given явно (пометить сид-единицы `BOOKED` в `@BeforeEach` либо описать подготовку одного доступного юнита) |
| I3 | Inconsistency | LOW | rest.md (пример `ApiError(error, List<String> details)`) vs FR-010/contracts/tasks (`{error, message}`) | Источник `docs/jmix/rest.md` даёт другой сигнатурный пример; spec (источник истины) — строка `message` | Implement следует контракту FR-010/`contracts/`, а не примеру из rest.md |
| I4 | Inconsistency | LOW | tasks.md T008 ([P]) vs T009 | T008 ([P], контроллер) ссылается на `EquipmentRequestService`, создаваемый в T009 — скрытая зависимость компиляции | Снять [P] с T008 или создать сервис до контроллера |
| A1 | Ambiguity | LOW | spec.md:174-175 (Assumption) | «ограниченной разумной длины … уточнит этап plan» — устарело: plan/data-model уже зафиксировали `VARCHAR(500)` | Обновить Assumption до фактического `500` |
| A2 | Ambiguity | LOW | spec.md:149-150 (Key Entities) | «зарезервирована» для состояния единицы не совпадает с enum `BOOKED` (data-model) | Уточнить слово-в-спецификации как «забронирована (BOOKED)» |
| D1 | Duplication | LOW | spec.md:104-105 (edge) vs US3-Acceptance 2 / FR-005 | Edge case «повторная заявка с другой причиной» дублирует FR-005/US3 | Безвредно; при желании оставить ссылку вместо текста |

## Constitution Alignment Issues

Нет. Все 7 принципов (I–VII) соблюдены: DTO-only (VI), `@RestController` под `/api/**` (III), только новый changeSet (IV), только `UnconstrainedDataManager` во вторичных слоях, `security/**`/`ApiSecurityConfiguration`/`build.gradle` не трогаются (проверены в plan и T024).

## Unmapped Tasks

Нет. Каждая задача T001–T024 мапится на сюжет, инфраструктуру или проверку конституции.

## Coverage Summary

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 | ✅ | T006, T008, T011 | |
| FR-002 | ✅ | T012, T020, T017 | + A8 (нормализация) |
| FR-003 | ✅ | T013, T019 | |
| FR-004 | ✅ | T006, T021, T019 | |
| FR-005 | ✅ | T018, T002, T017 | + уникальный индекс |
| FR-006 | ✅ | T014, T011 | |
| FR-007 | ✅ | T016, T015 | |
| FR-008 | ✅ | T016, T015 | |
| FR-009 | ✅ | T016, T018 | implicit (записи не переставляются) |
| FR-010 | ✅ | T007, T008, T020, T021 | |
| SC-001 | ✅ | T008 (endpoint) | implicit |
| SC-002 | ✅ | T011, T015, T017, T019, quickstart | но без A8 в тексте (см. I1) |
| SC-003 | ✅ | T007, T020, T021 | |
| SC-004 | ✅ | T018, T002 | |
| SC-005 | ✅ | T014, T022 | |

## Metrics

- Total Requirements: **15** (10 FR + 5 SC)
- Total Tasks: **24**
- Coverage: **100%** (каждое требование имеет ≥1 задачу; 2 — implicit, помечены)
- Ambiguity Count: **2**
- Duplication Count: **1**
- Critical Issues Count: **0**

## Next Actions

Нет CRITICAL — можно переходить к `/speckit.implement`. Перед стартом стоит закрыть 4 MEDIUM, чтобы implement не спотыкался:

- **I1** — поправить SC-002 (включить A8).
- **I2** — добавить `malformed_request` в таблицу кодов контракта.
- **U1** — выделить/закрепить создание `EquipmentRequestException`.
- **U2** — уточнить Given-состояние конкурентного теста T022.

LOW (I3, I4, A1, A2, D1) — можно поправить в том же проходе или оставить.

Конкретные команды правок: ручная правка `spec.md`, `contracts/equipment-requests-api.md`, `tasks.md` (или повторные `/speckit.specify`/`/speckit.tasks` для точечных уточнений). Конституция не требует изменений.

---

Хотите, чтобы я предложил **конкретные тексты правок** для 4 MEDIUM-находок (I1, I2, U1, U2)? Применять их автоматически я не буду — только после вашего явного подтверждения.
