import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/context'

export function Layout() {
  const { user, signOut } = useAuth()

  return (
    <>
      <header className="topbar">
        <nav className="container nav">
          <NavLink to="/" className="brand" end>
            Ticketing
          </NavLink>
          <NavLink to="/" end>
            Events
          </NavLink>
          {user?.role === 'USER' && <NavLink to="/bookings">My bookings</NavLink>}
          {user?.role === 'ADMIN' && <NavLink to="/admin/events">Manage events</NavLink>}
          <span className="spacer" />
          {user ? (
            <>
              <span className="muted">{user.email}</span>
              <button type="button" className="link-button" onClick={signOut}>
                Log out
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login">Log in</NavLink>
              <NavLink to="/register">Register</NavLink>
            </>
          )}
        </nav>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </>
  )
}
