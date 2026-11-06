import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { apiFetch } from './client.ts'

export interface HistoryMeeting {
  id: string
  // The date it was held, YYYY-MM-DD, as in its minutes.
  date: string
  // All of its decisions, in agenda order.
  decisions: string[]
  // The tasks created in it.
  taskCount: number
}

export interface HistorySeries {
  id: string
  name: string
  // Newest first.
  meetings: HistoryMeeting[]
}

// The team's closed meetings by series, the series with the newest meeting first (MF-23). With a
// search, only meetings whose series name or one of whose decisions contains every word. While a
// new search loads, the previous results stay on screen.
export function useHistory(teamId: string, search: string) {
  const path = `/teams/${encodeURIComponent(teamId)}/history`
  return useQuery({
    queryKey: ['teams', teamId, 'history', search],
    queryFn: () =>
      apiFetch<HistorySeries[]>(search ? `${path}?q=${encodeURIComponent(search)}` : path),
    placeholderData: keepPreviousData,
  })
}
