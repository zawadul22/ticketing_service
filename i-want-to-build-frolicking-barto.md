# Plan: Minimal Event Ticketing System (practice project)

## Context
The goal is an **event ticketing** app. Admins create events with limited capacity. Logged-in users book tickets, see their bookings, and cancel them. It runs on **PostgreSQL** (the existing `local-postgres` Docker container) and uses **simple auth**: USER/ADMIN roles with JWT. The main lesson is to **never oversell** under concurrent bookings.

Payments, seat maps, email, and refresh tokens are out of scope.

---

## Status (updated 2026-10-11)
**Done** (committed on `main`):
- Infra: dependencies, datasource for `local-postgres`, Flyway `V1__init.sql`, Testcontainers (`postgres:18`) test config, Swagger UI via springdoc 3.1.1.
- `entity/`: `User`, `Role`, `Event`, `Booking`, `BookingStatus`.
- `repository/`: `UserRepository`, `EventRepository` (incl. atomic seat decrement/increment), `BookingRepository` (incl. `existsByEventId`, "my bookings" with `@EntityGraph`).
- Users (registration part): `POST /api/auth/register` (returns `UserResponse`, 201), BCrypt hashing via `spring-security-crypto`, email trimmed and lowercased, `AdminSeeder` (`ApplicationRunner`), `GlobalExceptionHandler` (`ConflictException` incl. duplicate email and `DataIntegrityViolationException` → 409, `NotFoundException` → 404, validation → 400 with `errors` map). Tests: `AuthControllerTest`, `UserServiceTest`.
- Events CRUD (no security yet): `EventController`, `EventService`, `EventRequest`/`EventResponse`. `GET /api/events?includePast=true` lists past events too (for the admin page; make it ADMIN-only in step 3). Capacity changes go through the atomic `EventRepository.updateCapacity` query (409 if below seats sold), and `capacity`/`availableSeats` are `@Column(updatable = false)` so entity saves can't overwrite concurrent seat changes. Delete returns 409 if the event has any bookings. Tests: `EventControllerTest`, `EventServiceTest` (incl. a two-transaction lost-update test).

- Security: `SecurityConfig` (stateless, CSRF off, URL rules, `@EnableMethodSecurity`), HS256 `JwtEncoder`/`JwtDecoder` from `app.jwt.secret` (startup fails if < 32 bytes; issuer validated), `role` claim → `ROLE_*`. `JwtService` issues tokens (`sub` = user id, `email`, `role`, 2h TTL). `AuthService`: register returns `AuthResponse(token, expiresAt, user)`; login gives the same 401 for unknown email and wrong password (dummy BCrypt check against timing leaks). `GET /api/auth/me`. `includePast=true` is ADMIN-only via `@PreAuthorize`. 401/403 come back as ProblemDetail. Swagger has a Bearer "Authorize" button. Tests: `AuthControllerTest`, `EventControllerTest` (auth rules), `AuthServiceTest`, `AuthFlowTest` (end to end with real tokens).

**Next:** step 4, bookings. Open decision: store `unit_price_cents` on bookings via a `V2` migration (recommended) or show no prices on bookings.

**Decisions made along the way:**
- Packages are by layer (`entity/`, `repository/`, `service/`, `controller/`, `dto/`, `exception/`, `config/`), not by feature as section 3 originally described.
- No docker-compose: the app uses the existing `local-postgres` container (PostgreSQL 18, `localhost:5432/ticketing_system`, `postgres`/`1234`).
- Build order changed: users first, then events, then security, then bookings. Bookings need the logged-in user, so security must come before them.
- Entities have only one-way `@ManyToOne` relations (no inverse `@OneToMany`, no cascades), lazy, with explicit `@JoinColumn`.
- Login, `/api/auth/me` and role rules wait for the security step. At that point, register may switch to returning `AuthResponse(token, ...)`.

---

