import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import useResource from '../hooks/useResource'
import useNow from '../hooks/useNow'
import { useAuth } from '../auth/context'
import { api } from '../lib/api'
import { date, money, time } from '../lib/format'
import { Alert, ErrorState, EventArt, Icon, Loading } from '../components/ui'

export default function EventPage() {
  const { id } = useParams()
  const resource = useResource(`/events/${id}`)
  if (resource.loading) return <Loading label="Loading event" />
  if (resource.error) return <ErrorState error={resource.error} retry={resource.reload} />
  return <EventDetails key={id} event={resource.data} reload={resource.reload} />
}

function EventDetails({ event, reload }) {
  const now = useNow()
  const { user, checking } = useAuth()
  const navigate = useNavigate()
  const [quantity, setQuantity] = useState(1)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const started = Date.parse(event.startTime) <= now
  const max = Math.min(10, event.availableSeats)
  const selected = Math.min(quantity, Math.max(1, max))
  async function book() {
    if (!user) { navigate('/login', { state: { from: `/events/${event.id}` } }); return }
    if (Date.parse(event.startTime) <= Date.now()) { setError('This event has already started.'); reload(); return }
    setPending(true); setError('')
    try {
      const booking = await api(`/events/${event.id}/bookings`, { method: 'POST', body: { quantity: selected } })
      navigate('/bookings', { state: { message: `You’re going to ${event.name}! ${booking.quantity} ${booking.quantity === 1 ? 'ticket is' : 'tickets are'} confirmed.` } })
    } catch (failure) {
      setError(failure.status === 409 ? `${failure.message} Availability has been refreshed.` : failure.message)
      if (failure.status === 409) reload()
    } finally { setPending(false) }
  }
  return <><Link className="back-link" to="/events">← All events</Link><div className="detail-layout"><article>
    <EventArt event={event} large /><div className="event-heading"><span className="eyebrow">{started ? 'PAST EVENT' : 'MAKE A DATE OF IT'}</span><h1>{event.name}</h1><div className="detail-meta"><span><Icon name="calendar" />{date(event.startTime, { weekday: 'long' })}</span><span><Icon name="clock" />{time(event.startTime)}</span><span><Icon name="pin" />{event.venue}</span></div></div>
    <section className="event-about"><h2>Something to look forward to.</h2><p className="description">{event.description || 'The date is set. The place is ready. All that’s missing is you.'}</p></section>
  </article><aside className="booking-panel"><span className="eyebrow">YOUR PLACE IN THE CROWD</span><div className="ticket-price">{BigInt(event.ticketPriceCents) === 0n ? 'Free' : money(event.ticketPriceCents)}<span> per ticket</span></div><p className="seat-line"><span className={event.availableSeats > 0 && !started ? 'live-dot' : 'inactive-dot'} />{started ? 'This event has started' : event.availableSeats > 0 ? `${event.availableSeats} tickets available` : 'Sold out'}</p>
    {error && <Alert>{error}</Alert>}
    {!started && max > 0 && <><label className="quantity-label" htmlFor="quantity">How many are coming?</label><div className="quantity-picker"><button aria-label="Remove one ticket" onClick={() => setQuantity(selected - 1)} disabled={selected <= 1 || pending}>−</button><select id="quantity" value={selected} onChange={e => setQuantity(Number(e.target.value))} disabled={pending}>{Array.from({ length: max }, (_, i) => <option value={i + 1} key={i}>{i + 1} {i === 0 ? 'ticket' : 'tickets'}</option>)}</select><button aria-label="Add one ticket" onClick={() => setQuantity(selected + 1)} disabled={selected >= max || pending}>+</button></div><div className="order-total"><span>Total</span><strong>{money(BigInt(event.ticketPriceCents) * BigInt(selected))}</strong></div></>}
    <button className="button button-primary button-full" onClick={book} disabled={pending || started || max === 0 || checking}>{pending ? 'Confirming…' : started ? 'Booking closed' : max === 0 ? 'Sold out' : user ? 'Book tickets' : 'Sign in to book'}{!started && max > 0 && <Icon name="arrow" />}</button>
    <p className="booking-note"><Icon name="check" size={16} />Instant confirmation. No payment required.</p><p className="fine-print">Plans change. You can cancel your booking any time before the event starts.</p>
  </aside></div></>
}
