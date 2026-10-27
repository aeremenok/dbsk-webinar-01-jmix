# REST: собственный `@RestController` под `/api/**` (Jmix 2.6)

Источник: `jmix-complete-docs.adoc`, страница «Custom Endpoints» (строки 73147–73434, раздел Public Endpoints с 73190). Здесь — то, что нужно для кейса, плюс как это устроено на стенде.

## Не использовать

**Не добавляй `jmix-rest-starter`, не используй Generic REST (`/rest/entities/...`), REST DataStore и Authorization Server.** Это отдельные аддоны (модули `rest`, `rest-ds`, `authorization-server` в документации), они меняют `build.gradle` и цепочки безопасности. Конституция запрещает всё это: наружу выставляются только собственные `@RestController` под `/api/**`, новых зависимостей нет.

## Цепочка безопасности уже настроена

Класс `src/main/java/com/debugskills/equipment/api/ApiSecurityConfiguration.java` **трогать нельзя**. Что он делает:

```java
@Bean
@Order(JmixSecurityFilterChainOrder.CUSTOM)          // раньше любой цепочки Jmix
SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/**")                   // только запросы /api/**
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())                 // POST без CSRF-токена
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    JmixHttpSecurityUtils.configureAnonymous(http);   // анонимный пользователь из UserRepository Jmix
    return http.build();
}
```

Импорты: `io.jmix.core.JmixSecurityFilterChainOrder`, `io.jmix.security.util.JmixHttpSecurityUtils`. Все остальные URL (`/`, `/login`) по-прежнему идут через цепочку Jmix UI.

Следствия для кода:

- Любой запрос к `/api/**` проходит без логина и пароля. Аутентификацию и роли в API не добавляем.
- Любой новый контроллер достаточно положить под `/api/...`: отдельной настройки безопасности ему не нужно.
- Если вместо ответа приходит 401/403, включи `logging.level.org.springframework.security=TRACE` (раздел Troubleshooting, строка 73421). Проверь, что путь начинается с `/api/`.

## Почему `UnconstrainedDataManager`

Запрос к `/api/**` выполняется от анонимного пользователя Jmix, а у него по умолчанию нет никаких прав («The anonymous user has no permissions by default», строка 73949). Обычный `DataManager` проверяет права: `load()` вернёт пустой результат, `save()` и `remove()` бросят `AccessDeniedException` (строка 18055).

Поэтому в контроллерах и сервисах API инжектируй **`io.jmix.core.UnconstrainedDataManager`**. Методы те же, что у `DataManager`, проверок прав нет (строка 18065). Роли и права под анонимного пользователя не добавляй: `security/**` трогать нельзя.

```java
private final UnconstrainedDataManager dataManager;   // не DataManager

public MyService(UnconstrainedDataManager dataManager) {
    this.dataManager = dataManager;
}
```

## Шаблон: DTO, контроллер, сервис

Наружу — только DTO (Java record), не сущности: у сущности есть служебные поля (`version`, `createdBy`, `deleteTs`) и ленивые ссылки, а формат ответа определяет контракт, а не модель БД. Имена полей DTO — ровно как в `docs/brief/portal-requirement.md`.

```java
// каждый record — в своём файле, пакет com.debugskills.equipment.api
public record CreateRequestBody(                      // вход
        @NotBlank String employeeEmail,
        @NotBlank String equipmentType,               // String: неизвестный тип проверяем сами
        @NotBlank String reason) {}

@JsonInclude(JsonInclude.Include.NON_NULL)            // пустые поля не попадают в JSON
public record RequestResponse(                        // выход
        UUID id, String status, String inventoryNumber, Integer queuePosition) {
    static RequestResponse from(EquipmentRequest r) { /* поля сущности -> DTO */ }
}

@RestController
@RequestMapping("/api/equipment-requests")
public class EquipmentRequestController {

    private final EquipmentRequestService service;    // бизнес-логика в сервисе, не в контроллере

    public EquipmentRequestController(EquipmentRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RequestResponse> create(@Valid @RequestBody CreateRequestBody body) {
        return ResponseEntity.ok(service.submit(body));
    }
}
```

