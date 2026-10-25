import { BrowserRouter, Link, Route, Routes } from 'react-router'
import { AuthProvider } from './auth/AuthProvider'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './components/Layout'
import { AuthForm } from './pages/AuthForm'
import { EventDetailPage } from './pages/EventDetailPage'
import { EventsPage } from './pages/EventsPage'
import { MyBookingsPage } from './pages/MyBookingsPage'
import { AdminEventsPage } from './pages/admin/AdminEventsPage'
import { EventFormPage } from './pages/admin/EventFormPage'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<EventsPage />} />
            <Route path="events/:id" element={<EventDetailPage />} />
            <Route path="login" element={<AuthForm key="login" mode="login" />} />
            <Route path="register" element={<AuthForm key="register" mode="register" />} />
            <Route
              path="bookings"
              element={
                <RequireAuth role="USER">
                  <MyBookingsPage />
                </RequireAuth>
              }
            />
            <Route
              path="admin/events"
              element={
                <RequireAuth role="ADMIN">
                  <AdminEventsPage />
                </RequireAuth>
              }
            />
            <Route
              path="admin/events/new"
              element={
                <RequireAuth role="ADMIN">
                  <EventFormPage />
                </RequireAuth>
              }
            />
            <Route
              path="admin/events/:id/edit"
              element={
                <RequireAuth role="ADMIN">
                  <EventFormPage />
                </RequireAuth>
              }
            />
            <Route
              path="*"
              element={
                <p className="notice">
                  Page not found. <Link to="/">Back to events</Link>
                </p>
              }
            />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
