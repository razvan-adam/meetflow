import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useMinutes, type MinutesItem, type MinutesTask } from '../api/minutes.ts'
import { useSeries } from '../api/series.ts'
import { Breadcrumb } from './Breadcrumb.tsx'
import { formatMeetingTime } from './meetingTime.ts'
import { formatDeadline, TASK_STATUS_LABELS } from './taskDisplay.ts'
import pageStyles from './TeamPage.module.css'
import styles from './MinutesPage.module.css'

// The record of a closed meeting (S9). Nobody can edit it; only the note on how each task stands
// today can differ from what was emailed.
export function MinutesPage() {
  const { meetingId = '' } = useParams()
  const minutes = useMinutes(meetingId)
  const series = useSeries(minutes.data?.seriesId)

  if (minutes.isPending) {
    return null
  }
  if (minutes.isError) {
    return (
      <main className={pageStyles.page}>
        <p role="alert" className={pageStyles.empty}>
          {minutes.error instanceof ApiError && minutes.error.status === 404
            ? 'These minutes don’t exist or aren’t in one of your teams.'
            : 'Something went wrong. Try again in a few minutes.'}
        </p>
      </main>
    )
  }
  const { data } = minutes
  const reviewed = data.commitments.filter((task) => !task.removed)
  const removed = data.commitments.filter((task) => task.removed)
  return (
    <main className={pageStyles.page}>
      <div>
        <Breadcrumb teamId={data.teamId} series={series.data} />
        <h1 className={pageStyles.title}>Minutes: {formatDeadline(data.meetingDate)}</h1>
        <p className={pageStyles.sub}>
          {data.seriesName} · Closed by {data.closedByName} on {formatMeetingTime(data.closedAt)} ·{' '}
          {emailText(data.emailsSent, data.emailRecipients)} ·{' '}
          <span className={styles.tag}>read-only</span>
        </p>
        {data.scheduledDate !== data.meetingDate && (
          <p className={pageStyles.sub}>Planned for {formatDeadline(data.scheduledDate)}</p>
        )}
        <p className={pageStyles.sub}>
          <Link to={`/meetings/${data.meetingId}`}>Open the meeting page</Link>
        </p>
      </div>
      <article className={styles.doc}>
        <section aria-labelledby="minutes-participants">
          <h2 id="minutes-participants" className={styles.heading}>
            Participants
          </h2>
          <p className={styles.text}>
            {data.participants
              .map(({ name, organizer }) => (organizer ? `${name} (organizer)` : name))
              .join(', ')}
          </p>
        </section>
        {reviewed.length > 0 && (
          <section aria-labelledby="minutes-commitments">
            <h2 id="minutes-commitments" className={styles.heading}>
              Commitments reviewed
            </h2>
            <TaskTable tasks={reviewed} label="Commitments reviewed" showChange />
          </section>
        )}
        {removed.length > 0 && (
          <section aria-labelledby="minutes-removed">
            <h2 id="minutes-removed" className={styles.heading}>
              Removed during this meeting
            </h2>
            <ul className={styles.items}>
              {removed.map((task) => (
                <li key={task.id}>
                  <s>{task.description}</s> · {task.ownerName}
                </li>
              ))}
            </ul>
          </section>
        )}
        {data.agenda.map((item, index) => (
          <AgendaBlock key={index} item={item} />
        ))}
        {data.newTasks.length > 0 && (
          <section aria-labelledby="minutes-new-tasks">
            <h2 id="minutes-new-tasks" className={styles.heading}>
              New tasks
            </h2>
            <TaskTable tasks={data.newTasks} label="New tasks" showChange={false} />
          </section>
        )}
      </article>
    </main>
  )
}

// Says "emailed" only once every email has gone out.
function emailText(sent: number, recipients: number): string {
  if (sent < recipients) {
    return `sending the email: ${sent} of ${recipients} sent so far`
  }
  return `emailed to ${recipients} ${recipients === 1 ? 'participant' : 'participants'}`
}

function AgendaBlock({ item }: { item: MinutesItem }) {
  return (
    <section>
      <h2 className={styles.heading}>{item.title}</h2>
      {item.notes && <p className={styles.notes}>{item.notes}</p>}
      {!item.covered && (
        <p className={styles.note}>Not covered. Carried over to the next meeting.</p>
      )}
      {item.decisions.length > 0 && (
        <ul className={styles.items}>
          {item.decisions.map((decision, index) => (
            <li key={index}>
              <b>Decision:</b> {decision}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function TaskTable({
  tasks,
  label,
  showChange,
}: {
  tasks: MinutesTask[]
  label: string
  showChange: boolean
}) {
  return (
    <div className={styles.tableWrap}>
      <table className={styles.table} aria-label={label}>
        <thead>
          <tr>
            <th scope="col">Task</th>
            <th scope="col">Owner</th>
            <th scope="col">Deadline</th>
            <th scope="col">Status</th>
          </tr>
        </thead>
        <tbody>
          {tasks.map((task) => (
            <tr key={task.id} data-overdue={task.daysOverdue > 0 || undefined}>
              <td>
                <Link to={`/tasks/${task.id}`}>{task.description}</Link>
              </td>
              <td>{task.ownerName}</td>
              <td className={styles.date}>
                {formatDeadline(task.deadline)}
                {task.daysOverdue > 0 && (
                  <>
                    {' · '}
                    <b className={styles.overdue}>
                      {task.daysOverdue} {task.daysOverdue === 1 ? 'day' : 'days'} overdue
                    </b>
                  </>
                )}
              </td>
              <td>
                {statusText(task, showChange)}
                <TodayNote task={task} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// "Open → In progress" when the status changed during the meeting.
function statusText(task: MinutesTask, showChange: boolean): string {
  const atClose = TASK_STATUS_LABELS[task.statusAtClose]
  return showChange && task.statusBefore !== task.statusAtClose
    ? `${TASK_STATUS_LABELS[task.statusBefore]} → ${atClose}`
    : atClose
}

function TodayNote({ task }: { task: MinutesTask }) {
  if (task.removedSince) {
    return <span className={styles.today}>Removed since</span>
  }
  if (task.currentStatus !== task.statusAtClose) {
    return (
      <span className={styles.today}>
        Now {TASK_STATUS_LABELS[task.currentStatus].toLowerCase()}
      </span>
    )
  }
  return null
}
