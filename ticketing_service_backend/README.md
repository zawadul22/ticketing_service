# Ticketing service backend

Backend Modules 1–6 are implemented and verified: foundation, persistence model, user authentication, event management, transactional booking/cancellation, and backend verification/documentation. Frontend implementation and browser flows remain separate work. Flyway is excluded; Hibernate manages the local schema with `ddl-auto=update`.

## Requirements

- Java 21 (Gradle wrapper included)
- PostgreSQL, or Docker with Compose for the supplied local database

## Run locally (PowerShell)

From `ticketing_service_backend`:

```powershell
.\gradlew.bat bootRun
```

Use the existing PostgreSQL instance configured in `application.properties`: port `5432`, database `ticketing_service`, username `postgres`, and password `root`. These defaults are for local development. Connection settings can be overridden before starting the backend:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/ticketing_service'
$env:DB_USERNAME = 'ticketing'
$env:DB_PASSWORD = 'your-password'
$env:SERVER_PORT = '8080'
.\gradlew.bat bootRun
```

The supplied Compose file is optional; the application does not require a new container. If using Compose instead of your existing database, set `DB_URL=jdbc:postgresql://localhost:5433/ticketing_service`, `DB_USERNAME=ticketing`, and `DB_PASSWORD=ticketing_local`, then run `docker compose up -d --wait`. Compose reads `DB_USERNAME` and `DB_PASSWORD` when creating a new database volume; changing them does not update credentials in an existing volume.

- Health: http://localhost:8080/api/health (includes database connectivity; returns 503 when unhealthy)
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI: http://localhost:8080/v3/api-docs

Health, API documentation, registration, login, and event browsing are public. Other user APIs require a Bearer token. Event management and listings that include past events require an admin token.

## User authentication

| Method | Path | Request / response |
|---|---|---|
| POST | `/api/auth/register` | `{ "email": "user@example.com", "password": "example-password" }`; returns 201 |
| POST | `/api/auth/login` | Same request shape; returns 200 |
| GET | `/api/auth/me` | Requires `Authorization: Bearer <accessToken>`; returns the current user |

Registration/login return `{ "accessToken": "...", "tokenType": "Bearer", "expiresAt": "...", "user": { "id": 1, "email": "user@example.com", "role": "USER", "createdAt": "..." } }`. Password hashes are never included. Swagger UI supports Bearer authentication using its **Authorize** button.

- Emails are trimmed and normalized to lowercase. Registration always creates `USER`, regardless of any supplied role.
- Passwords must contain 8–72 characters and at most 72 UTF-8 bytes. They are stored as BCrypt hashes.
- `JWT_SECRET` optionally overrides the local development signing secret in `application.properties` and must contain at least 32 characters. Set a private secret outside local development. Retain the same secret across restarts if existing tokens should remain valid. Changing it invalidates all existing tokens.
- `JWT_TTL_SECONDS` defaults to `3600` and accepts values from `60` to `86400`. Tokens use HS256, a fixed issuer, and the user ID as their subject.
- Requests use current database roles; removed accounts cannot authenticate with old tokens.
- Authentication is stateless. Logout means discarding the token in the client. There is no server-side logout/revocation endpoint; a copied token remains usable until expiry.
- Errors share `timestamp`, `status`, `error`, `message`, and `fieldErrors`. Validation returns 400, invalid credentials/tokens 401, insufficient permissions 403, missing resources 404, and duplicate registration 409.

### Optional admin setup

Set these variables before starting the backend:

```powershell
$env:ADMIN_ENABLED = 'true'
$env:ADMIN_EMAIL = 'admin@example.com'
$env:ADMIN_PASSWORD = 'choose-a-strong-password'
```

Startup creates the admin if absent. Repeated startup preserves the existing admin and password; configuration does not reset passwords or promote a registered regular user. An existing regular user with that email causes startup to fail. Disable setup with `ADMIN_ENABLED=false` after creating the admin if desired. Admins log in through the same login endpoint.

## Verify

Tests use real PostgreSQL and the same `DB_*` environment variables as the application:

```powershell
.\gradlew.bat build
```

The foundation smoke tests verify PostgreSQL connectivity, HTTP health, OpenAPI/Swagger availability, and denial of anonymous access to other routes. Transactional persistence tests verify entity relationships, defaults, email uniqueness, price snapshots, event ordering, numeric constraints, and foreign keys protecting booking history. Authentication integration tests cover registration/login, current-user lookup, BCrypt hashing, duplicate/concurrent registration, validation, invalid/expired JWTs, role restrictions, and admin setup. Persistence tests roll back records; HTTP tests remove their own accounts. The test profile supplies a test-only signing key and disables automatic admin setup, so `JWT_SECRET` is not required to run tests.

