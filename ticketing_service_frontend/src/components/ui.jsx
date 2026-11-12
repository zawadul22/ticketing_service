import { useEffect, useRef } from 'react'

export function Icon({ name = 'arrow', size = 20 }) {
  const paths = {
    arrow: 'M4 12h16m-6-6 6 6-6 6',
    calendar: 'M8 2v4m8-4v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2Z',
    pin: 'M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0ZM15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
    ticket: 'M3 5h18v5a2 2 0 0 0 0 4v5H3v-5a2 2 0 0 0 0-4V5Zm12 0v3m0 3v2m0 3v3',
    search: 'm20 20-5-5M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z',
    plus: 'M12 4v16M4 12h16',
    check: 'm5 12 4 4L19 6',
    close: 'm6 6 12 12M6 18 18 6',
    clock: 'M12 7v5l3 2M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
    edit: 'm16 3 5 5L8 21H3v-5L16 3Zm-3 3 5 5',
    moon: 'M20.8 13A9 9 0 0 1 11 3.2 9 9 0 1 0 20.8 13Z',
    sun: 'M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  }
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name] || paths.arrow} /></svg>
}

export function Alert({ children, kind = 'error', onDismiss }) {
  return <div className={`alert alert-${kind}`} role={kind === 'error' ? 'alert' : 'status'}><span>{children}</span>{onDismiss && <button className="icon-button" aria-label="Dismiss message" onClick={onDismiss}><Icon name="close" /></button>}</div>
}

export function Loading({ label = 'Loading events' }) {
  return <div className="loading-state" role="status"><span className="spinner" />{label}…</div>
}

export function Empty({ title, message, action }) {
  return <section className="empty-state"><span className="empty-icon"><Icon name="ticket" size={32} /></span><h2>{title}</h2><p>{message}</p>{action}</section>
}

export function ErrorState({ error, retry }) {
  return <Empty title={error.status === 404 ? 'We couldn’t find that event.' : 'A little interruption.'} message={error.message}
    action={retry && <button className="button button-dark" onClick={retry}>Try again <Icon name="arrow" /></button>} />
}

export function Field({ label, error, hint, children, id }) {
  return <div className="field"><label htmlFor={id}>{label}</label>{children}{hint && <small id={`${id}-hint`}>{hint}</small>}{error && <small className="field-error" id={`${id}-error`}>{error}</small>}</div>
}

export function ConfirmDialog({ title, children, confirmLabel, busy, onConfirm, onClose }) {
  const ref = useRef(null)
  useEffect(() => { const dialog = ref.current; dialog.showModal(); return () => dialog.close() }, [])
  return <dialog ref={ref} className="confirm-dialog" aria-labelledby="dialog-title" onCancel={event => { event.preventDefault(); if (!busy) onClose() }}>
    <h2 id="dialog-title">{title}</h2><div className="dialog-copy">{children}</div><div className="dialog-actions"><button className="button button-outline" onClick={onClose} disabled={busy} autoFocus>Keep it</button><button className="button button-danger" onClick={onConfirm} disabled={busy}>{busy ? 'Working…' : confirmLabel}</button></div>
  </dialog>
}

export function EventArt({ event, large = false }) {
  const theme = Number(BigInt(event.id || 0) % 4n)
  return <div className={`event-art theme-${theme}${large ? ' event-art-large' : ''}`} aria-hidden="true"><div className="art-orbit orbit-one" /><div className="art-orbit orbit-two" /><div className="art-ticket"><Icon name="ticket" size={large ? 92 : 64} /><span>BE THERE.</span></div><span className="art-caption">SOMETHING TO LOOK FORWARD TO</span></div>
}
