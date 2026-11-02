import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useDecisions } from '../api/decisions.ts'
import { useMeeting, useSeriesMeetings } from '../api/meetings.ts'
import { useSeries } from '../api/series.ts'
import {
  useChangeTask,
  useDeleteTask,
  useTask,
  type Task,
  type TaskFieldChange,
  type TaskStatus,
} from '../api/tasks.ts'
import { useTeamMembers } from '../api/teams.ts'
import { useCurrentUser } from '../api/users.ts'
import { Breadcrumb } from './Breadcrumb.tsx'
import liveStyles from './LiveAgenda.module.css'
import { formatMeetingTime } from './meetingTime.ts'
import {
  dueText,
  formatDeadline,
  isOverdue,
  TASK_STATUS_LABELS,
  taskChangeFailure,
  taskCorrectionFailure,
  todayDate,
} from './taskDisplay.ts'
import { TaskField } from './TaskField.tsx'
import { TaskHistory } from './TaskHistory.tsx'
import pageStyles from './TeamPage.module.css'
import styles from './TaskPage.module.css'

const STATUSES = Object.keys(TASK_STATUS_LABELS) as TaskStatus[]

// A task (S8): its status, owner, deadline, where it came from and its history (MF-18). Every member
// of the team can open it; the owner and the series organizer can change parts of it.
export function TaskPage() {
  const { taskId = '' } = useParams()
  const task = useTask(taskId)

  if (task.isPending) {
    return null
  }
  if (task.isError) {
    return (
      <main className={pageStyles.page}>
        <p role="alert" className={pageStyles.empty}>
          {task.error instanceof ApiError && task.error.status === 404
            ? 'This task doesn’t exist or isn’t in one of your teams.'
            : 'Something went wrong. Try again in a few minutes.'}
        </p>
      </main>
    )
  }
  return <TaskDetails task={task.data} />
}

function permissionsText(organizes: boolean, owns: boolean): string {
  if (organizes) {
    return 'You can change the status, owner and deadline because you organize this series. The owner can change the status only.'
  }
  if (owns) {
    return 'You can change the status because this is your task. Only the series organizer can change the owner or deadline.'
  }
  return 'Only the task’s owner and the series organizer can change this task.'
}

