import { Link } from 'react-router'
import type { Task, TaskStatus } from '../api/tasks.ts'
import styles from './LiveAgenda.module.css'
import { dueText, isOverdue, TASK_STATUS_LABELS } from './taskDisplay.ts'

interface Props {
  tasks: Task[]
  label: string
  // The meeting's date (YYYY-MM-DD): on an agenda, tasks are overdue as of the meeting.
  overdueOn: string
  canChangeStatus: (task: Task) => boolean
  pending: boolean
  onChangeStatus: (task: Task, status: TaskStatus) => void
}

const STATUSES = Object.keys(TASK_STATUS_LABELS) as TaskStatus[]

// The tasks under a decision or an agenda item, each with its owner, status and deadline. Each one
// links to its own screen, where the rest can be changed (S8).
export function TaskList({
  tasks,
  label,
  overdueOn,
  canChangeStatus,
  pending,
  onChangeStatus,
}: Props) {
  if (tasks.length === 0) {
    return null
  }
  return (
    <ul className={styles.tasks} aria-label={label}>
      {tasks.map((task) => (
        <li
          key={task.id}
          className={styles.task}
          data-overdue={isOverdue(task, overdueOn) || undefined}
        >
          <div className={styles.taskMain}>
            <p className={styles.taskDescription}>
              <Link to={`/tasks/${task.id}`}>{task.description}</Link>
            </p>
            <p className={styles.taskMeta}>
              {canChangeStatus(task)
                ? task.ownerName
                : `${task.ownerName} · ${TASK_STATUS_LABELS[task.status]}`}
            </p>
          </div>
          <span className={styles.taskDue}>{dueText(task, overdueOn)}</span>
          {canChangeStatus(task) && (
            <select
              className={styles.taskStatus}
              aria-label={`Status of ${task.description}`}
              value={task.status}
              disabled={pending}
              onChange={(event) => onChangeStatus(task, event.target.value as TaskStatus)}
            >
              {STATUSES.map((status) => (
                <option key={status} value={status}>
                  {TASK_STATUS_LABELS[status]}
                </option>
              ))}
            </select>
          )}
        </li>
      ))}
    </ul>
  )
}
