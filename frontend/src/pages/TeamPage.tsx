import { useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useTeam } from '../api/teams.ts'
import { TeamInvitations } from './TeamInvitations.tsx'
import styles from './TeamPage.module.css'

// The team page (S3). Meeting series are added here by MF-12.
export function TeamPage() {
  const { teamId = '' } = useParams()
  const team = useTeam(teamId)

  if (team.isPending) {
    return null
  }
  if (team.isError) {
    return (
      <main className={styles.page}>
        <p role="alert" className={styles.empty}>
          {team.error instanceof ApiError && team.error.status === 404
            ? 'This team doesn’t exist or you’re not a member of it.'
            : 'Something went wrong. Try again in a few minutes.'}
        </p>
      </main>
    )
  }
  const isAdmin = team.data.role === 'ADMIN'
  return (
    <main className={styles.page}>
      <div>
        <h1 className={styles.title}>{team.data.name}</h1>
        <p className={styles.sub}>{isAdmin ? 'You are an admin' : 'You are a member'}</p>
      </div>
      <p className={styles.empty}>The team’s meeting series will appear here.</p>
      {isAdmin && <TeamInvitations teamId={team.data.id} />}
    </main>
  )
}
