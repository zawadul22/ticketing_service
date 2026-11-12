import { useCallback, useEffect, useState } from 'react'
import { api } from '../lib/api'
import { readSession, saveSession, SESSION_KEY } from '../lib/session'
import { AuthContext } from './context'

export default function AuthProvider({ children }) {
  const [session, setSession] = useState(readSession)
  const [checking, setChecking] = useState(() => Boolean(readSession()))
  const [notice, setNotice] = useState('')
  const [sessionError, setSessionError] = useState('')

  const logout = useCallback(() => {
    saveSession(null)
    setSession(null)
    setSessionError('')
  }, [])

  useEffect(() => {
    const expire = () => {
      setSession(null)
      setSessionError('')
      setNotice('Your session has expired. Please sign in again.')
    }
    const sync = (event) => { if (event.key === SESSION_KEY) setSession(readSession()) }
    window.addEventListener('auth:expired', expire)
    window.addEventListener('storage', sync)
    return () => { window.removeEventListener('auth:expired', expire); window.removeEventListener('storage', sync) }
  }, [])

  useEffect(() => {
    if (!session) return
    const timeout = setTimeout(() => {
      saveSession(null)
      window.dispatchEvent(new Event('auth:expired'))
    }, Math.min(2147483647, Math.max(0, Date.parse(session.expiresAt) - Date.now())))
    return () => clearTimeout(timeout)
  }, [session])

  useEffect(() => {
    if (!readSession()) return
    const controller = new AbortController()
    api('/auth/me', { signal: controller.signal }).then(user => {
      const current = readSession()
      if (current) {
        const updated = { ...current, user }
        saveSession(updated)
        setSession(updated)
      }
    }).catch(error => {
      if (error.name !== 'AbortError' && error.status !== 401) setSessionError(error.message)
    }).finally(() => { if (!controller.signal.aborted) setChecking(false) })
    return () => controller.abort()
  }, [])

  async function authenticate(mode, credentials) {
    const result = await api(`/auth/${mode}`, { method: 'POST', body: credentials, authenticated: false })
    saveSession(result)
    setSession(result)
    setNotice('')
    setSessionError('')
    setChecking(false)
  }

  return <AuthContext.Provider value={{ user: session?.user, checking, notice, sessionError, authenticate, logout,
    dismissNotice: () => setNotice('') }}>{children}</AuthContext.Provider>
}
