export const CURRENCY = import.meta.env.VITE_CURRENCY || 'USD'
const currency = new Intl.NumberFormat(undefined, { style: 'currency', currency: CURRENCY })

export function money(cents) {
  const value = BigInt(cents ?? 0)
  // Keep cents exact; use locale currency formatting for ordinary prices.
  if (value <= BigInt(Number.MAX_SAFE_INTEGER)) return currency.format(Number(value) / 100)
  return `${CURRENCY} ${(value / 100n).toLocaleString()}.${(value % 100n).toString().padStart(2, '0')}`
}

export function date(value, options = {}) {
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric', ...options }).format(new Date(value))
}

export function time(value) {
  return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit', timeZoneName: 'short' }).format(new Date(value))
}

export function toLocalInput(value) {
  const d = new Date(value)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}T${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

export function decimalPrice(cents) {
  const value = BigInt(cents)
  return `${value / 100n}.${String(value % 100n).padStart(2, '0')}`
}

export function priceToCents(value) {
  if (!/^\d+(\.\d{1,2})?$/.test(value)) throw new Error('Enter a price with at most two decimal places.')
  const [whole, fraction = ''] = value.split('.')
  const cents = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'))
  if (cents > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('This price exceeds the supported limit for the event form.')
  return Number(cents)
}
