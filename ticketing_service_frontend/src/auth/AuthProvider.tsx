import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { setAuthToken, setUnauthorizedHandler } from '../api/client'
import type { AuthResponse } from '../api/types'
import { AuthContext } from './context'

// The session lives in localStorage so it survives reloads. Any script on the page can read it (XSS risk),
// which is acceptable for this practice app; an httpOnly cookie would avoid that.
const STORAGE_KEY = 'ticketing.session'

function loadSession(): AuthResponse | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const session: AuthResponse = JSON.parse(raw)
    if (new Date(session.expiresAt) <= new Date()) {
      localStorage.removeItem(STORAGE_KEY)
      return null
    }
    return session
  } catch {
    return null
  }
}

// Read once at startup so the very first requests already carry the token
const initialSession = loadSession()
setAuthToken(initialSession?.token ?? null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState(initialSession)

  const signIn = useCallback((auth: AuthResponse) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(auth))
    setAuthToken(auth.token)
    setSession(auth)
  }, [])

  const signOut = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY)
    setAuthToken(null)
    setSession(null)
  }, [])

  // The server rejected our token (expired or invalid): drop it
  useEffect(() => {
    setUnauthorizedHandler(signOut)
    return () => setUnauthorizedHandler(null)
  }, [signOut])

  // Log out when the token expires, instead of waiting for the next request to fail
  useEffect(() => {
    if (!session) return
    const timer = setTimeout(signOut, new Date(session.expiresAt).getTime() - Date.now())
    return () => clearTimeout(timer)
  }, [session, signOut])

  return (
    <AuthContext.Provider value={{ user: session?.user ?? null, signIn, signOut }}>
      {children}
    </AuthContext.Provider>
  )
}
