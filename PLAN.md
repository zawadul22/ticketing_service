# Event Ticketing Service — Implementation Plan

## Goal and scope

Build a simple event ticketing app using the existing Spring Boot backend and React frontend boilerplates. PostgreSQL stores users, events, and bookings. Concurrent bookings must never exceed an event's capacity.

This plan replaces the historical completion claims in the existing project notes as the scope for the new implementation. Inspect the boilerplates before making changes and preserve useful existing setup.

### Users

- Register, log in, and log out.
- Browse upcoming events and view event details.
- Book 1–10 tickets per booking.
- View their own bookings.
- Cancel a booking before the event starts.

### Admins

- Create, edit, and delete events.
- Set event capacity and ticket price.
- View upcoming and past events.

Payments, assigned seats, email notifications, refresh tokens, and deployment are outside the initial scope. Prices are informational; booking confirms tickets without payment.

## 1. Project foundation

- Backend directory: `ticketing_service_backend/` — Spring Boot, Java, Gradle.
- Frontend directory: `ticketing_service_frontend/` — React, Vite.
- Inspect the existing code and dependencies before implementation.
- Use the versions already configured in the boilerplates where compatible.
- Add backend support for Spring MVC, JPA, validation, security, PostgreSQL, and API documentation.
- Configure local database access with credentials configurable through environment variables.
- Use Hibernate schema management (`ddl-auto=update`) for local development; Flyway is excluded from scope.
- Configure Vite to proxy `/api` requests to the backend.

**Deliverable:** the backend connects to PostgreSQL, the frontend starts, and the frontend can call the backend.

## 2. Data model

| Entity | Main fields |
|---|---|
| User | ID, unique email, password hash, role, creation time |
| Event | ID, name, description, venue, start time, capacity, available seats, ticket price |
| Booking | ID, user, event, quantity, unit price at booking, status, creation time |

- Roles: `USER` and `ADMIN`.
- Booking states: `CONFIRMED` and `CANCELLED`.
- Store prices as integer cents and event timestamps with timezone awareness.
- Snapshot the unit price when booking so later price changes do not alter booking totals.
- Add database constraints for unique emails, valid quantities, nonnegative prices, and valid seat counts.
- Organize backend packages by layer: entity, repository, service, controller, DTO, configuration, and exception handling.
- Expose DTOs through controllers rather than persistence entities.

**Deliverable:** database schema managed through JPA/Hibernate, entities, and repositories.

## 3. Authentication and authorization

- Register accounts with the `USER` role.
- Normalize emails and hash passwords with BCrypt.
- Return a JWT on registration and login.
- Provide an endpoint for the current authenticated user.
- Seed an admin through configuration.
- Restrict event management to admins.
- Derive booking ownership from the authenticated user rather than request-supplied user IDs.
- Return consistent errors for validation, authentication, authorization, missing records, and conflicts.

**Deliverable:** tested registration/login flow and protected endpoints.

## 4. Event management

- Public endpoints list upcoming events and return event details.
- Admin endpoints create, update, and delete events, and list past events.
- Sort event listings by start time.

### Business rules

- Capacity must be positive; price cannot be negative.
- New events must start in the future.
- Capacity cannot be reduced below the number of tickets already booked.
- Events with booking history cannot be deleted, including events with cancelled bookings.
- Available seats must remain consistent when capacity changes, including during concurrent bookings.

**Deliverable:** event APIs with validation and service tests.

## 5. Booking and cancellation

### Booking

- Allow authenticated users to book 1–10 tickets for an upcoming event.
- Reserve seats and create the booking in one database transaction.
- Use an atomic conditional update to prevent overselling:

```sql
UPDATE events
SET available_seats = available_seats - :quantity
WHERE id = :eventId
  AND available_seats >= :quantity;
```

- Return `409 Conflict` when seats are insufficient.
- Roll back the seat reservation if booking creation fails.
- Return the booking details and price snapshot.

### Cancellation

- Verify that the caller owns the booking.
- Reject cancellation after the event starts.
- Transition a confirmed booking to cancelled exactly once.
- Restore seats in the same transaction.
- Ensure repeated or concurrent cancellations never restore seats twice.

