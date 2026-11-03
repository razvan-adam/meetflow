import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Meeting } from '../api/meetings.ts'
import type { Series } from '../api/series.ts'
import type { Task } from '../api/tasks.ts'
import type { Team } from '../api/teams.ts'
import { ANA, mockApi, renderApp, requestBody } from '../test/renderApp.tsx'

const OPS: Team = { id: '6d0c1a52-3c1e-4f0b-9a57-2d4b8e7f1c10', name: 'Ops team', role: 'MEMBER' }
const BO = { id: '9c8b7a6d-5e4f-4321-8765-0fedcba98765', name: 'Bo Lind' }
const WEEKLY: Series = {
  id: '3b5d7f9a-1c2e-4a6b-8d0f-2e4a6c8e0a1b',
  teamId: OPS.id,
  name: 'Weekly ops sync',
  organizerId: ANA.id,
  organizerName: ANA.name,
}
const BO_ORGANIZES: Series = { ...WEEKLY, organizerId: BO.id, organizerName: BO.name }
const SCHEDULED: Meeting = {
  id: '7e6d5c4b-3a29-4180-9f8e-7d6c5b4a3928',
  teamId: OPS.id,
  seriesId: WEEKLY.id,
  scheduledAt: '2026-11-09T09:30:00.000Z',
  status: 'SCHEDULED',
  closedAt: null,
  participants: [{ id: ANA.id, name: ANA.name }],
}
const RUNNING: Meeting = { ...SCHEDULED, status: 'IN_PROGRESS' }
const CLOSED: Meeting = { ...SCHEDULED, status: 'CLOSED', closedAt: '2026-11-09T10:14:00.000Z' }
const CONTRACT: Task = {
  id: '44444444-4444-4444-8444-444444444444',
  teamId: OPS.id,
  seriesId: WEEKLY.id,
  meetingId: '55555555-5555-4555-8555-555555555555',
  agendaItemId: '11111111-1111-4111-8111-111111111111',
  decisionId: null,
  description: 'Sign the contract',
  ownerId: BO.id,
  ownerName: BO.name,
  deadline: '2999-11-20',
  status: 'OPEN',
  deletion: null,
}

const MEETING = `/api/meetings/${SCHEDULED.id}`
const CARRIED = `${MEETING}/carried-over-tasks`
const TASK = `/api/tasks/${CONTRACT.id}`

