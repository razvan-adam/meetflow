import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from './client.ts'
import { TEAMS, type Team } from './teams.ts'

// A pending invitation, as team admins see it.
export interface Invitation {
  id: string
  email: string
  invitedBy: string
  createdAt: string
  expiresAt: string
}

// What the holder of an invitation link sees before joining.
export interface InvitationLink {
  teamName: string
  invitedBy: string
  email: string
  status: 'PENDING' | 'EXPIRED' | 'ACCEPTED'
}

const invitationsKey = (teamId: string) => [...TEAMS, teamId, 'invitations']

// Admins only; the API answers 403 to other members.
export function usePendingInvitations(teamId: string) {
  return useQuery({
    queryKey: invitationsKey(teamId),
    queryFn: () => apiFetch<Invitation[]>(`/teams/${encodeURIComponent(teamId)}/invitations`),
  })
}

export function useInvite(teamId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (email: string) =>
      apiFetch<Invitation>(`/teams/${encodeURIComponent(teamId)}/invitations`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: invitationsKey(teamId) }),
  })
}

export function useCancelInvitation(teamId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (invitationId: string) =>
      apiFetch<void>(`/invitations/${encodeURIComponent(invitationId)}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: invitationsKey(teamId) }),
  })
}

const linkKey = (token: string) => ['invitationLink', token]

export function useInvitationLink(token: string) {
  return useQuery({
    queryKey: linkKey(token),
    queryFn: () => apiFetch<InvitationLink>(`/invitation-links/${encodeURIComponent(token)}`),
  })
}

export function useAcceptInvitation(token: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiFetch<Team>(`/invitation-links/${encodeURIComponent(token)}/accept`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: TEAMS }),
    // An expired or used link answers 410; show its current state.
    onError: () => queryClient.invalidateQueries({ queryKey: linkKey(token) }),
  })
}
