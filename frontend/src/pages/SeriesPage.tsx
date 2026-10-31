import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useSeriesMeetings, type Meeting } from '../api/meetings.ts'
import { useChangeOrganizer, useSeries, type Series } from '../api/series.ts'
import { useTeam, useTeamMembers } from '../api/teams.ts'
import { useCurrentUser } from '../api/users.ts'
import { Breadcrumb } from './Breadcrumb.tsx'
import { formatMeetingTime } from './meetingTime.ts'
import meetingStyles from './MeetingPage.module.css'
import styles from './TeamPage.module.css'

// A meeting series (S4): its next and past meetings. Its open tasks are added here by MF-19.
export function SeriesPage() {
  const { seriesId = '' } = useParams()
  const series = useSeries(seriesId)
  const { data: user } = useCurrentUser()

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
        <Breadcrumb teamId={series.data.teamId} />
        <h1 className={styles.title}>{series.data.name}</h1>
        <p className={styles.sub}>Organized by {isOrganizer ? 'you' : series.data.organizerName}</p>
      </div>
      <SeriesMeetings seriesId={series.data.id} isOrganizer={isOrganizer} />
      <ChangeOrganizer series={series.data} />
    </main>
  )
}

// The next meeting, which only the organizer schedules, and the past ones (MF-13).
function SeriesMeetings({ seriesId, isOrganizer }: { seriesId: string; isOrganizer: boolean }) {
  const meetings = useSeriesMeetings(seriesId)
  if (!meetings.data) {
    return null
  }
  // A series has at most one meeting that isn't closed.
  const next = meetings.data.find((meeting) => meeting.status !== 'CLOSED')
  const past = meetings.data.filter((meeting) => meeting.status === 'CLOSED')
  return (
    <>
      <section aria-labelledby="next-title">
        <div className={meetingStyles.head}>
          <h2 id="next-title" className={styles.sectionLabel}>
            Next meeting
          </h2>
          {isOrganizer && !next && (
            <Link
              className={`${styles.primary} ${meetingStyles.linkButton}`}
              to={`/series/${seriesId}/schedule`}
            >
              Schedule next meeting
            </Link>
          )}
        </div>
        {next ? (
          <ul className={styles.list}>
            <MeetingRow meeting={next} />
          </ul>
        ) : (
          <p className={`${styles.empty} ${styles.below}`}>
            {isOrganizer
              ? 'No meeting scheduled. Schedule the next one when you know the date.'
              : 'No meeting scheduled yet. The organizer schedules the meetings.'}
          </p>
        )}
      </section>
      {past.length > 0 && (
        <section aria-labelledby="past-title">
          <h2 id="past-title" className={styles.sectionLabel}>
            Past meetings
          </h2>
          <ul className={styles.list}>
            {past.map((meeting) => (
              <MeetingRow key={meeting.id} meeting={meeting} />
            ))}
          </ul>
        </section>
      )}
    </>
  )
}

function MeetingRow({ meeting }: { meeting: Meeting }) {
  return (
    <li className={styles.row}>
      <div>
        <Link className={meetingStyles.time} to={`/meetings/${meeting.id}`}>
          {formatMeetingTime(meeting.scheduledAt)}
        </Link>
        <div className={styles.meta}>
          {meeting.participants.length === 1
            ? '1 participant'
            : `${meeting.participants.length} participants`}
        </div>
      </div>
      {meeting.status === 'IN_PROGRESS' && <span className={meetingStyles.tag}>in progress</span>}
    </li>
  )
}

// Team admins can hand the organizer role to another member of the team.
function ChangeOrganizer({ series }: { series: Series }) {
  const { data: team } = useTeam(series.teamId)
  const isAdmin = team?.role === 'ADMIN'
  if (!isAdmin) {
    return null
  }
  return <ChangeOrganizerForm series={series} />
}

function ChangeOrganizerForm({ series }: { series: Series }) {
  const members = useTeamMembers(series.teamId)
  const change = useChangeOrganizer(series.id)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const organizerId = String(new FormData(event.currentTarget).get('organizerId'))
    change.mutate(organizerId)
  }

  // The select picks its default on the first render, so wait for the members.
  if (!members.data) {
    return null
  }
  return (
    <section className={styles.card} aria-labelledby="organizer-title">
      <div>
        <h2 id="organizer-title" className={styles.cardTitle}>
          Change organizer
        </h2>
        <p className={styles.sub}>
          The organizer schedules the meetings and prepares their agenda. Only team admins can
          change it.
        </p>
      </div>
      <form className={styles.addRow} onSubmit={handleSubmit}>
        <select
          key={series.organizerId}
          className={styles.input}
          name="organizerId"
          aria-label="New organizer"
          defaultValue={series.organizerId}
        >
          {members.data.map((member) => (
            <option key={member.id} value={member.id}>
              {member.name}
            </option>
          ))}
        </select>
        <button className={styles.primary} type="submit" disabled={change.isPending}>
          Save
        </button>
      </form>
      {change.isError && (
        <p role="alert" className={styles.error}>
          The organizer couldn’t be changed. Try again in a few minutes.
        </p>
      )}
    </section>
  )
}
