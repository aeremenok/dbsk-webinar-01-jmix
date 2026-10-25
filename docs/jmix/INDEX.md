# INDEX.md — карта `jmix-complete-docs.adoc`

`jmix-complete-docs.adoc` — полная документация Jmix 2.6 одним файлом: 84 991 строка, 3,4 МБ. Целиком его не читают: контекст кончится раньше, чем найдётся нужное. Этот файл — навигатор: по нему находят **диапазон строк** и читают только его.

> Кто и когда сюда приходит: INDEX.md и `.adoc` открывает **только этап `/speckit.plan`**. На `specify`, `clarify`, `tasks`, `analyze` и `implement` полную документацию не открывают (правило 1 в `AGENTS.md`). Всё нужное implement берёт из `research.md` и тематических `.md` этой папки.

## Как читать диапазон

**Якорь — текст заголовка, номер строки — подсказка.** Если файл обновят, номера съедут, а заголовки останутся. Поэтому в `Source:` у решения в `research.md` пишут заголовок и строку: «Using DataManager, ≈ строка 17545».

Проверить, что подсказка не устарела:

```bash
sed -n '17545p' docs/jmix/jmix-complete-docs.adoc      # ждём: = Using DataManager
grep -n '^= Using DataManager' docs/jmix/jmix-complete-docs.adoc   # если номер уехал
```

Прочитать диапазон:

```bash
sed -n '17545,17700p' docs/jmix/jmix-complete-docs.adoc
```

Или инструментом Read: `file_path=docs/jmix/jmix-complete-docs.adoc`, `offset=17545`, `limit=160`. Берите 100–300 строк за раз: страница документации обычно короче 400 строк. Конец страницы — это строка перед следующим заголовком `= ` (таблица ниже даёт и начало, и конец).

Уровни заголовков: `= Module: имя` — модуль (глава верхнего уровня), `= Заголовок` — страница, `==` и глубже — секции страницы. Список страниц модуля: `awk 'NR>=17474 && NR<=19688 && /^= /{print NR": "$0}' docs/jmix/jmix-complete-docs.adoc`.

В тексте встречаются `xref:...` и `{spring-security-doc}` — это ссылки внутри исходной документации, переходить по ним не нужно: ищите заголовок в этой таблице.

## Нужно для кейса

Номера проверены по файлу командой `sed -n 'Np'` (в таблице — строка, на которой стоит якорь).

| Тема | Строка | Диапазон страницы | Якорь (текст на этой строке) |
|---|---:|---|---|
| Using DataManager | 17545 | 17545–18083 | `= Using DataManager` |
| Запросы JPQL через DataManager | 17634 | 17634–17682 | `=== Loading Entities by Query` |
| Скаляры и агрегаты (`loadValue`) | 17727 | 17727–17785 | `=== Loading Scalar and Aggregate Values` |
| Блокировки (`lockMode`) | 17839 | 17839–17853 | `=== Using Locks` |
| Сохранение и удаление | 17854 | 17854–18022 | `== Saving Entities` (дальше `== Removing Entities`, 17969) |
| `UnconstrainedDataManager` | 18065 | 18055–18083 | строка внутри `== Security in DataManager` |
| Fetching Data (ленивая загрузка) | 18667 | 18667–18897 | `= Fetching Data` |
| JPQL Extensions (`@enum`, макросы дат) | 19091 | 19091–19527 | `= JPQL Extensions` |
| Transaction Management (`@Transactional`) | 19528 | 19528–19688 | `= Transaction Management` |
| Bean Validation | 19694 | 19694–20041 | `= Bean Validation` |
| Database Schema Migration | 20655 | 20655–20761 | `= Database Schema Migration` |
| Ручные changelog'и Liquibase | 20719 | 20675–20725 | строка «You can write Liquibase changelog files manually…» внутри `== Changelog Files Structure` |
| Entities | 20762 | 20762–21644 | `= Entities` |
| Audit-поля сущности (`@CreatedDate` и др.) | 20874 | 20874–20903 | `=== Audit Traits` |
| Enumerations (`EnumClass`) | 21645 | 21645–21718 | `= Enumerations` |
| Authentication | 72559 | 72559–72946 | `= Authentication` |
| `SystemAuthenticator` | 72754 | 72750–72807 | строка внутри `== System Authentication` |
| Custom Endpoints | 73147 | 73147–73434 | `= Custom Endpoints` |
| `JmixHttpSecurityUtils.configureAnonymous` | 73226 | 73190–73237 | строка `JmixHttpSecurityUtils.configureAnonymous(http); //<5>` внутри `=== Public Endpoints` |
| Анонимный пользователь без прав | 73949 | 73939–73951 | строка «The anonymous user has no permissions by default.» внутри `== Built-in Users` |
| Custom Endpoints: диагностика 401/403 | 73421 | 73421–73434 | `== Troubleshooting` |
| Testing: интеграционные тесты | 78466 | 78466–78801 | `= Integration Tests` |
| Testing: очистка тестовых данных | 78554 | 78554–78578 | `=== Test Data Cleanup` |
| Testing: пользователь в тесте | 78579 | 78579–78653 | `== Security Context in Tests` |
| Testing: `@MockBean` и подмена бинов | 78654 | 78654–78801 | `== Overriding Application Behavior` |

MockMvc в полной документации не упоминается ни разу. Шаблон теста REST лежит в [testing.md](testing.md).

## НЕ используем

