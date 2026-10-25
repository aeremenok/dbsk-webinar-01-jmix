# DataManager — расширенно (Jmix 2.6)

Базовый fluent-API — в `AGENTS.md`, раздел 5. Здесь то, чего там нет. Примеры — на сущностях стенда: `Employee`, `Equipment`, `EquipmentType`, `EquipmentStatus`. Источник в полной документации: «Using DataManager», строки 17545–18083.

## Какой DataManager

```java
import io.jmix.core.DataManager;
import io.jmix.core.UnconstrainedDataManager;
```

- В REST-контроллерах и сервисах API, а также в тестах — **`UnconstrainedDataManager`**. Методы те же, права не проверяются (строка 18065). Запрос к `/api/**` идёт от анонимного пользователя без прав, и обычный `DataManager` вернул бы пустой результат или бросил `AccessDeniedException`. Подробнее: [rest.md](rest.md).
- `DataManager` применяет права текущего пользователя (CRUD-политики и row-level роли). На стенде им пользоваться незачем.

Инжекция — через конструктор или `@Autowired`:

```java
private final UnconstrainedDataManager dataManager;

public EquipmentLookup(UnconstrainedDataManager dataManager) {
    this.dataManager = dataManager;
}
```

## JPQL: имя сущности, а не класса

В запросе пишется имя из `@Entity(name = "equipment_X")`, а не имя Java-класса:

```java
// верно
"select e from equipment_Equipment e where e.status = :st"
// неверно: Equipment — это имя класса, JPQL его не знает
"select e from Equipment e where e.status = :st"
```

Это относится и к `loadValue`, и к полному запросу в `query(...)`. В сокращённой форме (`query("e.status = :st")`) имя сущности берётся из `load(Class)`, алиас — всегда `e`.

Параметр-enum передаётся самим значением enum, Jmix сам преобразует его в `id` (пример в документации, строка 17634):

```java
List<Equipment> free = dataManager.load(Equipment.class)
        .query("select e from equipment_Equipment e where e.type = :type and e.status = :st")
        .parameter("type", EquipmentType.LAPTOP)
        .parameter("st", EquipmentStatus.AVAILABLE)
        .list();
```

## Создание и сохранение

```java
Employee e = dataManager.create(Employee.class);   // новый инстанс, id сгенерирован, в БД нет
e.setFullName("Иван");
e.setEmail("ivan@example.com");
Employee saved = dataManager.save(e);               // insert или update, возвращает сохранённый инстанс
```

Возвращённый инстанс — не тот же объект, что был передан. Дальше работай с возвращённым.

Изменение существующей записи: загрузить, поправить, сохранить.

```java
Equipment eq = dataManager.load(Equipment.class).id(id).one();
eq.setStatus(EquipmentStatus.BOOKED);
dataManager.save(eq);
```

Несколько сущностей одной транзакцией (порядок аргументов не важен):

```java
EntitySet saved = dataManager.save(request, equipment);   // varargs
Equipment savedEq = saved.get(equipment);
// или
SaveContext sc = new SaveContext().saving(request).saving(equipment);
dataManager.save(sc);
```

Коллекция: `dataManager.saveAll(list)`. Если сохранённый результат не нужен: `saveWithoutReload(...)`.

## Загрузка

```java
// по id: one() бросает исключение, если не найдено; optional() возвращает Optional
Equipment eq = dataManager.load(Equipment.class).id(id).one();

// по условию, с сортировкой и ограничением
List<Equipment> list = dataManager.load(Equipment.class)
        .query("select e from equipment_Equipment e where e.type = :type order by e.inventoryNumber")
        .parameter("type", EquipmentType.LAPTOP)
        .maxResults(10)
        .list();

// все записи, с сортировкой по полям сущности и вложенным
List<Equipment> all = dataManager.load(Equipment.class)
        .all()
        .sort(Sort.by(Sort.Direction.DESC, "createTs"))
        .list();
```

Сортировка по времени создания — по полю `createTs` (оно есть в `StandardJmixEntity`), не `createdAt` и не `createdDate`.

Один результат или ничего: `.optional()` после `.query(...)`. Нужен «первый по порядку» — `order by` в запросе плюс `.maxResults(1)`.

## Ссылки и fetch plan

Ссылочные атрибуты (`@ManyToOne`) подгружаются лениво: обращение к `request.getEmployee()` загружает связанную сущность через DataManager по требованию, в том числе вне исходной транзакции (раздел Fetching Data, строка 18667). Если ссылки нужны сразу (например, при сборке DTO для списка), укажи fetch plan:

```java
.fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("employee").add("equipment"))
```

`FetchPlan.BASE` — локальные атрибуты и instance name ссылок. `add("employee")` добавляет ссылку.

## Скаляры и агрегаты

```java
long count = dataManager.loadValue(
        "select count(e) from equipment_Equipment e where e.type = :type and e.status = :st", Long.class)
        .parameter("type", EquipmentType.LAPTOP)
        .parameter("st", EquipmentStatus.AVAILABLE)
        .one();
```

`loadValue` работает только с постоянными атрибутами JPA-сущностей одного хранилища.

## Проверка существования

```java
boolean exists = !dataManager.load(Equipment.class)
        .query("select e from equipment_Equipment e where e.inventoryNumber = :inv")
        .parameter("inv", "LAP-001")
        .maxResults(1)
        .list().isEmpty();
```

## Удаление

```java
dataManager.remove(entity);          // метод называется remove(), НЕ delete()
dataManager.remove(list);            // коллекция
dataManager.remove(child, parent);   // зависимая сущность первой
dataManager.remove(Id.of(id, Employee.class));   // если есть только id
```

В `StandardJmixEntity` нет `@DeletedDate` / `@DeletedBy`, поэтому мягкое удаление не включено: `remove()` удаляет строку.

## Транзакции

По умолчанию `DataManager` присоединяется к текущей транзакции или открывает новую на одну операцию. Несколько шагов, которые должны пройти атомарно (прочитать, изменить, сохранить несколько сущностей), оборачивай в `@org.springframework.transaction.annotation.Transactional` на методе сервиса (раздел Transaction Management, строка 19528). Работает только при вызове через инжектированный бин.

## Чего НЕТ (галлюцинации)

- `findAll()`, `findById()`, `findByExample()`, `getRepository()` — это Spring Data, не Jmix.
- `commit()` / `dataManager.commit(...)` — Jmix 1.x, используй `save()`.
- `dataManager.delete(...)` — правильно `remove(...)`.
- `EntityManager` напрямую — только при явной необходимости.
- JPQL по имени класса (`from Equipment e`) — нужно имя сущности (`from equipment_Equipment e`).
