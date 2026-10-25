import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { api, errorMessage } from '../api/client'
import type { BookingResponse, EventResponse } from '../api/types'
import { useAuth } from '../auth/context'
import { SeatsBadge } from '../components/SeatsBadge'
import { formatDateTime, formatPrice, isPast } from '../format'

// Matches BookingRequest's @Max(10)
const MAX_PER_BOOKING = 10

export function EventDetailPage() {
  const { id } = useParams()
  const [event, setEvent] = useState<EventResponse | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    api.getEvent(id!).then(setEvent, (e) => setLoadError(errorMessage(e)))
  }, [id])

  if (loadError) return <p className="error">{loadError}</p>
  if (!event) return <p className="muted">Loading event…</p>

  return (
    <>
      <p>
        <Link to="/">← All events</Link>
      </p>
      <div className="card">
        <h1>{event.name}</h1>
        <dl className="details">
          <dt>When</dt>
          <dd>{formatDateTime(event.startsAt)}</dd>
          <dt>Where</dt>
          <dd>{event.venue}</dd>
          <dt>Price</dt>
          <dd>{formatPrice(event.priceCents)}</dd>
          <dt>Seats</dt>
          <dd>
            <SeatsBadge available={event.availableSeats} /> <span className="muted">of {event.capacity}</span>
          </dd>
        </dl>
        {event.description && <p className="description">{event.description}</p>}
      </div>
      <BookingPanel event={event} onBooked={setEvent} />
    </>
  )
}

function BookingPanel({ event, onBooked }: { event: EventResponse; onBooked: (event: EventResponse) => void }) {
  const { user } = useAuth()
  const location = useLocation()
  const [quantity, setQuantity] = useState(1)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [booking, setBooking] = useState<BookingResponse | null>(null)

  if (isPast(event.startsAt)) return <p className="notice">This event has already started.</p>
  if (!user) {
    return (
      <p className="notice">
        <Link to="/login" state={{ from: location.pathname }}>
          Log in
        </Link>{' '}
        to book tickets.
      </p>
    )
  }
  if (user.role === 'ADMIN') {
    return (
      <p className="notice">
        Admins can't book tickets. <Link to={`/admin/events/${event.id}/edit`}>Edit this event</Link>
      </p>
    )
  }

  const maxQuantity = Math.min(MAX_PER_BOOKING, event.availableSeats)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    setBooking(null)
    try {
      setBooking(await api.book(event.id, quantity))
      setQuantity(1)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
      // Refresh seats either way: a 409 usually means someone else just took them
      api.getEvent(event.id).then(onBooked, () => {})
    }
  }

  return (
    <div className="card">
      <h2>Book tickets</h2>
      {booking && (
        <p className="success">
          Booked {booking.quantity} ticket{booking.quantity > 1 && 's'}. <Link to="/bookings">View my bookings</Link>
        </p>
      )}
      {error && <p className="error">{error}</p>}
      {maxQuantity === 0 ? (
        <p className="muted">This event is sold out.</p>
      ) : (
        <form className="inline-form" onSubmit={handleSubmit}>
          <label>
            Quantity{' '}
            <select value={quantity} onChange={(e) => setQuantity(Number(e.target.value))}>
              {Array.from({ length: maxQuantity }, (_, i) => i + 1).map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </label>
          <span className="muted">Total {formatPrice(event.priceCents * quantity)}</span>
          <button type="submit" disabled={submitting}>
            {submitting ? 'Booking…' : 'Book'}
          </button>
        </form>
      )}
    </div>
  )
}
