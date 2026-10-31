import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Invitation, InvitationLink } from '../api/invitations.ts'
import type { Team } from '../api/teams.ts'
import {
  ANA,
  loggedOut,
  mockApi,
  noSeries,
  noTeams,
  renderApp,
  requestBody,
} from '../test/renderApp.tsx'

const OPS: Team = { id: '6d0c1a52-3c1e-4f0b-9a57-2d4b8e7f1c10', name: 'Ops team', role: 'ADMIN' }
const INVITATIONS = `/api/teams/${OPS.id}/invitations`

const SOFIA_INVITATION: Invitation = {
  id: 'f1e2d3c4-b5a6-4789-8abc-def012345678',
  email: 'sofia@example.com',
  invitedBy: 'Ana Costa',
  createdAt: '2026-10-27T09:00:00Z',
  expiresAt: '2026-11-03T09:00:00Z',
}

const TOKEN = 'k3y-T0ken'
const LINK = `/api/invitation-links/${TOKEN}`
const SOFIA = {
  id: '2f4e6a8c-0b1d-4e3f-a5b7-c9d1e3f5a7b9',
  name: 'Sofia Martin',
  email: 'sofia@example.com',
}

const link = (status: InvitationLink['status']) => () =>
  Response.json({ teamName: 'Ops team', invitedBy: 'Ana Costa', email: SOFIA.email, status })

