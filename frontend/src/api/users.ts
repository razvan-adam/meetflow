import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, apiFetch } from './client.ts'

export interface User {
  id: string
  name: string
  email: string
}

export interface SignUp {
  name: string
  email: string
  password: string
}

export const CURRENT_USER = ['currentUser']

// null when nobody is logged in: the API answers 401.
async function fetchCurrentUser(): Promise<User | null> {
  try {
    return await apiFetch<User>('/users/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

export function useCurrentUser() {
  return useQuery({ queryKey: CURRENT_USER, queryFn: fetchCurrentUser, retry: false })
}

// Signing up also logs in.
export function useSignUp() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (signUp: SignUp) =>
      apiFetch<User>('/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(signUp),
      }),
    onSuccess: (user) => queryClient.setQueryData(CURRENT_USER, user),
  })
}
