import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import type { Task, TaskStatus } from '../api/tasks.ts'
import agendaStyles from './Agenda.module.css'
import styles from './LiveAgenda.module.css'
import { dueText, isOverdue, TASK_STATUS_LABELS, todayDate } from './taskDisplay.ts'
import pageStyles from './TeamPage.module.css'

interface Props {
  tasks: Task[]
  label: string
  canChangeStatus: (task: Task) => boolean
  canReword: boolean
  canDelete: boolean
  pending: boolean
  onChangeStatus: (task: Task, status: TaskStatus) => void
  onReword: (task: Task, description: string, onSuccess: () => void) => void
  onDelete: (task: Task, onSettled: () => void) => void
}

const STATUSES = Object.keys(TASK_STATUS_LABELS) as TaskStatus[]

// The tasks under a decision or an agenda item, each with its owner, status and deadline. Each one
// links to its own screen, where the rest can be changed (S8).
export function TaskList({ tasks, label, ...row }: Props) {
  const [today] = useState(todayDate)

  if (tasks.length === 0) {
    return null
  }
  return (
    <ul className={styles.tasks} aria-label={label}>
      {tasks.map((task) => (
        <TaskRow key={task.id} task={task} today={today} {...row} />
      ))}
    </ul>
  )
}

type RowProps = Omit<Props, 'tasks' | 'label'> & { task: Task; today: string }

function TaskRow({
  task,
  today,
  canChangeStatus,
  canReword,
  canDelete,
  pending,
  onChangeStatus,
  onReword,
  onDelete,
}: RowProps) {
  const [mode, setMode] = useState<'view' | 'edit' | 'delete'>('view')
  const showView = () => setMode('view')

  function handleReword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const description = String(new FormData(event.currentTarget).get('description')).trim()
    if (description) {
      onReword(task, description, showView)
    }
  }

  if (mode === 'edit' && canReword) {
    return (
      <li className={styles.task}>
        <form className={styles.taskEdit} onSubmit={handleReword}>
          <input
            className={pageStyles.input}
            name="description"
            aria-label="Task description"
            defaultValue={task.description}
            maxLength={500}
            required
            autoFocus
          />
          <button className={pageStyles.primary} type="submit" disabled={pending}>
            Save
          </button>
          <button className={pageStyles.secondary} type="button" onClick={showView}>
            Cancel
          </button>
        </form>
      </li>
    )
  }
  if (mode === 'delete' && canDelete) {
    return (
      <li className={styles.task}>
        <div
          className={agendaStyles.confirmation}
          role="group"
          aria-label={`Confirm deleting task: ${task.description}`}
        >
          <p className={agendaStyles.confirmationText}>
            Delete this task? It disappears from the meeting and its owner’s list.
          </p>
          <button
            className={pageStyles.danger}
            type="button"
            disabled={pending}
            onClick={() => onDelete(task, showView)}
          >
            Delete task
          </button>
          {/* Focus starts on the safe choice, so Enter doesn't delete it by accident. */}
          <button className={pageStyles.secondary} type="button" onClick={showView} autoFocus>
            Keep task
          </button>
        </div>
      </li>
    )
  }
  return (
    <li className={styles.task} data-overdue={isOverdue(task, today) || undefined}>
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
      <span className={styles.taskDue}>{dueText(task, today)}</span>
      {(canChangeStatus(task) || canReword || canDelete) && (
        <div className={styles.taskControls}>
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
          <span className={agendaStyles.actions}>
            {canReword && (
              <button
                className={agendaStyles.action}
                type="button"
                aria-label={`Edit task: ${task.description}`}
                onClick={() => setMode('edit')}
              >
                Edit
              </button>
            )}
            {canDelete && (
              <button
                className={agendaStyles.action}
                type="button"
                aria-label={`Delete task: ${task.description}`}
                onClick={() => setMode('delete')}
              >
                ×
              </button>
            )}
          </span>
        </div>
      )}
    </li>
  )
}
