import { useMutation, useQuery } from '@tanstack/react-query'
import { useReloadAfterChange, type AgendaContext } from './agenda.ts'
import { apiFetch } from './client.ts'
import { meetingKey } from './meetings.ts'

export interface Decision {
  id: string
  agendaItemId: string
  text: string
}

const decisionsKey = (meetingId: string) => [...meetingKey(meetingId), 'decisions']
const decisionPath = (decisionId: string) => `/decisions/${encodeURIComponent(decisionId)}`
const JSON_HEADERS = { 'Content-Type': 'application/json' }

// A meeting's decisions, oldest first. Every member of the team can read them.
export function useDecisions(meetingId: string) {
  return useQuery({
    queryKey: decisionsKey(meetingId),
    queryFn: () => apiFetch<Decision[]>(`/meetings/${encodeURIComponent(meetingId)}/decisions`),
  })
}

// The changes below are for the series organizer only, while the meeting runs.

export function useAddDecision(context: AgendaContext) {
  const reload = useReloadAfterChange(context, decisionsKey(context.meetingId))
  return useMutation({
    mutationFn: ({ itemId, text }: { itemId: string; text: string }) =>
      apiFetch<Decision>(`/agenda-items/${encodeURIComponent(itemId)}/decisions`, {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({ text }),
      }),
    onSettled: (_data, error) => reload(error),
  })
}

export function useEditDecision(context: AgendaContext) {
  const reload = useReloadAfterChange(context, decisionsKey(context.meetingId))
  return useMutation({
    mutationFn: ({ decisionId, text }: { decisionId: string; text: string }) =>
      apiFetch<Decision>(decisionPath(decisionId), {
        method: 'PUT',
        headers: JSON_HEADERS,
        body: JSON.stringify({ text }),
      }),
    onSettled: (_data, error) => reload(error),
  })
}

export function useRemoveDecision(context: AgendaContext) {
  const reload = useReloadAfterChange(context, decisionsKey(context.meetingId))
  return useMutation({
    mutationFn: (decisionId: string) =>
      apiFetch<void>(decisionPath(decisionId), { method: 'DELETE' }),
    onSettled: (_data, error) => reload(error),
  })
}
