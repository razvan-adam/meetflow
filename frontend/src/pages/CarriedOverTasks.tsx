import { useSeriesMeetings, type Meeting } from '../api/meetings.ts'
import { useCarriedOverTasks, useChangeTask, useDeleteTask, type Task } from '../api/tasks.ts'
import { useCurrentUser } from '../api/users.ts'
import { taskChangeFailure, taskCorrectionFailure } from './taskDisplay.ts'
import { TaskList } from './TaskList.tsx'
import pageStyles from './TeamPage.module.css'

interface Props {
  meetingId: string
  seriesId: string
  status: Meeting['status']
  // The series organizer, whatever the meeting's status.
  organizes: boolean
}

// The commitments carried over from earlier meetings of the series, above the agenda (S6, S7). They
// have no remove button: they leave only when they are done or deleted (MF-19). The owner and the
// organizer can update the status at any time; the organizer can reword and delete while a meeting
// of the series runs, which may be a later one if this meeting is closed. Deleted ones move to their
// own section, so the meeting's record keeps them without mixing them with the live commitments.
export function CarriedOverTasks({ meetingId, seriesId, status, organizes }: Props) {
  const closed = status === 'CLOSED'
  const tasks = useCarriedOverTasks(meetingId)
  const changeTask = useChangeTask({ meetingId, seriesId })
  // Apart from the status, because a 409 here means a meeting closed.
  const rewordTask = useChangeTask({ meetingId, seriesId })
  const deleteTask = useDeleteTask({ meetingId, seriesId })
  const seriesMeetings = useSeriesMeetings(seriesId, closed && organizes)
  const { data: user } = useCurrentUser()

  if (tasks.isError) {
    return (
      <p role="alert" className={pageStyles.error}>
        The open commitments couldn’t be loaded. Try again in a few minutes.
      </p>
    )
  }
  if (!tasks.data || tasks.data.length === 0) {
    return null
  }
  const current = tasks.data.filter((task) => !task.deletion)
  const deleted = tasks.data.filter((task) => task.deletion)
  const canCorrect =
    organizes &&
    (status === 'IN_PROGRESS' ||
      (closed && Boolean(seriesMeetings.data?.some((found) => found.status === 'IN_PROGRESS'))))
  const correctionFailure = rewordTask.error ?? deleteTask.error

  function resetErrors() {
    changeTask.reset()
    rewordTask.reset()
    deleteTask.reset()
  }

  const taskList = (list: Task[], label: string) => (
    <TaskList
      tasks={list}
      label={label}
      canChangeStatus={(task) => organizes || task.ownerId === user?.id}
      canCorrect={canCorrect}
      pending={changeTask.isPending || rewordTask.isPending || deleteTask.isPending}
      onChangeStatus={(task, newStatus) => {
        resetErrors()
        changeTask.mutate({ taskId: task.id, change: { status: newStatus } })
      }}
      onReword={(task, description, onSuccess) => {
        resetErrors()
        rewordTask.mutate({ taskId: task.id, change: { description } }, { onSuccess })
      }}
      onDelete={(task, onSettled) => {
        resetErrors()
        deleteTask.mutate(task.id, { onSettled })
      }}
    />
  )

  return (
    <>
      <section aria-labelledby="carried-over-title">
        <h2 id="carried-over-title" className={pageStyles.sectionLabel}>
          {closed ? 'Commitments reviewed' : 'Open commitments'}
        </h2>
        {status === 'SCHEDULED' && (
          <p className={pageStyles.meta}>
            Carried over from earlier meetings. They stay on the agenda until they’re done.
          </p>
        )}
        {current.length > 0 ? (
          taskList(current, 'Carried-over tasks')
        ) : (
          <p className={pageStyles.empty}>No open commitments left.</p>
        )}
        {changeTask.error && (
          <p role="alert" className={`${pageStyles.error} ${pageStyles.below}`}>
            {taskChangeFailure(changeTask.error)}
          </p>
        )}
        {correctionFailure && (
          <p role="alert" className={`${pageStyles.error} ${pageStyles.below}`}>
            {taskCorrectionFailure(correctionFailure)}
          </p>
        )}
      </section>
      {deleted.length > 0 && (
        <section aria-labelledby="removed-commitments-title">
          <h2 id="removed-commitments-title" className={pageStyles.sectionLabel}>
            Removed commitments
          </h2>
          {taskList(deleted, 'Removed carried-over tasks')}
        </section>
      )}
    </>
  )
}
