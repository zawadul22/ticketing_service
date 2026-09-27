# Plan: Minimal Event Ticketing System (practice project)

## Context
Both halves are fresh scaffolds. The backend `ticketing_service/` has only `TicketingServiceApplication.java` and a `contextLoads` test, with Spring Boot 4.1.1 and the webmvc starter; the Postgres driver is commented out in `build.gradle`. The frontend `ticketing_service_frontend/` is the default Vite 8 + React 19 + TS template with the React Compiler.

The goal is an **event ticketing** app. Admins create events with limited capacity. Logged-in users book tickets, see their bookings, and cancel them. It runs on **PostgreSQL via Docker** and uses **simple auth**: USER/ADMIN roles with JWT. The main lesson is to **never oversell** under concurrent bookings.

Payments, seat maps, email, and refresh tokens are out of scope.

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
- `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql`
- `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` (JWT via Nimbus with an HMAC secret, no extra JWT library)
- Uncomment `runtimeOnly 'org.postgresql:postgresql'`
- Test: `spring-boot-starter-security-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-testcontainers`, and the Testcontainers Postgres module

### 2. Infra and config
- `docker-compose.yml` at the repo root runs `postgres:17` on port 5432 with db/user/pass `ticketing`.
- `application.properties` gets:
  - `spring.datasource.*`
  - `spring.jpa.hibernate.ddl-auto=validate`
  - `spring.jpa.open-in-view=false`
  - `spring.mvc.problemdetails.enabled=true`
  - `app.jwt.secret`, `app.jwt.ttl=PT2H`
  - `app.admin.email` and `app.admin.password` for seeding the admin
- Tests use a `@TestConfiguration` with a `@ServiceConnection PostgreSQLContainer`, so `contextLoads` passes without a local DB. This follows the CLAUDE.md warning.

### 3. Packages (by feature, under `com.example.ticketing_service`)
- `user/`: `User` entity (Lombok `@Getter @Setter @NoArgsConstructor`), `Role` enum, `UserRepository`
- `auth/`: `AuthController`, `AuthService`, `JwtService` (issues tokens with `JwtEncoder`), DTO records `RegisterRequest`, `LoginRequest`, `AuthResponse(token, email, role)`
- `config/`: `SecurityConfig` has a stateless `SecurityFilterChain`, CSRF off, `oauth2ResourceServer().jwt()`, the role claim mapped to `ROLE_*` authorities, `BCryptPasswordEncoder`, and `JwtEncoder`/`JwtDecoder` beans built from the secret. `AdminSeeder` is a `CommandLineRunner` that creates the admin if it's missing.
- `event/`: `Event` entity, `EventRepository` (holds the atomic decrement/increment `@Modifying` queries), `EventService`, `EventController`, and DTO records with `@Valid` constraints
- `booking/`: `Booking` entity, `BookingStatus`, `BookingRepository`, `BookingService` (`book`, `cancel`, `listMine`), `BookingController`
- `common/`: `GlobalExceptionHandler` (`@RestControllerAdvice`) maps `NotFoundException` to 404, `NotEnoughSeatsException` and duplicate email to 409, validation failures to 400, and forbidden actions to 403. All responses are `ProblemDetail`.

### 4. REST API
| Method | Path | Access |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | public |
| GET | `/api/auth/me` | authenticated |
| GET | `/api/events` (upcoming, sorted by `starts_at`), `/api/events/{id}` | public |
| POST / PUT / DELETE | `/api/events`, `/api/events/{id}` | ADMIN |
| POST | `/api/events/{id}/bookings` `{quantity}` (1–10) | USER |
| GET | `/api/bookings/me` | authenticated |
| POST | `/api/bookings/{id}/cancel` (only the owner, only before the event starts) | authenticated |

Business rules:
- You can't book past events.
- You can't reduce an event's capacity below the number of seats already sold.
- You can't delete an event that has confirmed bookings.

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
1. docker-compose, dependencies, datasource, Flyway `V1`, Testcontainers test config. `./gradlew test` passes.
2. Events CRUD (no security yet), exception handler, and tests.
3. Security: users, register/login, JWT, role rules, admin seeder, and tests.
4. Bookings with atomic seat decrement, cancel, and the concurrency test.
5. Frontend: router, proxy, auth context, login/register.
6. Frontend: events list/detail, booking, my bookings.
7. Frontend: admin event pages.
8. Update `CLAUDE.md`: remove the stale Postgres note and document docker compose and the frontend commands.

## Verification
- `cd ticketing_service && ./gradlew test`: all tests green, including the concurrency test (needs Docker running).
- `docker compose up -d`, then `./gradlew bootRun`. Use curl to register, log in, and get a token. As admin, create an event with capacity 2. As a user, book 2 (201), then book 1 more (409). Cancel, then book again (201).
- `cd ticketing_service_frontend && npm run dev`, then click through as admin (create an event) and as a user (book, view, cancel). `npm run build` and `npm run lint` must pass.
