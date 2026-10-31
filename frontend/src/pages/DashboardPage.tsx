import { Link } from 'react-router'
import { useTeams } from '../api/teams.ts'
import styles from './DashboardPage.module.css'

// The landing page after login (S2). Lists pending tasks once tasks exist.
export function DashboardPage() {
  const teams = useTeams()

  return (
    <main className={styles.page}>
      <h1 className={styles.title}>Your tasks</h1>
      {teams.data?.length === 0 && (
        <p className={styles.empty}>
          You’re not in a team yet. <Link to="/teams/new">Create a team</Link> to start meeting with
          your colleagues; your open tasks from every team will appear here.
        </p>
      )}
      {teams.data && teams.data.length > 0 && (
        <p className={styles.empty}>Your open tasks from every team will appear here.</p>
      )}
    </main>
  )
}
