import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from './client.ts'

export interface AgendaItem {
  id: string
  title: string
  // Not covered at the previous meeting of the series.
  carriedOver: boolean
}

const agendaKey = (meetingId: string) => ['meetings', meetingId, 'agenda-items']
const agendaPath = (meetingId: string) => `/meetings/${encodeURIComponent(meetingId)}/agenda-items`
const itemPath = (itemId: string) => `/agenda-items/${encodeURIComponent(itemId)}`
const JSON_HEADERS = { 'Content-Type': 'application/json' }

// In agenda order. Every member of the team can read it.
export function useAgendaItems(meetingId: string) {
  return useQuery({
    queryKey: agendaKey(meetingId),
    queryFn: () => apiFetch<AgendaItem[]>(agendaPath(meetingId)),
  })
}

// The changes below are for the series organizer only, until the meeting is closed. They reload
// the agenda even when they fail, since a failure usually means the page was out of date.

export function useAddAgendaItem(meetingId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (title: string) =>
      apiFetch<AgendaItem>(agendaPath(meetingId), {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({ title }),
      }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: agendaKey(meetingId) }),
  })
}

export function useRenameAgendaItem(meetingId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ itemId, title }: { itemId: string; title: string }) =>
      apiFetch<AgendaItem>(itemPath(itemId), {
        method: 'PUT',
        headers: JSON_HEADERS,
        body: JSON.stringify({ title }),
      }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: agendaKey(meetingId) }),
  })
}

export function useRemoveAgendaItem(meetingId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (itemId: string) => apiFetch<void>(itemPath(itemId), { method: 'DELETE' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: agendaKey(meetingId) }),
  })
}

// Takes every item in the new order, and shows that order right away so a drag doesn't jump back.
export function useReorderAgenda(meetingId: string) {
  const queryClient = useQueryClient()
  const key = agendaKey(meetingId)
  return useMutation({
    mutationFn: (items: AgendaItem[]) =>
      apiFetch<AgendaItem[]>(`${agendaPath(meetingId)}/order`, {
        method: 'PUT',
        headers: JSON_HEADERS,
        body: JSON.stringify({ itemIds: items.map((item) => item.id) }),
      }),
    onMutate: async (items) => {
      await queryClient.cancelQueries({ queryKey: key })
      queryClient.setQueryData(key, items)
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: key }),
  })
}
