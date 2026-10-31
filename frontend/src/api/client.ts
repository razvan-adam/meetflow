// Thin wrapper over fetch for the MeetFlow API. The session cookie is sent automatically (same origin);
// for requests that change data, Spring Security expects the XSRF-TOKEN cookie echoed in a header.

export class ApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`API request failed with status ${status}`)
    this.status = status
  }
}

// Query retry rule: a 4xx (not found, no access, logged out) won't change by asking again, so it is
// shown at once; server and network errors are retried up to 3 times.
export function shouldRetry(failureCount: number, error: Error): boolean {
  if (error instanceof ApiError && error.status < 500) {
    return false
  }
  return failureCount < 3
}

const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS']

function csrfToken(): string | undefined {
  return document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith('XSRF-TOKEN='))
    ?.slice('XSRF-TOKEN='.length)
}

// Resolves to undefined for responses without a body (204 No Content); call those as apiFetch<void>.
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  const token = csrfToken()
  if (token && !SAFE_METHODS.includes(init.method?.toUpperCase() ?? 'GET')) {
    headers.set('X-XSRF-TOKEN', decodeURIComponent(token))
  }

  const response = await fetch(`/api${path}`, { ...init, headers })
  if (!response.ok) {
    throw new ApiError(response.status)
  }
  const body = await response.text()
  return (body ? JSON.parse(body) : undefined) as T
}
