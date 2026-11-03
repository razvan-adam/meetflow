import { useQuery } from '@tanstack/react-query'
import { apiFetch } from './client.ts'
import type { TaskStatus } from './tasks.ts'

export interface MinutesTask {
  id: string
  // All as they were when the meeting closed.
  description: string
  ownerName: string
  // A calendar date: YYYY-MM-DD.
  deadline: string
  statusBefore: TaskStatus
  statusAtClose: TaskStatus
  // Counted to the meeting's date; 0 if it wasn't overdue.
  daysOverdue: number
  // Removed during this meeting.
  removed: boolean
  // How it stands today: the one part of the minutes that can change.
  removedSince: boolean
  currentStatus: TaskStatus
}

export interface MinutesItem {
  title: string
  notes: string | null
  covered: boolean
  carriedOver: boolean
  decisions: string[]
}

export interface Minutes {
  meetingId: string
  teamId: string
  seriesId: string
  seriesName: string
  // Calendar dates, YYYY-MM-DD: when it was held and when it was planned for.
  meetingDate: string
  scheduledDate: string
  // ISO-8601 in UTC.
  closedAt: string
  closedByName: string
  participants: { id: string; name: string; organizer: boolean }[]
  commitments: MinutesTask[]
  agenda: MinutesItem[]
  newTasks: MinutesTask[]
  // How many participants the summary goes to, and how many it has reached so far.
  emailRecipients: number
  emailsSent: number
}

const WHILE_EMAILING_MS = 3000

// Written once when the meeting closes, so there is nothing to change. While the emails are still
// going out it keeps asking, so the page moves on to "emailed" once they have all gone.
export function useMinutes(meetingId: string) {
  return useQuery({
    queryKey: ['meetings', meetingId, 'minutes'],
    queryFn: () => apiFetch<Minutes>(`/meetings/${encodeURIComponent(meetingId)}/minutes`),
    refetchInterval: ({ state }) =>
      state.data && state.data.emailsSent < state.data.emailRecipients ? WHILE_EMAILING_MS : false,
  })
}
