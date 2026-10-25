# equipment-requests-starter

Минимальный рабочий каркас Jmix-приложения — заготовка для вебинара
«Учёт заявок на оборудование». Здесь только инфраструктура: бизнес-сущности,
CRUD и миграции агент будет создавать в эфире.

**Стек:** Java 21 · Jmix 2.6.2 · Spring Boot 3 · Vaadin Flow 24 · EclipseLink · HSQLDB (in-memory).

## Запуск

Перед первой сборкой убедитесь, что `JAVA_HOME` указывает на **JDK 21** — на нём стартует
демон Gradle (подробности и альтернатива — в `gradle.properties`):

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew bootRun
```

- Приложение: http://localhost:8080
- Логин по умолчанию: **admin** / **admin**
- БД: HSQLDB in-memory (`jdbc:hsqldb:mem:equipment`, пользователь `sa`, без пароля).
  Jmix 2.6–2.8 не поддерживает H2 из коробки, поэтому используется HSQLDB (как в Jmix Studio).

Первый запуск дольше обычного: Vaadin собирает фронтенд (npm). Нужен Node.js (18+).

## Тесты

```bash
./gradlew test
```

Один тест `EquipmentRequestsApplicationTests.contextLoads()` — проверяет, что
Spring-контекст поднимается.

## Что в проекте

- `EquipmentRequestsApplication` — точка входа (`@SpringBootApplication`, тема).
- `view/main` — `MainView` (`StandardMainView`) с пустым меню.
- `view/login` — стандартная форма логина Jmix.
- `entity/User`, `entity/UserRole`, `security/*` — минимальная безопасность + учётка admin.
- `liquibase/` — мастер-changelog + схема безопасности и сид админа.

## Чего НЕ в проекте (это — задание участника)

- Нет бизнес-сущностей `Employee` / `Equipment` / `EquipmentRequest`.
- Нет CRUD-представлений и пунктов меню для них.
- Нет Liquibase-changeSet'ов с их таблицами.
- Нет сервиса `EquipmentRequestService` и тестов на него.

## Перед вебинаром

Смотреть можно без подготовки — всё будет на экране. Если хотите повторять руками:

1. **JDK 21** и `JAVA_HOME` на него (см. «Запуск» выше).
2. **opencode** с подключённой моделью. Проверьте ключ заранее — это самая частая засада.
3. Прогрейте сборку: `./gradlew build`. Первый раз Vaadin долго собирает фронтенд,
   в эфире вы это ждать не захотите.

Gradle и Jmix отдельно ставить не нужно: в проекте есть Gradle wrapper, зависимости
подтянутся сами.

## Контекстный слой

В ветке `main` (стартер вебинара) контекстного слоя нет — с этого начинался вебинар.
В ветке `lab-sdd/start` он есть: `AGENTS.md` и `docs/jmix/` (включая полную документацию Jmix).

## Лаба SDD (ветка lab-sdd/start)

Ветка `lab-sdd/start` — стенд лабораторной «SDD: спецификация, которую исполняет агент»
(Spec Kit + opencode): стартер, контекстный слой для агента, REST-каркас, справочники сотрудников и
склада, Spec Kit с конституцией, бизнес-бриф и скрипты проверки. Папки `specs/` в ней нет: её создаст
агент в ходе лабы. Остальные ветки `lab-sdd/*` (чекпоинты, демо, поломки) нужны только на самой лабе.

Бриф для работы лежит в `docs/brief/`: [business-brief.md](docs/brief/business-brief.md) и
[portal-requirement.md](docs/brief/portal-requirement.md).

### Что должно стоять

- JDK 21 и `JAVA_HOME` на него (см. «Запуск» выше);
- Node.js 22 LTS (нужен opencode и сборке фронтенда Vaadin);
- `opencode-ai@1.19.4`: `npm i -g opencode-ai@1.19.4`;
- git, curl, jq;
- Windows — только через WSL, репозиторий клонировать в файловую систему WSL (`~/…`), не в `/mnt/c`;
- ключ DeepSeek, который выдаёт ведущий лично (не в общий чат), в переменной окружения
  `DEEPSEEK_API_KEY`: `opencode.json` читает его оттуда, в репозиторий ключ не попадает.

### Домашняя подготовка (до 28.10, 18:00)

```bash
git clone -b lab-sdd/start https://github.com/aeremenok/dbsk-webinar-01-jmix.git
cd dbsk-webinar-01-jmix
./gradlew build                 # прогрев: первый раз Vaadin долго собирает фронтенд
scripts/doctor.sh               # версии, ключ, ответ обеих моделей, компиляция
./gradlew bootRun               # в отдельном терминале
curl -s localhost:8080/api/ping # → {"status":"ok","employees":5}
```

Вывод `scripts/doctor.sh` и ответ `/api/ping` пришлите в чат подготовки. Кто не прислал, на лабе
работает в паре или смотрит демо.

### Скрипты

| Скрипт | Что делает |
|---|---|
| `scripts/doctor.sh` | Проверка рабочего места: версии Java (21), Node (22), opencode, git, curl, jq; ключ (виден только как `***`); короткий запрос к моделям Pro и Flash с замером времени; `./gradlew -q compileJava`. Флаг `--no-llm` пропускает запросы к моделям. Итог: GREEN или RED |
| `scripts/check.sh` | Технические проверки результата агента: `./gradlew test`; охрана конституции (security, `ApiSecurityConfiguration`, `build.gradle` не изменены, `jmix-rest` не подключён); в `spec.md` не осталось `[NEEDS CLARIFICATION]`; сколько задач в `tasks.md` не закрыто; число тестов не уменьшилось. Строки PASS / FAIL / SKIP, код возврата не ноль при FAIL |
| `scripts/accept.sh` | Бизнес-приёмка A1–A9 через curl и jq против запущенного приложения (`BASE_URL`, по умолчанию `http://localhost:8080`). Запускать на свежем `bootRun`. Последняя строка — «N/9». Те же запросы для IDE: `http/acceptance.http` |
| `scripts/catchup.sh N` | Догнать группу: `N` — `1`, `1r`, `2` или `3`. Создаёт ветку `001-equipment-request-cpN` из `origin/lab-sdd/checkpoint-N`; несохранённые изменения прячет в `git stash` |
