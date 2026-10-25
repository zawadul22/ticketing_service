import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api, errorMessage } from '../api/client'
import type { EventResponse } from '../api/types'
import { SeatsBadge } from '../components/SeatsBadge'
import { formatDateTime, formatPrice } from '../format'

export function EventsPage() {
  const [events, setEvents] = useState<EventResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.listEvents().then(setEvents, (e) => setError(errorMessage(e)))
  }, [])

  if (error) return <p className="error">{error}</p>
  if (!events) return <p className="muted">Loading events…</p>

  return (
    <>
      <h1>Upcoming events</h1>
      {events.length === 0 ? (
        <p className="muted">No upcoming events yet.</p>
      ) : (
        <ul className="event-grid">
          {events.map((event) => (
            <li key={event.id}>
              <Link to={`/events/${event.id}`} className="card event-card">
                <h2>{event.name}</h2>
                <p className="muted">
                  {formatDateTime(event.startsAt)} · {event.venue}
                </p>
                <div className="event-card-footer">
                  <strong>{formatPrice(event.priceCents)}</strong>
                  <SeatsBadge available={event.availableSeats} />
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
