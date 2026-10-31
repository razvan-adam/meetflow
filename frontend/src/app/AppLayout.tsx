import { Outlet } from 'react-router'
import { useLogOut } from '../api/session.ts'
import { useCurrentUser } from '../api/users.ts'
import styles from './AppLayout.module.css'

// The frame around every page for a logged-in user.
export function AppLayout() {
  const { data: user } = useCurrentUser()
  const logOut = useLogOut()

  return (
    <>
      <header className={styles.bar}>
        <span className={styles.logo}>MeetFlow</span>
        <span className={styles.user}>{user?.name}</span>
        <button
          type="button"
          className={styles.logOut}
          disabled={logOut.isPending}
          onClick={() => logOut.mutate()}
        >
          Log out
        </button>
      </header>
      <Outlet />
    </>
  )
}
