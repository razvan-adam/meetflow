import { useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useMeeting, useUpdateMeeting, type Meeting } from '../api/meetings.ts'
import { useSeries, type Series } from '../api/series.ts'
import { useCurrentUser } from '../api/users.ts'
import { Breadcrumb } from './Breadcrumb.tsx'
import { MeetingError } from './MeetingPage.tsx'
import { MeetingForm } from './MeetingForm.tsx'
import { toDateAndTime } from './meetingTime.ts'
import styles from './TeamPage.module.css'

// Change a meeting's date, time and participants (S5). The series organizer only, until the
// meeting is closed (MF-13).
export function EditMeetingPage() {
  const { meetingId = '' } = useParams()
  const meeting = useMeeting(meetingId)
  const series = useSeries(meeting.data?.seriesId)

  const error = meeting.error ?? series.error
  if (error) {
    return <MeetingError error={error} />
  }
  if (!meeting.data || !series.data) {
    return null
  }
  return <EditMeeting meeting={meeting.data} series={series.data} />
}

function EditMeeting({ meeting, series }: { meeting: Meeting; series: Series }) {
  const { data: user } = useCurrentUser()
  const update = useUpdateMeeting(meeting)
  const navigate = useNavigate()
  const canEdit = series.organizerId === user?.id && meeting.status !== 'CLOSED'

  return (
    <main className={styles.page}>
      <div>
        <Breadcrumb teamId={meeting.teamId} series={series} />
        <h1 className={styles.title}>Edit the meeting</h1>
        <p className={styles.sub}>
          {canEdit
            ? 'Participants see the changes when they open the meeting.'
            : 'Only the series organizer can change a meeting, and only until it is closed.'}
        </p>
      </div>
      {canEdit && (
        <MeetingForm
          // Starts over from the saved meeting after each save.
          key={meeting.version}
          series={series}
          initial={{
            ...toDateAndTime(meeting.scheduledAt),
            participantIds: meeting.participants.map((participant) => participant.id),
          }}
          submitLabel="Save changes"
          pending={update.isPending}
          onSubmit={(changes) =>
            update.mutate(changes, {
              onSuccess: (updated) => navigate(`/meetings/${updated.id}`),
            })
          }
        />
      )}
      {update.isError && (
        <p role="alert" className={styles.error}>
          {update.error instanceof ApiError && update.error.status === 409
            ? 'Someone else changed or closed this meeting meanwhile. Reload the page and try again.'
            : 'The changes couldn’t be saved. Try again in a few minutes.'}
        </p>
      )}
    </main>
  )
}
