import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from './client.ts'

export interface Series {
  id: string
  teamId: string
  name: string
  organizerId: string
  organizerName: string
}

const SERIES = ['series']
const teamSeriesKey = (teamId: string) => [...SERIES, 'team', teamId]
const seriesKey = (seriesId: string) => [...SERIES, seriesId]

// The team's meeting series, sorted by name.
export function useTeamSeries(teamId: string) {
  return useQuery({
    queryKey: teamSeriesKey(teamId),
    queryFn: () => apiFetch<Series[]>(`/teams/${encodeURIComponent(teamId)}/meeting-series`),
  })
}

// Doesn't fetch without a series ID.
export function useSeries(seriesId: string | undefined) {
  return useQuery({
    queryKey: seriesKey(seriesId ?? ''),
    queryFn: () => apiFetch<Series>(`/meeting-series/${encodeURIComponent(seriesId ?? '')}`),
    enabled: seriesId !== undefined,
  })
}

export function useCreateSeries(teamId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) =>
      apiFetch<Series>(`/teams/${encodeURIComponent(teamId)}/meeting-series`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name }),
      }),
    onSuccess: (series) => {
      queryClient.setQueryData(seriesKey(series.id), series)
      return queryClient.invalidateQueries({ queryKey: teamSeriesKey(teamId) })
    },
  })
}

// Team admins only.
export function useChangeOrganizer(seriesId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (organizerId: string) =>
      apiFetch<Series>(`/meeting-series/${encodeURIComponent(seriesId)}/organizer`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ organizerId }),
      }),
    onSuccess: (series) => {
      queryClient.setQueryData(seriesKey(series.id), series)
      return queryClient.invalidateQueries({ queryKey: teamSeriesKey(series.teamId) })
    },
  })
}
