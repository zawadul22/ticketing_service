// Prices are stored in cents with no currency, so the display currency is a frontend choice
const CURRENCY = 'USD'

const dateTimeFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
const priceFormat = new Intl.NumberFormat(undefined, { style: 'currency', currency: CURRENCY })

export function formatDateTime(iso: string): string {
  return dateTimeFormat.format(new Date(iso))
}

export function formatPrice(cents: number): string {
  return cents === 0 ? 'Free' : priceFormat.format(cents / 100)
}

export function isPast(iso: string): boolean {
  return new Date(iso) <= new Date()
}

// <input type="datetime-local"> wants local time as "YYYY-MM-DDTHH:mm", without a zone
export function toDateTimeLocal(iso: string): string {
  const date = new Date(iso)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}
