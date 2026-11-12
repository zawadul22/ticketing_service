import { useEffect } from 'react'
import { Link, NavLink, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from './auth/context'
import { Alert, Empty, Icon, Loading } from './components/ui'
import EventsPage from './pages/EventsPage'
import EventPage from './pages/EventPage'
import AuthPage from './pages/AuthPage'
import BookingsPage from './pages/BookingsPage'
import AdminPage from './pages/AdminPage'
import EventFormPage from './pages/EventFormPage'
import useTheme from './hooks/useTheme'
import './App.css'
import './dark.css'

function Layout() {
  const { theme, toggleTheme } = useTheme()
  const { user, checking, logout, notice, dismissNotice, sessionError } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  useEffect(() => { window.scrollTo(0, 0) }, [location.pathname])
  return <>
    <a className="skip-link" href="#main">Skip to content</a>
    <header className="site-header"><div className="nav-wrap">
      <Link to="/events" className="brand" aria-label="Gather home"><span className="brand-mark"><Icon name="ticket" /></span>gather<span className="brand-dot">.</span></Link>
      <nav aria-label="Main navigation"><NavLink to="/events">Discover</NavLink>
        {user && <NavLink to="/bookings">My bookings</NavLink>}
        {user?.role === 'ADMIN' && <NavLink to="/admin">Manage events</NavLink>}
      </nav>
      <div className="account-nav"><button className="theme-toggle" type="button" onClick={toggleTheme} aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`} aria-pressed={theme === 'dark'} title={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}><Icon name={theme === 'dark' ? 'sun' : 'moon'} size={18} /></button>{checking ? <span className="muted">Checking session…</span> : user ? <>
        <span className="account-name" title={user.email}><span className="avatar">{user.email.charAt(0).toUpperCase()}</span><span>{user.email}</span></span>
        <button className="button button-quiet" onClick={() => { logout(); navigate('/events') }}>Sign out</button>
      </> : <><Link className="login-link" to="/login">Sign in</Link><Link className="button button-dark button-small" to="/register">Join the crowd <Icon name="arrow" /></Link></>}</div>
    </div></header>
    <main id="main" className="main-wrap" tabIndex="-1">
      {notice && <Alert onDismiss={dismissNotice}>{notice}</Alert>}
      {sessionError && <Alert>{sessionError} <button className="text-button" onClick={() => window.location.reload()}>Retry session check</button></Alert>}
      <Outlet />
    </main>
    <footer className="site-footer"><Link className="brand" to="/events">gather<span className="brand-dot">.</span></Link><p>Good company. Great experiences.</p><span>Make time for a good time.</span></footer>
  </>
}

function Protected({ admin = false }) {
  const { user, checking } = useAuth()
  const location = useLocation()
  if (checking) return <Loading label="Checking your session" />
  if (!user) return <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />
  if (admin && user.role !== 'ADMIN') return <Empty title="This space is for event organizers." message="Your account does not have admin access." action={<Link className="button button-primary" to="/events">Discover events</Link>} />
  return <Outlet />
}

export default function App() {
  return <Routes><Route element={<Layout />}>
    <Route index element={<Navigate to="/events" replace />} />
    <Route path="events" element={<EventsPage />} />
    <Route path="events/:id" element={<EventPage />} />
    <Route path="login" element={<AuthPage mode="login" />} />
    <Route path="register" element={<AuthPage mode="register" />} />
    <Route element={<Protected />}><Route path="bookings" element={<BookingsPage />} /></Route>
    <Route element={<Protected admin />}>
      <Route path="admin" element={<AdminPage />} />
      <Route path="admin/events/new" element={<EventFormPage />} />
      <Route path="admin/events/:id/edit" element={<EventFormPage />} />
    </Route>
    <Route path="*" element={<Empty title="This page got away." message="There are still plenty of good plans to discover." action={<Link className="button button-primary" to="/events">Back to events</Link>} />} />
  </Route></Routes>
}
