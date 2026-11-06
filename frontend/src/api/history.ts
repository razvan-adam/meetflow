import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query'
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

// One page: up to 20 meetings, newest first. A series can go on to the next page.
export interface HistoryPage {
  series: HistorySeries[]
  // The meeting to ask for older ones after, or null when there are no more.
  next: string | null
}

// The team's closed meetings by series, the series with the newest meeting first (MF-23), a page at
// a time. With a search, only meetings whose series name or one of whose decisions contains every
// word. While a new search loads, the previous results stay on screen.
export function useHistory(teamId: string, search: string) {
  return useInfiniteQuery({
    queryKey: ['teams', teamId, 'history', search],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams()
      if (search) {
        params.set('q', search)
      }
      if (pageParam) {
        params.set('after', pageParam)
      }
      const query = params.size > 0 ? `?${params}` : ''
      return apiFetch<HistoryPage>(`/teams/${encodeURIComponent(teamId)}/history${query}`)
    },
    initialPageParam: '',
    getNextPageParam: (last) => last.next ?? undefined,
    select: (data) => mergePages(data.pages),
    placeholderData: keepPreviousData,
  })
}

// Joins the pages into one list. A page's meetings are all older than the previous page's, so a
// series already shown continues, and a new one goes after the others.
export function mergePages(pages: HistoryPage[]): HistorySeries[] {
  const bySeries = new Map<string, HistorySeries>()
  for (const series of pages.flatMap((page) => page.series)) {
    const shown = bySeries.get(series.id)
    bySeries.set(
      series.id,
      shown ? { ...shown, meetings: [...shown.meetings, ...series.meetings] } : series,
    )
  }
  return [...bySeries.values()]
}
