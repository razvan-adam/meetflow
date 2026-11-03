import { useState } from 'react'
import { useAgendaItems, useSavingNotesCount } from '../api/agenda.ts'
import { ApiError } from '../api/client.ts'
import { useCloseMeeting, useStartMeeting, type Meeting } from '../api/meetings.ts'
import pageStyles from './TeamPage.module.css'
import styles from './MeetingPage.module.css'

function failureMessage(failure: Error): string {
  if (failure instanceof ApiError && failure.status === 403) {
    return 'You’re no longer the organizer of this series, so you can’t run this meeting.'
  }
  if (failure instanceof ApiError && failure.status === 409) {
    return 'This meeting changed meanwhile. Check it and try again.'
  }
  return 'That didn’t work. Try again in a few minutes.'
}

interface Props {
  meeting: Meeting
  // The series organizer, until the meeting is closed.
  canRun: boolean
}

// Starting and closing a meeting. Closing asks first and says what will happen, since it can't be
// undone. It stays on the page when the buttons go, so a rejected request can still say why.
export function MeetingActions({ meeting, canRun }: Props) {
  const start = useStartMeeting(meeting.id, meeting.seriesId)
  const close = useCloseMeeting(meeting.id, meeting.seriesId)
  const [confirming, setConfirming] = useState(false)
  const failure = start.error ?? close.error

  return (
    <>
      {canRun && meeting.status === 'SCHEDULED' && (
        <button
          className={pageStyles.primary}
          type="button"
          disabled={start.isPending}
          onClick={() => start.mutate()}
        >
          Start meeting
        </button>
      )}
      {canRun && meeting.status === 'IN_PROGRESS' && !confirming && (
        <button
          className={pageStyles.danger}
          type="button"
          onClick={() => {
            close.reset()
            setConfirming(true)
          }}
        >
          Close meeting
        </button>
      )}
      {canRun && meeting.status === 'IN_PROGRESS' && confirming && (
        <CloseConfirmation
          meetingId={meeting.id}
          participants={meeting.participants.length}
          pending={close.isPending}
          onConfirm={() => close.mutate(undefined, { onSettled: () => setConfirming(false) })}
          onCancel={() => setConfirming(false)}
        />
      )}
      {failure && (
        <p role="alert" className={`${pageStyles.error} ${styles.fullWidth}`}>
          {failureMessage(failure)}
        </p>
      )}
    </>
  )
}

interface CloseConfirmationProps {
  meetingId: string
  participants: number
  pending: boolean
  onConfirm: () => void
  onCancel: () => void
}

// Focus starts on the safe choice, so pressing Enter by accident doesn't close the meeting. Closing
// waits for notes still being saved, since they would be rejected once the meeting is closed.
function CloseConfirmation({
  meetingId,
  participants,
  pending,
  onConfirm,
  onCancel,
}: CloseConfirmationProps) {
  const { data: items = [] } = useAgendaItems(meetingId)
  const saving = useSavingNotesCount(meetingId) > 0
  const unmarked = items.filter((item) => item.status === 'PENDING').length

  return (
    <div className={styles.banner} role="group" aria-label="Confirm closing the meeting">
      <strong>Close this meeting?</strong>
      <ul className={styles.bannerList}>
        <li>Notes, decisions and the agenda will be locked. This can’t be undone.</li>
        {unmarked > 0 && (
          <li>
            {unmarked === 1
              ? '1 item isn’t marked, so it will count as not covered.'
              : `${unmarked} items aren’t marked, so they will count as not covered.`}
          </li>
        )}
        <li>
          {participants === 1
            ? 'The minutes will be emailed to the 1 participant.'
            : `The minutes will be emailed to all ${participants} participants.`}
        </li>
        <li>Each task owner gets one email with their tasks from this meeting.</li>
      </ul>
      <div className={styles.bannerActions}>
        <button
          className={pageStyles.danger}
          type="button"
          disabled={pending || saving}
          onClick={onConfirm}
        >
          {saving ? 'Saving notes…' : 'Close the meeting'}
        </button>
        <button className={pageStyles.secondary} type="button" onClick={onCancel} autoFocus>
          Keep it open
        </button>
      </div>
    </div>
  )
}
