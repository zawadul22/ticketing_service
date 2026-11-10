# Ticketing service backend

Modules 1–3 provide the Spring Boot foundation, persistence model, and user authentication. Event and booking APIs will be added in subsequent modules. Flyway is excluded; Hibernate manages the local schema with `ddl-auto=update`.

## Requirements

- Java 21 (Gradle wrapper included)
- PostgreSQL, or Docker with Compose for the supplied local database

## Run locally (PowerShell)

From `ticketing_service_backend`:

```powershell
docker compose up -d --wait
.\gradlew.bat bootRun
```

The local database uses port `5433`, database `ticketing_service`, username `ticketing`, and password `ticketing_local`. Port 5433 avoids conflicting with an existing PostgreSQL installation on 5432. These defaults are for local development. Alternatively, create a PostgreSQL database yourself and configure the connection before starting the backend:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/ticketing_service'
$env:DB_USERNAME = 'ticketing'
$env:DB_PASSWORD = 'your-password'
$env:SERVER_PORT = '8080'
.\gradlew.bat bootRun
```

Compose also reads `DB_USERNAME` and `DB_PASSWORD` from the shell when creating a new database volume. Changing them does not update credentials in an existing volume.

- Health: http://localhost:8080/api/health (includes database connectivity; returns 503 when unhealthy)
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI: http://localhost:8080/v3/api-docs

Health, API documentation, registration, and login are public. Other user APIs require a Bearer token. Event-management routes are restricted to admins; event and booking handlers arrive in later modules.

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
docker compose up -d --wait
.\gradlew.bat build
```

The foundation smoke tests verify PostgreSQL connectivity, HTTP health, OpenAPI/Swagger availability, and denial of anonymous access to other routes. Transactional persistence tests verify entity relationships, defaults, email uniqueness, price snapshots, event ordering, numeric constraints, and foreign keys protecting booking history. Authentication integration tests cover registration/login, current-user lookup, BCrypt hashing, duplicate/concurrent registration, validation, invalid/expired JWTs, role restrictions, and admin setup. Persistence tests roll back records; HTTP tests remove their own accounts. The test profile supplies a test-only signing key and disables automatic admin setup, so `JWT_SECRET` is not required to run tests.

## Persistence model

- `User` maps to `users`: normalized unique email, password hash, `USER`/`ADMIN` role, and creation timestamp. Supply a hashed password; BCrypt hashing belongs to the authentication module.
- `Event` maps to `events`: event information, timezone-aware start time, capacity, available seats, and price in integer cents. New instances start with all seats available.
- `Booking` maps to `bookings`: lazy user/event relationships, quantity 1–10, price snapshot, `CONFIRMED`/`CANCELLED` status, and creation timestamp. Booking history is preserved without cascading deletes.
- Repositories provide CRUD plus email lookup, chronological event listing, personal booking listing, and booking-history checks.

Constructing or saving a booking does not reserve seats. Transactional seat changes and cancellation will be implemented in Module 5. Future-start validation and safe capacity changes belong to the event service in Module 4; past events must remain persistable.

Stop the local database with `docker compose stop` (data is preserved).
