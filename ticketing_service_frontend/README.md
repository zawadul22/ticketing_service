# Gather frontend

React + Vite frontend for the ticketing service. Includes public discovery and event details, registration/login, personal bookings and cancellation, and admin event management. The application uses the real `/api` endpoints; sample records exist only in browser tests.

## Run locally

Start the Spring Boot backend on port 8080 with the existing PostgreSQL connection. See `../ticketing_service_backend/README.md` for backend setup and optional admin account creation.

From `ticketing_service_frontend`:

```powershell
npm.cmd ci
npm.cmd run dev
```

Open the URL printed by Vite, normally http://localhost:5173. Both the development server and `npm.cmd run preview` proxy `/api` to http://localhost:8080. If the backend port changes, update the proxy targets in `vite.config.js`. No new database container is required.

## Pages

| Path | Purpose |
|---|---|
| `/events` | Browse upcoming events, search names/venues, filter ticket availability |
| `/events/:id` | Event details, ticket quantity, booking; conflicts refresh availability |
| `/login`, `/register` | Authenticate; return to the requested protected page after login |
| `/bookings` | Current user's bookings, price snapshots/totals, cancellation confirmation |
| `/admin` | Admin event listing, search, upcoming/past filters, deletion |
| `/admin/events/new` | Admin event creation |
| `/admin/events/:id/edit` | Admin event editing with local date/time conversion |

Backend permissions apply independently of frontend route guards. Registering creates a regular user; use the backend's configured admin setup for an admin account. Tickets are confirmed without payment.

## Sessions and prices

The header theme toggle switches between light and dark mode. The first visit follows the system preference; an explicit selection is saved in local storage and applies across routes, reloads, and tabs. Theme initialization runs before rendering to avoid a light flash when dark mode is selected.

The browser stores the JWT session in local storage, restores the user through `/api/auth/me`, and clears expired/rejected sessions. Logout removes the local session; it does not revoke a copied token on the server. Session changes synchronize between tabs.

Dates display in the browser's timezone. Forms convert local date/time to UTC and preserve an unchanged event timestamp exactly when editing. Capacity edits cannot reduce below current reservations; the backend checks again for concurrent changes.

Prices are stored as integer cents. `VITE_CURRENCY` controls the display currency (default `USD`); the backend does not specify a currency. Set it in `.env.local` before starting/building, for example:

```text
VITE_CURRENCY=USD
```

The UI preserves large integer prices and totals returned by modern browsers' JSON parsing. The admin price form accepts amounts with at most two decimal places and rejects values outside JavaScript's safe-integer cent range. Google Fonts enhance the typography; system sans-serif fonts are used when unavailable.

## Verification

```powershell
npm.cmd run build
npm.cmd run lint
npm.cmd run test:e2e
```

Browser tests run on desktop and mobile viewport sizes. On Windows, they use installed Microsoft Edge. Elsewhere, install the Chromium browser with `npx.cmd playwright install chromium` (use `npx` outside Windows); `PLAYWRIGHT_CHANNEL` optionally selects another installed browser channel.

The standard suite contains 20 deterministic browser checks using controlled API responses. They cover registration, session restoration/logout/expiry, protected routes, discovery/search, sold-out and error states, booking/cancellation, seat conflicts, admin CRUD, deletion restrictions, responsive layout, and light/dark theme persistence and system preferences. They do not modify the database.

To include two additional read-only checks against the running Spring Boot/PostgreSQL API through Vite:

```powershell
$env:LIVE_API = 'true'
npm.cmd run test:e2e
```

Verified on 2026-11-12: production build, lint, and all 18 desktop/mobile browser checks passed. Live checks verify health and actual public event responses. Authenticated browser mutation flows use controlled API responses; the backend's separate 73-test PostgreSQL suite verifies real authentication, transactional mutations, permissions, concurrency, rollback, and connected user/admin HTTP flows.

Playwright starts its own Vite server on port 5174 and stops it afterwards. Screenshots/traces are written to ignored `test-results/`. The source checkout is local; deployment remains outside the project scope.
