# Enum-атрибуты в Jmix 2.6 (`EnumClass`)

Jmix хранит enum через строковый (или числовой) **id** константы, а не через `@Enumerated` / `ordinal()` / `name()`. Это даёт стабильное хранение: константы можно переименовать и переставить, пока `id` остаётся прежним; значение в БД, которого нет в enum, загружается как `null`, а не роняет загрузку. Плоский Java-enum без `EnumClass` скомпилируется, но не будет корректно работать с метаданными Jmix. Источник: «Enumerations», строки 21645–21718.

На стенде уже есть два enum: `EquipmentType` (`LAPTOP`, `MONITOR`, `HEADSET`) и `EquipmentStatus` (`AVAILABLE`, `BOOKED`, `ISSUED`, `MAINTENANCE`) в `src/main/java/com/debugskills/equipment/entity/`. Новый enum делай по их образцу.

## Enum

```java
package com.debugskills.equipment.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

public enum EquipmentStatus implements EnumClass<String> {

    AVAILABLE("AVAILABLE"),
    BOOKED("BOOKED"),
    ISSUED("ISSUED"),
    MAINTENANCE("MAINTENANCE");

    private final String id;

    EquipmentStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static EquipmentStatus fromId(String id) {
        for (EquipmentStatus at : EquipmentStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
```

Обязательны `getId()` и статический `fromId(...)`, который возвращает `null` для неизвестного значения.

## Использование в сущности

Поле хранится как строка (`id`), а геттер и сеттер работают с типом enum:

```java
@Column(name = "STATUS", nullable = false)
private String status;

public EquipmentStatus getStatus() {
    return status == null ? null : EquipmentStatus.fromId(status);
}

public void setStatus(EquipmentStatus status) {
    this.status = status == null ? null : status.getId();
}
```

Поле — `String` (или `Integer`, если `EnumClass<Integer>`), не сам enum. Аннотации JPA стоят на поле.

В Liquibase такая колонка — обычный `VARCHAR`:

```xml
<column name="STATUS" type="VARCHAR(50)"/>
```

## В запросах

В JPQL параметр передаётся значением enum, Jmix преобразует его в `id`:

```java
.query("select e from equipment_Equipment e where e.status = :st")
.parameter("st", EquipmentStatus.AVAILABLE)
```

Константу прямо в тексте запроса пиши макросом `@enum`: `e.status = @enum(com.debugskills.equipment.entity.EquipmentStatus.AVAILABLE)` (раздел JPQL Extensions, строка 19513).

## В REST

В DTO значение enum лучше отдавать и принимать строкой (`status.getId()`, `EquipmentType.fromId(value)`): `fromId` вернёт `null` для неизвестного значения, и вход можно отклонить своим сообщением (см. [rest.md](rest.md)). У обоих enum стенда `id` совпадает с именем константы, поэтому Jackson-представление (`name()`) и `id` одинаковы. Если делаешь новый enum с другим `id`, в API ориентируйся на `id`.

## Локализация названий (только для UI)

В REST не нужна. Для экранов Jmix названия лежат в `messages.properties`:

```
com.debugskills.equipment.entity/EquipmentStatus.AVAILABLE=Доступно
com.debugskills.equipment.entity/EquipmentStatus.BOOKED=Забронировано
```
