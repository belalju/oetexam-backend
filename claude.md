# CLAUDE.md

## Project Overview

This project is a Spring Boot-based backend service designed for scalability, security, and maintainability.

* Java: 17+
* Spring Boot: 3.x
* Architecture: Clean / Hexagonal
* Build Tool: Maven / Gradle
* Database: MySQL
* Cache: Redis (optional)
* Messaging: Kafka (optional)

---

## Coding Standards

### General Rules

* Follow SOLID principles
* Prefer composition over inheritance
* Keep classes < 300 lines
* Keep methods < 30 lines
* Use meaningful names (no abbreviations)
* Avoid tight coupling

### Package Structure

com.oet.project
├── config
├── controller
├── service
├── domain
├── repository
├── dto
├── mapper
├── exception
├── security
└── util

---

## Spring Boot Guidelines

### Configuration

* Use `application.yml`
* Never hardcode secrets
* Use environment variables or external config (AWS Parameter Store / Vault)

### Profiles

* Use `dev`, `staging`, `prod`
* Keep configs isolated per environment

---

## REST API Design

* Follow REST conventions
* Use proper HTTP methods and status codes
* Validate all inputs (`@Valid`)
* Do not expose entities directly

### Standard Response Format

{
"timestamp": "...",
"status": 200,
"data": {},
"error": null
}

---

## Exception Handling

* Use `@ControllerAdvice`
* Map exceptions to proper HTTP status codes
* Never expose stack traces

---

## Security

* Use Spring Security
* Prefer JWT / OAuth2
* Enable HTTPS
* Configure CORS properly
* Store tokens in HttpOnly cookies when possible
* Never log sensitive data

---

## Database & JPA

* Use `@Transactional` at service layer
* Avoid N+1 problem (use fetch join / DTO projection)
* Add indexes for frequently queried columns

---

## Performance

* Use HikariCP connection pooling
* Use pagination for large datasets
* Use Redis caching where applicable
* Avoid unnecessary blocking calls

---

## Logging

* Use SLF4J (Logback)
* ERROR → failures
* WARN → unexpected situations
* INFO → business flow
* DEBUG → development only

---

## Observability

* Enable Spring Boot Actuator
* Integrate Micrometer + Prometheus + Grafana
* Monitor:

  * JVM metrics
  * API latency
  * DB performance

---

## Testing

* Use JUnit 5 + Mockito
* Write:

  * Unit tests
  * Integration tests
* Target ≥70% coverage

---

## API Documentation

* Use OpenAPI / Swagger
* Keep documentation updated

---

## Kafka (If Used)

* Use idempotent producers
* Handle retries and DLQ
* Ensure message ordering where needed

---

## Redis (If Used)

* Use for caching and rate limiting
* Always set TTL

---

## CI/CD

Pipeline steps:

1. Build
2. Test
3. Security Scan
4. Docker Build
5. Deploy

---

## Docker

* Use multi-stage build
* Use lightweight base images (distroless/slim)
* Run as non-root user

---

## Code Review Rules

* No business logic in controllers
* No entity exposure in API
* Proper exception handling required
* Validate all inputs
* Follow project structure strictly

---

## Anti-Patterns to Avoid

* Fat controllers
* Anemic services
* Hardcoded configs
* Missing transactions
* Ignoring error handling

---

## AI Assistant Instructions

When generating code:

* Follow existing project structure
* Write production-ready code
* Include validation and exception handling
* Avoid unnecessary dependencies

When reviewing code:

* Check performance issues
* Check security vulnerabilities
* Suggest improvements clearly

---

## Example Controller

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

```
private final UserService userService;

@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    return ResponseEntity.ok(userService.getUser(id));
}
```

}

---

## Example Service

@Service
@RequiredArgsConstructor
public class UserService {

```
private final UserRepository userRepository;

@Transactional(readOnly = true)
public UserResponse getUser(Long id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("User not found"));

    return UserMapper.toResponse(user);
}
```

}

---

## DTO

- Prefer Record rather Lombok

## What Claude Should Avoid

- Do **not** add `@Autowired` on fields — always use constructor injection.
- Do **not** catch `Exception` broadly and swallow it silently.
- Do **not** write business logic inside `@Controller` or `@RestController` classes.
- Do **not** commit secrets, credentials, or `.env` files.
- Do **not** use `ddl-auto=create-drop` outside local/test environments.
- Do **not** return raw JPA entities from REST endpoints — always map to DTOs.

---

## Final Notes

* Always prioritize readability over complexity
* Ensure scalability and fault tolerance
* Follow best practices consistently
