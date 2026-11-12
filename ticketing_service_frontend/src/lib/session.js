export const SESSION_KEY = 'gather.session'

export function readSession() {
  try {
    const session = JSON.parse(localStorage.getItem(SESSION_KEY))
    return typeof session?.accessToken === 'string' && session.user?.id != null && typeof session.user.email === 'string'
      && ['USER', 'ADMIN'].includes(session.user.role) && Date.parse(session.expiresAt) > Date.now() ? session : null
  } catch {
    return null
  }
}

export function saveSession(session) {
  try {
    if (session) localStorage.setItem(SESSION_KEY, JSON.stringify(session))
    else localStorage.removeItem(SESSION_KEY)
  } catch {
    throw new Error('Please allow local storage to sign in on this browser.')
  }
}
