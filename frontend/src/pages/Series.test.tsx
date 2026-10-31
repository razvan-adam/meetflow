import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Series } from '../api/series.ts'
import type { Member, Team } from '../api/teams.ts'
import { ANA, mockApi, renderApp, requestBody } from '../test/renderApp.tsx'

const OPS: Team = { id: '6d0c1a52-3c1e-4f0b-9a57-2d4b8e7f1c10', name: 'Ops team', role: 'ADMIN' }
const BO = { id: '9c8b7a6d-5e4f-4321-8765-0fedcba98765', name: 'Bo Lind' }
const WEEKLY: Series = {
  id: '3b5d7f9a-1c2e-4a6b-8d0f-2e4a6c8e0a1b',
  teamId: OPS.id,
  name: 'Weekly ops sync',
  organizerId: ANA.id,
  organizerName: ANA.name,
}
const MEMBERS: Member[] = [
  { id: ANA.id, name: ANA.name, role: 'ADMIN' },
  { id: BO.id, name: BO.name, role: 'MEMBER' },
]

const loggedIn = () => Response.json(ANA)

describe('meeting series on the team page', () => {
  it('lists the team’s series with their organizers', async () => {
    mockApi({
      'GET /api/users/me': loggedIn,
      'GET /api/teams': () => Response.json([OPS]),
      [`GET /api/teams/${OPS.id}`]: () => Response.json(OPS),
      [`GET /api/teams/${OPS.id}/invitations`]: () => Response.json([]),
      [`GET /api/teams/${OPS.id}/meeting-series`]: () =>
        Response.json([{ ...WEEKLY, organizerId: BO.id, organizerName: BO.name }]),
    })
    renderApp(`/teams/${OPS.id}`)

    const series = await screen.findByRole('link', { name: 'Weekly ops sync' })
    expect(series).toHaveAttribute('href', `/series/${WEEKLY.id}`)
    expect(screen.getByText('Organizer: Bo Lind')).toBeInTheDocument()
  })

  it('creates a series and opens it, with the user as organizer', async () => {
    const member = { ...OPS, role: 'MEMBER' }
    const fetchMock = mockApi({
      'GET /api/users/me': loggedIn,
      'GET /api/teams': () => Response.json([member]),
      [`GET /api/teams/${OPS.id}`]: () => Response.json(member),
      [`GET /api/teams/${OPS.id}/meeting-series`]: () => Response.json([]),
      [`POST /api/teams/${OPS.id}/meeting-series`]: () => Response.json(WEEKLY, { status: 201 }),
      [`GET /api/meeting-series/${WEEKLY.id}/meetings`]: () => Response.json([]),
    })
    const router = renderApp(`/teams/${OPS.id}`)

    expect(await screen.findByText(/No meeting series yet/)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('New series name'), {
      target: { value: ' Weekly ops sync ' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Create series' }))

    expect(await screen.findByRole('heading', { name: 'Weekly ops sync' })).toBeInTheDocument()
    expect(screen.getByText('Organized by you')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe(`/series/${WEEKLY.id}`)
    expect(requestBody(fetchMock, `POST /api/teams/${OPS.id}/meeting-series`)).toEqual({
      name: 'Weekly ops sync',
    })
    // A member who isn't an admin can't change the organizer.
    expect(screen.queryByRole('heading', { name: 'Change organizer' })).not.toBeInTheDocument()
  })
})

describe('the series page', () => {
  it('links back to the team and selects it in the team switcher', async () => {
    const SALES: Team = { id: '1f2e3d4c-5b6a-4789-8a9b-0c1d2e3f4a5b', name: 'Sales', role: 'ADMIN' }
    mockApi({
      'GET /api/users/me': loggedIn,
      'GET /api/teams': () => Response.json([OPS, SALES]),
      [`GET /api/teams/${OPS.id}`]: () => Response.json(OPS),
      [`GET /api/teams/${OPS.id}/members`]: () => Response.json(MEMBERS),
      [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(WEEKLY),
      [`GET /api/meeting-series/${WEEKLY.id}/meetings`]: () => Response.json([]),
    })
    renderApp(`/series/${WEEKLY.id}`)

    expect(await screen.findByRole('link', { name: 'Ops team' })).toHaveAttribute(
      'href',
      `/teams/${OPS.id}`,
    )
    expect(screen.getByRole('combobox', { name: 'Team' })).toHaveValue(OPS.id)
  })

  it('lets a team admin hand the organizer role to another member', async () => {
    let series = WEEKLY
    const fetchMock = mockApi({
      'GET /api/users/me': loggedIn,
      'GET /api/teams': () => Response.json([OPS]),
      [`GET /api/teams/${OPS.id}`]: () => Response.json(OPS),
      [`GET /api/teams/${OPS.id}/members`]: () => Response.json(MEMBERS),
      [`GET /api/teams/${OPS.id}/meeting-series`]: () => Response.json([series]),
      [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(series),
      [`GET /api/meeting-series/${WEEKLY.id}/meetings`]: () => Response.json([]),
      [`PUT /api/meeting-series/${WEEKLY.id}/organizer`]: () => {
        series = { ...WEEKLY, organizerId: BO.id, organizerName: BO.name }
        return Response.json(series)
      },
    })
    renderApp(`/series/${WEEKLY.id}`)

    const card = await screen.findByRole('region', { name: 'Change organizer' })
    const select = await within(card).findByRole('combobox', { name: 'New organizer' })
    expect(select).toHaveValue(ANA.id)
    fireEvent.change(select, { target: { value: BO.id } })
    fireEvent.click(within(card).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Organized by Bo Lind')).toBeInTheDocument()
    expect(requestBody(fetchMock, `PUT /api/meeting-series/${WEEKLY.id}/organizer`)).toEqual({
      organizerId: BO.id,
    })
  })

  it('says so when the series is not in one of the user’s teams', async () => {
    mockApi({
      'GET /api/users/me': loggedIn,
      'GET /api/teams': () => Response.json([OPS]),
      [`GET /api/meeting-series/${WEEKLY.id}`]: () =>
        Response.json({ status: 404 }, { status: 404 }),
    })
    renderApp(`/series/${WEEKLY.id}`)

    expect(await screen.findByRole('alert')).toHaveTextContent('isn’t in one of your teams')
  })
})
