import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError, api, errorMessage } from '../../api/client'
import type { EventRequest, EventResponse } from '../../api/types'
import { Field } from '../../components/Field'
import { toDateTimeLocal } from '../../format'

// Inputs hold strings; they become an EventRequest on submit
interface FormState {
  name: string
  venue: string
  startsAt: string
  capacity: string
  price: string
  description: string
}

const EMPTY: FormState = { name: '', venue: '', startsAt: '', capacity: '', price: '0', description: '' }

function toFormState(event: EventResponse): FormState {
  return {
    name: event.name,
    venue: event.venue,
    startsAt: toDateTimeLocal(event.startsAt),
    capacity: String(event.capacity),
    price: (event.priceCents / 100).toFixed(2),
    description: event.description ?? '',
  }
}

function toRequest(form: FormState): EventRequest {
  return {
    name: form.name.trim(),
    venue: form.venue.trim(),
    // datetime-local is in the browser's zone; toISOString converts it to UTC for the Instant
    startsAt: new Date(form.startsAt).toISOString(),
    capacity: Number(form.capacity),
    priceCents: Math.round(Number(form.price) * 100),
    description: form.description.trim() || null,
  }
}

// Create when there's no :id in the URL, edit otherwise
export function EventFormPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [form, setForm] = useState<FormState | null>(id ? null : EMPTY)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    if (!id) return
    api.getEvent(id).then(
      (event) => setForm(toFormState(event)),
      (e) => setError(errorMessage(e)),
    )
  }, [id])

  if (!form) return error ? <p className="error">{error}</p> : <p className="muted">Loading event…</p>

  function update(field: keyof FormState, value: string) {
    setForm((current) => (current ? { ...current, [field]: value } : current))
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!form) return
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      const request = toRequest(form)
      await (id ? api.updateEvent(id, request) : api.createEvent(request))
      navigate('/admin/events')
    } catch (err) {
      setError(errorMessage(err))
      if (err instanceof ApiError) setFieldErrors(err.fieldErrors)
      setSubmitting(false)
    }
  }

  return (
    <div className="card narrow">
      <h1>{id ? 'Edit event' : 'New event'}</h1>
      {error && <p className="error">{error}</p>}
      <form className="form" onSubmit={handleSubmit}>
        <Field label="Name" error={fieldErrors.name}>
          <input required maxLength={200} value={form.name} onChange={(e) => update('name', e.target.value)} />
        </Field>
        <Field label="Venue" error={fieldErrors.venue}>
          <input required maxLength={200} value={form.venue} onChange={(e) => update('venue', e.target.value)} />
        </Field>
        <Field label="Starts at" error={fieldErrors.startsAt}>
          <input
            type="datetime-local"
            required
            value={form.startsAt}
            onChange={(e) => update('startsAt', e.target.value)}
          />
        </Field>
        <div className="form-row">
          <Field label="Capacity" error={fieldErrors.capacity}>
            <input
              type="number"
              required
              min={1}
              value={form.capacity}
              onChange={(e) => update('capacity', e.target.value)}
            />
          </Field>
          <Field label="Price" error={fieldErrors.priceCents}>
            <input
              type="number"
              required
              min={0}
              step="0.01"
              value={form.price}
              onChange={(e) => update('price', e.target.value)}
            />
          </Field>
        </div>
        <Field label="Description" error={fieldErrors.description}>
          <textarea
            rows={5}
            maxLength={5000}
            value={form.description}
            onChange={(e) => update('description', e.target.value)}
          />
        </Field>
        <div className="form-actions">
          <button type="submit" disabled={submitting}>
            {submitting ? 'Saving…' : id ? 'Save changes' : 'Create event'}
          </button>
          <Link to="/admin/events" className="button secondary">
            Cancel
          </Link>
        </div>
      </form>
    </div>
  )
}
