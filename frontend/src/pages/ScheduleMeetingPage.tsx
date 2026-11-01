import { useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useScheduleMeeting } from '../api/meetings.ts'
import { useSeries } from '../api/series.ts'
import { useCurrentUser } from '../api/users.ts'
import { Breadcrumb } from './Breadcrumb.tsx'
import { MeetingForm } from './MeetingForm.tsx'
import styles from './TeamPage.module.css'

// Schedule the next meeting of a series (S5). The series organizer only (MF-13).
export function ScheduleMeetingPage() {
  const { seriesId = '' } = useParams()
  const series = useSeries(seriesId)
  const { data: user } = useCurrentUser()
  const schedule = useScheduleMeeting(seriesId)
  const navigate = useNavigate()

  if (series.isPending) {
    return null
  }
  if (series.isError) {
    return (
      <main className={styles.page}>
        <p role="alert" className={styles.empty}>
          {series.error instanceof ApiError && series.error.status === 404
            ? 'This meeting series doesn’t exist or isn’t in one of your teams.'
            : 'Something went wrong. Try again in a few minutes.'}
        </p>
      </main>
    )
  }
  const isOrganizer = series.data.organizerId === user?.id
  return (
    <main className={styles.page}>
      <div>
        <Breadcrumb teamId={series.data.teamId} series={series.data} />
        <h1 className={styles.title}>Schedule the next meeting</h1>
        <p className={styles.sub}>
          {isOrganizer
            ? 'You organize this series.'
            : `Only the organizer, ${series.data.organizerName}, can schedule meetings.`}
        </p>
      </div>
      {isOrganizer && (
        <MeetingForm
          series={series.data}
          submitLabel="Schedule meeting"
          pending={schedule.isPending}
          onSubmit={(changes) =>
            schedule.mutate(changes, {
              onSuccess: (meeting) => navigate(`/meetings/${meeting.id}`),
            })
          }
        />
      )}
      {schedule.isError && (
        <p role="alert" className={styles.error}>
          {schedule.error instanceof ApiError && schedule.error.status === 409
            ? 'This series already has a meeting that isn’t closed. Close it before scheduling the next one.'
            : 'The meeting couldn’t be scheduled. Try again in a few minutes.'}
        </p>
      )}
    </main>
  )
}
