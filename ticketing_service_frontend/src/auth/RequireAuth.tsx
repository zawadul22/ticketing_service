import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import type { Role } from '../api/types'
import { useAuth } from './context'

// Hides a page from the wrong users. The backend enforces the same rules; this only avoids showing doomed pages.
export function RequireAuth({ role, children }: { role?: Role; children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />
  if (role && user.role !== role) return <p className="notice">You don't have access to this page.</p>
  return children
}
