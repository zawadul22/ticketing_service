import type {
  AuthResponse,
  BookingResponse,
  EventRequest,
  EventResponse,
  ProblemDetail,
} from './types'

export class ApiError extends Error {
  status: number
  // Per-field messages from a 400 validation failure, keyed by request field name
  fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Could not reach the server'
}

// Set by AuthProvider, so the client stays free of React
let authToken: string | null = null
let onUnauthorized: (() => void) | null = null

export function setAuthToken(token: string | null) {
  authToken = token
}

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (authToken) headers.Authorization = `Bearer ${authToken}`

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (!response.ok) {
    const problem: ProblemDetail = await response.json().catch(() => ({}))
    // A 401 while holding a token means it expired or was rejected; a failed login has no token yet
    if (response.status === 401 && authToken) onUnauthorized?.()
    // Validation failures carry per-field messages; their generic detail ("Invalid request content.") says little
    const message = problem.errors
      ? 'Please fix the highlighted fields.'
      : (problem.detail ?? problem.title ?? `Request failed (${response.status})`)
    throw new ApiError(response.status, message, problem.errors)
  }
  if (response.status === 204) return undefined as T
  return response.json()
}

export const api = {
  register: (email: string, password: string) =>
    request<AuthResponse>('POST', '/api/auth/register', { email, password }),
  login: (email: string, password: string) =>
    request<AuthResponse>('POST', '/api/auth/login', { email, password }),

  listEvents: (includePast = false) =>
    request<EventResponse[]>('GET', includePast ? '/api/events?includePast=true' : '/api/events'),
  getEvent: (id: string | number) => request<EventResponse>('GET', `/api/events/${id}`),
  createEvent: (event: EventRequest) => request<EventResponse>('POST', '/api/events', event),
  updateEvent: (id: string | number, event: EventRequest) =>
    request<EventResponse>('PUT', `/api/events/${id}`, event),
  deleteEvent: (id: number) => request<void>('DELETE', `/api/events/${id}`),

  book: (eventId: number, quantity: number) =>
    request<BookingResponse>('POST', `/api/events/${eventId}/bookings`, { quantity }),
  myBookings: () => request<BookingResponse[]>('GET', '/api/bookings/me'),
  cancelBooking: (id: number) => request<BookingResponse>('POST', `/api/bookings/${id}/cancel`),
}
