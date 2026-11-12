import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import useResource from '../hooks/useResource'
import { api } from '../lib/api'
import { CURRENCY, decimalPrice, priceToCents, toLocalInput } from '../lib/format'
import { Alert, ErrorState, Field, Icon, Loading } from '../components/ui'

export default function EventFormPage() {
  const { id } = useParams()
  return id ? <EditEvent key={id} id={id} /> : <EventForm key="new" />
}

function EditEvent({ id }) {
  const { data, error, loading, reload } = useResource(`/events/${id}`)
  if (loading) return <Loading label="Loading event" />
  if (error) return <ErrorState error={error} retry={reload} />
  return <EventForm event={data} />
}

function EventForm({ event }) {
  const navigate = useNavigate()
  const [values, setValues] = useState(() => ({ name: event?.name || '', description: event?.description || '', venue: event?.venue || '',
    startTime: event ? toLocalInput(event.startTime) : '', capacity: event?.capacity ?? '', price: event ? decimalPrice(event.ticketPriceCents) : '' }))
  const [error, setError] = useState(null)
  const [pending, setPending] = useState(false)
  const reserved = event ? event.capacity - event.availableSeats : 0
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
  function update(e) { setValues(previous => ({ ...previous, [e.target.name]: e.target.value })) }
  async function submit(e) {
    e.preventDefault(); setError(null)
    let body
    try {
      if (!values.name.trim() || !values.venue.trim()) throw new Error('Please enter an event name and venue.')
      const unchangedTime = event && values.startTime === toLocalInput(event.startTime)
      const startTime = unchangedTime ? event.startTime : new Date(values.startTime).toISOString()
      if (!unchangedTime && Date.parse(startTime) <= Date.now()) throw new Error('Choose a future start time.')
      const capacity = Number(values.capacity)
      if (!Number.isInteger(capacity) || capacity < 1 || capacity > 2147483647) throw new Error('Capacity must be a positive whole number up to 2,147,483,647.')
      if (capacity < reserved) throw new Error(`Capacity cannot be less than ${reserved} reserved tickets.`)
      body = { name: values.name.trim(), description: values.description.trim() || null, venue: values.venue.trim(),
        startTime, capacity, ticketPriceCents: priceToCents(values.price) }
    } catch (failure) { setError(failure); return }
    setPending(true)
    try {
      await api(event ? `/events/${event.id}` : '/events', { method: event ? 'PUT' : 'POST', body })
      navigate('/admin', { state: { message: event ? 'Event updated. Looking good!' : 'Event created. Let the good times begin.' } })
    } catch (failure) { setError(failure) }
    finally { setPending(false) }
  }
  function input(name, props = {}) {
    const fieldName = name === 'price' ? 'ticketPriceCents' : name
    return <input id={name} name={name} value={values[name]} onChange={update} required aria-invalid={Boolean(error?.fieldErrors?.[fieldName])} aria-describedby={error?.fieldErrors?.[fieldName] ? `${name}-error` : props['aria-describedby']} {...props} />
  }
  return <><Link className="back-link" to="/admin">← Manage events</Link><div className="page-heading"><div><span className="eyebrow">MAKE A LITTLE MAGIC</span><h1>{event ? 'Fine-tune the plans.' : 'Set the scene.'}</h1><p>{event ? 'Keep your event details up to date.' : 'Give people something to look forward to.'}</p></div></div>
    <div className="event-form-layout"><form className="event-form" onSubmit={submit}>
      {error && <Alert>{error.message}</Alert>}
      <h2>The essentials</h2>
      <Field id="name" label="Event name" error={error?.fieldErrors?.name}>{input('name', { maxLength: 255, placeholder: 'A name worth remembering' })}</Field>
      <Field id="description" label="Description (optional)"><textarea id="description" name="description" value={values.description} rows={5} onChange={update} placeholder="What makes this experience special?" /></Field>
      <Field id="venue" label="Venue" error={error?.fieldErrors?.venue}>{input('venue', { maxLength: 255, placeholder: 'Where it all happens' })}</Field>
      <Field id="startTime" label="Start date and time" hint={`Shown in your local timezone: ${timezone}.`} error={error?.fieldErrors?.startTime}>{input('startTime', { type: 'datetime-local', 'aria-describedby': 'startTime-hint' })}</Field>
      <h2 className="form-subheading">Make room for your crowd</h2><div className="form-row"><Field id="capacity" label="Capacity" error={error?.fieldErrors?.capacity} hint={event ? `${reserved} tickets currently reserved.` : 'The total number of tickets.'}>{input('capacity', { type: 'number', min: Math.max(1, reserved), max: 2147483647, step: 1, placeholder: '100', 'aria-describedby': 'capacity-hint' })}</Field><Field id="price" label={`Price per ticket (${CURRENCY})`} error={error?.fieldErrors?.ticketPriceCents} hint="Set 0 for a free event.">{input('price', { type: 'text', inputMode: 'decimal', placeholder: '25.00', 'aria-describedby': 'price-hint' })}</Field></div>
      <div className="form-actions"><Link className="button button-outline" to="/admin">Cancel</Link><button className="button button-primary" disabled={pending}>{pending ? 'Saving…' : event ? 'Save changes' : 'Create event'}<Icon name="arrow" /></button></div>
    </form><aside className="form-aside"><span className="empty-icon"><Icon name="ticket" size={32} /></span><h2>Small details.<br />Great experiences.</h2><p>A clear description, the right venue, and a date to remember. You’re almost there.</p><hr /><p>Capacity changes preserve existing reservations. Events with booking history stay on record.</p><p>Ticket prices are informational. Booking confirms tickets without payment.</p></aside></div>
  </>
}
