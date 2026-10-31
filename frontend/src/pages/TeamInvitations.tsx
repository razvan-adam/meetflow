import type { FormEvent } from 'react'
import { ApiError } from '../api/client.ts'
import { useCancelInvitation, useInvite, usePendingInvitations } from '../api/invitations.ts'
import styles from './TeamPage.module.css'

const dateFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' })

function inviteError(error: Error): string {
  if (error instanceof ApiError && error.status === 409) {
    return 'This person is already a member of the team.'
  }
  if (error instanceof ApiError && error.status === 400) {
    return 'Enter a valid email address.'
  }
  if (error instanceof ApiError && error.status === 503) {
    return 'The invitation email couldn’t be sent. Try again in a few minutes.'
  }
  return 'Something went wrong. Try again in a few minutes.'
}

// The "Invite colleagues" card on the team page (S3), shown to team admins only (MF-10).
export function TeamInvitations({ teamId }: { teamId: string }) {
  const pending = usePendingInvitations(teamId)
  const invite = useInvite(teamId)
  const cancel = useCancelInvitation(teamId)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const email = String(new FormData(form).get('email')).trim()
    invite.mutate(email, { onSuccess: () => form.reset() })
  }

  return (
    <section className={styles.card} aria-labelledby="invite-title">
      <div>
        <h2 id="invite-title" className={styles.cardTitle}>
          Invite colleagues
        </h2>
        <p className={styles.sub}>
          They get an email with a link that works once and expires after 7 days.
        </p>
      </div>
      <form className={styles.addRow} onSubmit={handleSubmit}>
        <input
          className={styles.input}
          name="email"
          type="email"
          placeholder="colleague@company.com"
          aria-label="Email to invite"
          required
          maxLength={254}
        />
        <button className={styles.primary} type="submit" disabled={invite.isPending}>
          Send invitation
        </button>
      </form>
      {invite.isError && (
        <p role="alert" className={styles.error}>
          {inviteError(invite.error)}
        </p>
      )}
      {invite.isSuccess && (
        <p role="status" className={styles.sub}>
          Invitation sent to {invite.data.email}.
        </p>
      )}
      <div>
        <p className={styles.sectionLabel}>Pending invitations</p>
        {pending.data?.length === 0 && <p className={styles.sub}>No pending invitations.</p>}
        {pending.data && pending.data.length > 0 && (
          <ul className={styles.list}>
            {pending.data.map((invitation) => (
              <li key={invitation.id} className={styles.row}>
                <div>
                  <div>{invitation.email}</div>
                  <div className={styles.meta}>
                    Sent {dateFormat.format(new Date(invitation.createdAt))} by{' '}
                    {invitation.invitedBy} · expires{' '}
                    {dateFormat.format(new Date(invitation.expiresAt))}
                  </div>
                </div>
                <button
                  type="button"
                  className={styles.secondary}
                  disabled={cancel.isPending}
                  onClick={() => cancel.mutate(invitation.id)}
                  aria-label={`Cancel the invitation to ${invitation.email}`}
                >
                  Cancel
                </button>
              </li>
            ))}
          </ul>
        )}
        {cancel.isError && (
          <p role="alert" className={styles.error}>
            The invitation couldn’t be cancelled. Try again in a few minutes.
          </p>
        )}
      </div>
    </section>
  )
}
