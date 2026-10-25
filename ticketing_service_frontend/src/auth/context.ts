import { createContext, useContext } from 'react'
import type { AuthResponse, UserResponse } from '../api/types'

export interface AuthState {
  user: UserResponse | null
  signIn: (auth: AuthResponse) => void
  signOut: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const state = useContext(AuthContext)
  if (!state) throw new Error('useAuth must be used inside <AuthProvider>')
  return state
}
