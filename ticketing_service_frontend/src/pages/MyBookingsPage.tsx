import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api, errorMessage } from '../api/client'
import type { BookingResponse } from '../api/types'
import { formatDateTime, isPast } from '../format'

export function MyBookingsPage() {
  const [bookings, setBookings] = useState<BookingResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cancellingId, setCancellingId] = useState<number | null>(null)

  useEffect(() => {
    api.myBookings().then(setBookings, (e) => setError(errorMessage(e)))
  }, [])

  async function cancel(booking: BookingResponse) {
    if (!confirm(`Cancel ${booking.quantity} ticket(s) for ${booking.eventName}?`)) return
    setCancellingId(booking.id)
    setError(null)
    try {
      const updated = await api.cancelBooking(booking.id)
      setBookings((current) => current?.map((b) => (b.id === updated.id ? updated : b)) ?? null)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setCancellingId(null)
    }
  }

  if (!bookings) return error ? <p className="error">{error}</p> : <p className="muted">Loading bookings…</p>

  return (
    <>
      <h1>My bookings</h1>
      {error && <p className="error">{error}</p>}
      {bookings.length === 0 ? (
        <p className="muted">
          You have no bookings yet. <Link to="/">Browse events</Link>
        </p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Event</th>
                <th>When</th>
                <th>Tickets</th>
                <th>Status</th>
                <th>Booked</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {bookings.map((booking) => (
                <tr key={booking.id}>
                  <td>
                    <Link to={`/events/${booking.eventId}`}>{booking.eventName}</Link>
                    <div className="muted small">{booking.eventVenue}</div>
                  </td>
                  <td>{formatDateTime(booking.eventStartsAt)}</td>
                  <td>{booking.quantity}</td>
                  <td>
                    <span className={booking.status === 'CONFIRMED' ? 'badge badge-ok' : 'badge'}>
                      {booking.status === 'CONFIRMED' ? 'Confirmed' : 'Cancelled'}
                    </span>
                  </td>
                  <td className="muted">{formatDateTime(booking.createdAt)}</td>
                  <td className="actions">
                    {booking.status === 'CONFIRMED' && !isPast(booking.eventStartsAt) && (
                      <button
                        type="button"
                        className="secondary"
                        disabled={cancellingId === booking.id}
                        onClick={() => cancel(booking)}
                      >
                        {cancellingId === booking.id ? 'Cancelling…' : 'Cancel'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
