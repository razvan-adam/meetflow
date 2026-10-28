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

  it('shows the API as unreachable when the health check fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })))

    renderHomePage()

    expect(await screen.findByText('Unreachable')).toBeInTheDocument()
  })
})
