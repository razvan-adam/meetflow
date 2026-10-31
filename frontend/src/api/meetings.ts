import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from './client.ts'

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
  // Sorted by name; the series organizer is always one of them.
  participants: Participant[]
  // Sent back with an edit, so an edit of an outdated copy is rejected (409).
  version: number
}

export interface MeetingChanges {
  scheduledAt: string
  participantIds: string[]
}

const MEETINGS = ['meetings']
const seriesMeetingsKey = (seriesId: string) => [...MEETINGS, 'series', seriesId]
const meetingKey = (meetingId: string) => [...MEETINGS, meetingId]

// The series' meetings, latest first.
export function useSeriesMeetings(seriesId: string) {
  return useQuery({
    queryKey: seriesMeetingsKey(seriesId),
    queryFn: () => apiFetch<Meeting[]>(`/meeting-series/${encodeURIComponent(seriesId)}/meetings`),
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

// The series organizer only, until the meeting is closed.
export function useUpdateMeeting(meeting: Meeting) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (changes: MeetingChanges) =>
      apiFetch<Meeting>(`/meetings/${encodeURIComponent(meeting.id)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...changes, version: meeting.version }),
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(meetingKey(updated.id), updated)
      return queryClient.invalidateQueries({ queryKey: seriesMeetingsKey(updated.seriesId) })
    },
  })
}
