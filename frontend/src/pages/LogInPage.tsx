import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useLogIn } from '../api/session.ts'
import styles from './AuthForm.module.css'

function errorMessage(error: Error): string {
  if (error instanceof ApiError && error.status === 401) {
    return 'Email or password is incorrect.'
  }
  return 'Something went wrong. Try again in a few minutes.'
}

export function LogInPage() {
  const logIn = useLogIn()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    logIn.mutate({ email: String(form.get('email')), password: String(form.get('password')) })
  }

  return (
    <>
      <header className={styles.bar}>MeetFlow</header>
      <main>
        <form className={styles.form} onSubmit={handleSubmit}>
          <h1 className={styles.title}>Log in</h1>
          <label className={styles.field}>
            Email
            <input
              className={styles.input}
              name="email"
              type="email"
              autoComplete="username"
              required
            />
          </label>
          <label className={styles.field}>
            Password
            <input
              className={styles.input}
              name="password"
              type="password"
              autoComplete="current-password"
              required
            />
          </label>
          {logIn.isError && (
            <p role="alert" className={styles.error}>
              {errorMessage(logIn.error)}
            </p>
          )}
          <button className={styles.submit} type="submit" disabled={logIn.isPending}>
            Log in
          </button>
          <p className={styles.sub}>
            New to MeetFlow? <Link to="/sign-up">Create an account</Link>
          </p>
        </form>
      </main>
    </>
  )
}
