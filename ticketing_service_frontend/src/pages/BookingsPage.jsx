import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/context'
import useResource from '../hooks/useResource'
import useNow from '../hooks/useNow'
import { api } from '../lib/api'
import { date, money, time } from '../lib/format'
import { Alert, ConfirmDialog, Empty, ErrorState, Icon, Loading } from '../components/ui'

export default function BookingsPage() {
  const { user } = useAuth()
  return <UserBookings key={user.id} />
}

function UserBookings() {
  const now = useNow()
  const { data: bookings, loading, error, reload } = useResource('/bookings/me')
  const location = useLocation()
  const [message, setMessage] = useState(location.state?.message || '')
  const [failure, setFailure] = useState('')
  const [selected, setSelected] = useState(null)
  const [pending, setPending] = useState(false)
  const [filter, setFilter] = useState('all')
  const shown = (bookings || []).filter(booking => filter === 'all' || (filter === 'cancelled' ? booking.status === 'CANCELLED' : booking.status === 'CONFIRMED' && Date.parse(booking.event.startTime) > now))
  async function cancel() {
    setPending(true); setFailure('')
    try { await api(`/bookings/${selected.id}/cancel`, { method: 'POST' }); setMessage('Booking cancelled. Your tickets have been released.'); setSelected(null); reload() }
    catch (error) { setFailure(error.message); setSelected(null); reload() }
    finally { setPending(false) }
  }
  return <><div className="page-heading"><div><span className="eyebrow">A FEW THINGS TO LOOK FORWARD TO</span><h1>Your plans<span className="accent">.</span></h1><p>All your tickets, in one good place.</p></div><Link to="/events" className="button button-outline">Find another experience <Icon name="arrow" /></Link></div>
    {message && <Alert kind="success" onDismiss={() => setMessage('')}>{message}</Alert>}{failure && <Alert onDismiss={() => setFailure('')}>{failure}</Alert>}
    <div className="tabs" role="group" aria-label="Filter bookings">{[['all', 'All bookings'], ['upcoming', 'Upcoming'], ['cancelled', 'Cancelled']].map(([value, label]) => <button className={filter === value ? 'active' : ''} aria-pressed={filter === value} key={value} onClick={() => setFilter(value)}>{label}</button>)}</div>
    {loading ? <Loading label="Finding your bookings" /> : error ? <ErrorState error={error} retry={reload} /> : shown.length === 0 ? <Empty title="Your next story is waiting." message={bookings.length ? 'No bookings in this view. Try another filter.' : 'Find an experience you love and your tickets will appear here.'} action={<Link className="button button-primary" to="/events">Discover events <Icon name="arrow" /></Link>} /> : <div className="bookings-list">{shown.map(booking => {
      const started = Date.parse(booking.event.startTime) <= now
      return <article className={`booking-card ${booking.status === 'CANCELLED' ? 'booking-cancelled' : ''}`} key={booking.id}><div className="booking-date"><span>{date(booking.event.startTime, { month: 'short', year: undefined, day: undefined })}</span><strong>{new Date(booking.event.startTime).getDate()}</strong></div><div className="booking-info"><span className={`status status-${booking.status.toLowerCase()}`}>{booking.status === 'CANCELLED' ? 'Cancelled' : started ? 'Past event' : 'Confirmed'}</span><h2><Link to={`/events/${booking.event.id}`}>{booking.event.name}</Link></h2><p><Icon name="pin" size={16} />{booking.event.venue} <span>·</span> {time(booking.event.startTime)}</p><small>Booking #{booking.id} · {booking.quantity} {booking.quantity === 1 ? 'ticket' : 'tickets'} × {money(booking.unitPriceCents)}</small></div><div className="booking-summary"><strong>{money(booking.totalPriceCents)}</strong><small>Total</small>{booking.status === 'CONFIRMED' && !started && <button className="text-button danger-text" onClick={() => { setFailure(''); setSelected(booking) }}>Cancel booking</button>}</div></article>
    })}</div>}
    {selected && <ConfirmDialog title="Let these plans go?" confirmLabel="Cancel booking" busy={pending} onClose={() => setSelected(null)} onConfirm={cancel}><p>Your {selected.quantity} {selected.quantity === 1 ? 'ticket' : 'tickets'} for <strong>{selected.event.name}</strong> will be released. You can book again if seats remain.</p></ConfirmDialog>}
  </>
}
