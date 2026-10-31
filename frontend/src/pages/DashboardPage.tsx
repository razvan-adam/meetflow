import styles from './DashboardPage.module.css'

// The landing page after login (S2). Lists pending tasks once teams and tasks exist.
export function DashboardPage() {
  return (
    <main className={styles.page}>
      <h1 className={styles.title}>Your tasks</h1>
      <p className={styles.empty}>
        You’re not in a team yet. Your open tasks from every team will appear here.
      </p>
    </main>
  )
}