Final backend verification on 2026-11-12: `./gradlew.bat clean build` passed all 73 tests using the existing PostgreSQL connection. `BackendFlowTests` exercises admin setup/login, user registration/login, public event browsing, booking, personal bookings, cancellation, admin event updates/deletion, and booking-history deletion restrictions as a connected HTTP flow. Swagger checks verify the served UI page, every API path, and Bearer security declarations; these are automated HTTP/contract checks, not interactive browser clicks.

To repeat just the connected-flow and Swagger checks:

```powershell
.\gradlew.bat test --tests '*BackendFlowTests'
```

For interactive Swagger verification, open the UI, register/login, copy `accessToken` into **Authorize**, browse events, book tickets, list `/api/bookings/me`, and cancel. Log in with an admin token to create/edit/delete events and list past events. Events with booking history must return 409 on deletion, even after cancellation.

## Persistence model

- `User` maps to `users`: normalized unique email, password hash, `USER`/`ADMIN` role, and creation timestamp. Supply a hashed password; BCrypt hashing belongs to the authentication module.
- `Event` maps to `events`: event information, timezone-aware start time, capacity, available seats, and price in integer cents. New instances start with all seats available.
- `Booking` maps to `bookings`: lazy user/event relationships, quantity 1–10, price snapshot, `CONFIRMED`/`CANCELLED` status, and creation timestamp. Booking history is preserved without cascading deletes.
- Repositories provide CRUD plus email lookup, chronological event listing, personal booking listing, and booking-history checks.

Constructing or saving a booking directly does not reserve seats. Use `BookingService` to reserve seats, create bookings, or cancel them transactionally.

## Event management

| Method | Path | Access / result |
|---|---|---|
| GET | `/api/events` | Public upcoming events, sorted by start time |
| GET | `/api/events/{id}` | Public details, including past events |
| GET | `/api/events?includePast=true` | Admin listing of upcoming and past events |
| POST | `/api/events` | Admin creation; returns 201 with Location header |
| PUT | `/api/events/{id}` | Admin full update; returns 200 |
| DELETE | `/api/events/{id}` | Admin deletion without booking history; returns 204 |

Creation and updates accept this shape (all fields except description are required):

```json
{
  "name": "Live concert",
  "description": "An evening of music",
  "venue": "Main hall",
  "startTime": "2099-01-01T18:00:00+06:00",
  "capacity": 100,
  "ticketPriceCents": 2500
}
```

Responses also include `id` and `availableSeats`. New events start with all seats available. Prices use integer cents; zero permits free events. Dates accept timezone offsets and are returned in UTC.

New events must start in the future. On update, an unchanged past start time is allowed; rescheduling requires a future time. Capacity changes preserve the number of reserved seats and return 409 if capacity falls below that count. Any booking history prevents deletion, including cancelled bookings. Event price changes do not modify existing booking price snapshots.

Capacity changes acquire a PostgreSQL row lock before reading seat counts. Concurrent conditional seat reservations serialize with those changes. Integration tests exercise both lock orders, as well as HTTP CRUD, permissions, validation, ordering, deletion restrictions, and Swagger authentication.

For the optional Compose database, `docker compose stop` stops it while preserving data.

## Booking and cancellation

All booking endpoints require `Authorization: Bearer <accessToken>` and support Swagger's **Authorize** button.

| Method | Path | Behavior |
|---|---|---|
| POST | `/api/events/{eventId}/bookings` | Accepts `{ "quantity": 1 }`; returns 201 with the confirmed booking |
| GET | `/api/bookings/me` | Returns only the caller's bookings, newest first, including cancellations |
| POST | `/api/bookings/{id}/cancel` | Owner cancels a confirmed booking before the event starts; returns 200 |

Booking responses contain `id`, nested `event` details/current availability, `quantity`, the `unitPriceCents` snapshot, `totalPriceCents`, `status`, and `createdAt`. Totals use the booking price snapshot, so later event price changes do not change booking totals. A supplied user ID is ignored; ownership comes from the authenticated caller.

- Quantity must be 1–10. No payment is collected.
- Missing events/bookings return 404; another user's cancellation attempt returns 403.
- Insufficient seats, bookings/cancellations after the start time, and repeated cancellations return 409.
- Seat reservation uses an atomic conditional update inside the booking transaction. The event row is locked first so capacity, start-time, and price changes cannot race with the booking.
- Cancellation locks the event and booking, changes the status once, and restores seats in the same transaction. Booking history remains stored.
- All seat-changing operations lock the event first, keeping their lock order consistent. If a transaction fails, both the seat change and booking change roll back.

PostgreSQL integration tests cover the full HTTP flow, ownership, validation, price snapshots, rollback on injected failures, concurrent bookings without overselling, simultaneous cancellations without duplicate seat restoration, and concurrent bookings/cancellations/capacity changes. These tests use the existing connection settings and remove only their own fixtures.
