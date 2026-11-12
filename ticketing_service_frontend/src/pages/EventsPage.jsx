import { useState } from 'react'
import { Link } from 'react-router-dom'
import useResource from '../hooks/useResource'
import { date, money, time } from '../lib/format'
import { Empty, ErrorState, EventArt, Icon, Loading } from '../components/ui'

export default function EventsPage() {
  const { data: events, loading, error, reload } = useResource('/events')
  const [search, setSearch] = useState('')
  const [availableOnly, setAvailableOnly] = useState(false)
  const filtered = (events || []).filter(event => `${event.name} ${event.venue}`.toLowerCase().includes(search.toLowerCase()) && (!availableOnly || event.availableSeats > 0))
  return <>
    <section className="discovery-hero"><div><span className="eyebrow"><span className="live-dot" /> OUT OF THE ORDINARY. INTO YOUR CALENDAR.</span><h1>Good plans.<br /><span>Great memories.</span></h1><p>Live a little. Find your next night out, new perspective,<br className="desktop-break" /> or something worth showing up for.</p><a className="button button-dark" href="#upcoming">Explore what’s on <Icon name="arrow" /></a></div>
      <div className="hero-graphic" aria-hidden="true"><div className="hero-circle" /><div className="hero-ticket"><span>YOUR NEXT<br />GOOD TIME</span><Icon name="ticket" size={68} /><div className="ticket-dashes" /><small>ADMIT YOU + A LITTLE CURIOSITY</small></div><span className="hero-stamp">GO.<br />BE THERE.</span><span className="hero-spark">✳</span></div>
    </section>
    <section id="upcoming" className="events-section"><div className="section-heading"><div><span className="eyebrow">THE CALENDAR IS OPEN</span><h2>Coming up<span className="accent">.</span></h2></div><span className="result-count">{events ? `${events.length} upcoming ${events.length === 1 ? 'event' : 'events'}` : 'Find your next experience'}</span></div>
      <div className="filter-bar"><div className="search-field"><Icon name="search" /><input aria-label="Search events" placeholder="Search events or venues" value={search} onChange={event => setSearch(event.target.value)} />{search && <button className="icon-button" aria-label="Clear search" onClick={() => setSearch('')}><Icon name="close" size={16} /></button>}</div><label className="checkbox-label"><input type="checkbox" checked={availableOnly} onChange={event => setAvailableOnly(event.target.checked)} /> Tickets available</label></div>
      {loading ? <Loading /> : error ? <ErrorState error={error} retry={reload} /> : filtered.length === 0 ? <Empty title={events.length ? 'Nothing matches just yet.' : 'Good things are on their way.'} message={events.length ? 'Try another event name or venue, or change your filters.' : 'Check back soon for upcoming events.'} action={events.length > 0 && <button className="button button-outline" onClick={() => { setSearch(''); setAvailableOnly(false) }}>Clear filters</button>} /> : <div className="event-grid">{filtered.map(event => <article className="event-card" key={event.id}>
        <Link className="event-card-art" to={`/events/${event.id}`} aria-label={`View ${event.name}`}><EventArt event={event} /><span className={`availability ${event.availableSeats === 0 ? 'sold-out' : ''}`}>{event.availableSeats === 0 ? 'Sold out' : event.availableSeats <= 10 ? `Only ${event.availableSeats} left` : 'Tickets available'}</span></Link>
        <div className="event-card-body"><div className="event-date"><Icon name="calendar" size={16} /> {date(event.startTime)} <span>·</span> {time(event.startTime)}</div><h3><Link to={`/events/${event.id}`}>{event.name}</Link></h3><p className="venue"><Icon name="pin" size={16} />{event.venue}</p><div className="event-card-bottom"><span>{BigInt(event.ticketPriceCents) === 0n ? 'Free' : money(event.ticketPriceCents)}<small> / ticket</small></span><Link className="round-link" to={`/events/${event.id}`} aria-label={`Event details for ${event.name}`}><Icon name="arrow" /></Link></div></div>
      </article>)}</div>}
    </section>
    <section className="closing-banner"><Icon name="ticket" size={38} /><div><h2>The best plans start with a yes.</h2><p>Pick your moment. Bring your people. Make a memory.</p></div><span className="banner-spark" aria-hidden="true">✳</span></section>
  </>
}
