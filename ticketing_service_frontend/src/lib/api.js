import { readSession, saveSession } from './session'

export class ApiError extends Error {
  constructor(message, status, fieldErrors = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export async function api(path, { method = 'GET', body, signal, authenticated = true } = {}) {
  const token = authenticated ? readSession()?.accessToken : null
  let response
  try {
    response = await fetch(`/api${path}`, {
      method, signal,
      headers: { Accept: 'application/json', ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    })
  } catch (error) {
    if (error.name === 'AbortError') throw error
    throw new ApiError('We couldn’t reach the server. Check your connection and try again.', 0)
  }
  const text = await response.text()
  let data
  try {
    // Preserve large integer prices/IDs rather than silently rounding them in JavaScript.
    data = text ? JSON.parse(text, (_key, value, context) =>
      typeof value === 'number' && !Number.isSafeInteger(value) && context?.source ? context.source : value) : null
  } catch {
    throw new ApiError('The server returned an unexpected response. Please try again.', response.status)
  }
  if (!response.ok) {
    if (response.status === 401 && token && readSession()?.accessToken === token) {
      saveSession(null)
      window.dispatchEvent(new Event('auth:expired'))
    }
    throw new ApiError(data?.message || `Request failed (${response.status}).`, response.status, data?.fieldErrors)
  }
  return data
}