| Модуль | Строка | Почему не открывать |
|---|---:|---|
| `rest` | 66023 | Аддон Generic REST (`jmix-rest-starter`, `/rest/entities`). Конституция его запрещает: только собственные `@RestController` под `/api/**`. См. [rest.md](rest.md) |
| `rest-ds` | 69113 | REST DataStore: клиент к чужому Generic REST. Запрещён тем же правилом |
| `authorization-server` | 351 | OAuth 2.1 сервер токенов. Стенд работает с анонимным доступом к `/api/**`, токены не нужны |
| `studio` | 73985 | Глава про Jmix Studio (миграции там начинаются с «Studio creates migration scripts»). Studio на стенде нет: миграции пишутся руками, см. [db-migrations.md](db-migrations.md) |
| `flow-ui` | 24878 (до 52778) | Весь UI на Vaadin Flow, треть файла. Лаба — REST, представления не пишем |

Прочие модули (BPM, отчёты, карты, календарь, графики и так далее) к кейсу отношения не имеют. Если кажется, что нужен один из них, это повод остановиться и спросить, а не открывать.

## Карта модулей

49 модулей, порядок — как в файле. «Конец» — строка перед следующим модулем. Колонка «Для кейса»: **да** — читаем, **нет** — запрещено, пусто — не нужен.

| Модуль | Начало | Конец | О чём | Для кейса |
|---|---:|---:|---|---|
| appsettings | 20 | 96 | Application Settings | |
| audit | 97 | 350 | Entity Log, Entity Snapshots | |
| authorization-server | 351 | 915 | OAuth2 Authorization Server | нет |
| bpm | 916 | 11493 | BPM | |
| bulk-edit | 11494 | 11543 | Bulk Editor | |
| business-calendar | 11544 | 11756 | Business Calendars | |
| calendar | 11757 | 15712 | Calendar, FullCalendar | |
| charts | 15713 | 16761 | Charts | |
| concepts | 16762 | 17402 | Architecture Overview, Key Features, Concepts | обзор |
| cuba | 17403 | 17421 | Миграция с CUBA | |
| dashboards | 17422 | 17473 | Dashboards | |
| data-access | 17474 | 19688 | DataManager, JPQL, транзакции, события, кэш | да |
| data-model | 19689 | 22045 | Сущности, enum, миграции, валидация, мягкое удаление | да |
| data-tools | 22046 | 22147 | Entity Inspector | |
| deployment | 22148 | 23302 | Развёртывание | |
| dyn-attr | 23303 | 23669 | Dynamic Attributes | |
| email | 23670 | 24274 | Email | |
| features | 24275 | 24418 | Кластер, генерация последовательностей | |
| files | 24419 | 24877 | Файлы и хранилища | |
| flow-ui | 24878 | 52778 | Vaadin Flow UI | нет |
| grapes-js | 52779 | 52838 | GrapesJS | |
| grid-export | 52839 | 53171 | Экспорт из таблиц | |
| jmx-console | 53172 | 53198 | JMX Console | |
| kanban | 53199 | 54862 | Kanban | |
| ldap | 54863 | 55261 | LDAP | |
| localization | 55262 | 55616 | Локализация | |
| maps | 55617 | 58932 | Карты | |
| message-templates | 58933 | 59837 | Message Templates | |
| modularity | 59838 | 60319 | Аддоны и расширения | |
| multitenancy | 60320 | 60540 | Multitenancy | |
| notifications | 60541 | 60811 | Notifications | |
| oidc | 60812 | 61285 | OpenID Connect | |
| pessimistic-lock | 61286 | 61372 | Pessimistic Locking (аддон) | |
| pivot-table | 61373 | 62679 | Pivot Table | |
| quartz | 62680 | 62884 | Quartz | |
| reports | 62885 | 66022 | Reports | |
| rest | 66023 | 69112 | Generic REST (аддон) | нет |
| rest-ds | 69113 | 69631 | REST DataStore | нет |
| ROOT | 69632 | 71261 | Application Properties, Setup, Dependency Versions и др. | |
| search | 71262 | 72553 | Full-text Search | |
| security | 72554 | 73984 | Authentication, Authorization, Custom Endpoints, Resource Roles, Users | да |
| studio | 73985 | 76664 | Jmix Studio | нет |
| superset | 76665 | 77397 | Superset | |
| tabbed-mode | 77398 | 78356 | Tabbed Application Mode | |
| testing | 78357 | 79588 | Интеграционные и unit-тесты | да |
| tutorial | 79589 | 82450 | Учебный сценарий | |
| uiconstraints | 82451 | 82608 | UI Constraints | |
| webdav | 82609 | 84667 | WebDAV | |
| whats-new | 84668 | 84991 | What's New | |

Страницы четырёх нужных модулей (проверено командой `awk '/^= /'`):

- `data-access` (17474): Entity and Query Cache 17479 · **Using DataManager 17545** · Data Access Properties 18084 · Data Repositories 18145 · Entity Events 18335 · Using EntityManager 18540 · Fetching Data 18667 · Data Access 18898 · Using JDBC 18927 · **JPQL Extensions 19091** · **Transaction Management 19528**.
- `data-model` (19689): **Bean Validation 19694** · Data Stores 20042 · Datatype Interface 20280 · **Database Schema Migration 20655** · **Entities 20762** · **Enumerations 21645** · Data Model 21719 · Metadata 21733 · Soft Deletion 21804.
- `security` (72554): **Authentication 72559** · Authorization 72947 · **Custom Endpoints 73147** · Security 73435 · Resource Roles 73526 · Row-level Roles 73738 · **Users 73907**.
- `testing` (78357): Getting Started with Testing 78362 · Testing 78454 · **Integration Tests 78466** · End-to-end UI Tests 78802 · UI Integration Tests 79233 · Unit Tests 79362.
