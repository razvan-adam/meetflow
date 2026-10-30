import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from './client.ts'
import { CURRENT_USER, type User } from './users.ts'

export interface LogIn {
  email: string
  password: string
}

export function useLogIn() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (logIn: LogIn) =>
      apiFetch<User>('/session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(logIn),
      }),
    onSuccess: (user) => queryClient.setQueryData(CURRENT_USER, user),
  })
}

export function useLogOut() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiFetch<void>('/session', { method: 'DELETE' }),
    // Nobody is logged in now, which sends the app to the login page; nothing cached for the
    // previous user is kept.
    onSuccess: () => {
      queryClient.setQueryData(CURRENT_USER, null)
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== CURRENT_USER[0] })
    },
  })
}
