import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ANA, loggedOut, mockApi, noTeams, renderApp, requestBody } from '../test/renderApp.tsx'

async function fillInAndSubmit() {
  await screen.findByRole('heading', { name: 'Create your account' })
  fireEvent.change(screen.getByLabelText('Name'), { target: { value: ' Ana Costa ' } })
  fireEvent.change(screen.getByLabelText('Work email'), { target: { value: 'ana@example.com' } })
  fireEvent.change(screen.getByLabelText(/^Password/), {
    target: { value: 'correct horse battery' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
}

describe('SignUpPage', () => {
  it('creates the account and lands on the dashboard', async () => {
    const fetchMock = mockApi({
      'GET /api/users/me': loggedOut,
      'POST /api/users': () => Response.json(ANA, { status: 201 }),
      'GET /api/teams': noTeams,
    })
    renderApp('/sign-up')

    await fillInAndSubmit()

    expect(await screen.findByRole('heading', { name: 'Your tasks' })).toBeInTheDocument()
    expect(screen.getByText('Ana Costa')).toBeInTheDocument()
    expect(requestBody(fetchMock, 'POST /api/users')).toEqual({
      name: 'Ana Costa',
      email: 'ana@example.com',
      password: 'correct horse battery',
    })
  })

  it('says when the email already has an account', async () => {
    mockApi({
      'GET /api/users/me': loggedOut,
      'POST /api/users': () => Response.json({ status: 409 }, { status: 409 }),
    })
    renderApp('/sign-up')

    await fillInAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'An account with this email already exists.',
    )
  })

  it('asks only for the name, email and password', async () => {
    mockApi({ 'GET /api/users/me': loggedOut })
    renderApp('/sign-up')

    await screen.findByRole('heading', { name: 'Create your account' })
    expect(screen.getAllByRole('textbox')).toHaveLength(2)
    expect(screen.getByLabelText(/^Password/)).toHaveAttribute('minLength', '12')
  })
})
