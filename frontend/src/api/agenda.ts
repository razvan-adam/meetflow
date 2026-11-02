import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, apiFetch } from './client.ts'
import { meetingKey } from './meetings.ts'
import { seriesKey } from './series.ts'

export type AgendaItemStatus = 'PENDING' | 'COVERED' | 'NOT_COVERED'

export interface AgendaItem {
  id: string
  title: string
  notes: string | null
  status: AgendaItemStatus
  // Not covered at the previous meeting of the series.
  carriedOver: boolean
}

// The meeting whose agenda changes, and its series, which says who may change it.
export interface AgendaContext {
  meetingId: string
  seriesId: string
}

const agendaKey = (meetingId: string) => [...meetingKey(meetingId), 'agenda-items']
const agendaPath = (meetingId: string) => `/meetings/${encodeURIComponent(meetingId)}/agenda-items`
const itemPath = (itemId: string) => `/agenda-items/${encodeURIComponent(itemId)}`
const notesMutationKey = (meetingId: string) => ['save-notes', meetingId]
const JSON_HEADERS = { 'Content-Type': 'application/json' }

// In agenda order. Every member of the team can read it.
export function useAgendaItems(meetingId: string) {
  return useQuery({
    queryKey: agendaKey(meetingId),
    queryFn: () => apiFetch<AgendaItem[]>(agendaPath(meetingId)),
  })
}

// The changes below are for the series organizer only, until the meeting is closed. They reload
// the agenda even when they fail, since a failure usually means the page was out of date. A 403
// means the user is no longer the organizer, so the series reloads too and the page turns read-only.
// A 409 may mean the meeting was closed, so it reloads as well and the page can tell.
function useReloadAfterChange({ meetingId, seriesId }: AgendaContext) {
  const queryClient = useQueryClient()
  return async (error: Error | null) => {
    if (error instanceof ApiError && error.status === 403) {
      await queryClient.invalidateQueries({ queryKey: seriesKey(seriesId), exact: true })
    }
    if (error instanceof ApiError && error.status === 409) {
      await queryClient.invalidateQueries({ queryKey: meetingKey(meetingId), exact: true })
    }
    await queryClient.invalidateQueries({ queryKey: agendaKey(meetingId) })
  }
}

export function useAddAgendaItem(context: AgendaContext) {
  const reload = useReloadAfterChange(context)
  return useMutation({
    mutationFn: (title: string) =>
      apiFetch<AgendaItem>(agendaPath(context.meetingId), {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({ title }),
      }),
    onSettled: (_data, error) => reload(error),
  })
}

export function useRenameAgendaItem(context: AgendaContext) {
  const reload = useReloadAfterChange(context)
  return useMutation({
    mutationFn: ({ itemId, title }: { itemId: string; title: string }) =>
      apiFetch<AgendaItem>(itemPath(itemId), {
        method: 'PUT',
        headers: JSON_HEADERS,
        body: JSON.stringify({ title }),
      }),
    onSettled: (_data, error) => reload(error),
  })
}

export function useRemoveAgendaItem(context: AgendaContext) {
  const reload = useReloadAfterChange(context)
  return useMutation({
    mutationFn: (itemId: string) => apiFetch<void>(itemPath(itemId), { method: 'DELETE' }),
    onSettled: (_data, error) => reload(error),
  })
}

// Takes every item in the new order, and shows that order right away so a drag doesn't jump back.
export function useReorderAgenda(context: AgendaContext) {
  const queryClient = useQueryClient()
  const reload = useReloadAfterChange(context)
  const key = agendaKey(context.meetingId)
  return useMutation({
    mutationFn: (items: AgendaItem[]) =>
      apiFetch<AgendaItem[]>(`${agendaPath(context.meetingId)}/order`, {
        method: 'PUT',
        headers: JSON_HEADERS,
        body: JSON.stringify({ itemIds: items.map((item) => item.id) }),
      }),
    onMutate: async (items) => {
      await queryClient.cancelQueries({ queryKey: key })
      queryClient.setQueryData(key, items)
    },
    onSettled: (_data, error) => reload(error),
  })
}

// Writes a change into the loaded agenda right away, so the page shows it before the save answers.
function usePatchItem(meetingId: string) {
  const queryClient = useQueryClient()
  const key = agendaKey(meetingId)
  return async (itemId: string, change: Partial<AgendaItem>) => {
    await queryClient.cancelQueries({ queryKey: key })
    queryClient.setQueryData<AgendaItem[]>(key, (items) =>
      items?.map((item) => (item.id === itemId ? { ...item, ...change } : item)),
    )
  }
}

// While the meeting runs. Saves run one at a time, in order, so an older text can't replace a newer
// one, and the agenda only reloads after the last of them, for the same reason.
export function useSaveNotes(context: AgendaContext) {
  const queryClient = useQueryClient()
  const patchItem = usePatchItem(context.meetingId)
  const reload = useReloadAfterChange(context)
  const mutationKey = notesMutationKey(context.meetingId)
  return useMutation({
    mutationKey,
    scope: { id: `save-notes-${context.meetingId}` },
    mutationFn: ({ itemId, notes }: { itemId: string; notes: string }) =>
      apiFetch<AgendaItem>(`${itemPath(itemId)}/notes`, {
        method: 'PUT',
        headers: JSON_HEADERS,
        body: JSON.stringify({ notes }),
      }),
    onMutate: ({ itemId, notes }) => patchItem(itemId, { notes }),
    onSettled: (_data, error) => {
      if (error || queryClient.isMutating({ mutationKey }) === 1) {
        return reload(error)
      }
    },
  })
}

// How many notes are still being saved, so the meeting isn't closed before they arrive.
export function useSavingNotesCount(meetingId: string) {
  return useIsMutating({ mutationKey: notesMutationKey(meetingId) })
}

// While the meeting runs.
export function useMarkAgendaItem(context: AgendaContext) {
  const patchItem = usePatchItem(context.meetingId)
  const reload = useReloadAfterChange(context)
  return useMutation({
    mutationFn: ({ itemId, status }: { itemId: string; status: AgendaItemStatus }) =>
      apiFetch<AgendaItem>(`${itemPath(itemId)}/status`, {
        method: 'PUT',
        headers: JSON_HEADERS,
        body: JSON.stringify({ status }),
      }),
    onMutate: ({ itemId, status }) => patchItem(itemId, { status }),
    onSettled: (_data, error) => reload(error),
  })
}
