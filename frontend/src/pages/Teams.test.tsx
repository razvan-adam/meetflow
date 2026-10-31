import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Team } from '../api/teams.ts'
import { ANA, mockApi, noTeams, renderApp, requestBody } from '../test/renderApp.tsx'

const OPS: Team = { id: '6d0c1a52-3c1e-4f0b-9a57-2d4b8e7f1c10', name: 'Ops team', role: 'ADMIN' }
const LEADERSHIP: Team = {
  id: 'a3e9b7c4-1f2d-4e6a-8b5c-7d9e0f1a2b3c',
  name: 'Leadership',
  role: 'MEMBER',
}

const loggedIn = () => Response.json(ANA)

describe('creating a team', () => {
  it('is offered on the dashboard while the user has no team', async () => {
    mockApi({ 'GET /api/users/me': loggedIn, 'GET /api/teams': noTeams })
    const router = renderApp('/')

    // The top bar offers it too.
    const main = await screen.findByRole('main')
    fireEvent.click(await within(main).findByRole('link', { name: 'Create a team' }))

    expect(await screen.findByRole('heading', { name: 'Create a team' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/teams/new')
  })

  it('creates the team and opens it, with the user as admin', async () => {
    let teams: Team[] = []
    const fetchMock = mockApi({
      'GET /api/users/me': loggedIn,
      'GET /api/teams': () => Response.json(teams),
      'POST /api/teams': () => {
        teams = [OPS]
        return Response.json(OPS, { status: 201 })
      },
    })
    const router = renderApp('/teams/new')

    fireEvent.change(await screen.findByLabelText('Team name'), {
      target: { value: ' Ops team ' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Create team' }))

    expect(await screen.findByRole('heading', { name: 'Ops team' })).toBeInTheDocument()
    expect(screen.getByText('You are an admin')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe(`/teams/${OPS.id}`)
    expect(requestBody(fetchMock, 'POST /api/teams')).toEqual({ name: 'Ops team' })
    // The new team shows up in the switcher.
    expect(await screen.findByRole('option', { name: 'Ops team' })).toBeInTheDocument()
  })

  it('says when the team could not be created', async () => {
    mockApi({
      'GET /api/users/me': loggedIn,
      'GET /api/teams': noTeams,
      'POST /api/teams': () => new Response(null, { status: 500 }),
    })
    renderApp('/teams/new')

    fireEvent.change(await screen.findByLabelText('Team name'), { target: { value: 'Ops' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create team' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })
})

describe('switching teams', () => {
  it('lists the user’s teams in the top bar and opens the chosen one', async () => {
    mockApi({
      'GET /api/users/me': loggedIn,
      'GET /api/teams': () => Response.json([LEADERSHIP, OPS]),
      [`GET /api/teams/${OPS.id}`]: () => Response.json(OPS),
      [`GET /api/teams/${LEADERSHIP.id}`]: () => Response.json(LEADERSHIP),
    })
    const router = renderApp(`/teams/${OPS.id}`)

    const switcher = await screen.findByRole('combobox', { name: 'Team' })
    expect(await screen.findByRole('heading', { name: 'Ops team' })).toBeInTheDocument()
    expect(switcher).toHaveValue(OPS.id)

    fireEvent.change(switcher, { target: { value: LEADERSHIP.id } })

    expect(await screen.findByRole('heading', { name: 'Leadership' })).toBeInTheDocument()
    expect(screen.getByText('You are a member')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe(`/teams/${LEADERSHIP.id}`)
  })

  it('offers to create another team', async () => {
    mockApi({ 'GET /api/users/me': loggedIn, 'GET /api/teams': () => Response.json([OPS]) })
    const router = renderApp('/')

    fireEvent.change(await screen.findByRole('combobox', { name: 'Team' }), {
      target: { value: 'new' },
    })

    expect(await screen.findByRole('heading', { name: 'Create a team' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/teams/new')
  })
})

describe('the team page', () => {
  it('says so when the team is not one of the user’s', async () => {
    mockApi({
      'GET /api/users/me': loggedIn,
      'GET /api/teams': () => Response.json([OPS]),
      [`GET /api/teams/${LEADERSHIP.id}`]: () => Response.json({ status: 404 }, { status: 404 }),
    })
    renderApp(`/teams/${LEADERSHIP.id}`)

    expect(await screen.findByRole('alert')).toHaveTextContent('you’re not a member of it')
  })
})
