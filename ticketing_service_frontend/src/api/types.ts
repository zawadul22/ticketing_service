// Mirrors the backend DTO records; Instants arrive as ISO-8601 strings

export type Role = 'USER' | 'ADMIN'

export interface UserResponse {
  id: number
  email: string
  role: Role
  createdAt: string
}

export interface AuthResponse {
  token: string
  expiresAt: string
  user: UserResponse
}

export interface EventResponse {
  id: number
  name: string
  description: string | null
  venue: string
  startsAt: string
  capacity: number
  availableSeats: number
  priceCents: number
  createdAt: string
}

export interface EventRequest {
  name: string
  description: string | null
  venue: string
  startsAt: string
  capacity: number
  priceCents: number
}

export type BookingStatus = 'CONFIRMED' | 'CANCELLED'

export interface BookingResponse {
  id: number
  quantity: number
  status: BookingStatus
  createdAt: string
  eventId: number
  eventName: string
  eventVenue: string
  eventStartsAt: string
}

export interface ProblemDetail {
  title?: string
  status?: number
  detail?: string
  errors?: Record<string, string>
}
