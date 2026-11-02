import { useTaskHistory, type TaskHistoryEntry } from '../api/tasks.ts'
import { formatMeetingTime } from './meetingTime.ts'
import { formatDeadline, TASK_STATUS_LABELS } from './taskDisplay.ts'
import pageStyles from './TeamPage.module.css'
import styles from './TaskPage.module.css'

function describe(entry: TaskHistoryEntry): string {
  const who = entry.changedByName
  if (!entry.oldStatus && entry.newStatus && entry.newOwnerName && entry.newDeadline) {
    return `${who} created the task. Owner ${entry.newOwnerName}, deadline ${formatDeadline(entry.newDeadline)}.`
  }
  if (entry.oldStatus && entry.newStatus) {
    const from = TASK_STATUS_LABELS[entry.oldStatus]
    return `${who} changed status ${from} → ${TASK_STATUS_LABELS[entry.newStatus]}`
  }
  if (entry.oldOwnerName && entry.newOwnerName) {
    return `${who} changed owner ${entry.oldOwnerName} → ${entry.newOwnerName}`
  }
  if (entry.oldDeadline && entry.newDeadline) {
    const from = formatDeadline(entry.oldDeadline)
    return `${who} changed deadline ${from} → ${formatDeadline(entry.newDeadline)}`
  }
  return `${who} changed the task`
}

// Who changed what and when, latest first (S8).
export function TaskHistory({ taskId }: { taskId: string }) {
  const history = useTaskHistory(taskId)

  return (
    <section aria-labelledby="history-title">
      <h2 id="history-title" className={pageStyles.sectionLabel}>
        History
      </h2>
      {history.isError && (
        <p role="alert" className={pageStyles.error}>
          The history couldn’t be loaded. Try again in a few minutes.
        </p>
      )}
      <ul className={styles.history}>
        {history.data?.map((entry) => (
          <li key={`${entry.changedAt}-${describe(entry)}`}>
            <time dateTime={entry.changedAt}>{formatMeetingTime(entry.changedAt)}</time>
            <span>
              {describe(entry)}
              {entry.duringMeeting && ' (during a meeting)'}
            </span>
          </li>
        ))}
      </ul>
    </section>
  )
}
