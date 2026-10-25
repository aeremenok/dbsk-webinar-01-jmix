# AGENTS.md — Jmix 2.6.2, REST-сервис «Заявки на оборудование»

> **Что это.** Контекстный слой L1 для AI-ассистента. opencode читает этот файл перед каждым запросом, на любом этапе Spec Kit. Файл не пересказывает документацию: он снимает частые галлюцинации модели на нишевом фреймворке и говорит, **какой контекст читать на каком этапе**.
>
> **Версия Jmix:** 2.6.2 (Gradle-плагин `io.jmix` и BOM). **Стек:** Java 21, Spring Boot 3, EclipseLink JPA, Liquibase, HSQLDB в памяти.
>
> **Задача проекта:** REST-сервис `POST /api/equipment-requests` (бриф — `docs/brief/`). UI-слой Jmix (`view/**`, `LoginView`, `MainView`) не трогаем и не расширяем.

---

## 0. Правила работы

### 0.1. Маршрутизация контекста по этапам

Определи этап по команде (`/speckit.specify`, `.clarify`, `.plan`, `.tasks`, `.analyze`, `.implement`) и читай **только** то, что в строке этапа:

| Этап | Читай | Не читай | Пиши |
|---|---|---|---|
| `specify`, `clarify` | `docs/brief/` (бриф, требование портала), `.specify/memory/constitution.md` | `docs/jmix/`, код | `spec.md` |
| `plan` | `spec.md`, конституция, `docs/jmix/INDEX.md`, далее `jmix-complete-docs.adoc` **только диапазонами строк** (`sed -n 'A,Bp'`), тематические `docs/jmix/*.md` | остальной `.adoc` | `plan.md`, `research.md`, `data-model.md`, `contracts/` |
| `tasks` | `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, при необходимости тематические `docs/jmix/*.md` | `.adoc`, `INDEX.md` | `tasks.md` |
| `analyze` | `spec.md`, `plan.md`, `tasks.md`, конституция | код, `.adoc` | отчёт (файлы не меняет) |
| `implement` | `tasks.md`, `research.md`, `data-model.md`, `contracts/`, тематические `docs/jmix/*.md` | **никогда не открывай `jmix-complete-docs.adoc` и `INDEX.md`** | код и тесты |

**`plan` и `research.md`.** У каждого Jmix-решения в `research.md` — Decision / Rationale / Alternatives плюс два обязательных поля:

- **Snippet** — фрагмент кода, которого достаточно для реализации без чтения документации;
- **Source** — откуда взято: заголовок раздела и строка в `jmix-complete-docs.adoc` или тематический файл (`Source: Custom Endpoints, ≈ строка 73147`).

Полную документацию читай по карте `docs/jmix/INDEX.md`: нашёл диапазон — прочитал диапазон, не файл целиком.

**Если не хватает знания (`implement`, `tasks`).** Нужного Jmix-факта нет в `research.md` и тематических `.md` — это дефект плана. Напиши в ответе `GAP: <чего именно не хватает>` и **остановись**. Не угадывай API, не открывай `.adoc`, не придумывай решение. Правка — через `/speckit.plan`.

### 0.2. Остальные правила

1. **Не путай Jmix 2.x с Jmix 1.x / CUBA.** Список запрещённого — раздел 7.
2. **Не выдумывай методы `DataManager`.** Полный список — раздел 5; если метода там нет, читай `docs/jmix/data-manager.md`.
3. **JPQL пишется по имени сущности из `@Entity(name = "equipment_X")`,** не по имени Java-класса: `select e from equipment_Employee e`, а не `from Employee e`.
4. **`createTs` уже помечен `@CreatedDate`** в базовом классе. Не добавляй второе поле «дата создания», не ставь значение вручную. Так же заполняются `createdBy`, `updateTs`, `updatedBy`.
5. **Никогда не запускай `./gradlew bootRun`:** процесс не завершится, сессия зависнет. Проверка — `./gradlew compileJava` и `./gradlew test`.
6. **Не трогай:** `src/main/java/com/debugskills/equipment/security/**` и `src/main/java/com/debugskills/equipment/api/ApiSecurityConfiguration.java`.
7. **Не меняй `build.gradle`:** новых зависимостей нет. Нужна библиотека — остановись и опиши причину, решение за человеком.
8. **Схема БД меняется только новым Liquibase changeSet'ом** (раздел 4). Существующие changeSet'ы не редактируй.
9. **REST — только собственные `@RestController` под `/api/**`** (раздел 6). `jmix-rest-starter`, Generic REST (`/rest/entities`), REST DataStore и Authorization Server запрещены.
10. **Каждый сценарий Given/When/Then из `spec.md` — тест.** `./gradlew test` зелёный до закрытия задачи; тесты не удаляй.
11. **Перед выдачей кода — чеклист раздела 8.**

---

## 1. Структура проекта

```
src/main/java/com/debugskills/equipment/
  EquipmentRequestsApplication.java   # @SpringBootApplication
  entity/        # JPA-сущности и enum; базовый класс StandardJmixEntity
  api/           # @RestController, DTO, обработчик ошибок; ApiSecurityConfiguration — НЕ ТРОГАТЬ
  app/           # сервисы (@Service) с бизнес-логикой; создай пакет, если его нет
  security/      # роли и репозиторий пользователей — НЕ ТРОГАТЬ
  view/          # UI Jmix (LoginView, MainView) — не трогаем

src/main/resources/com/debugskills/equipment/liquibase/
  changelog.xml                       # мастер-файл со всеми <include>
  changelog/NNN-*.xml                 # changelog'и: 001 безопасность, 002 справочники, 003 сид

src/test/java/com/debugskills/equipment/   # тесты (JUnit 5, Spring Boot Test)
src/test/resources/application.properties # отдельная БД в памяти для тестов
docs/brief/   docs/jmix/   specs/   .specify/
```

Базовый пакет — `com.debugskills.equipment`. Пакеты `com.company.app` и другие из чужих примеров не используй.

---

## 2. Сущность

Все сущности наследуют `com.debugskills.equipment.entity.StandardJmixEntity`. Класс проектный: в Jmix 2.6 фреймворкового `io.jmix.core.entity.StandardJmixEntity` нет, **не импортируй его**. Из базового класса уже есть:

- `id` — `UUID`, `@JmixGeneratedValue`;
- `version` — `Integer`, `@Version`;
- `createTs`, `createdBy`, `updateTs`, `updatedBy` — аудит, заполняется фреймворком;
- `deleteTs`, `deletedBy` — колонки без `@DeletedDate` / `@DeletedBy`: мягкое удаление **не** включено.

Эти поля в наследнике не объявляй.

```java
package com.debugskills.equipment.entity;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@JmixEntity
@Entity(name = "equipment_Employee")           // имя для JPQL: equipment_ + класс
@Table(name = "EQUIP_EMPLOYEE")                // таблица: EQUIP_ + UPPER_SNAKE_CASE
public class Employee extends StandardJmixEntity {

    @InstanceName
    @Column(name = "FULL_NAME", nullable = false)
    private String fullName;

    // getters / setters
}
```

Правила:

- **Обязательно:** `@JmixEntity`, `@Entity(name = "equipment_X")`, `@Table(name = "EQUIP_X")`.
- **Аннотации — на полях.** Нельзя `@IdClass`, `@ElementCollection`. Типы атрибутов — обёртки (`Integer`, `Boolean`), не `int` / `boolean`.
- **Импорты — Jakarta:** `jakarta.persistence.*`, `jakarta.validation.constraints.*`, не `javax.*`.
- **Уникальность** обеспечивает уникальный индекс в Liquibase; `@Column(unique = true)` Jmix не интерпретирует.
- Ссылка на другую сущность: `@ManyToOne(fetch = FetchType.LAZY)` + `@JoinColumn(name = "EMPLOYEE_ID")`.
- Таблица и колонки создаются **только** changelog'ом (раздел 4), сам по себе класс схему не меняет.

---

## 3. Enum

Enum хранится через `EnumClass` по строковому `id`, а не через `@Enumerated`. В сущности поле — `String`, геттер и сеттер работают с enum. Образцы: `EquipmentType` и `EquipmentStatus` в пакете `entity`. Подробности и код — `docs/jmix/enums.md`.

---

## 4. Миграции БД (Liquibase вручную)

Studio и `generateMigrationScripts` на стенде **нет**: changelog пишется руками.

1. Новый файл `src/main/resources/com/debugskills/equipment/liquibase/changelog/NNN-имя.xml` (номер на единицу больше максимального).
2. Строка `<include file="/com/debugskills/equipment/liquibase/changelog/NNN-имя.xml"/>` в конец `src/main/resources/com/debugskills/equipment/liquibase/changelog.xml`.
3. В таблице — все колонки `StandardJmixEntity` (`ID`, `VERSION`, `CREATE_TS`, `CREATED_BY`, `UPDATE_TS`, `UPDATED_BY`, `DELETE_TS`, `DELETED_BY`), тип id и ссылок — `${uuid.type}`.
4. Существующие `001`–`003` и их changeSet'ы не редактируй, не переименовывай, не удаляй.

Шаблон файла, имена индексов и внешних ключей, типы колонок — `docs/jmix/db-migrations.md`.

---

## 5. DataManager — fluent API

`DataManager` — главный способ работы с данными. `EntityManager` напрямую не используй без явной причины.

**В REST-коде и в тестах инжектируй `UnconstrainedDataManager`** (`io.jmix.core.UnconstrainedDataManager`): у анонимного пользователя `/api/**` нет прав, обычный `DataManager` вернёт пустой результат или бросит `AccessDeniedException`. Методы у них одинаковые.

```java
private final UnconstrainedDataManager dataManager;   // конструктор или @Autowired
```

### 5.1. Загрузка по ID

```java
Employee e = dataManager.load(Employee.class).id(uuid).one();          // исключение, если не найдено
Optional<Employee> o = dataManager.load(Employee.class).id(uuid).optional();
```

### 5.2. Загрузка по запросу

```java
List<Equipment> list = dataManager.load(Equipment.class)
        .query("select e from equipment_Equipment e where e.type = :type and e.status = :st")
        .parameter("type", EquipmentType.LAPTOP)           // enum передаётся значением
        .parameter("st", EquipmentStatus.AVAILABLE)
        .list();
```

Сокращённая форма (без `select` / `from`, алиас по умолчанию `e`):

```java
dataManager.load(Equipment.class).query("e.inventoryNumber = :n").parameter("n", "LAP-001").optional();
```

Сортировка и ограничение: `.sort(Sort.by("inventoryNumber"))`, `.maxResults(10)`; все записи: `.all()`. Сортировать по времени создания — по полю `createTs`.

### 5.3. Скаляры

```java
Long count = dataManager.loadValue(
        "select count(e) from equipment_Equipment e where e.status = :st", Long.class)
        .parameter("st", EquipmentStatus.AVAILABLE)
        .one();
```

### 5.4. Сохранение, удаление, создание

```java
Employee saved = dataManager.save(employee);     // insert или update; дальше работай с возвращённым
dataManager.remove(employee);                    // удаление: remove(), не delete()
Employee fresh = dataManager.create(Employee.class);   // id сгенерирован, в БД ещё нет
```

Несколько шагов, которые должны пройти атомарно, выполняй в одном методе сервиса с `@org.springframework.transaction.annotation.Transactional`.

**Чего у DataManager нет** (частые галлюцинации): `findAll()`, `findById()`, `findByExample()`, `getRepository()` (это Spring Data), `delete(...)` (правильно `remove`), `commit(...)` (правильно `save`).

---

## 6. REST-слой

Безопасность для `/api/**` уже настроена в `ApiSecurityConfiguration`: доступ открыт всем, CSRF выключен, пользователь анонимный. Отдельно настраивать ничего не нужно. Подробности и шаблон кода — `docs/jmix/rest.md`.

- Эндпоинты — собственные `@RestController` / `@RequestMapping("/api/...")` в пакете `api`.
- **Наружу только DTO** (Java `record`), сущности Jmix в ответ и в тело запроса не попадают.
- Бизнес-логика — в `@Service` пакета `app`, контроллер только принимает запрос и формирует ответ.
- В контроллерах и сервисах — `UnconstrainedDataManager`.
- Вход проверяй `@Valid` + Bean Validation (`jakarta.validation`), отказ — `ResponseEntity` с 4xx и понятным телом. Коды ответа и формат ошибки выбирает план (`contracts/`), а не контроллер «на глаз».
- Тест эндпоинта — `@SpringBootTest` + `@AutoConfigureMockMvc` (`docs/jmix/testing.md`). Базу между тестами изолируй сам: она в памяти и общая.

---

## 7. Запрещённые конструкции

Если выдаёшь что-то из левого столбца, ты спутал версию или стек. Перепиши.

| Запрещено | Правильно |
|---|---|
| `javax.persistence.*`, `javax.validation.*` | `jakarta.persistence.*`, `jakarta.validation.*` |
| `commit()`, `dataManager.commit(...)` | `dataManager.save(...)` |
| `dataManager.delete(...)`, `findAll()`, `findById()` | `dataManager.remove(...)`, `load(...)` |
| `@Inject` | `@Autowired` или конструктор |
| `import io.jmix.core.entity.StandardJmixEntity` | `com.debugskills.equipment.entity.StandardJmixEntity` |
| JPQL `from Employee e` | JPQL `from equipment_Employee e` |
| `@Enumerated`, enum в `@Column` напрямую | `EnumClass` и `String`-поле (раздел 3) |
| `jmix-rest-starter`, `/rest/entities`, REST DataStore | свой `@RestController` под `/api/**` |
| Возврат сущности из контроллера | DTO-`record` |
| `DataManager` в контроллере | `UnconstrainedDataManager` |
| Правка существующих changeSet'ов, `generateMigrationScripts` | новый файл `NNN-*.xml` + `<include>` |
| `@UiController`, `Screen`, `StandardLookup`, `<window>`, `caption=` (Jmix 1.x / CUBA) | в проекте UI не пишем; в 2.x это `@ViewController`, `View`, `<view>`, `title=` |

---

## 8. Чеклист перед выдачей кода

1. [ ] Импорты — `jakarta.*`, не `javax.*`?
2. [ ] В JPQL — имена сущностей `equipment_X`, не имена классов?
3. [ ] Используется `UnconstrainedDataManager`, а не `DataManager`? Все методы есть в разделе 5?
4. [ ] В сущности нет повторных полей базового класса (`id`, `version`, `createTs`...)?
5. [ ] Схема изменена только новым changelog'ом и `<include>` в `changelog.xml`?
6. [ ] Из контроллера уходит DTO, не сущность?
7. [ ] Не тронуты `security/**`, `ApiSecurityConfiguration`, `build.gradle`?
8. [ ] Каждый сценарий Given/When/Then покрыт тестом, `./gradlew test` зелёный?
9. [ ] Не запускался `bootRun`?
10. [ ] Если не хватило знания о Jmix — выведено `GAP`, а не придумано решение?

Если пункт под вопросом — читай соответствующий файл из `docs/jmix/` (раздел 9), а не угадывай.

---

## 9. Файлы в `docs/jmix/` (существуют только эти)

| Тема | Файл |
|---|---|
| Оглавление и лицензия документации | `docs/jmix/README.md` |
| Карта полной документации (модули, номера строк) | `docs/jmix/INDEX.md` |
| Полная документация Jmix 2.6 (читает только `plan`, по диапазонам строк) | `docs/jmix/jmix-complete-docs.adoc` |
| REST: `@RestController`, безопасность `/api/**`, DTO, коды ответов | `docs/jmix/rest.md` |
| Liquibase: новый changelog, имена, типы | `docs/jmix/db-migrations.md` |
| DataManager: запросы, сохранение, транзакции, права | `docs/jmix/data-manager.md` |
| Enum через `EnumClass` | `docs/jmix/enums.md` |
| Тесты: MockMvc, изоляция данных | `docs/jmix/testing.md` |

Других файлов в `docs/jmix/` нет. Если ссылка ведёт на несуществующий файл, это не повод его выдумывать: остановись по правилу 0.1.