function TaskDetails({ task }: { task: Task }) {
  const context = { meetingId: task.meetingId, seriesId: task.seriesId }
  const { data: user } = useCurrentUser()
  const series = useSeries(task.seriesId)
  const meeting = useMeeting(task.meetingId)
  const seriesMeetings = useSeriesMeetings(task.seriesId)
  const decisions = useDecisions(task.meetingId)
  const members = useTeamMembers(task.teamId)
  const change = useChangeTask(context)
  // Apart from the rest because a 409 here means a meeting closed, not a change at the same time.
  const correct = useChangeTask(context)
  const remove = useDeleteTask(context)
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(false)
  const [today] = useState(todayDate)

  const organizes = Boolean(user && series.data?.organizerId === user.id)
  const owns = task.ownerId === user?.id
  // During any meeting of the series, including one after the task's own.
  const canReword =
    organizes && Boolean(seriesMeetings.data?.some((found) => found.status === 'IN_PROGRESS'))
  // Only during the task's own meeting.
  const canDelete = organizes && meeting.data?.status === 'IN_PROGRESS'
  const decision = decisions.data?.find((found) => found.id === task.decisionId)

  function save(fields: TaskFieldChange, onSuccess?: () => void) {
    correct.reset()
    remove.reset()
    change.mutate({ taskId: task.id, change: fields }, { onSuccess })
  }

  function saveDescription(description: string, onSuccess: () => void) {
    change.reset()
    remove.reset()
    correct.mutate({ taskId: task.id, change: { description } }, { onSuccess })
  }

  function handleDelete() {
    change.reset()
    correct.reset()
    remove.mutate(task.id, {
      onSuccess: () => navigate(`/meetings/${task.meetingId}`),
      onError: () => setConfirming(false),
    })
  }

  const correctionFailure = correct.error ?? remove.error

  return (
    <main className={pageStyles.page}>
      <div>
        <Breadcrumb teamId={task.teamId} series={series.data} />
        <h1 className={`${pageStyles.title} ${styles.title}`}>{task.description}</h1>
        <p className={styles.due} data-overdue={isOverdue(task, today) || undefined}>
          {dueText(task, today)}
        </p>
      </div>
      {organizes || owns ? (
        <div className={`${liveStyles.marks} ${styles.statuses}`} role="group" aria-label="Status">
          {STATUSES.map((status) => (
            <button
              key={status}
              className={liveStyles.mark}
              type="button"
              aria-pressed={task.status === status}
              disabled={change.isPending}
              onClick={() => save({ status })}
            >
              {TASK_STATUS_LABELS[status]}
            </button>
          ))}
        </div>
      ) : (
        <p className={styles.status}>Status: {TASK_STATUS_LABELS[task.status]}</p>
      )}
      <dl className={styles.details}>
        {canReword && (
          <TaskField
            label="Description"
            value={task.description}
            canChange
            pending={correct.isPending}
            input={
              <input
                className={pageStyles.input}
                name="value"
                aria-label="Description"
                defaultValue={task.description}
                maxLength={500}
                required
                autoFocus
              />
            }
            onSave={saveDescription}
          />
        )}
        <TaskField
          label="Owner"
          value={task.ownerName}
          canChange={organizes && Boolean(members.data)}
          pending={change.isPending}
          input={
            <select
              className={pageStyles.input}
              name="value"
              aria-label="Owner"
              defaultValue={task.ownerId}
            >
              {members.data?.map((member) => (
                <option key={member.id} value={member.id}>
                  {member.name}
                </option>
              ))}
            </select>
          }
          onSave={(ownerId, onSuccess) => save({ ownerId }, onSuccess)}
        />
        <TaskField
          label="Deadline"
          value={formatDeadline(task.deadline)}
          canChange={organizes}
          pending={change.isPending}
          input={
            <input
              className={pageStyles.input}
              name="value"
              type="date"
              aria-label="Deadline"
              defaultValue={task.deadline}
              required
            />
          }
          onSave={(deadline, onSuccess) => save({ deadline }, onSuccess)}
        />
        <dt>Created in</dt>
        <dd>
          <Link to={`/meetings/${task.meetingId}`}>
            {series.data?.name ?? 'Meeting'}
            {meeting.data && `, ${formatMeetingTime(meeting.data.scheduledAt)}`}
          </Link>
        </dd>
        {decision && (
          <>
            <dt>From decision</dt>
            <dd className={styles.decision}>{decision.text}</dd>
          </>
        )}
      </dl>
      <p className={styles.callout}>{permissionsText(organizes, owns)}</p>
      {(change.error || correctionFailure) && (
        <p role="alert" className={pageStyles.error}>
          {change.error
            ? taskChangeFailure(change.error)
            : correctionFailure && taskCorrectionFailure(correctionFailure)}
        </p>
      )}
      {canDelete &&
        (confirming ? (
          <div
            className={styles.confirmation}
            role="group"
            aria-label={`Confirm deleting task: ${task.description}`}
          >
            <p className={styles.confirmationText}>
              Delete this task? It disappears from the meeting and its owner’s list.
            </p>
            <button
              className={pageStyles.danger}
              type="button"
              disabled={remove.isPending}
              onClick={handleDelete}
            >
              Delete task
            </button>
            {/* Focus starts on the safe choice, so Enter doesn't delete it by accident. */}
            <button
              className={pageStyles.secondary}
              type="button"
              onClick={() => setConfirming(false)}
              autoFocus
            >
              Keep task
            </button>
          </div>
        ) : (
          <div>
            <button
              className={pageStyles.secondary}
              type="button"
              onClick={() => setConfirming(true)}
            >
              Delete this task
            </button>
            <p className={`${pageStyles.meta} ${styles.hint}`}>
              A task can be deleted until its meeting closes.
            </p>
          </div>
        ))}
      <TaskHistory taskId={task.id} />
    </main>
  )
}
