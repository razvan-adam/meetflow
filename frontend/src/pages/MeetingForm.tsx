import type { FormEvent } from 'react'
import type { MeetingChanges } from '../api/meetings.ts'
import type { Series } from '../api/series.ts'
import { useTeamMembers } from '../api/teams.ts'
import { fromDateAndTime } from './meetingTime.ts'
import pageStyles from './TeamPage.module.css'
import styles from './MeetingPage.module.css'

interface Props {
  series: Series
  // Empty to schedule a new meeting, with every member of the team ticked.
  initial?: { date: string; time: string; participantIds: string[] }
  submitLabel: string
  pending: boolean
  onSubmit: (changes: MeetingChanges) => void
}

// Date, time and participants of a meeting (S5). Participants come from the team; the organizer
// always attends.
export function MeetingForm({ series, initial, submitLabel, pending, onSubmit }: Props) {
  const members = useTeamMembers(series.teamId)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const chosen = form.getAll('participantIds').map(String)
    onSubmit({
      scheduledAt: fromDateAndTime(String(form.get('date')), String(form.get('time'))),
      participantIds: [series.organizerId, ...chosen],
    })
  }

  // The checkboxes pick their defaults on the first render, so wait for the members.
  if (!members.data) {
    return null
  }
  return (
    <form className={pageStyles.card} onSubmit={handleSubmit}>
      <div className={styles.fields}>
        <label className={styles.field}>
          Date
          <input
            className={pageStyles.input}
            type="date"
            name="date"
            required
            defaultValue={initial?.date}
          />
        </label>
        <label className={styles.field}>
          Time
          <input
            className={pageStyles.input}
            type="time"
            name="time"
            required
            defaultValue={initial?.time}
          />
        </label>
      </div>
      <fieldset className={styles.people}>
        <legend className={pageStyles.sectionLabel}>Participants · team members only</legend>
        {members.data.map((member) => {
          const isOrganizer = member.id === series.organizerId
          return (
            <label key={member.id} className={styles.person}>
              <input
                type="checkbox"
                name="participantIds"
                value={member.id}
                defaultChecked={
                  isOrganizer || (initial ? initial.participantIds.includes(member.id) : true)
                }
                disabled={isOrganizer}
              />
              {member.name}
              {isOrganizer && <small>organizer</small>}
            </label>
          )
        })}
      </fieldset>
      <div>
        <button className={pageStyles.primary} type="submit" disabled={pending}>
          {submitLabel}
        </button>
      </div>
    </form>
  )
}
