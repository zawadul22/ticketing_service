import { expect, test } from '@playwright/test'

const event = { id: 11, name: 'An Evening of Jazz', description: 'An intimate evening of live music with your favorite people.', venue: 'The Garden Hall', startTime: '2099-06-15T18:30:00Z', capacity: 40, availableSeats: 12, ticketPriceCents: 2500 }
const user = { id: 1, email: 'person@example.com', role: 'USER', createdAt: '2026-01-01T00:00:00Z' }

async function setup(page, { signedIn = false, role = 'USER', conflict = false, deletionConflict = false, unavailable = false } = {}) {
  let events = [structuredClone(event), { ...event, id: 12, name: 'A New Perspective', venue: 'City Studio', availableSeats: 0 }]
  const currentUser = { ...user, role }
  const session = { accessToken: 'browser-test-token', expiresAt: '2099-01-01T00:00:00Z', tokenType: 'Bearer', user: currentUser }
  let bookings = []
  let conflictPending = conflict
  const requests = []
  if (signedIn) await page.addInitScript(session => localStorage.setItem('gather.session', JSON.stringify(session)), session)
  await page.route('**/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api', '')
    const method = request.method()
    requests.push({ path, method, authorization: request.headers().authorization, body: request.postDataJSON() })
    const reply = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: status === 204 ? undefined : JSON.stringify(data) })
    const error = (message, status) => reply({ status, message, fieldErrors: {} }, status)
    if (path === '/auth/me') return reply(currentUser)
    if (path === '/auth/login' || path === '/auth/register') return reply(session, path.endsWith('register') ? 201 : 200)
    if (path === '/events' && method === 'GET') {
      if (unavailable) return error('The event service is temporarily unavailable.', 503)
      return reply(events)
    }
    if (path === '/events' && method === 'POST') {
      const created = { ...request.postDataJSON(), id: 13, availableSeats: request.postDataJSON().capacity }
      events.push(created)
      return reply(created, 201)
    }
    if (path.match(/^\/events\/\d+$/)) {
      const id = Number(path.split('/')[2])
      const current = events.find(event => event.id === id)
      if (!current) return error('Event not found', 404)
      if (method === 'DELETE') {
        if (deletionConflict) return error('Events with booking history cannot be deleted', 409)
        events = events.filter(event => event.id !== id)
        return reply(null, 204)
      }
      if (method === 'PUT') { Object.assign(current, request.postDataJSON()); current.availableSeats = current.capacity; return reply(current) }
      return reply(current)
    }
    if (path.match(/^\/events\/\d+\/bookings$/)) {
      if (conflictPending) { conflictPending = false; events[0].availableSeats = 1; return error('Seats are insufficient', 409) }
      const quantity = request.postDataJSON().quantity
      events[0].availableSeats -= quantity
      const booking = { id: 21, event: events[0], quantity, unitPriceCents: 2500, totalPriceCents: 2500 * quantity, status: 'CONFIRMED', createdAt: '2026-01-01T00:00:00Z' }
      bookings.unshift(booking)
      return reply(booking, 201)
    }
    if (path === '/bookings/me') return reply(bookings)
    if (path === '/bookings/21/cancel') { bookings[0].status = 'CANCELLED'; events[0].availableSeats += bookings[0].quantity; return reply(bookings[0]) }
    return error('Not found', 404)
  })
  return requests
}

