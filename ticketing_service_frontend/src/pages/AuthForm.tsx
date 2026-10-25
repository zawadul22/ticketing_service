import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { ApiError, api, errorMessage } from '../api/client'
import { useAuth } from '../auth/context'
import { Field } from '../components/Field'

type Mode = 'login' | 'register'

// Login and register share one form: both take an email and password and return a session
export function AuthForm({ mode }: { mode: Mode }) {
  const { user, signIn } = useAuth()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  // Signing in re-renders with a user, which sends them back where they came from
  if (user) {
    const from = (location.state as { from?: string } | null)?.from ?? '/'
    return <Navigate to={from} replace />
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      signIn(mode === 'login' ? await api.login(email, password) : await api.register(email, password))
    } catch (err) {
      setError(errorMessage(err))
      if (err instanceof ApiError) setFieldErrors(err.fieldErrors)
      setSubmitting(false)
    }
  }

  const isLogin = mode === 'login'

  return (
    <div className="card narrow">
      <h1>{isLogin ? 'Log in' : 'Create an account'}</h1>
      {error && <p className="error">{error}</p>}
      <form className="form" onSubmit={handleSubmit}>
        <Field label="Email" error={fieldErrors.email}>
          <input
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </Field>
        <Field label="Password" error={fieldErrors.password}>
          <input
            type="password"
            autoComplete={isLogin ? 'current-password' : 'new-password'}
            required
            minLength={isLogin ? undefined : 8}
            maxLength={isLogin ? undefined : 72}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        <button type="submit" disabled={submitting}>
          {submitting ? 'Please wait…' : isLogin ? 'Log in' : 'Register'}
        </button>
      </form>
      <p className="muted">
        {isLogin ? (
          <>
            No account yet?{' '}
            <Link to="/register" state={location.state}>
              Register
            </Link>
          </>
        ) : (
          <>
            Already registered?{' '}
            <Link to="/login" state={location.state}>
              Log in
            </Link>
          </>
        )}
      </p>
    </div>
  )
}
