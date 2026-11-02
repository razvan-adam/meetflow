import { ApiError } from '../api/client.ts'
import type { Task, TaskDeletion, TaskStatus } from '../api/tasks.ts'
import { formatMeetingTime, toDateAndTime } from './meetingTime.ts'

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

// Overdue isn't a status: the task isn't done and its deadline is before today (YYYY-MM-DD). These
// compare as text.
export function isOverdue(task: Task, today: string): boolean {
  return task.status !== 'DONE' && task.deadline < today
}

// Whole calendar days, counted in UTC so a clock change can't make a day shorter.
function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000)
}

// "Due Fri 20 Nov 2026", saying how late it is in words when overdue, not only with a color.
export function dueText(task: Task, today: string): string {
  const due = `Due ${formatDeadline(task.deadline)}`
  if (!isOverdue(task, today)) {
    return due
  }
  const days = daysBetween(task.deadline, today)
  return `${due} · ${days} ${days === 1 ? 'day' : 'days'} overdue`
}

export function deletionText(deletion: TaskDeletion): string {
  const when = deletion.meetingScheduledAt
    ? `during the meeting of ${formatMeetingTime(deletion.meetingScheduledAt)}`
    : `on ${formatMeetingTime(deletion.at)}`
  return `Deleted by ${deletion.byName} ${when}`
}

// For rewording and deleting, which need a meeting running: a 409 means it closed.
export function taskCorrectionFailure(failure: Error): string {
  return failure instanceof ApiError && failure.status === 409
    ? 'The meeting was closed meanwhile, so the task wasn’t changed.'
    : taskChangeFailure(failure)
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