describe('inviting colleagues from the team page', () => {
  it('sends an invitation and lists it as pending', async () => {
    let pending: Invitation[] = []
    const fetchMock = mockApi({
      'GET /api/users/me': () => Response.json(ANA),
      'GET /api/teams': () => Response.json([OPS]),
      [`GET /api/teams/${OPS.id}`]: () => Response.json(OPS),
      [`GET /api/teams/${OPS.id}/meeting-series`]: noSeries,
      [`GET ${INVITATIONS}`]: () => Response.json(pending),
      [`POST ${INVITATIONS}`]: () => {
        pending = [SOFIA_INVITATION]
        return Response.json(SOFIA_INVITATION, { status: 201 })
      },
    })
    renderApp(`/teams/${OPS.id}`)

    expect(await screen.findByText('No pending invitations.')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Email to invite'), {
      target: { value: ' sofia@example.com ' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Send invitation' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Invitation sent to sofia@example.com',
    )
    const list = await screen.findByRole('list')
    expect(within(list).getByText('sofia@example.com')).toBeInTheDocument()
    expect(within(list).getByText(/by Ana Costa/)).toBeInTheDocument()
    expect(requestBody(fetchMock, `POST ${INVITATIONS}`)).toEqual({ email: 'sofia@example.com' })
  })

  it('cancels a pending invitation', async () => {
    let pending = [SOFIA_INVITATION]
    const fetchMock = mockApi({
      'GET /api/users/me': () => Response.json(ANA),
      'GET /api/teams': () => Response.json([OPS]),
      [`GET /api/teams/${OPS.id}`]: () => Response.json(OPS),
      [`GET /api/teams/${OPS.id}/meeting-series`]: noSeries,
      [`GET ${INVITATIONS}`]: () => Response.json(pending),
      [`DELETE /api/invitations/${SOFIA_INVITATION.id}`]: () => {
        pending = []
        return new Response(null, { status: 204 })
      },
    })
    renderApp(`/teams/${OPS.id}`)

    fireEvent.click(
      await screen.findByRole('button', { name: 'Cancel the invitation to sofia@example.com' }),
    )

    expect(await screen.findByText('No pending invitations.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/invitations/${SOFIA_INVITATION.id}`,
      expect.objectContaining({ method: 'DELETE' }),
    )
  })

  it('says when the person is already a member', async () => {
    mockApi({
      'GET /api/users/me': () => Response.json(ANA),
      'GET /api/teams': () => Response.json([OPS]),
      [`GET /api/teams/${OPS.id}`]: () => Response.json(OPS),
      [`GET /api/teams/${OPS.id}/meeting-series`]: noSeries,
      [`GET ${INVITATIONS}`]: () => Response.json([]),
      [`POST ${INVITATIONS}`]: () => Response.json({ status: 409 }, { status: 409 }),
    })
    renderApp(`/teams/${OPS.id}`)

    fireEvent.change(await screen.findByLabelText('Email to invite'), {
      target: { value: 'bo@example.com' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Send invitation' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('already a member')
  })

  it('is only offered to team admins', async () => {
    const member = { ...OPS, role: 'MEMBER' }
    mockApi({
      'GET /api/users/me': () => Response.json(ANA),
      'GET /api/teams': () => Response.json([member]),
      [`GET /api/teams/${OPS.id}`]: () => Response.json(member),
      [`GET /api/teams/${OPS.id}/meeting-series`]: noSeries,
    })
    renderApp(`/teams/${OPS.id}`)

    expect(await screen.findByText('You are a member')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Invite colleagues' })).not.toBeInTheDocument()
  })
})

describe('joining from an invitation link', () => {
  it('signs up with the invited address and joins the team', async () => {
    let user: typeof SOFIA | null = null
    const fetchMock = mockApi({
      'GET /api/users/me': () => (user ? Response.json(user) : loggedOut()),
      [`GET ${LINK}`]: link('PENDING'),
      'POST /api/users': () => {
        user = SOFIA
        return Response.json(SOFIA, { status: 201 })
      },
      [`POST ${LINK}/accept`]: () => Response.json({ ...OPS, role: 'MEMBER' }),
      'GET /api/teams': () => Response.json([{ ...OPS, role: 'MEMBER' }]),
      [`GET /api/teams/${OPS.id}`]: () => Response.json({ ...OPS, role: 'MEMBER' }),
      [`GET /api/teams/${OPS.id}/meeting-series`]: noSeries,
    })
    const router = renderApp(`/join/${TOKEN}`)

    expect(await screen.findByRole('heading', { name: 'Join Ops team' })).toBeInTheDocument()
    expect(screen.getByText('Ana Costa invited you.')).toBeInTheDocument()
    expect(screen.getByLabelText('Work email')).toHaveValue('sofia@example.com')
    expect(screen.getByLabelText('Work email')).toHaveAttribute('readOnly')
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Sofia Martin' } })
    fireEvent.change(screen.getByLabelText(/^Password/), {
      target: { value: 'correct horse battery' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Create account and join' }))

    expect(await screen.findByText('You are a member')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe(`/teams/${OPS.id}`)
    expect(requestBody(fetchMock, 'POST /api/users')).toEqual({
      name: 'Sofia Martin',
      email: 'sofia@example.com',
      password: 'correct horse battery',
    })
  })

  it('logs in with an existing account and joins', async () => {
    let user: typeof SOFIA | null = null
    const fetchMock = mockApi({
      'GET /api/users/me': () => (user ? Response.json(user) : loggedOut()),
      [`GET ${LINK}`]: link('PENDING'),
      'POST /api/session': () => {
        user = SOFIA
        return Response.json(SOFIA)
      },
      [`POST ${LINK}/accept`]: () => Response.json({ ...OPS, role: 'MEMBER' }),
      'GET /api/teams': () => Response.json([{ ...OPS, role: 'MEMBER' }]),
      [`GET /api/teams/${OPS.id}`]: () => Response.json({ ...OPS, role: 'MEMBER' }),
      [`GET /api/teams/${OPS.id}/meeting-series`]: noSeries,
    })
    renderApp(`/join/${TOKEN}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Log in to join' }))
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: 'correct horse battery' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Log in and join' }))

    expect(await screen.findByText('You are a member')).toBeInTheDocument()
    expect(requestBody(fetchMock, 'POST /api/session')).toEqual({
      email: 'sofia@example.com',
      password: 'correct horse battery',
    })
  })

  it('joins straight away when already logged in with the invited address', async () => {
    mockApi({
      'GET /api/users/me': () => Response.json(SOFIA),
      [`GET ${LINK}`]: link('PENDING'),
      [`POST ${LINK}/accept`]: () => Response.json({ ...OPS, role: 'MEMBER' }),
      'GET /api/teams': () => Response.json([{ ...OPS, role: 'MEMBER' }]),
      [`GET /api/teams/${OPS.id}`]: () => Response.json({ ...OPS, role: 'MEMBER' }),
      [`GET /api/teams/${OPS.id}/meeting-series`]: noSeries,
    })
    renderApp(`/join/${TOKEN}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Join Ops team' }))

    expect(await screen.findByText('You are a member')).toBeInTheDocument()
  })

  it('explains when logged in with another address', async () => {
    mockApi({
      'GET /api/users/me': () => Response.json(ANA),
      [`GET ${LINK}`]: link('PENDING'),
      'GET /api/teams': noTeams,
    })
    renderApp(`/join/${TOKEN}`)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This invitation is for sofia@example.com, but you’re logged in as ana@example.com.',
    )
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
  })

  it.each([
    ['EXPIRED', 'This invitation has expired.'],
    ['ACCEPTED', 'This invitation was already used.'],
  ] as const)('says when the link is %s', async (status, message) => {
    mockApi({ 'GET /api/users/me': loggedOut, [`GET ${LINK}`]: link(status) })
    renderApp(`/join/${TOKEN}`)

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('says when the link is not valid', async () => {
    mockApi({
      'GET /api/users/me': loggedOut,
      [`GET ${LINK}`]: () => Response.json({ status: 404 }, { status: 404 }),
    })
    renderApp(`/join/${TOKEN}`)

    expect(await screen.findByRole('alert')).toHaveTextContent('This invitation link isn’t valid.')
  })
})
