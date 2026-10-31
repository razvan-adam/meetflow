import { useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useAcceptInvitation, useInvitationLink, type InvitationLink } from '../api/invitations.ts'
import { useLogIn, useLogOut } from '../api/session.ts'
import { useCurrentUser, useSignUp } from '../api/users.ts'
import styles from './AuthForm.module.css'

const MIN_PASSWORD_LENGTH = 12

function acceptError(error: Error): string {
  if (error instanceof ApiError && error.status === 403) {
    return 'This invitation is for another email address.'
  }
  return 'Something went wrong. Try again in a few minutes.'
}

// Opened from the invitation email (J2, MF-10): sign up or log in with the invited address, then
// join the team. Works logged in or out, so it sits outside the route guards.
export function JoinPage() {
  const { token = '' } = useParams()
  const link = useInvitationLink(token)
  const currentUser = useCurrentUser()

  let content: ReactNode = null
  if (link.isError) {
    content =
      link.error instanceof ApiError && link.error.status === 404 ? (
        <Callout title="This invitation link isn’t valid.">
          Check the link in the email, or ask a team admin to send you a new one.
        </Callout>
      ) : (
        <Callout title="Something went wrong.">Try again in a few minutes.</Callout>
      )
  } else if (link.data?.status === 'EXPIRED') {
    content = (
      <Callout title="This invitation has expired.">
        Invitations work for 7 days. Ask a team admin to send you a new one.
      </Callout>
    )
  } else if (link.data?.status === 'ACCEPTED') {
    content = (
      <Callout title="This invitation was already used.">
        Log in with the account that accepted it, or ask a team admin for a new one.
      </Callout>
    )
  } else if (link.data && !currentUser.isPending) {
    content = <Join token={token} link={link.data} userEmail={currentUser.data?.email} />
  }

  return (
    <>
      <header className={styles.bar}>MeetFlow</header>
      <main>
        <div className={styles.form}>{content}</div>
      </main>
    </>
  )
}

function Callout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div role="alert" className={styles.error}>
      <b>{title}</b> {children}
    </div>
  )
}

function Join({
  token,
  link,
  userEmail,
}: {
  token: string
  link: InvitationLink
  userEmail: string | undefined
}) {
  const accept = useAcceptInvitation(token)
  const navigate = useNavigate()
  const logOut = useLogOut()
  const [mode, setMode] = useState<'sign-up' | 'log-in'>('sign-up')

  const join = () =>
    accept.mutateAsync().then((team) => navigate(`/teams/${team.id}`, { replace: true }))

  const heading = (
    <div>
      <h1 className={styles.title}>Join {link.teamName}</h1>
      <p className={styles.sub}>{link.invitedBy} invited you.</p>
    </div>
  )
  const acceptAlert = accept.isError && (
    <p role="alert" className={styles.error}>
      {acceptError(accept.error)}
    </p>
  )

  if (userEmail !== undefined) {
    if (userEmail.toLowerCase() !== link.email.toLowerCase()) {
      return (
        <>
          {heading}
          <p role="alert" className={styles.error}>
            This invitation is for {link.email}, but you’re logged in as {userEmail}. Log out, then
            open the link again to join with the invited address.
          </p>
          <button
            className={styles.submit}
            type="button"
            disabled={logOut.isPending}
            onClick={() => logOut.mutate()}
          >
            Log out
          </button>
        </>
      )
    }
    return (
      <>
        {heading}
        {acceptAlert}
        <button
          className={styles.submit}
          type="button"
          disabled={accept.isPending}
          onClick={() => void join().catch(() => {})}
        >
          Join {link.teamName}
        </button>
      </>
    )
  }

  return mode === 'sign-up' ? (
    <SignUpAndJoin link={link} heading={heading} join={join} onHasAccount={() => setMode('log-in')}>
      {acceptAlert}
    </SignUpAndJoin>
  ) : (
    <LogInAndJoin link={link} heading={heading} join={join} onNoAccount={() => setMode('sign-up')}>
      {acceptAlert}
    </LogInAndJoin>
  )
}

interface FormProps {
  link: InvitationLink
  heading: ReactNode
  join: () => Promise<unknown>
  children: ReactNode
}

function SignUpAndJoin({
  link,
  heading,
  join,
  onHasAccount,
  children,
}: FormProps & { onHasAccount: () => void }) {
  const signUp = useSignUp()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    signUp
      .mutateAsync({
        name: String(form.get('name')).trim(),
        email: link.email,
        password: String(form.get('password')),
      })
      .then(join)
      .catch(() => {})
  }

  return (
    <form className={styles.stack} onSubmit={handleSubmit}>
      {heading}
      <p className={styles.sub}>Create your account to see the team’s meetings and your tasks.</p>
      <label className={styles.field}>
        Name
        <input className={styles.input} name="name" autoComplete="name" required maxLength={100} />
      </label>
      <label className={styles.field}>
        Work email
        <input className={styles.input} value={link.email} readOnly />
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
          {signUp.error instanceof ApiError && signUp.error.status === 409 ? (
            <>
              You already have an account with this email.{' '}
              <button type="button" className={styles.link} onClick={onHasAccount}>
                Log in to join
              </button>
            </>
          ) : (
            'Check your name and password, then try again.'
          )}
        </p>
      )}
      {children}
      <button className={styles.submit} type="submit" disabled={signUp.isPending}>
        Create account and join
      </button>
      <p className={styles.sub}>
        Already have an account?{' '}
        <button type="button" className={styles.link} onClick={onHasAccount}>
          Log in to join
        </button>
      </p>
      <p className={styles.hint}>We only store your name and email.</p>
    </form>
  )
}

function LogInAndJoin({
  link,
  heading,
  join,
  onNoAccount,
  children,
}: FormProps & { onNoAccount: () => void }) {
  const logIn = useLogIn()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    logIn
      .mutateAsync({ email: link.email, password: String(form.get('password')) })
      .then(join)
      .catch(() => {})
  }

  return (
    <form className={styles.stack} onSubmit={handleSubmit}>
      {heading}
      <label className={styles.field}>
        Email
        <input className={styles.input} value={link.email} readOnly />
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
          {logIn.error instanceof ApiError && logIn.error.status === 401
            ? 'The password is incorrect.'
            : 'Something went wrong. Try again in a few minutes.'}
        </p>
      )}
      {children}
      <button className={styles.submit} type="submit" disabled={logIn.isPending}>
        Log in and join
      </button>
      <p className={styles.sub}>
        No account yet?{' '}
        <button type="button" className={styles.link} onClick={onNoAccount}>
          Create one to join
        </button>
      </p>
    </form>
  )
}
