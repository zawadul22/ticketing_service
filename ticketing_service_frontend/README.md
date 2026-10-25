# Ticketing frontend

React 19 + TypeScript + Vite UI for the ticketing backend in `../ticketing_service`.

## Run

1. Start the backend (`./gradlew bootRun` in `ticketing_service/`, port 8080).
2. `npm install`, then `npm run dev` and open http://localhost:5173.

Vite proxies `/api` to `http://localhost:8080`, so the backend needs no CORS setup.
The seeded admin is `admin@ticketing.local` / `admin12345` (from `application.properties`).

## Structure

- `src/api/`: `client.ts` (fetch wrapper: Bearer token, ProblemDetail errors, sign-out on 401) and `types.ts` (mirrors the backend DTOs)
- `src/auth/`: `AuthProvider` (session in `localStorage`, auto sign-out at token expiry), `useAuth`, `RequireAuth`
- `src/pages/`: events list and detail (booking), login/register, my bookings, `admin/` event list and create/edit form
- `src/index.css`: all styles, plain CSS with light and dark themes
