# Ticketing service backend

Modules 1 and 2 provide the Spring Boot foundation and persistence model. Business APIs will be added in subsequent modules. Flyway is excluded; Hibernate manages the local schema with `ddl-auto=update`.

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

Health and API documentation are public. Other routes are denied until the authentication module is implemented. No domain API endpoints are implemented yet.

## Verify

Tests use real PostgreSQL and the same `DB_*` environment variables as the application:

```powershell
docker compose up -d --wait
.\gradlew.bat build
```

The foundation smoke tests verify PostgreSQL connectivity, HTTP health, OpenAPI/Swagger availability, and denial of anonymous access to other routes. Transactional persistence tests verify entity relationships, defaults, email uniqueness, price snapshots, event ordering, numeric constraints, and foreign keys protecting booking history. Test records are rolled back.

## Persistence model

- `User` maps to `users`: normalized unique email, password hash, `USER`/`ADMIN` role, and creation timestamp. Supply a hashed password; BCrypt hashing belongs to the authentication module.
- `Event` maps to `events`: event information, timezone-aware start time, capacity, available seats, and price in integer cents. New instances start with all seats available.
- `Booking` maps to `bookings`: lazy user/event relationships, quantity 1–10, price snapshot, `CONFIRMED`/`CANCELLED` status, and creation timestamp. Booking history is preserved without cascading deletes.
- Repositories provide CRUD plus email lookup, chronological event listing, personal booking listing, and booking-history checks.

Constructing or saving a booking does not reserve seats. Transactional seat changes and cancellation will be implemented in Module 5. Future-start validation and safe capacity changes belong to the event service in Module 4; past events must remain persistable.

Stop the local database with `docker compose stop` (data is preserved).
