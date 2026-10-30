import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { HomePage } from './HomePage.tsx'

function renderHomePage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <HomePage />
    </QueryClientProvider>,
  )
}

describe('HomePage', () => {
  it('shows the API as up when the health check succeeds', async () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ status: 'UP' }))
    vi.stubGlobal('fetch', fetchMock)

    renderHomePage()

    expect(await screen.findByText('Up')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/health', expect.anything())
  })

  it('shows the API as down when the health check answers 503', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(Response.json({ status: 'DOWN' }, { status: 503 })),
    )

    renderHomePage()

    expect(await screen.findByText('Down')).toBeInTheDocument()
  })

  // The Vite dev proxy answers 502 when the backend isn't running.
  it('shows the API as unreachable when the backend does not answer', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 502 })))

    renderHomePage()

    expect(await screen.findByText('Unreachable')).toBeInTheDocument()
  })

  it('shows the API as unreachable when the request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    renderHomePage()

    expect(await screen.findByText('Unreachable')).toBeInTheDocument()
  })
})
