import { NavLink, useMatch, useNavigate } from 'react-router'
import { useTeams } from '../api/teams.ts'
import styles from './AppLayout.module.css'

const NEW_TEAM = 'new'

// Users with several teams switch between them in the top bar (S3). Choosing a team opens its page.
export function TeamSwitcher() {
  const { data: teams } = useTeams()
  const navigate = useNavigate()
  const current = useMatch('/teams/:teamId')?.params.teamId

  if (!teams) {
    return null
  }
  if (teams.length === 0) {
    return (
      <NavLink to="/teams/new" className={styles.navLink}>
        Create a team
      </NavLink>
    )
  }
  const selected = teams.some((team) => team.id === current) ? current : ''
  return (
    <select
      aria-label="Team"
      className={styles.teamSelect}
      value={selected}
      onChange={(event) => navigate(`/teams/${event.target.value}`)}
    >
      {!selected && (
        <option value="" disabled>
          Teams
        </option>
      )}
      {teams.map((team) => (
        <option key={team.id} value={team.id}>
          {team.name}
        </option>
      ))}
      <option value={NEW_TEAM}>+ New team</option>
    </select>
  )
}
