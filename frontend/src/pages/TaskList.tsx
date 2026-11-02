import type { Task, TaskStatus } from '../api/tasks.ts'
import styles from './LiveAgenda.module.css'
import { formatDeadline } from './taskDeadline.ts'

interface Props {
  tasks: Task[]
  label: string
}

const STATUS_LABELS: Record<TaskStatus, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  DONE: 'Done',
}

// The tasks under a decision or an agenda item, each with its owner, status and deadline.
export function TaskList({ tasks, label }: Props) {
  if (tasks.length === 0) {
    return null
  }
  return (
    <ul className={styles.tasks} aria-label={label}>
      {tasks.map((task) => (
        <li key={task.id} className={styles.task}>
          <div className={styles.taskMain}>
            <p className={styles.taskDescription}>{task.description}</p>
            <p className={styles.taskMeta}>
              {task.ownerName} · {STATUS_LABELS[task.status]}
            </p>
          </div>
          <span className={styles.taskDue}>Due {formatDeadline(task.deadline)}</span>
        </li>
      ))}
    </ul>
  )
}
