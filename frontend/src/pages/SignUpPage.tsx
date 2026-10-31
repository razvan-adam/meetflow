import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useSignUp } from '../api/users.ts'
import styles from './AuthForm.module.css'

// The same rules as the API, so most mistakes are caught before sending.
const MIN_PASSWORD_LENGTH = 12

function errorMessage(error: Error): string {
  if (error instanceof ApiError && error.status === 409) {
    return 'An account with this email already exists. Log in instead.'
  }
  if (error instanceof ApiError && error.status === 400) {
    return 'Check your name, email and password, then try again.'
  }
  return 'Something went wrong. Try again in a few minutes.'
}

export function SignUpPage() {
  const signUp = useSignUp()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    signUp.mutate({
      name: String(form.get('name')).trim(),
      email: String(form.get('email')),
      password: String(form.get('password')),
    })
  }

  return (
    <>
      <header className={styles.bar}>MeetFlow</header>
      <main>
        <form className={styles.form} onSubmit={handleSubmit}>
          <h1 className={styles.title}>Create your account</h1>
          <label className={styles.field}>
            Name
            <input
              className={styles.input}
              name="name"
              autoComplete="name"
              required
              maxLength={100}
            />
          </label>
          <label className={styles.field}>
            Work email
            <input
              className={styles.input}
              name="email"
              type="email"
              autoComplete="email"
              required
              maxLength={254}
            />
          </label>
          <label className={styles.field}>
            Password
            <input
              className={styles.input}
              name="password"
              type="password"
              autoComplete="new-password"
              required
              minLength={MIN_PASSWORD_LENGTH}
            />
            <span className={styles.hint}>At least {MIN_PASSWORD_LENGTH} characters.</span>
          </label>
          {signUp.isError && (
            <p role="alert" className={styles.error}>
              {errorMessage(signUp.error)}
            </p>
          )}
          <button className={styles.submit} type="submit" disabled={signUp.isPending}>
            Create account
          </button>
          <p className={styles.sub}>
            Already have an account? <Link to="/log-in">Log in</Link>
          </p>
          <p className={styles.hint}>We only store your name and email.</p>
        </form>
      </main>
    </>
  )
}
