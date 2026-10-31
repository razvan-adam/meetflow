import { describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, shouldRetry } from './client.ts'

describe('shouldRetry', () => {
  it('does not retry client errors such as 404', () => {
    expect(shouldRetry(0, new ApiError(404))).toBe(false)
    expect(shouldRetry(0, new ApiError(401))).toBe(false)
  })

  it('retries server and network errors up to 3 times', () => {
    expect(shouldRetry(0, new ApiError(502))).toBe(true)
    expect(shouldRetry(2, new TypeError('Failed to fetch'))).toBe(true)
    expect(shouldRetry(3, new ApiError(502))).toBe(false)
  })
})

describe('apiFetch', () => {
  it('returns the parsed JSON body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ name: 'Ops' })))

    await expect(apiFetch('/teams/1')).resolves.toEqual({ name: 'Ops' })
  })

  it('resolves to undefined for a 204 response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    await expect(apiFetch<void>('/tasks/1', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('throws an ApiError with the status for an error response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 404 })))

    await expect(apiFetch('/teams/1')).rejects.toEqual(new ApiError(404))
    await expect(apiFetch('/teams/1')).rejects.toHaveProperty('status', 404)
  })

  it('sends the CSRF token on requests that change data', async () => {
    document.cookie = 'XSRF-TOKEN=abc%3D'
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch<void>('/tasks/1', { method: 'DELETE' })
    await apiFetch('/teams/1')

    const [deleteHeaders, getHeaders] = fetchMock.mock.calls.map(([, init]) => init.headers)
    expect(deleteHeaders.get('X-XSRF-TOKEN')).toBe('abc=')
    expect(getHeaders.has('X-XSRF-TOKEN')).toBe(false)
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })
})