Сервис — Spring-бин (`@Service`) в пакете `com.debugskills.equipment.app`. Всё, что читает и пишет в БД за один вызов, идёт в одной транзакции: `@org.springframework.transaction.annotation.Transactional` на методе сервиса (`DataManager` присоединяется к текущей транзакции, раздел Transaction Management, строка 19528). Аннотация работает только при вызове метода через инжектированный бин, а не изнутри того же класса.

```java
@Service
public class EquipmentRequestService {
    private final UnconstrainedDataManager dataManager;
    // конструктор как выше

    @Transactional
    public RequestResponse submit(CreateRequestBody body) {
        Employee employee = dataManager.load(Employee.class)
                .query("select e from equipment_Employee e where e.email = :email")
                .parameter("email", body.employeeEmail())
                .optional()
                .orElseThrow(/* ошибка по правилам контракта */);
        // ...
    }
}
```

В JPQL — имя сущности из `@Entity(name = "equipment_X")`, а не имя Java-класса (подробнее в [data-manager.md](data-manager.md)).

## Валидация и ответ 400

Зависимость `spring-boot-starter-validation` уже в `build.gradle`; аннотации — `jakarta.validation.constraints.*` (не `javax`). `@Valid @RequestBody` проверяет DTO до входа в метод; при нарушении Spring бросает `MethodArgumentNotValidException`. Битый JSON даёт `HttpMessageNotReadableException`.

Преобразуй эти исключения в 400 с понятным телом **сам**, через `@RestControllerAdvice` в пакете `api`:

```java
@RestControllerAdvice(basePackages = "com.debugskills.equipment.api")
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onInvalid(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        return ResponseEntity.badRequest().body(new ApiError("validation_failed", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ApiError("malformed_request", List.of()));
    }

    public record ApiError(String error, List<String> details) {}
}
```

Зачем свой обработчик: стандартная реакция Spring на ошибку — `sendError` и повторный заход на `/error`, а `/error` не попадает под `securityMatcher("/api/**")` и идёт через цепочку Jmix UI. Это предосторожность: поведение стандартного `/error` на стенде отдельно не проверялось. MockMvc эту разницу не показывает (error dispatch в нём не выполняется), поэтому финальную проверку делай через `scripts/accept.sh` или `curl`.

## Коды ответа

Портал коды не фиксирует, их выбирает команда: решение записывается в `spec.md` и `contracts/`, а не угадывается в коде.

| Ситуация | Типичный выбор | Как вернуть |
|---|---|---|
| Успех, создана новая запись | `201 Created` (или `200`, если контракт так решил) | `ResponseEntity.status(HttpStatus.CREATED).body(dto)` |
| Успех, вернули уже существующую запись | `200 OK` | `ResponseEntity.ok(dto)` |
| Тело не прошло валидацию, неизвестное значение enum | `400 Bad Request` | `ResponseEntity.badRequest().body(error)` |
| Бизнес-отказ (сотрудник не найден, уволен) | `404`, `409` или `422`: выбор по контракту | `ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error)` |

Единые правила: один формат тела ошибки на весь API (`ApiError` выше), текст ошибки понятен человеку, пустое тело при ошибке не отдаём. Бизнес-приёмка проверяет класс ответа (2xx или 4xx) и поля тела, а не конкретные коды: сами коды фиксирует спецификация.

## Где что лежит

```
src/main/java/com/debugskills/equipment/
  api/        ApiSecurityConfiguration (не трогать), контроллеры, DTO, ApiExceptionHandler
  app/        сервисы с бизнес-логикой (@Service, @Transactional)
  entity/     сущности и enum
```

## Проверено на стенде

- Ошибки под `/api/**` (например, 405, 400 от валидации) Spring отдаёт через `/error` как JSON
  с исходным кодом ответа; редиректа на `/login` нет.
- Неизвестный путь под `/api/` (нет такого маппинга) возвращает HTML-страницу Vaadin с кодом 200,
  а не 404. Проверяйте путь контроллера по контракту, а не по коду ответа.
