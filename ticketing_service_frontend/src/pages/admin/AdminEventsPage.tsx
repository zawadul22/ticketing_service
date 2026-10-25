import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api, errorMessage } from '../../api/client'
import type { EventResponse } from '../../api/types'
import { formatDateTime, formatPrice, isPast } from '../../format'

export function AdminEventsPage() {
  const [events, setEvents] = useState<EventResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.listEvents(true).then(setEvents, (e) => setError(errorMessage(e)))
  }, [])

  async function remove(event: EventResponse) {
    if (!confirm(`Delete "${event.name}"? This cannot be undone.`)) return
    setError(null)
    try {
      await api.deleteEvent(event.id)
      setEvents((current) => current?.filter((e) => e.id !== event.id) ?? null)
    } catch (err) {
      // 409 when the event already has bookings
      setError(errorMessage(err))
    }
  }

  if (!events) return error ? <p className="error">{error}</p> : <p className="muted">Loading events…</p>

  return (
    <>
      <div className="page-header">
        <h1>Manage events</h1>
        <Link to="/admin/events/new" className="button">
          New event
        </Link>
      </div>
      {error && <p className="error">{error}</p>}
      {events.length === 0 ? (
        <p className="muted">No events yet.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Event</th>
                <th>When</th>
                <th>Seats sold</th>
                <th>Price</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {events.map((event) => {
                const past = isPast(event.startsAt)
                return (
                  <tr key={event.id} className={past ? 'past' : undefined}>
                    <td>
                      <Link to={`/events/${event.id}`}>{event.name}</Link>
                      <div className="muted small">{event.venue}</div>
                    </td>
                    <td>
                      {formatDateTime(event.startsAt)}
                      {past && <div className="muted small">Past</div>}
                    </td>
                    <td>
                      {event.capacity - event.availableSeats} / {event.capacity}
                    </td>
                    <td>{formatPrice(event.priceCents)}</td>
                    <td className="actions">
                      {!past && (
                        <Link to={`/admin/events/${event.id}/edit`} className="button secondary">
                          Edit
                        </Link>
                      )}
                      <button type="button" className="danger" onClick={() => remove(event)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