test('discovery search, availability filter, event details, and responsive layout', async ({ page }) => {
  await setup(page)
  await page.goto('/events')
  await expect(page.getByRole('heading', { name: 'Good plans. Great memories.' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'An Evening of Jazz' })).toBeVisible()
  await page.getByRole('checkbox', { name: 'Tickets available' }).check()
  await expect(page.getByRole('heading', { name: 'A New Perspective' })).toHaveCount(0)
  await page.getByRole('textbox', { name: 'Search events' }).fill('does not exist')
  await expect(page.getByText('Nothing matches just yet.')).toBeVisible()
  await page.getByRole('button', { name: 'Clear filters' }).click()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  await page.screenshot({ path: `test-results/discovery-${test.info().project.name}.png`, fullPage: true })
  await page.getByRole('heading', { name: 'An Evening of Jazz' }).getByRole('link').click()
  await expect(page.getByRole('heading', { name: 'An Evening of Jazz' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Sign in to book' })).toBeVisible()
  await page.goBack()
  await expect(page.getByRole('heading', { name: 'Good plans. Great memories.' })).toBeVisible()
})

test('sign-in redirect, booking, confirmation, and cancellation', async ({ page }) => {
  const requests = await setup(page)
  await page.goto('/events/11')
  await page.getByRole('button', { name: 'Sign in to book' }).click()
  await page.getByLabel('Email address').fill(user.email)
  await page.getByLabel('Password', { exact: true }).fill('Test-password-123')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL(/\/events\/11$/)
  await page.getByLabel('How many are coming?').selectOption('2')
  await page.getByRole('button', { name: 'Book tickets' }).click()
  await expect(page.locator('.alert-success')).toContainText('2 tickets are confirmed')
  await expect(page.getByRole('heading', { name: 'An Evening of Jazz' })).toBeVisible()
  await page.getByRole('button', { name: 'Cancel booking', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel booking' }).click()
  await expect(page.locator('.alert-success')).toContainText('Booking cancelled')
  await expect(page.locator('.booking-card').getByText('Cancelled', { exact: true })).toBeVisible()
  expect(requests.find(request => request.path === '/events/11/bookings').body).toEqual({ quantity: 2 })
  expect(requests.find(request => request.path === '/events/11/bookings').authorization).toBe('Bearer browser-test-token')
})

test('register, session persistence, logout, and route protection', async ({ page }) => {
  await setup(page)
  await page.goto('/bookings')
  await expect(page).toHaveURL(/\/login$/)
  await page.getByRole('link', { name: 'Create an account' }).click()
  await page.getByLabel('Email address').fill(user.email)
  await page.getByLabel('Password', { exact: true }).fill('Test-password-123')
  await page.getByRole('button', { name: 'Create account', exact: true }).click()
  await expect(page).toHaveURL(/\/bookings$/)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Your plans.' })).toBeVisible()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('link', { name: 'Sign in', exact: true })).toBeVisible()
  await page.goto('/admin')
  await expect(page).toHaveURL(/\/login$/)
})

test('refreshes seat availability after a booking conflict', async ({ page }) => {
  await setup(page, { signedIn: true, conflict: true })
  await page.goto('/events/11')
  await page.getByLabel('How many are coming?').selectOption('2')
  await page.getByRole('button', { name: 'Book tickets' }).click()
  await expect(page.getByRole('alert')).toContainText('Availability has been refreshed')
  await expect(page.getByLabel('How many are coming?')).toHaveValue('1')
  await expect(page.getByText('1 tickets available')).toBeVisible()
})

test('handles server errors, missing records, and sold out events', async ({ page }) => {
  await setup(page, { unavailable: true })
  await page.goto('/events')
  await expect(page.getByRole('heading', { name: 'A little interruption.' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Try again' })).toBeVisible()
  await page.goto('/events/999')
  await expect(page.getByRole('heading', { name: 'We couldn’t find that event.' })).toBeVisible()
  await page.goto('/events/12')
  await expect(page.getByRole('button', { name: 'Sold out' })).toBeDisabled()
})

test('regular users cannot open admin screens and expired sessions are cleared', async ({ page }) => {
  await setup(page, { signedIn: true })
  await page.goto('/admin')
  await expect(page.getByRole('heading', { name: 'This space is for event organizers.' })).toBeVisible()
  await page.route('**/api/bookings/me', route => route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ message: 'Token invalid' }) }))
  await page.goto('/bookings')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('alert')).toContainText('Your session has expired')
  expect(await page.evaluate(() => localStorage.getItem('gather.session'))).toBeNull()
})

test('admin creates, edits, and deletes events with timezone-safe forms', async ({ page }) => {
  const requests = await setup(page, { signedIn: true, role: 'ADMIN' })
  await page.goto('/admin')
  await page.getByRole('link', { name: 'Create event', exact: true }).click()
  await page.getByLabel('Event name').fill('Community gathering')
  await page.getByLabel('Description (optional)').fill('A new experience for everyone.')
  await page.getByLabel('Venue', { exact: true }).fill('Community Hall')
  await page.getByLabel('Start date and time').fill('2099-07-01T18:00')
  await page.getByLabel('Capacity', { exact: true }).fill('50')
  await page.getByLabel(/Price per ticket/).fill('12.50')
  await page.getByRole('button', { name: 'Create event', exact: true }).click()
  await expect(page.locator('.alert-success')).toContainText('Event created')
  await page.getByRole('link', { name: 'Edit Community gathering', exact: true }).click()
  await page.getByLabel('Event name').fill('Community gathering updated')
  await page.getByRole('button', { name: 'Save changes' }).click()
  await expect(page.locator('.alert-success')).toContainText('Event updated')
  await page.getByRole('button', { name: 'Delete Community gathering updated', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Delete event' }).click()
  await expect(page.locator('.alert-success')).toContainText('Event deleted')
  const created = requests.find(request => request.method === 'POST' && request.path === '/events').body
  expect(created.ticketPriceCents).toBe(1250)
  expect(created.capacity).toBe(50)
  expect(created.startTime).toMatch(/Z$/)
})

test('admin deletion restrictions are displayed clearly', async ({ page }) => {
  await setup(page, { signedIn: true, role: 'ADMIN', deletionConflict: true })
  await page.goto('/admin')
  await page.getByRole('button', { name: 'Delete An Evening of Jazz', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Delete event' }).click()
  await expect(page.getByRole('alert')).toContainText('Events with booking history cannot be deleted')
  await expect(page.getByRole('link', { name: 'An Evening of Jazz', exact: true })).toBeVisible()
})

test('dark theme follows the system, toggles, and persists across pages and reloads', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' })
  await setup(page, { signedIn: true, role: 'ADMIN' })
  await page.goto('/events')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await expect(page.locator('html')).toHaveCSS('background-color', 'rgb(23, 28, 24)')
  await page.getByRole('button', { name: 'Switch to light mode' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.getByRole('button', { name: 'Switch to dark mode' }).click()
  await expect(page.getByRole('button', { name: 'Switch to light mode' })).toHaveAttribute('aria-pressed', 'true')
  await page.screenshot({ path: `test-results/dark-discovery-${test.info().project.name}.png`, fullPage: true })
  await page.getByRole('link', { name: 'Manage events', exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await expect(page.getByRole('table')).toBeVisible()
  await page.getByRole('link', { name: 'Create event', exact: true }).click()
  await expect(page.getByLabel('Event name')).toBeVisible()
  await expect(page.getByLabel('Event name')).toHaveCSS('background-color', 'rgb(32, 39, 32)')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  await page.screenshot({ path: `test-results/dark-form-${test.info().project.name}.png`, fullPage: true })
})

test('theme responds to system changes until a preference is saved', async ({ page }) => {
  await setup(page)
  await page.emulateMedia({ colorScheme: 'light' })
  await page.goto('/login')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.emulateMedia({ colorScheme: 'dark' })
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await expect(page.getByLabel('Email address')).toHaveCSS('background-color', 'rgb(32, 39, 32)')
  await page.getByRole('button', { name: 'Switch to light mode' }).click()
  await page.emulateMedia({ colorScheme: 'light' })
  await page.emulateMedia({ colorScheme: 'dark' })
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
})
