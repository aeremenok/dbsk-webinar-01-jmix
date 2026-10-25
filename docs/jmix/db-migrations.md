# Миграции БД: Liquibase вручную (Jmix 2.6)

Источник: `jmix-complete-docs.adoc`, «Database Schema Migration» (строки 20655–20761; ручные changelog'и — с 20719). Документация описывает Jmix Studio, который генерирует changelog'и сам. **Studio на стенде нет, команды `generateMigrationScripts` тоже нет**: схему меняет только человек (или агент), и только новым файлом changelog'а.

## Правила

1. **Только новые changeSet'ы.** Существующие `001-init-security.xml`, `002-reference.xml`, `003-seed.xml` не редактируем, не переименовываем, не удаляем. Ошибку в уже применённом changeSet'е исправляет новый changeSet.
2. **Новый файл на каждое изменение схемы:** `NNN-короткое-имя.xml` рядом с остальными, номер на единицу больше максимального из существующих (сейчас следующий — `004`).
3. **Файл подключается в мастер-файле** `src/main/resources/com/debugskills/equipment/liquibase/changelog.xml` строкой `<include>` — в конец, после последнего `<include>` проекта. Порядок выполнения — порядок `<include>`, поэтому номера держим по возрастанию. Include'ы Jmix (`io/jmix/...`) в начале мастер-файла не трогаем: `${uuid.type}` определён в `io/jmix/securitydata/liquibase/changelog.xml`, и наши файлы обязаны идти после него.
4. **Мастер-файл не удалять.** Свойство `main.liquibase.change-log=com/debugskills/equipment/liquibase/changelog.xml` в `application.properties` указывает именно на него.
5. **В БД в памяти ошибка контрольной суммы не видна:** база каждый запуск новая, поэтому правка старого changeSet'а «сработает». Это не разрешение: правила 1 это не отменяет.

## Где лежат файлы

```
src/main/resources/com/debugskills/equipment/liquibase/
  changelog.xml                  <- мастер-файл с <include>
  changelog/
    001-init-security.xml        <- таблицы EQUIP_USER, EQUIP_USER_ROLE, admin
    002-reference.xml            <- таблицы справочников
    003-seed.xml                 <- сид справочников
    004-....xml                  <- новое сюда
```

Строка в мастер-файле (путь абсолютный от корня classpath, с ведущим `/`):

```xml
<include file="/com/debugskills/equipment/liquibase/changelog/004-short-name.xml"/>
```

## Именование

| Что | Правило | Пример из проекта |
|---|---|---|
| Имя сущности (`@Entity(name=...)`) | `equipment_` + имя класса; в JPQL пишется именно оно | `equipment_Employee` |
| Таблица | `EQUIP_` + имя в `UPPER_SNAKE_CASE`, в `@Table(name=...)` и в changelog | `EQUIP_USER_ROLE`, `EQUIP_EMPLOYEE` |
| Колонка | `UPPER_SNAKE_CASE` от имени поля | `fullName` -> `FULL_NAME`, `inventoryNumber` -> `INVENTORY_NUMBER` |
| Колонка ссылки | имя поля + `_ID` | `employee` -> `EMPLOYEE_ID` |
| Индекс | `IDX_<ТАБЛИЦА>_ON_<КОЛОНКА>` | `IDX_EQUIP_EMPLOYEE_ON_EMAIL` |
| Внешний ключ | `FK_<ТАБЛИЦА>_ON_<ПОЛЕ>` | `FK_EQUIP_USER_ROLE_ON_USER` |
| changeSet `id` | `NNN-MMM-описание`: номер файла, номер changeSet'а в файле с шагом 10 | `002-010-create-equip-employee` |
| changeSet `author` | как в существующих файлах | `equipment-starter` |

Префикс `EQUIP_` / `equipment_` — соглашение проекта; в документации префикс описан как возможный (строка 20846).

## Колонки сущности: что обязательно

Все сущности проекта наследуют `src/main/java/com/debugskills/equipment/entity/StandardJmixEntity.java`. Таблица **обязана** содержать колонки базового класса, иначе первое же обращение к сущности упадёт:

| Поле в `StandardJmixEntity` | Колонка | Тип в changelog | Ограничения |
|---|---|---|---|
| `id` | `ID` | `${uuid.type}` | `primaryKey="true"`, `nullable="false"` |
| `version` | `VERSION` | `int` | `defaultValueNumeric="1"`, `nullable="false"` |
| `createTs` | `CREATE_TS` | `TIMESTAMP` | |
| `createdBy` | `CREATED_BY` | `VARCHAR(50)` | |
| `updateTs` | `UPDATE_TS` | `TIMESTAMP` | |
| `updatedBy` | `UPDATED_BY` | `VARCHAR(50)` | |
| `deleteTs` | `DELETE_TS` | `TIMESTAMP` | |
| `deletedBy` | `DELETED_BY` | `VARCHAR(50)` | |

Поля `createTs`, `createdBy`, `updateTs`, `updatedBy` заполняет фреймворк при сохранении (аннотации `@CreatedDate`, `@CreatedBy`, `@LastModifiedDate`, `@LastModifiedBy`, раздел Audit Traits, строки 20874–20903). Значения в коде руками не ставим и второго поля «дата создания» не добавляем. `deleteTs` и `deletedBy` в базовом классе есть, но без `@DeletedDate` / `@DeletedBy`, то есть мягкое удаление не включено.

Типы остальных колонок:

| Тип атрибута | Колонка |
|---|---|
| `String` | `VARCHAR(255)` (короче, если есть причина) |
| `Integer` | `int` |
| `Long` | `bigint` |
| `Boolean` | `BOOLEAN` (по умолчанию: `defaultValueBoolean="true"`) |
| `BigDecimal` | `DECIMAL(19, 2)` |
| `LocalDate` / `LocalDateTime` | `DATE` / `TIMESTAMP` |
| `UUID`, ссылка на сущность | `${uuid.type}` |
| enum (`EnumClass<String>`) | `VARCHAR(50)`, хранится `id` константы, не `ordinal` и не `name()` ([enums.md](enums.md)) |

`${uuid.type}` — свойство Liquibase, в HSQLDB оно раскрывается в `uuid`. Тип `uuid` / `UUID` напрямую не пишем.

## Шаблон changelog'а: таблица со ссылкой

Учебная сущность `Widget` со ссылкой на `Employee` (замени на свою). Сущность:

```java
@JmixEntity
@Entity(name = "equipment_Widget")
@Table(name = "EQUIP_WIDGET", indexes = {
        @Index(name = "IDX_EQUIP_WIDGET_ON_OWNER", columnList = "OWNER_ID")
})
public class Widget extends StandardJmixEntity {

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "STATUS", nullable = false)
    private String status;                    // enum хранится строкой

    @JoinColumn(name = "OWNER_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Employee owner;

    // getters / setters
}
```

`004-widget.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                      http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="004-010-create-equip-widget" author="equipment-starter">
        <createTable tableName="EQUIP_WIDGET">
            <column name="ID" type="${uuid.type}">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="VERSION" type="int" defaultValueNumeric="1">
                <constraints nullable="false"/>
            </column>
            <column name="CREATE_TS" type="TIMESTAMP"/>
            <column name="CREATED_BY" type="VARCHAR(50)"/>
            <column name="UPDATE_TS" type="TIMESTAMP"/>
            <column name="UPDATED_BY" type="VARCHAR(50)"/>
            <column name="DELETE_TS" type="TIMESTAMP"/>
            <column name="DELETED_BY" type="VARCHAR(50)"/>
            <column name="TITLE" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="STATUS" type="VARCHAR(50)">
                <constraints nullable="false"/>
            </column>
            <column name="OWNER_ID" type="${uuid.type}">
                <constraints nullable="false"/>
            </column>
        </createTable>
        <createIndex tableName="EQUIP_WIDGET" indexName="IDX_EQUIP_WIDGET_ON_OWNER">
            <column name="OWNER_ID"/>
        </createIndex>
        <addForeignKeyConstraint baseTableName="EQUIP_WIDGET" baseColumnNames="OWNER_ID"
                                 referencedTableName="EQUIP_EMPLOYEE" referencedColumnNames="ID"
                                 constraintName="FK_EQUIP_WIDGET_ON_OWNER"/>
    </changeSet>

</databaseChangeLog>
```

Подключение: `<include file="/com/debugskills/equipment/liquibase/changelog/004-widget.xml"/>` в конце `changelog.xml`.

Изменение существующей таблицы — тоже новый changeSet в новом файле:

```xml
<changeSet id="005-010-add-widget-note" author="equipment-starter">
    <addColumn tableName="EQUIP_WIDGET">
        <column name="NOTE" type="VARCHAR(500)"/>
    </addColumn>
</changeSet>
```

## Ограничения уникальности

Jmix сам уникальность не обеспечивает: `unique = true` в `@Column` фреймворк не интерпретирует (строка 21345). Уникальность — это уникальный индекс в changelog'е, который можно продублировать в `@Table(indexes = ...)` для документации:

```xml
<createIndex tableName="EQUIP_EMPLOYEE" indexName="IDX_EQUIP_EMPLOYEE_ON_EMAIL" unique="true">
    <column name="EMAIL"/>
</createIndex>
```

## Сид-данные

Начальные данные — `<insert>` в отдельном changeSet'е (как `003-seed.xml`): `ID` задаётся значением UUID, `VERSION` равен `1`, булевы значения — `valueBoolean`, остальные поля — `value`. Существующие сид-changeSet'ы не правим; нужны другие данные — новый changeSet с `<insert>` или `<update>`. Состояние данных между тестами возвращает сам тест ([testing.md](testing.md)).

## Проверка

- `./gradlew test` поднимает контекст на пустой БД в памяти и прогоняет весь мастер-файл: опечатка в XML, неверный тип или пропущенный `<include>` видны сразу.
- `./gradlew bootRun` **не запускать** (зависнет).
