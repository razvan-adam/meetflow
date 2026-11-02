import { ApiError } from '../api/client.ts'
import type { Task, TaskStatus } from '../api/tasks.ts'
import { toDateAndTime } from './meetingTime.ts'

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  DONE: 'Done',
}

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

// Today in the user's own time zone (YYYY-MM-DD).
export function todayDate(): string {
  return toDateAndTime(new Date().toISOString()).date
}

// Overdue isn't a status: the task isn't done and its deadline is before this date (YYYY-MM-DD),
// which is today on the task screen and the meeting's date on its agenda. These compare as text.
export function isOverdue(task: Task, onDate: string): boolean {
  return task.status !== 'DONE' && task.deadline < onDate
}

// "Due Fri 20 Nov 2026", saying so in words when it is overdue, not only with a color.
export function dueText(task: Task, onDate: string): string {
  const due = `Due ${formatDeadline(task.deadline)}`
  return isOverdue(task, onDate) ? `${due} · overdue` : due
}

export function taskChangeFailure(failure: Error): string {
  if (failure instanceof ApiError && failure.status === 400) {
    return 'That person is no longer a member of this team, so your last change wasn’t saved.'
  }
  if (failure instanceof ApiError && failure.status === 403) {
    return 'You can no longer change this task, so your last change wasn’t saved.'
  }
  if (failure instanceof ApiError && failure.status === 404) {
    return 'This task was deleted meanwhile.'
  }
  if (failure instanceof ApiError && failure.status === 409) {
    return 'This task changed meanwhile. Check it and try again.'
  }
  return 'Your last change couldn’t be saved. Try again in a few minutes.'
}