**Deliverable:** booking APIs and integration tests proving capacity and cancellation safety.

## 6. REST API

| Method | Path | Access / purpose |
|---|---|---|
| POST | `/api/auth/register` | Public registration |
| POST | `/api/auth/login` | Public login |
| GET | `/api/auth/me` | Current authenticated user |
| GET | `/api/events` | Public upcoming events |
| GET | `/api/events/{id}` | Public event details |
| GET | `/api/events?includePast=true` | Admin event listing |
| POST | `/api/events` | Admin creates an event |
| PUT | `/api/events/{id}` | Admin updates an event |
| DELETE | `/api/events/{id}` | Admin deletes an event without booking history |
| POST | `/api/events/{id}/bookings` | User books tickets with `{ "quantity": 1 }` |
| GET | `/api/bookings/me` | Authenticated user's bookings |
| POST | `/api/bookings/{id}/cancel` | Owner cancels a booking |

Use appropriate HTTP statuses, including `201` for creation, `400` for invalid input, `401` for missing or invalid authentication, `403` for forbidden actions, `404` for missing resources, and `409` for business conflicts.

## 7. React frontend

| Page | Purpose |
|---|---|
| Events | Browse upcoming events and remaining seats |
| Event details | View details, choose quantity, and book tickets |
| Login / Register | Authenticate users |
| My bookings | View booking details and totals, and cancel |
| Admin events | Manage upcoming and past events |
| Event form | Create or edit events |

- Add routing and navigation reflecting the user's login state and role.
- Add a shared API client with JWT authentication and consistent error parsing.
- Manage authentication state and clear expired sessions.
- Protect authenticated and admin routes; enforce permissions independently on the backend.
- Provide loading, empty, success, and error states.
- Refresh event availability after a booking conflict.
- Use simple responsive CSS.

**Deliverable:** complete browser flows for users and admins.

## 8. Verification and documentation

- Test authentication, permissions, validation, and business rules.
- Use PostgreSQL integration tests, with Testcontainers where appropriate.
- Run concurrent bookings against a small-capacity event and verify no overselling.
- Test simultaneous cancellations and concurrent capacity changes.
- Verify rollback behavior when a booking transaction fails.
- Run the frontend build and lint checks.
- Verify the browser flow: register → browse → book → view bookings → cancel.
- Verify admin create, edit, and delete behavior, including restricted deletion.
- Update project documentation with accurate setup commands, required configuration, and implementation status.

**Deliverable:** verified application and up-to-date local development instructions.

## Implementation sequence

### Progress

- [x] Module 1: backend foundation, environment-configurable PostgreSQL, Hibernate schema management, validation/security dependencies, Swagger/OpenAPI, and Vite API proxy.
  - Verified: Gradle build and four PostgreSQL smoke tests; frontend build and lint; health request through the Vite proxy.
  - Local PostgreSQL runs through Compose on port 5433. See `ticketing_service_backend/README.md` for setup.
- [x] Module 2: data model, entities, and repositories.
  - Added User, Event, Booking, role/status enums, database constraints, and repository queries.
  - Verified PostgreSQL persistence, defaults, relationships, email uniqueness, price snapshots, event ordering, and invalid numeric values/deletion restrictions.
- [x] Module 3: authentication and authorization.
  - Implemented registration/login JWT responses, BCrypt passwords, current-user lookup, configured admin setup, role-based event-route protection, and consistent API errors.
  - Verified PostgreSQL HTTP integration tests for validation, duplicate/concurrent registration, token verification/expiry, permissions, and admin setup.
- [ ] Module 4: event management.
- [ ] Module 5: booking and cancellation.
- [ ] Module 6: broader backend verification and documentation.

### Planned order

1. Inspect boilerplates and establish the project foundation.
2. Add the database schema, entities, and repositories.
3. Implement authentication and authorization.
4. Implement event management.
5. Implement transactional booking and cancellation.
6. Build the React frontend.
7. Complete end-to-end verification and documentation.

Each stage should finish with working, reviewable functionality before proceeding. Track progress against this plan as implementation proceeds.
