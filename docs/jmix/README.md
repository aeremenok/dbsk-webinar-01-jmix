# docs/jmix — контекст по Jmix 2.6 для агента (L2)

Расширенный контекст для случаев, когда шпаргалки `AGENTS.md` (L1) не хватает. Кто какие файлы читает на каком этапе Spec Kit, записано в правиле 1 `AGENTS.md`. Коротко: полную документацию открывает только `/speckit.plan`, остальные этапы работают по `research.md` и тематическим файлам из этой папки.

## Оглавление

| Файл | Что внутри | Кто читает |
|---|---|---|
| [INDEX.md](INDEX.md) | Карта `jmix-complete-docs.adoc`: модули, начальные строки, нужные для кейса диапазоны, что не используем | plan |
| [jmix-complete-docs.adoc](jmix-complete-docs.adoc) | Полная документация Jmix 2.6, 85 тыс. строк. Читать только диапазонами, найденными по INDEX.md | plan |
| [rest.md](rest.md) | Собственный `@RestController` под `/api/**`: цепочка безопасности, `UnconstrainedDataManager`, DTO, валидация, коды ответов. Generic REST запрещён | plan, implement |
| [db-migrations.md](db-migrations.md) | Ручные Liquibase-changelog'и: новый файл, `<include>`, имена таблиц и колонок, типы | plan, implement |
| [data-manager.md](data-manager.md) | DataManager: запросы, сохранение, транзакции, права | plan, implement |
| [enums.md](enums.md) | Enum-атрибуты через `EnumClass` | plan, implement |
| [testing.md](testing.md) | Интеграционные тесты, шаблон MockMvc для REST, изоляция данных между тестами | plan, implement |

Официальная документация: https://docs.jmix.io/

## Атрибуция

Файл `jmix-complete-docs.adoc` собран из репозитория https://github.com/jmix-framework/jmix-docs и распространяется по лицензии [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Файл включён без изменений.

Авторы документации — команда Jmix; при цитировании указывайте источник. Остальные файлы этой папки (`INDEX.md`, `rest.md`, `db-migrations.md`, `data-manager.md`, `enums.md`, `testing.md`, `README.md`) написаны для стенда и ссылаются на документацию, но не копируют её целиком.
