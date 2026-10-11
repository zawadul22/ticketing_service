# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Layout

- The git root is this directory. The backend Gradle project lives in `ticketing_service/`, and the frontend (Vite + React 19 + TS) lives in `ticketing_service_frontend/`.
- `i-want-to-build-frolicking-barto.md` is the project plan. Its **Status** section tracks what's done and what's next.

## Commands

Backend, run from `ticketing_service/`:

- Build: `./gradlew build`
- Run the app: `./gradlew bootRun` (needs the `local-postgres` container running, see Database)
- All tests: `./gradlew test` (needs Docker; tests start their own Postgres via Testcontainers)
- Single test class: `./gradlew test --tests 'com.example.ticketing_service.service.UserServiceTest'`
- Single test method: `./gradlew test --tests 'com.example.ticketing_service.service.UserServiceTest.adminIsSeededAtStartup'`
- Swagger UI: http://localhost:8080/swagger-ui.html (OpenAPI JSON at `/v3/api-docs`)

Frontend, run from `ticketing_service_frontend/`: `npm run dev`, `npm run build`, `npm run lint`.

## Stack

- Spring Boot 4.1.1 on Java 21 (Gradle toolchain), Gradle 9.7.1 wrapper, Groovy DSL (`build.gradle`).
- Spring Boot 4 uses modular starters, e.g. `spring-boot-starter-webmvc` / `spring-boot-starter-webmvc-test`, `spring-boot-starter-flyway`, `spring-boot-starter-data-jpa-test`. Check artifact names against the Boot 4.1.1 BOM, not Boot 3 docs.
- Jackson 3 (`tools.jackson.*` packages; annotations stay in `com.fasterxml.jackson.annotation`).
- Testcontainers 2.x: the module is `org.testcontainers:testcontainers-postgresql`, class `org.testcontainers.postgresql.PostgreSQLContainer`.
- Security: stateless JWT (HS256, Nimbus via `spring-boot-starter-oauth2-resource-server`). Access rules live in `config/SecurityConfig`; the token's `sub` is the user id and its `role` claim maps to `ROLE_USER`/`ROLE_ADMIN`. Controllers get the caller with `@AuthenticationPrincipal Jwt jwt` and `Long.valueOf(jwt.getSubject())`. 401/403 from the filters are written as ProblemDetail by `ProblemDetailSecurityHandler`.
- Lombok is set up for both main and test sources.

## Database

- Local dev uses the existing Docker container `local-postgres` (PostgreSQL 18) on `localhost:5432`: database `ticketing_system`, user `postgres`, password `1234`. There is no docker-compose file.
- Flyway owns the schema: migrations live in `src/main/resources/db/migration/` (`V1__init.sql`, ...). Never edit an applied migration; add a new `V<n>__*.sql`.
- `spring.jpa.hibernate.ddl-auto=validate`, so entities must match the migrations exactly. `spring.jpa.open-in-view=false`, so lazy relations must be loaded inside `@Transactional` service methods (or with `JOIN FETCH` / `@EntityGraph`).

## Code

- Base package: `com.example.ticketing_service`. Components must live under it for component scanning to find them.
- Packages are by layer: `entity/`, `repository/`, `service/`, `controller/`, `dto/`, `exception/`, `config/`.
- Entities use Lombok `@Getter @Setter @NoArgsConstructor` (not `@Data`). Relations are one-way `@ManyToOne(fetch = LAZY, optional = false)` with explicit `@JoinColumn`; no inverse `@OneToMany` sides and no cascades.
- `Event.capacity` and `Event.availableSeats` are `@Column(updatable = false)`: after insert they change only through the atomic `@Modifying` queries in `EventRepository`, so entity saves can't overwrite concurrent bookings.
- Controllers take and return DTO records, never entities. Services map entities to DTOs inside the transaction.
- Errors are `ProblemDetail` responses from `exception/GlobalExceptionHandler` (extends `ResponseEntityExceptionHandler`); validation failures include an `errors` map per field.
- Tests: controller slices use `@WebMvcTest` + `@Import(SecurityConfig.class)` + `MockMvcTester` + `@MockitoBean`, with spring-security-test's `jwt()` to fake a USER/ADMIN; integration tests use `@SpringBootTest @Import(TestcontainersConfiguration.class)`.
- Configuration goes in `application.properties` (not YAML).
- Source files use tab indentation.
