import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { vi } from 'vitest'
import { routes } from '../app/router.tsx'

// Renders the whole app (routes, guards, layout) at the given path.
export function renderApp(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

export const ANA = {
  id: '0b4d6f0e-5a8e-4c43-9b8e-3f2c1d0a9e71',
  name: 'Ana Costa',
  email: 'ana@example.com',
}

// Answers fetch by "METHOD /api/path"; any other request fails the test.
export function mockApi(responses: Record<string, () => Response>) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const request = `${init?.method ?? 'GET'} ${url}`
    const respond = responses[request]
    if (!respond) {
      return Promise.reject(new Error(`Unexpected request: ${request}`))
    }
    return Promise.resolve(respond())
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export const loggedOut = () => new Response(null, { status: 401 })

export function requestBody(fetchMock: ReturnType<typeof mockApi>, request: string): unknown {
  const call = fetchMock.mock.calls.find(
    ([url, init]) => `${init?.method ?? 'GET'} ${url}` === request,
  )
  return JSON.parse(String(call?.[1]?.body))
}
