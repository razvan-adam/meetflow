import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useCreateSeries, useTeamSeries } from '../api/series.ts'
import styles from './TeamPage.module.css'

// The team's meeting series on the team page (S3). Any member can create one and becomes its
// organizer (MF-12).
export function TeamSeries({ teamId }: { teamId: string }) {
  const series = useTeamSeries(teamId)
  const create = useCreateSeries(teamId)
  const navigate = useNavigate()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const name = String(new FormData(event.currentTarget).get('name')).trim()
    create.mutate(name, { onSuccess: (created) => navigate(`/series/${created.id}`) })
  }

  return (
    <section aria-labelledby="series-title">
      <h2 id="series-title" className={styles.sectionLabel}>
        Meeting series
      </h2>
      {series.data?.length === 0 && (
        <p className={styles.empty}>
          No meeting series yet. Create one for each kind of recurring meeting, e.g. a weekly sync.
        </p>
      )}
      {series.data && series.data.length > 0 && (
        <ul className={styles.list}>
          {series.data.map((item) => (
            <li key={item.id} className={styles.row}>
              <div>
                <Link to={`/series/${item.id}`}>{item.name}</Link>
                <div className={styles.meta}>Organizer: {item.organizerName}</div>
              </div>
            </li>
          ))}
        </ul>
      )}
      <form className={`${styles.addRow} ${styles.below}`} onSubmit={handleSubmit}>
        <input
          className={styles.input}
          name="name"
          placeholder="New series name, e.g. Weekly ops sync"
          aria-label="New series name"
          required
          maxLength={100}
        />
        <button className={styles.secondary} type="submit" disabled={create.isPending}>
          Create series
        </button>
      </form>
      {create.isError && (
        <p role="alert" className={`${styles.error} ${styles.below}`}>
          The series couldn’t be created. Try again in a few minutes.
        </p>
      )}
    </section>
  )
}