## Domain model (Flyway migration `V1__init.sql`)
| Table | Columns |
|---|---|
| `users` | id (bigserial), email (unique), password_hash, role (`USER`/`ADMIN`), created_at |
| `events` | id, name, description, venue, starts_at (timestamptz), capacity, available_seats (CHECK `>= 0`), price_cents, created_at |
| `bookings` | id, user_id FK, event_id FK, quantity (CHECK `> 0`), status (`CONFIRMED`/`CANCELLED`), created_at |

**How overselling is prevented:** a single atomic conditional update in `EventRepository`:
```sql
UPDATE events SET available_seats = available_seats - :qty
WHERE id = :id AND available_seats >= :qty
```
It runs in the same `@Transactional` method that inserts the booking. If it updates 0 rows, the request returns `409 Conflict` ("not enough seats"). Cancelling does the reverse (`+ :qty`) and sets the status to `CANCELLED`. The DB CHECK constraint is a second safety net.

---

## Backend (`ticketing_service/`)

### 1. Dependencies (`build.gradle`)
These use the Boot 4 modular starter names. Check each against the Boot 4.1 dependency catalog while implementing.
- Added: `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql`, `runtimeOnly 'org.postgresql:postgresql'`, `springdoc-openapi-starter-webmvc-ui:3.1.1`, `spring-security-crypto`
- Added for tests: `spring-boot-starter-data-jpa-test`, `spring-boot-testcontainers`, `org.testcontainers:testcontainers-postgresql`
- Security step: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` (JWT via Nimbus with an HMAC secret, no extra JWT library), and `spring-boot-starter-security-test`

### 2. Infra and config
- Database: the existing `local-postgres` container (PostgreSQL 18) at `localhost:5432/ticketing_system`, user `postgres`, password `1234`.
- `application.properties` gets:
  - `spring.datasource.*`
  - `spring.jpa.hibernate.ddl-auto=validate`
  - `spring.jpa.open-in-view=false`
  - `spring.mvc.problemdetails.enabled=true`
  - `app.admin.email` and `app.admin.password` for seeding the admin
  - Security step: `app.jwt.secret` (at least 32 bytes for HS256), `app.jwt.ttl=PT2H`
- Tests use a `@TestConfiguration` with a `@ServiceConnection PostgreSQLContainer` (`postgres:18`), so tests don't need the local DB.

### 3. Packages (by layer, under `com.example.ticketing_service`)
- `entity/`: `User`, `Role`, `Event`, `Booking`, `BookingStatus` (Lombok `@Getter @Setter @NoArgsConstructor`)
- `repository/`: `UserRepository`, `EventRepository` (holds the atomic decrement/increment `@Modifying` queries), `BookingRepository`
- `service/`: `UserService` (done), `EventService`, `BookingService` (`book`, `cancel`, `listMine`), and in the security step `JwtService` (issues tokens with `JwtEncoder`) plus login logic
- `controller/`: `AuthController` (register done; login and `me` in the security step), `EventController`, `BookingController`
- `dto/`: request/response records with `@Valid` constraints: `RegisterRequest`, `UserResponse` (done), event and booking DTOs, and in the security step `LoginRequest`, `AuthResponse(token, email, role)`
- `config/`: `PasswordEncoderConfig` and `AdminSeeder` (`ApplicationRunner`) are done. Security step: `SecurityConfig` with a stateless `SecurityFilterChain`, CSRF off, `oauth2ResourceServer().jwt()`, the role claim mapped to `ROLE_*` authorities, `JwtEncoder`/`JwtDecoder` beans built from the secret, `/swagger-ui/**` and `/v3/api-docs/**` permitted, and a Bearer JWT scheme for Swagger's Authorize button.
- `exception/`: `GlobalExceptionHandler` (`@RestControllerAdvice`) maps `NotFoundException` to 404, `NotEnoughSeatsException`, duplicate email, and `DataIntegrityViolationException` (a safety net for any foreign key conflict that slips past the service checks) to 409, validation failures to 400, and forbidden actions to 403. All responses are `ProblemDetail`.

### 4. REST API
| Method | Path | Access |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | public |
| GET | `/api/auth/me` | authenticated |
| GET | `/api/events` (upcoming, sorted by `starts_at`), `/api/events/{id}` | public |
| GET | `/api/events?includePast=true` (past events too) | ADMIN |
| POST / PUT / DELETE | `/api/events`, `/api/events/{id}` | ADMIN |
| POST | `/api/events/{id}/bookings` `{quantity}` (1–10) | USER |
| GET | `/api/bookings/me` | authenticated |
| POST | `/api/bookings/{id}/cancel` (only the owner, only before the event starts) | authenticated |

Business rules:
- You can't book past events.
- You can't reduce an event's capacity below the number of seats already sold.
- You can only delete an event that has no bookings at all, confirmed or cancelled. `EventService` checks `bookingRepository.existsByEventId(id)` and returns `409 Conflict` otherwise. Cancelled bookings count too, because their rows still reference the event through the `bookings.event_id` foreign key, and Postgres would reject the delete. Events with bookings can still be edited.

### 5. Tests
- `BookingServiceTest` with Testcontainers covers:
  - happy path
  - sold out → 409
  - cancel restores seats
  - **concurrency:** 50 threads each book 1 seat on an event with capacity 10, and exactly 10 must succeed with `available_seats == 0`
- `@WebMvcTest` slices for the controllers check auth rules: 401 when anonymous, 403 when USER calls admin endpoints, and 400 on validation errors.

---

## Frontend (`ticketing_service_frontend/`)
- Add `react-router`. `vite.config.ts` gets `server.proxy: { '/api': 'http://localhost:8080' }`, so there's no CORS setup on the backend.
- `src/api/client.ts` is a `fetch` wrapper that attaches `Authorization: Bearer`, parses `ProblemDetail` errors, and logs out on 401. `src/api/types.ts` holds the shared types.
- `src/auth/AuthContext.tsx` keeps the token and user in `localStorage` (fine for practice; the XSS tradeoff is noted) and provides `<RequireAuth role?>`.
- Pages in `src/pages/`:
  - `EventsPage`: list with seats left
  - `EventDetailPage`: quantity picker and Book button, which shows the 409 message when seats run out
  - `LoginPage`, `RegisterPage`
  - `MyBookingsPage`: with cancel
  - `admin/EventFormPage`: create/edit
  - `admin/AdminEventsPage`: list with edit/delete
- Replace the template `App.tsx`/`App.css` with the router, a nav bar showing login state, and simple plain CSS.

---

## Build order (each step ends in something runnable)
1. ✅ Dependencies, datasource, Flyway `V1`, Testcontainers test config, Swagger UI, entities, repositories.
1b. ✅ Users: registration, password hashing, admin seeder, exception handler, and tests.
2. ✅ Events CRUD (no security yet) and tests.
3. ✅ Security: login, JWT, `/api/auth/me`, role rules, Swagger auth, and tests.
4. **← next** Bookings with atomic seat decrement, cancel, and the concurrency test.
5. Frontend: router, proxy, auth context, login/register.
6. Frontend: events list/detail, booking, my bookings.
7. Frontend: admin event pages.
8. Keep `CLAUDE.md` up to date (it was already updated on 2026-10-11 for the DB setup, layer packages and frontend commands).

## Verification
- `cd ticketing_service && ./gradlew test`: all tests green, including the concurrency test (needs Docker running).
- With `local-postgres` running, `./gradlew bootRun`. Use curl to register, log in, and get a token. As admin, create an event with capacity 2. As a user, book 2 (201), then book 1 more (409). Cancel, then book again (201).
- `cd ticketing_service_frontend && npm run dev`, then click through as admin (create an event) and as a user (book, view, cancel). `npm run build` and `npm run lint` must pass.
