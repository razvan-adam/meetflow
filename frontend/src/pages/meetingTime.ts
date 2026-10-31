// Meeting times are stored in UTC and shown, entered and edited in the user's own time zone.

const meetingTimeFormat = new Intl.DateTimeFormat(undefined, {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

// E.g. "Mon 2 Nov 2026, 09:30", in the user's locale.
export function formatMeetingTime(scheduledAt: string): string {
  return meetingTimeFormat.format(new Date(scheduledAt))
}

// The values of a date input (YYYY-MM-DD) and a time input (HH:MM) for this moment.
export function toDateAndTime(scheduledAt: string): { date: string; time: string } {
  const at = new Date(scheduledAt)
  const pad = (n: number) => String(n).padStart(2, '0')
  return {
    date: `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}`,
    time: `${pad(at.getHours())}:${pad(at.getMinutes())}`,
  }
}

// The moment, in UTC, that a date input and a time input describe.
export function fromDateAndTime(date: string, time: string): string {
  return new Date(`${date}T${time}`).toISOString()
}
