import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useMeeting, type Meeting } from '../api/meetings.ts'
import { useSeries } from '../api/series.ts'
import { useCurrentUser } from '../api/users.ts'
import { Agenda } from './Agenda.tsx'
import { Breadcrumb } from './Breadcrumb.tsx'
import { LiveAgenda } from './LiveAgenda.tsx'
import { MeetingActions } from './MeetingActions.tsx'
import { formatMeetingTime } from './meetingTime.ts'
import pageStyles from './TeamPage.module.css'
import styles from './MeetingPage.module.css'

const STATUS_LABELS: Record<Meeting['status'], string> = {
  SCHEDULED: 'Scheduled',
  IN_PROGRESS: 'In progress',
  CLOSED: 'Closed',
}

// A meeting: when, who attends and its agenda. Every member of the team can open it; the series
// organizer can change it until it is closed, and starts and closes it (MF-13, MF-14, MF-15).
export function MeetingPage() {
  const { meetingId = '' } = useParams()
  const meeting = useMeeting(meetingId)
  const series = useSeries(meeting.data?.seriesId)
  const { data: user } = useCurrentUser()

  if (meeting.isPending) {
    return null
  }
  if (meeting.isError) {
    return <MeetingError error={meeting.error} />
  }
  const canEdit = series.data?.organizerId === user?.id && meeting.data.status !== 'CLOSED'
  return (
    <main className={pageStyles.page}>
      <div className={styles.head}>
        <div>
          <Breadcrumb teamId={meeting.data.teamId} series={series.data} />
          <h1 className={`${pageStyles.title} ${styles.time}`}>
            {formatMeetingTime(meeting.data.scheduledAt)}
          </h1>
          <p className={pageStyles.sub}>{STATUS_LABELS[meeting.data.status]}</p>
        </div>
        <div className={styles.actions}>
          {canEdit && (
            <Link
              className={`${pageStyles.secondary} ${styles.linkButton}`}
              to={`/meetings/${meeting.data.id}/edit`}
            >
              Edit date and participants
            </Link>
          )}
          <MeetingActions meeting={meeting.data} canRun={canEdit} />
        </div>
      </div>
      {meeting.data.closedAt && (
        <p className={`${styles.banner} ${styles.bannerClosed}`}>
          <span>
            <strong>This meeting was closed</strong> on {formatMeetingTime(meeting.data.closedAt)}.
            It can no longer be changed.
          </span>
        </p>
      )}
      <section aria-labelledby="participants-title">
        <h2 id="participants-title" className={pageStyles.sectionLabel}>
          Participants ({meeting.data.participants.length})
        </h2>
        <ul className={pageStyles.list}>
          {meeting.data.participants.map((participant) => (
            <li key={participant.id} className={pageStyles.row}>
              <span>
                {participant.name}
                {participant.id === series.data?.organizerId && (
                  <span className={pageStyles.meta}> · organizer</span>
                )}
              </span>
            </li>
          ))}
        </ul>
      </section>
      {meeting.data.status === 'SCHEDULED' ? (
        <Agenda meetingId={meeting.data.id} seriesId={meeting.data.seriesId} canEdit={canEdit} />
      ) : (
        <LiveAgenda
          meetingId={meeting.data.id}
          seriesId={meeting.data.seriesId}
          participants={meeting.data.participants}
          canEdit={canEdit}
          closed={meeting.data.status === 'CLOSED'}
        />
      )}
    </main>
  )
}

export function MeetingError({ error }: { error: Error }) {
  return (
    <main className={pageStyles.page}>
      <p role="alert" className={pageStyles.empty}>
        {error instanceof ApiError && error.status === 404
          ? 'This meeting doesn’t exist or isn’t in one of your teams.'
          : 'Something went wrong. Try again in a few minutes.'}
      </p>
    </main>
  )
}
