import { NavLink, Outlet } from 'react-router'
import { useLogOut } from '../api/session.ts'
import { useCurrentUser } from '../api/users.ts'
import styles from './AppLayout.module.css'
import { TeamSwitcher } from './TeamSwitcher.tsx'
import { ThemeSwitch } from './ThemeSwitch.tsx'

// The frame around every page for a logged-in user.
export function AppLayout() {
  const { data: user } = useCurrentUser()
  const logOut = useLogOut()

  return (
    <>
      <header className={styles.bar}>
        <span className={styles.logo}>MeetFlow</span>
        <nav className={styles.nav}>
          <NavLink to="/" end className={styles.navLink}>
            My tasks
          </NavLink>
          <TeamSwitcher />
        </nav>
        <span className={styles.user}>{user?.name}</span>
        <ThemeSwitch />
        <button
          type="button"
          className={styles.barButton}
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
