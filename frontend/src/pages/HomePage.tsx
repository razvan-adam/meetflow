import { useHealth } from '../api/health.ts'
import styles from './HomePage.module.css'

// Placeholder start page for the project skeleton: shows whether the backend is reachable.
export function HomePage() {
  const health = useHealth()

  let status = 'Checking…'
  if (health.isError) {
    status = 'Unreachable'
  } else if (health.data) {
    status = health.data.status === 'UP' ? 'Up' : health.data.status
  }

  return (
    <main className={styles.page}>
      <h1 className={styles.title}>MeetFlow</h1>
      <p className={styles.lead}>Meetings that end in commitments, followed until they are done.</p>
      <dl className={styles.status}>
        <dt className={styles.label}>API</dt>
        <dd className={health.isError ? styles.down : undefined}>{status}</dd>
      </dl>
    </main>
  )
}
