import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import useResource from '../hooks/useResource'
import useNow from '../hooks/useNow'
import { api } from '../lib/api'
import { date, money, time } from '../lib/format'
import { Alert, ConfirmDialog, Empty, ErrorState, Icon, Loading } from '../components/ui'

export default function AdminPage() {
  const now = useNow()
  const { data: events, loading, error, reload } = useResource('/events?includePast=true')
  const location = useLocation()
  const [filter, setFilter] = useState('all')
  const [search, setSearch] = useState('')
  const [message, setMessage] = useState(location.state?.message || '')
  const [failure, setFailure] = useState('')
  const [selected, setSelected] = useState(null)
  const [pending, setPending] = useState(false)
  const upcoming = (events || []).filter(event => Date.parse(event.startTime) > now)
  const shown = (events || []).filter(event => `${event.name} ${event.venue}`.toLowerCase().includes(search.toLowerCase()) && (filter === 'all' || (filter === 'upcoming' ? Date.parse(event.startTime) > now : Date.parse(event.startTime) <= now)))
  async function remove() {
    setPending(true); setFailure('')
    try { await api(`/events/${selected.id}`, { method: 'DELETE' }); setMessage('Event deleted.'); setSelected(null); reload() }
    catch (error) { setFailure(error.message); setSelected(null); reload() }
    finally { setPending(false) }
  }
  return <><div className="page-heading"><div><span className="eyebrow">THE ORGANIZER’S CORNER</span><h1>Make it happen<span className="accent">.</span></h1><p>Your events. Their next great experience.</p></div><Link className="button button-primary" to="/admin/events/new"><Icon name="plus" />Create event</Link></div>
    <div className="stats-grid"><div><span>All events</span><strong>{events?.length ?? '—'}</strong></div><div><span>Coming up</span><strong>{events ? upcoming.length : '—'}</strong></div><div><span>Tickets available · upcoming</span><strong>{events ? upcoming.reduce((sum, event) => sum + BigInt(event.availableSeats), 0n).toLocaleString() : '—'}</strong></div></div>
    {message && <Alert kind="success" onDismiss={() => setMessage('')}>{message}</Alert>}{failure && <Alert onDismiss={() => setFailure('')}>{failure}</Alert>}
    <div className="admin-toolbar"><div className="tabs" role="group" aria-label="Filter events">{[['all', 'All events'], ['upcoming', 'Upcoming'], ['past', 'Past events']].map(([value, label]) => <button className={filter === value ? 'active' : ''} aria-pressed={filter === value} key={value} onClick={() => setFilter(value)}>{label}</button>)}</div><div className="search-field"><Icon name="search" /><input aria-label="Search managed events" placeholder="Search your events" value={search} onChange={e => setSearch(e.target.value)} /></div></div>
    {loading ? <Loading label="Loading your events" /> : error ? <ErrorState error={error} retry={reload} /> : shown.length === 0 ? <Empty title="Room for something great." message={events.length ? 'No events match this view.' : 'Create your first event and give people something to look forward to.'} action={<Link to="/admin/events/new" className="button button-primary">Create event <Icon name="plus" /></Link>} /> : <div className="table-wrap"><table><thead><tr><th scope="col">Event</th><th scope="col">When</th><th scope="col">Availability</th><th scope="col">Ticket price</th><th scope="col">Actions</th></tr></thead><tbody>{shown.map(event => <tr key={event.id}><td><Link className="table-event-name" to={`/events/${event.id}`}>{event.name}</Link><small>{event.venue}</small><span className={`status ${Date.parse(event.startTime) > now ? 'status-confirmed' : 'status-past'}`}>{Date.parse(event.startTime) > now ? 'Upcoming' : 'Past event'}</span></td><td>{date(event.startTime)}<small>{time(event.startTime)}</small></td><td><strong>{event.availableSeats}</strong> / {event.capacity}<small>tickets available</small></td><td>{money(event.ticketPriceCents)}</td><td><div className="table-actions"><Link className="button button-outline button-small" to={`/admin/events/${event.id}/edit`} aria-label={`Edit ${event.name}`}><Icon name="edit" size={16} />Edit</Link><button className="text-button danger-text" onClick={() => { setFailure(''); setSelected(event) }} aria-label={`Delete ${event.name}`}>Delete</button></div></td></tr>)}</tbody></table></div>}
    <p className="fine-print admin-footnote">Events with booking history are kept for everyone’s records and cannot be deleted, even if all bookings are cancelled.</p>
    {selected && <ConfirmDialog title="Delete this event?" confirmLabel="Delete event" busy={pending} onClose={() => setSelected(null)} onConfirm={remove}><p><strong>{selected.name}</strong> will be removed. This is permanent and only possible if the event has no booking history.</p></ConfirmDialog>}
  </>
}
