import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useChangeOrganizer, useSeries, type Series } from '../api/series.ts'
import { useTeam, useTeamMembers } from '../api/teams.ts'
import { useCurrentUser } from '../api/users.ts'
import styles from './TeamPage.module.css'

// A meeting series (S4). Its meetings and open tasks are added here by MF-13 and MF-19.
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
  const organizer = series.data.organizerId === user?.id ? 'you' : series.data.organizerName
  return (
    <main className={styles.page}>
      <div>
        <TeamCrumb teamId={series.data.teamId} />
        <h1 className={styles.title}>{series.data.name}</h1>
        <p className={styles.sub}>Organized by {organizer}</p>
      </div>
      <p className={styles.empty}>The meetings of this series will appear here.</p>
      <ChangeOrganizer series={series.data} />
    </main>
  )
}

function TeamCrumb({ teamId }: { teamId: string }) {
  const { data: team } = useTeam(teamId)
  return (
    <div className={styles.meta}>
      <Link to={`/teams/${teamId}`}>{team?.name ?? 'Team'}</Link>
    </div>
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
