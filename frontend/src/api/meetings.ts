import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, apiFetch } from './client.ts'
import { seriesKey } from './series.ts'

export interface Participant {
  id: string
  name: string
}

export interface Meeting {
  id: string
  teamId: string
  seriesId: string
  // ISO-8601 in UTC.
  scheduledAt: string
  status: 'SCHEDULED' | 'IN_PROGRESS' | 'CLOSED'
  // ISO-8601 in UTC, once the meeting is closed.
  closedAt: string | null
  // Sorted by name; the series organizer is always one of them.
  participants: Participant[]
}

export interface MeetingChanges {
  scheduledAt: string
  participantIds: string[]
}

const MEETINGS = ['meetings']
export const seriesMeetingsKey = (seriesId: string) => [...MEETINGS, 'series', seriesId]
export const meetingKey = (meetingId: string) => [...MEETINGS, meetingId]

// The series' meetings, latest first.
export function useSeriesMeetings(seriesId: string, enabled = true) {
  return useQuery({
    queryKey: seriesMeetingsKey(seriesId),
    queryFn: () => apiFetch<Meeting[]>(`/meeting-series/${encodeURIComponent(seriesId)}/meetings`),
    enabled,
  })
}

// Doesn't fetch without a meeting ID.
export function useMeeting(meetingId: string | undefined) {
  return useQuery({
    queryKey: meetingKey(meetingId ?? ''),
    queryFn: () => apiFetch<Meeting>(`/meetings/${encodeURIComponent(meetingId ?? '')}`),
    enabled: meetingId !== undefined,
  })
}

// The series organizer only.
export function useScheduleMeeting(seriesId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (changes: MeetingChanges) =>
      apiFetch<Meeting>(`/meeting-series/${encodeURIComponent(seriesId)}/meetings`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(changes),
      }),
    onSuccess: (meeting) => {
      queryClient.setQueryData(meetingKey(meeting.id), meeting)
      return queryClient.invalidateQueries({ queryKey: seriesMeetingsKey(seriesId) })
    },
  })
}

// A rejected change usually means the page was out of date. A 403 means the user is no longer the
// organizer, so the series reloads; a 409 usually means the meeting was closed, so it reloads. Either
// way the page stops offering what can't be done any more.
function useReloadAfterRejection(meetingId: string, seriesId: string) {
  const queryClient = useQueryClient()
  return async (error: Error) => {
    if (error instanceof ApiError && error.status === 403) {
      await queryClient.invalidateQueries({ queryKey: seriesKey(seriesId), exact: true })
    }
    if (error instanceof ApiError && error.status === 409) {
      await queryClient.invalidateQueries({ queryKey: meetingKey(meetingId), exact: true })
    }
  }
}

function useMeetingChange<Variables>(
  meetingId: string,
  seriesId: string,
  request: (variables: Variables) => Promise<Meeting>,
) {
  const queryClient = useQueryClient()
  const reload = useReloadAfterRejection(meetingId, seriesId)
  return useMutation({
    mutationFn: request,
    onSuccess: (updated) => {
      queryClient.setQueryData(meetingKey(updated.id), updated)
      return queryClient.invalidateQueries({ queryKey: seriesMeetingsKey(updated.seriesId) })
    },
    onError: reload,
  })
}

// The series organizer only, until the meeting is closed. The latest edit wins.
export function useUpdateMeeting(meetingId: string, seriesId: string) {
  return useMeetingChange(meetingId, seriesId, (changes: MeetingChanges) =>
    apiFetch<Meeting>(`/meetings/${encodeURIComponent(meetingId)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(changes),
    }),
  )
}

// The series organizer only.
export function useStartMeeting(meetingId: string, seriesId: string) {
  return useMeetingChange(meetingId, seriesId, () =>
    apiFetch<Meeting>(`/meetings/${encodeURIComponent(meetingId)}/start`, { method: 'POST' }),
  )
}

// The series organizer only. Items nobody marked become not covered, so the agenda reloads too.
export function useCloseMeeting(meetingId: string, seriesId: string) {
  const queryClient = useQueryClient()
  return useMeetingChange(meetingId, seriesId, async () => {
    const closed = await apiFetch<Meeting>(`/meetings/${encodeURIComponent(meetingId)}/close`, {
      method: 'POST',
    })
    await queryClient.invalidateQueries({ queryKey: [...meetingKey(meetingId), 'agenda-items'] })
    return closed
  })
}
