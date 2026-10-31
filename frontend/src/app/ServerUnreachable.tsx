import styles from './ServerUnreachable.module.css'

export function ServerUnreachable() {
  return (
    <main className={styles.page}>
      <p role="alert" className={styles.message}>
        MeetFlow can’t reach the server right now. Try again in a few minutes.
      </p>
    </main>
  )
}
