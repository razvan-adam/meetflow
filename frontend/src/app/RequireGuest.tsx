import { Navigate, Outlet } from 'react-router'
import { useCurrentUser } from '../api/users.ts'
import { ServerUnreachable } from './ServerUnreachable.tsx'

// Sign-up and login. A logged-in user goes on to the app, which is also how both forms move on
// once they succeed.
export function RequireGuest() {
  const currentUser = useCurrentUser()

  if (currentUser.isError) {
    return <ServerUnreachable />
  }
  if (currentUser.isPending) {
    return null
  }
  return currentUser.data ? <Navigate to="/" replace /> : <Outlet />
}
