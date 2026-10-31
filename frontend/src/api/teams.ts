import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from './client.ts'

export interface Team {
  id: string
  name: string
  // The current user's role in the team.
  role: 'ADMIN' | 'MEMBER'
}

export const TEAMS = ['teams']

// The current user's teams, sorted by name.
export function useTeams() {
  return useQuery({ queryKey: TEAMS, queryFn: () => apiFetch<Team[]>('/teams') })
}

export function useTeam(teamId: string) {
  return useQuery({
    queryKey: [...TEAMS, teamId],
    queryFn: () => apiFetch<Team>(`/teams/${encodeURIComponent(teamId)}`),
  })
}

export function useCreateTeam() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) =>
      apiFetch<Team>('/teams', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name }),
      }),
    onSuccess: (team) => {
      queryClient.setQueryData([...TEAMS, team.id], team)
      return queryClient.invalidateQueries({ queryKey: TEAMS, exact: true })
    },
  })
}
