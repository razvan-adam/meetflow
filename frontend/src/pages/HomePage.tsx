import { type Health, useHealth } from '../api/health.ts'
import styles from './HomePage.module.css'

const STATUS_LABELS: Record<Health['status'], string> = {
  UP: 'Up',
  DOWN: 'Down',
  OUT_OF_SERVICE: 'Out of service',
  UNKNOWN: 'Unknown',
}

// Placeholder start page for the project skeleton: shows whether the backend is reachable and up.
export function HomePage() {
  const health = useHealth()

  let status = 'Checking…'
  if (health.isError) {
    status = 'Unreachable'
  } else if (health.data) {
    status = STATUS_LABELS[health.data.status]
  }
  const isDown = health.isError || (health.data !== undefined && health.data.status !== 'UP')

  return (
    <main className={styles.page}>
      <h1 className={styles.title}>MeetFlow</h1>
      <p className={styles.lead}>Meetings that end in commitments, followed until they are done.</p>
      <dl className={styles.status}>
        <dt className={styles.label}>API</dt>
        <dd className={isDown ? styles.down : undefined}>{status}</dd>
      </dl>
    </main>
  )
}
