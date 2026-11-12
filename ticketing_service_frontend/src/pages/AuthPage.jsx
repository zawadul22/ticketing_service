import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/context'
import { Alert, Field, Icon, Loading } from '../components/ui'

export default function AuthPage({ mode }) {
  const { user, checking, authenticate } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const register = mode === 'register'
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState(null)
  const destination = location.state?.from?.startsWith('/') && !location.state.from.startsWith('//') ? location.state.from : '/events'

  async function submit(event) {
    event.preventDefault()
    setError(null)
    if (new TextEncoder().encode(password).length > 72) { setError({ message: 'Password must not exceed 72 UTF-8 bytes.' }); return }
    setPending(true)
    try { await authenticate(mode, { email, password }); navigate(destination, { replace: true }) }
    catch (failure) { setError(failure) }
    finally { setPending(false) }
  }
  if (checking) return <Loading label="Checking your session" />
  if (user) return <Navigate to={destination} replace />
  return <section className="auth-layout" key={mode}><div className="auth-story"><span className="eyebrow">YOUR PEOPLE. YOUR PLANS.</span><h1>A little more<br /><em>being there.</em></h1><p>The kind of experiences that stay with you<br />long after the lights come on.</p><div className="auth-illustration" aria-hidden="true"><div className="auth-ring" /><span>GOOD<br />TIMES<br />AHEAD.</span><Icon name="ticket" size={70} /></div></div>
    <div className="auth-panel"><span className="eyebrow">{register ? 'MAKE ROOM FOR SOMETHING GOOD' : 'NICE TO HAVE YOU BACK'}</span><h2>{register ? 'Join the crowd.' : 'Welcome back.'}</h2><p>{register ? 'Your next great experience starts here.' : 'Sign in. Your next good plan is waiting.'}</p>
      {error && <Alert>{error.message}</Alert>}
      <form onSubmit={submit} key={mode}>
        <Field id="email" label="Email address" error={error?.fieldErrors?.email}><input id="email" type="email" autoComplete="email" maxLength={320} required value={email} onChange={event => setEmail(event.target.value)} placeholder="you@example.com" aria-invalid={Boolean(error?.fieldErrors?.email)} /></Field>
        <Field id="password" label="Password" hint={register ? 'Use 8–72 characters (at most 72 UTF-8 bytes).' : undefined} error={error?.fieldErrors?.password}><div className="password-field"><input id="password" type={showPassword ? 'text' : 'password'} autoComplete={register ? 'new-password' : 'current-password'} minLength={register ? 8 : undefined} maxLength={72} required value={password} onChange={event => setPassword(event.target.value)} aria-describedby={register ? 'password-hint' : undefined} /><button type="button" className="text-button" onClick={() => setShowPassword(value => !value)} aria-label={showPassword ? 'Hide password' : 'Show password'}>{showPassword ? 'Hide' : 'Show'}</button></div></Field>
        <button className="button button-primary button-full" disabled={pending}>{pending ? 'One moment…' : register ? 'Create account' : 'Sign in'}<Icon name="arrow" /></button>
      </form>
      <p className="auth-switch">{register ? 'Already part of the crowd?' : 'New around here?'} <Link to={register ? '/login' : '/register'} state={location.state} onClick={() => { setError(null); setPassword('') }}>{register ? 'Sign in' : 'Create an account'}</Link></p>
    </div>
  </section>
}
