import type { FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useCreateTeam } from '../api/teams.ts'
import styles from './AuthForm.module.css'

// Creating a team makes the user its first admin (J1, MF-9).
export function CreateTeamPage() {
  const createTeam = useCreateTeam()
  const navigate = useNavigate()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const name = String(new FormData(event.currentTarget).get('name')).trim()
    createTeam.mutate(name, { onSuccess: (team) => navigate(`/teams/${team.id}`) })
  }

  return (
    <main>
      <form className={styles.form} onSubmit={handleSubmit}>
        <h1 className={styles.title}>Create a team</h1>
        <p className={styles.sub}>
          A team is the people you meet with regularly. You’ll be its admin and can invite them
          next.
        </p>
        <label className={styles.field}>
          Team name
          <input
            className={styles.input}
            name="name"
            placeholder="e.g. Ops team"
            required
            maxLength={100}
          />
        </label>
        {createTeam.isError && (
          <p role="alert" className={styles.error}>
            Something went wrong. Try again in a few minutes.
          </p>
        )}
        <button className={styles.submit} type="submit" disabled={createTeam.isPending}>
          Create team
        </button>
      </form>
    </main>
  )
}