function api(overrides: Record<string, () => Response>) {
  return mockApi({
    'GET /api/users/me': () => Response.json(ANA),
    'GET /api/teams': () => Response.json([OPS]),
    [`GET /api/teams/${OPS.id}`]: () => Response.json(OPS),
    [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(WEEKLY),
    [`GET /api/meeting-series/${WEEKLY.id}/meetings`]: () => Response.json([CLOSED]),
    [`GET ${MEETING}`]: () => Response.json(SCHEDULED),
    [`GET ${MEETING}/agenda-items`]: () => Response.json([]),
    [`GET ${MEETING}/decisions`]: () => Response.json([]),
    [`GET ${MEETING}/tasks`]: () => Response.json([]),
    [`GET ${CARRIED}`]: () => Response.json([CONTRACT]),
    ...overrides,
  })
}

const carriedList = () => screen.findByRole('list', { name: 'Carried-over tasks' })
const deletedList = () => screen.findByRole('list', { name: 'Removed carried-over tasks' })
const DELETION = { byName: ANA.name, at: '2026-11-16T09:40:00.000Z', meetingScheduledAt: null }

describe('carried-over tasks', () => {
  it('shows them above the agenda of a scheduled meeting, with no way to remove them', async () => {
    api({})
    renderApp(`/meetings/${SCHEDULED.id}`)

    const list = await carriedList()
    expect(within(list).getByRole('link', { name: 'Sign the contract' })).toHaveAttribute(
      'href',
      `/tasks/${CONTRACT.id}`,
    )
    expect(screen.getByRole('heading', { name: 'Open commitments' })).toBeVisible()
    expect(screen.getByText(/They stay on the agenda until they’re done/)).toBeVisible()
    const agenda = await screen.findByRole('heading', { name: 'Agenda items' })
    expect(
      screen.getByRole('heading', { name: 'Open commitments' }).compareDocumentPosition(agenda) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
    expect(within(list).queryByRole('button')).not.toBeInTheDocument()
  })

  it('leaves the section out when nothing carried over', async () => {
    api({ [`GET ${CARRIED}`]: () => Response.json([]) })
    renderApp(`/meetings/${SCHEDULED.id}`)

    expect(await screen.findByRole('heading', { name: 'Agenda items' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Open commitments' })).not.toBeInTheDocument()
  })

  it('highlights overdue tasks and says how late they are', async () => {
    const overdue = { ...CONTRACT, deadline: '2020-01-01' }
    api({ [`GET ${CARRIED}`]: () => Response.json([overdue]) })
    renderApp(`/meetings/${SCHEDULED.id}`)

    const list = await carriedList()
    expect(within(list).getByText(/days overdue/)).toBeVisible()
    expect(within(list).getByRole('listitem')).toHaveAttribute('data-overdue')
  })

  it('takes a task marked done before the meeting starts off the agenda', async () => {
    let carried = [CONTRACT]
    const fetchMock = api({
      [`GET ${CARRIED}`]: () => Response.json(carried),
      [`PUT ${TASK}/status`]: () => {
        carried = []
        return Response.json({ ...CONTRACT, status: 'DONE' })
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.change(await screen.findByRole('combobox', { name: 'Status of Sign the contract' }), {
      target: { value: 'DONE' },
    })

    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Open commitments' })).not.toBeInTheDocument(),
    )
    expect(requestBody(fetchMock, `PUT ${TASK}/status`)).toEqual({ status: 'DONE' })
  })

  it('lets the organizer reword and delete them while the meeting runs', async () => {
    let carried = [CONTRACT]
    const fetchMock = api({
      [`GET ${MEETING}`]: () => Response.json(RUNNING),
      [`GET ${CARRIED}`]: () => Response.json(carried),
      [`PUT ${TASK}/description`]: () => {
        carried = [{ ...CONTRACT, description: 'Sign the new contract' }]
        return Response.json(carried[0])
      },
      [`DELETE ${TASK}`]: () => {
        carried = [{ ...carried[0]!, deletion: DELETION }]
        return new Response(null, { status: 204 })
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Edit task: Sign the contract' }))
    fireEvent.change(screen.getByRole('textbox', { name: 'Task description' }), {
      target: { value: 'Sign the new contract' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('link', { name: 'Sign the new contract' })).toBeVisible()
    expect(requestBody(fetchMock, `PUT ${TASK}/description`)).toEqual({
      description: 'Sign the new contract',
    })

    fireEvent.click(screen.getByRole('button', { name: 'Remove task: Sign the new contract' }))
    fireEvent.click(screen.getByRole('button', { name: 'Remove task' }))

    // It moves to its own section at once, and nothing is left to change on it.
    const row = within(await deletedList()).getByRole('listitem')
    expect(row).toHaveAttribute('data-deleted')
    expect(within(row).queryByRole('button')).not.toBeInTheDocument()
    expect(within(row).queryByRole('combobox')).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Carried-over tasks' })).not.toBeInTheDocument()
    expect(screen.getByText('No open commitments left.')).toBeVisible()
  })

  it('lets only the owner change the status for members who don’t organize the series', async () => {
    const mine = { ...CONTRACT, ownerId: ANA.id, ownerName: ANA.name }
    api({
      [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(BO_ORGANIZES),
      [`GET ${MEETING}`]: () => Response.json(RUNNING),
      [`GET ${CARRIED}`]: () => Response.json([mine, { ...CONTRACT, description: 'Book a room' }]),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    expect(
      await screen.findByRole('combobox', { name: 'Status of Sign the contract' }),
    ).toBeVisible()
    expect(
      screen.queryByRole('combobox', { name: 'Status of Book a room' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Edit task/ })).not.toBeInTheDocument()
  })

  it('lists deleted tasks apart from the others on a closed meeting', async () => {
    const deleted: Task = { ...CONTRACT, description: 'Book a room', deletion: DELETION }
    api({
      [`GET ${MEETING}`]: () => Response.json(CLOSED),
      [`GET ${CARRIED}`]: () => Response.json([CONTRACT, deleted]),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    expect(await screen.findByRole('heading', { name: 'Commitments reviewed' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Removed commitments' })).toBeVisible()
    const current = within(await carriedList()).getByRole('listitem')
    expect(within(current).getByRole('link', { name: 'Sign the contract' })).toBeVisible()
    const row = within(await deletedList()).getByRole('listitem')
    expect(within(row).getByRole('link', { name: 'Book a room' })).toBeVisible()
    expect(within(row).getByText(/Removed by Ana Costa/)).toBeVisible()
  })

  it('leaves the removed section out when nothing was removed', async () => {
    api({ [`GET ${MEETING}`]: () => Response.json(RUNNING) })
    renderApp(`/meetings/${SCHEDULED.id}`)

    expect(await carriedList()).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Removed commitments' })).not.toBeInTheDocument()
  })

  it('says so when they can’t be loaded', async () => {
    api({ [`GET ${CARRIED}`]: () => new Response(null, { status: 500 }) })
    renderApp(`/meetings/${SCHEDULED.id}`)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The open commitments couldn’t be loaded.',
    )
  })
})
