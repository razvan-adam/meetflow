import { useQuery } from '@tanstack/react-query'
import { ApiError, apiFetch } from './client.ts'

export interface Health {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'
}

async function fetchHealth(): Promise<Health> {
  try {
    return await apiFetch<Health>('/health')
  } catch (error) {
    // The health check answers 503 when the backend is running but not healthy (e.g. the database
    // is down): the API is reachable, so this is a result, not a failed request.
    if (error instanceof ApiError && error.status === 503) {
      return { status: 'DOWN' }
    }
    throw error
  }
}

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: fetchHealth,
    retry: false,
  })
}
