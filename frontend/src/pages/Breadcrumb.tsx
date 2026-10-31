import { Link } from 'react-router'
import type { Series } from '../api/series.ts'
import { useTeam } from '../api/teams.ts'
import styles from './TeamPage.module.css'

// Links back to the team, and to the series on the meeting pages.
export function Breadcrumb({ teamId, series }: { teamId: string; series?: Series }) {
  const { data: team } = useTeam(teamId)
  return (
    <nav aria-label="Breadcrumb" className={styles.meta}>
      <Link to={`/teams/${teamId}`}>{team?.name ?? 'Team'}</Link>
      {series && (
        <>
          {' › '}
          <Link to={`/series/${series.id}`}>{series.name}</Link>
        </>
      )}
    </nav>
  )
}
