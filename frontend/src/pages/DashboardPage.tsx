import { useState } from 'react'
import { Link } from 'react-router'
import { useChangeTask, usePendingTasks, type PendingTask, type TaskStatus } from '../api/tasks.ts'
import { useTeams } from '../api/teams.ts'
import styles from './DashboardPage.module.css'
import liveStyles from './LiveAgenda.module.css'
import { toDateAndTime } from './meetingTime.ts'
import {
  dueText,
  formatDeadline,
  isOverdue,
  TASK_STATUS_LABELS,
  taskChangeFailure,
  todayDate,
} from './taskDisplay.ts'

const STATUSES = Object.keys(TASK_STATUS_LABELS) as TaskStatus[]

// The landing page after login (S2, MF-21): what the user still has to do across their teams,
// soonest deadline first. A task whose status changes here stays in place until the filter changes
// or the page is left, so one marked done by mistake can be set back.
export function DashboardPage() {
  const teams = useTeams()
  const pending = usePendingTasks()
  const [teamId, setTeamId] = useState<string | null>(null)
  const [changed, setChanged] = useState<Record<string, PendingTask>>({})
  const [today] = useState(todayDate)

  function chooseTeam(chosen: string | null) {
    setTeamId(chosen)
    setChanged({})
  }

  const team = teams.data?.find((found) => found.id === teamId)
  const pendingIds = new Set(pending.data?.map((task) => task.id))
  const tasks = [
    ...(pending.data ?? []).map((task) => changed[task.id] ?? task),
    ...Object.values(changed).filter((task) => !pendingIds.has(task.id)),
  ]
    .filter((task) => !team || task.teamId === team.id)
    .sort((a, b) => a.deadline.localeCompare(b.deadline))
  const open = tasks.filter((task) => task.status !== 'DONE')
  const overdue = open.filter((task) => isOverdue(task, today))

  return (
    <main className={styles.page}>
      <div className={styles.head}>
        <div>
          <h1 className={styles.title}>My tasks</h1>
          <p className={styles.sub}>Everything you committed to, across your teams.</p>
        </div>
        {teams.data && teams.data.length > 1 && (
          <div className={styles.chips} role="group" aria-label="Filter by team">
            <button
              className={styles.chip}
              type="button"
              aria-pressed={!team}
              onClick={() => chooseTeam(null)}
            >
              All teams
            </button>
            {teams.data.map((found) => (
              <button
                key={found.id}
                className={styles.chip}
                type="button"
                aria-pressed={found.id === team?.id}
                onClick={() => chooseTeam(found.id)}
              >
                {found.name}
              </button>
            ))}
          </div>
        )}
      </div>
      {pending.isError && (
        <p role="alert" className={styles.error}>
          Your tasks couldn’t be loaded. Try again in a few minutes.
        </p>
      )}
      {teams.data?.length === 0 && (
        <p className={styles.empty}>
          You’re not in a team yet. <Link to="/teams/new">Create a team</Link> to start meeting with
          your colleagues; your open tasks from every team will appear here.
        </p>
      )}
      {teams.data && teams.data.length > 0 && pending.data && tasks.length === 0 && (
        <p className={styles.empty}>
          {team
            ? `You have no open tasks in ${team.name}.`
            : 'You have no open tasks. Tasks you’re given in a meeting will appear here.'}
        </p>
      )}
      {tasks.length > 0 && (
        <>
          <p className={styles.summary}>
            <span>
              <b>{open.length}</b> pending
            </span>
            {overdue.length > 0 && (
              <span className={styles.overdue}>
                <b>{overdue.length}</b> overdue
              </span>
            )}
          </p>
          <ul className={liveStyles.tasks} aria-label="My pending tasks">
            {tasks.map((task) => (
              <PendingTaskRow
                key={task.id}
                task={task}
                today={today}
                onChange={(updated) => setChanged((all) => ({ ...all, [updated.id]: updated }))}
              />
            ))}
          </ul>
        </>
      )}
    </main>
  )
}

interface RowProps {
  task: PendingTask
  today: string
  onChange: (task: PendingTask) => void
}

function PendingTaskRow({ task, today, onChange }: RowProps) {
  const change = useChangeTask({ meetingId: task.meetingId, seriesId: task.seriesId })
  const done = task.status === 'DONE'

  function handleChange(status: TaskStatus) {
    change.reset()
    onChange({ ...task, status })
    // A task that couldn't be changed shows its status as it was, with the reason.
    change.mutate({ taskId: task.id, change: { status } }, { onError: () => onChange(task) })
  }

  return (
    <li
      className={liveStyles.task}
      data-overdue={isOverdue(task, today) || undefined}
      data-done={done || undefined}
    >
      <div className={liveStyles.taskMain}>
        <p className={liveStyles.taskDescription}>
          <Link to={`/tasks/${task.id}`}>{task.description}</Link>
        </p>
        <p className={liveStyles.taskMeta}>
          {task.teamName} · {task.seriesName} ·{' '}
          <Link to={`/meetings/${task.meetingId}`}>
            from the meeting of {formatDeadline(toDateAndTime(task.meetingScheduledAt).date)}
          </Link>
        </p>
      </div>
      <span className={liveStyles.taskDue}>{dueText(task, today)}</span>
      <div className={liveStyles.taskControls}>
        <select
          className={liveStyles.taskStatus}
          aria-label={`Status of ${task.description}`}
          value={task.status}
          disabled={change.isPending}
          onChange={(event) => handleChange(event.target.value as TaskStatus)}
        >
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {TASK_STATUS_LABELS[status]}
            </option>
          ))}
        </select>
        {done && <span className={styles.doneNote}>Done. Leaves your list.</span>}
      </div>
      {change.error && (
        <p role="alert" className={styles.rowError}>
          {taskChangeFailure(change.error)}
        </p>
      )}
    </li>
  )
}
