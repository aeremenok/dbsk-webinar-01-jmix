# Implementation Plan: Заявки на оборудование — подача и статус

**Branch**: `001-submit-equipment-request` | **Date**: 2026-10-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-submit-equipment-request/spec.md`

## Summary

Реализовать единственный REST-эндпоинт `POST /api/equipment-requests` по контракту портала
(`docs/brief/portal-requirement.md`): сотрудник подаёт заявку (email, тип, причина) и сразу получает
либо `RESERVED` с инвентарным номером свободной единицы, либо `QUEUED` с позицией очереди. Дубликат
(повторная заявка того же сотрудника на тот же тип) возвращает существующую заявку. Заявки от
уволенных/неизвестных сотрудников, с пустой причиной и неизвестным типом отклоняются (HTTP 400).

Технический подход (детали — в `research.md`): новая сущность `EquipmentRequest` + enum
`EquipmentRequestStatus`, новый Liquibase changeSet `004-*.xml`, `@RestController` + DTO +
`@RestControllerAdvice` в пакете `api`, бизнес-логика в `@Service` пакета `app`, всё чтение/запись —
через `UnconstrainedDataManager`. Резервирование последней свободной единицы атомарно (пессимистическая
блокировка). Схема и `security/**`, `ApiSecurityConfiguration`, `build.gradle` не меняются.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3, Jmix 2.6.2 (BOM/плагин `io.jmix`)

**Primary Dependencies**: Jmix Core (DataManager, Security, Flow UI — не трогаем), EclipseLink JPA,
Liquibase, `spring-boot-starter-validation` (javax→jakarta). Новых зависимостей нет.

**Storage**: HSQLDB in-memory (`jdbc:hsqldb:mem:equipment` — dev; `jdbc:hsqldb:mem:equipment_test` — тесты)

**Testing**: JUnit 5 + Spring Boot Test + MockMvc (`./gradlew test`); финальная бизнес-приёмка — `scripts/accept.sh`

**Target Platform**: Linux-сервер (web-service), HTTP

**Project Type**: web-service (REST API)

**Performance Goals**: не заданы явно; сценарий лабы однопользовательский. Скорость отклика — «мгновенно» для одного запроса.

**Constraints**: не менять `build.gradle`, `security/**`, `ApiSecurityConfiguration.java`; UI не трогать;
схема — только новый changeSet; наружу — только DTO; `/api/**` без аутентификации (анонимный пользователь).

**Scale/Scope**: 5 сотрудников, 6 единиц оборудования (сид), один эндпоинт. Очередь считается per тип
оборудования, заявки в scope не закрываются.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип конституции | Оценка | Обоснование |
|---|---|---|---|
| I | Спецификация — источник истины | ✅ | `spec.md` зафиксирован; код ниже только по нему |
| II | Без новых зависимостей | ✅ | `build.gradle` не меняется; все нужды покрывают Jmix + Spring (validation) + Mockito (из starter-test) |
| III | Только собственные `@RestController` под `/api/**` | ✅ | Один `@RestController` в `api/`; `security/**`, `ApiSecurityConfiguration` не трогаем |
| IV | Схема — только новыми changeSet'ами | ✅ | Новый `004-equipment-request.xml` + `<include>`; `001–003` не редактируем |
| V | Контекст по этапам | ✅ | Jmix-факты собраны в `research.md` со `Snippet` и `Source` |
| VI | Наружу — только DTO | ✅ | Запрос/ответ — Java `record`; сущности не выходят из контроллера |
| VII | Каждый сценарий Given/When/Then — тест | ✅ | A1–A9 и все US — покрыты тестами (см. `quickstart.md`); `./gradlew test` зелёный |

**Итог:** нарушений нет. Complexity Tracking не заполняется.

## Project Structure

### Documentation (this feature)

```text
specs/001-submit-equipment-request/
├── plan.md              # этот файл
├── research.md          # Jmix-решения со Snippet + Source
├── data-model.md        # сущности, поля, валдиация, переходы состояний
├── quickstart.md        # сценарии проверки и команды
├── contracts/           # контракт POST /api/equipment-requests
└── tasks.md             # (создаёт /speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/debugskills/equipment/
├── api/
│   ├── ApiSecurityConfiguration.java      # НЕ ТРОГАТЬ
│   ├── PingController.java                # существующий
│   ├── EquipmentRequestController.java    # НОВЫЙ @RestController
│   ├── CreateEquipmentRequest.java        # НОВЫЙ DTO (record, вход)
│   ├── EquipmentRequestResponse.java      # НОВЫЙ DTO (record, выход)
│   ├── ApiError.java                      # НОВЫЙ DTO ошибки
│   └── ApiExceptionHandler.java           # НОВЫЙ @RestControllerAdvice (400)
├── app/
│   └── EquipmentRequestService.java       # НОВЫЙ @Service (бизнес-логика, @Transactional)
└── entity/
    ├── EquipmentRequest.java              # НОВАЯ сущность
    ├── EquipmentRequestStatus.java        # НОВЫЙ enum (EnumClass)
    └── (Employee, Equipment, EquipmentType, EquipmentStatus — существующие)

src/main/resources/com/debugskills/equipment/liquibase/
├── changelog.xml                          # + <include> нового файла (редактируем только добавление)
└── changelog/004-equipment-request.xml    # НОВАЯ таблица EQUIP_EQUIPMENT_REQUEST

src/test/java/com/debugskills/equipment/api/
├── EquipmentRequestControllerTest.java    # НОВЫЙ: сценарии A1–A9
└── EquipmentRequestServiceTest.java       # НОВЫЙ: логика + конкурентность резервирования
```

**Structure Decision**: один проект (web-service). Код живёт в существующих пакетах `api`, `app`,
`entity` — без новых модулей и без UI.

## Complexity Tracking

> Заполняется только при нарушении конституции. Нарушений нет — секция пустая.