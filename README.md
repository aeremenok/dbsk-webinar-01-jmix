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

## Контекстного слоя здесь нет

Это базовая версия стартера. Файлов, которые подсказывают AI-ассистенту специфику Jmix,
в ней нет — и агент на этом проекте будет уверенно ошибаться. С этого эфир и начнётся.
