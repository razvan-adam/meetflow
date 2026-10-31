import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ANA, loggedOut, mockApi, renderApp, requestBody } from '../test/renderApp.tsx'

function fillInAndSubmit(password: string) {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'ana@example.com' } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('LogInPage', () => {
  it('logs in and lands on the dashboard', async () => {
    const fetchMock = mockApi({
      'GET /api/users/me': loggedOut,
      'POST /api/session': () => Response.json(ANA),
    })
    renderApp('/log-in')
    await screen.findByRole('heading', { name: 'Log in' })

    fillInAndSubmit('correct horse battery')

    expect(await screen.findByRole('heading', { name: 'Your tasks' })).toBeInTheDocument()
    expect(requestBody(fetchMock, 'POST /api/session')).toEqual({
      email: 'ana@example.com',
      password: 'correct horse battery',
    })
  })

  it('says when the email or password is wrong', async () => {
    mockApi({
      'GET /api/users/me': loggedOut,
      'POST /api/session': () => Response.json({ status: 401 }, { status: 401 }),
    })
    renderApp('/log-in')
    await screen.findByRole('heading', { name: 'Log in' })

    fillInAndSubmit('wrong password')

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.')
  })
})
