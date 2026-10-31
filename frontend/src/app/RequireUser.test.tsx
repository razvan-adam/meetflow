import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ANA, loggedOut, mockApi, renderApp } from '../test/renderApp.tsx'

describe('pages that need a logged-in user', () => {
  it('send a logged-out user to the login page', async () => {
    mockApi({ 'GET /api/users/me': loggedOut })

    const router = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/log-in')
  })

  it('show the logged-in user and a way to log out', async () => {
    mockApi({ 'GET /api/users/me': () => Response.json(ANA) })

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Your tasks' })).toBeInTheDocument()
    expect(screen.getByText('Ana Costa')).toBeInTheDocument()
  })

  it('log out and return to the login page', async () => {
    const fetchMock = mockApi({
      'GET /api/users/me': () => Response.json(ANA),
      'DELETE /api/session': () => new Response(null, { status: 204 }),
    })
    renderApp('/')

    fireEvent.click(await screen.findByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/session',
      expect.objectContaining({ method: 'DELETE' }),
    )
  })

  it('say so when the server cannot be reached', async () => {
    // The Vite dev proxy answers 502 when the backend isn't running.
    mockApi({ 'GET /api/users/me': () => new Response(null, { status: 502 }) })

    renderApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('can’t reach the server')
  })
})

describe('sign-up and login pages', () => {
  it('send a logged-in user to the dashboard', async () => {
    mockApi({ 'GET /api/users/me': () => Response.json(ANA) })

    const router = renderApp('/log-in')

    expect(await screen.findByRole('heading', { name: 'Your tasks' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })
})
