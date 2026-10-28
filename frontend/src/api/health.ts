import { useQuery } from '@tanstack/react-query'
import { apiFetch } from './client.ts'

export interface Health {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'
}

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: () => apiFetch<Health>('/health'),
    retry: false,
  })
}
