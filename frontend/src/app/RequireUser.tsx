import { Navigate, Outlet } from 'react-router'
import { useCurrentUser } from '../api/users.ts'
import { ServerUnreachable } from './ServerUnreachable.tsx'

// Every page except sign-up and login needs a logged-in user; logging out lands here too.
export function RequireUser() {
  const currentUser = useCurrentUser()

  if (currentUser.isError) {
    return <ServerUnreachable />
  }
  if (currentUser.isPending) {
    return null
  }
  return currentUser.data ? <Outlet /> : <Navigate to="/log-in" replace />
}
