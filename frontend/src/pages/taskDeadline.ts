const deadlineFormat = new Intl.DateTimeFormat(undefined, {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

// E.g. "Fri 20 Nov 2026". A deadline is a calendar date, so it is read in local time: as UTC it
// would show the day before for anyone west of Greenwich.
export function formatDeadline(deadline: string): string {
  return deadlineFormat.format(new Date(`${deadline}T00:00`))
}
