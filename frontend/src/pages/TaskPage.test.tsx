import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Decision } from '../api/decisions.ts'
import type { Meeting } from '../api/meetings.ts'
import type { Series } from '../api/series.ts'
import type { Task, TaskHistoryEntry } from '../api/tasks.ts'
import type { Member, Team } from '../api/teams.ts'
import { ANA, mockApi, renderApp, requestBody } from '../test/renderApp.tsx'
import { formatDeadline } from './taskDisplay.ts'

const OPS: Team = { id: 'ops', name: 'Ops team', role: 'MEMBER' }
const BO = { id: 'bo', name: 'Bo Lind' }
const DI = { id: 'di', name: 'Di Vega' }
const MEMBERS: Member[] = [
  { id: ANA.id, name: ANA.name, role: 'ADMIN' },
  { ...BO, role: 'MEMBER' },
  { ...DI, role: 'MEMBER' },
]
const WEEKLY: Series = {
  id: 'weekly',
  teamId: OPS.id,
  name: 'Weekly ops sync',
  organizerId: ANA.id,
  organizerName: ANA.name,
}
const RUNNING: Meeting = {
  id: 'monday',
  teamId: OPS.id,
  seriesId: WEEKLY.id,
  scheduledAt: '2026-11-02T09:30:00.000Z',
  status: 'IN_PROGRESS',
  closedAt: null,
  participants: [],
}
const CLOSED: Meeting = { ...RUNNING, status: 'CLOSED', closedAt: '2026-11-02T10:30:00.000Z' }
const PICKERS: Decision = { id: 'pickers', agendaItemId: 'budget', text: 'Hire 6 pickers' }
const CONTRACT: Task = {
  id: 'contract',
  teamId: OPS.id,
  seriesId: WEEKLY.id,
  meetingId: RUNNING.id,
  agendaItemId: 'budget',
  decisionId: PICKERS.id,
  description: 'Sign the contract',
  ownerId: BO.id,
  ownerName: BO.name,
  // Far enough ahead that the tests don't depend on today's date.
  deadline: '2999-11-20',
  status: 'OPEN',
  deletion: null,
}
const CREATED: TaskHistoryEntry = {
  changedAt: '2026-11-02T09:40:00.000Z',
  changedByName: ANA.name,
  duringMeeting: true,
  oldStatus: null,
  newStatus: 'OPEN',
  oldOwnerName: null,
  newOwnerName: BO.name,
  oldDeadline: null,
  newDeadline: '2999-11-20',
  oldDescription: null,
  newDescription: null,
  deleted: false,
}

const TASK = `/api/tasks/${CONTRACT.id}`
const SERIES_MEETINGS = `GET /api/meeting-series/${WEEKLY.id}/meetings`
const reply = (status: number) => () => Response.json({ status }, { status })

function api(overrides: Record<string, () => Response>) {
  return mockApi({
    'GET /api/users/me': () => Response.json(ANA),
    'GET /api/teams': () => Response.json([OPS]),
    [`GET /api/teams/${OPS.id}`]: () => Response.json(OPS),
    [`GET /api/teams/${OPS.id}/members`]: () => Response.json(MEMBERS),
    [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(WEEKLY),
    [`GET /api/meetings/${RUNNING.id}`]: () => Response.json(RUNNING),
    [SERIES_MEETINGS]: () => Response.json([RUNNING]),
    [`GET /api/meetings/${RUNNING.id}/decisions`]: () => Response.json([PICKERS]),
    [`GET ${TASK}`]: () => Response.json(CONTRACT),
    [`GET ${TASK}/changes`]: () => Response.json([CREATED]),
    ...overrides,
  })
}

// Ana as a member who neither organizes the series nor owns the task.
const NOT_ORGANIZER = {
  [`GET /api/meeting-series/${WEEKLY.id}`]: () =>
    Response.json({ ...WEEKLY, organizerId: DI.id, organizerName: DI.name }),
}

const statusButton = (name: string) => screen.findByRole('button', { name })
const heading = () => screen.findByRole('heading', { name: 'Sign the contract' })

describe('showing a task', () => {
  it('shows its owner, deadline, origin and history', async () => {
    api({})
    renderApp(`/tasks/${CONTRACT.id}`)

    expect(await heading()).toBeVisible()
    expect(screen.getByRole('combobox', { name: 'Team' })).toHaveValue(OPS.id)
    expect(screen.getByText('Bo Lind')).toBeVisible()
    expect(await screen.findByText('Hire 6 pickers')).toBeVisible()
    expect(await screen.findByRole('link', { name: /Weekly ops sync,/ })).toHaveAttribute(
      'href',
      `/meetings/${RUNNING.id}`,
    )
    expect(
      await screen.findByText(
        `Ana Costa created the task. Owner Bo Lind, deadline ${formatDeadline('2999-11-20')}. (during a meeting)`,
      ),
    ).toBeVisible()
  })

  it('describes each kind of change in the history', async () => {
    const base = { ...CREATED, newStatus: null, newOwnerName: null, newDeadline: null }
    api({
      [`GET ${TASK}/changes`]: () =>
        Response.json([
          { ...base, duringMeeting: false, oldStatus: 'OPEN', newStatus: 'IN_PROGRESS' },
          { ...base, oldOwnerName: BO.name, newOwnerName: DI.name },
          { ...base, oldDeadline: '2026-11-20', newDeadline: '2026-12-01' },
        ]),
    })
    renderApp(`/tasks/${CONTRACT.id}`)

    expect(await screen.findByText('Ana Costa changed status Open → In progress')).toBeVisible()
    expect(
      screen.getByText('Ana Costa changed owner Bo Lind → Di Vega (during a meeting)'),
    ).toBeVisible()
    expect(
      screen.getByText(
        `Ana Costa changed deadline ${formatDeadline('2026-11-20')} → ${formatDeadline('2026-12-01')} (during a meeting)`,
      ),
    ).toBeVisible()
  })

  it('shows how many days overdue a task past its deadline is', async () => {
    api({ [`GET ${TASK}`]: () => Response.json({ ...CONTRACT, deadline: '2020-01-06' }) })
    renderApp(`/tasks/${CONTRACT.id}`)

    const due = `Due ${formatDeadline('2020-01-06')}`
    expect(await screen.findByText(new RegExp(`^${due} · \\d+ days overdue$`))).toBeVisible()
  })

  it('shows a rewording in the history with the old and new words', async () => {
    api({
      [`GET ${TASK}/changes`]: () =>
        Response.json([
          {
            ...CREATED,
            newStatus: null,
            newOwnerName: null,
            newDeadline: null,
            oldDescription: 'Sign it',
            newDescription: 'Sign the contract',
          },
        ]),
    })
    renderApp(`/tasks/${CONTRACT.id}`)

    expect(
      await screen.findByText(
        'Ana Costa reworded the task “Sign it” → “Sign the contract” (during a meeting)',
      ),
    ).toBeVisible()
  })

  it('doesn’t show a done task as overdue', async () => {
    api({
      [`GET ${TASK}`]: () => Response.json({ ...CONTRACT, deadline: '2020-01-06', status: 'DONE' }),
    })
    renderApp(`/tasks/${CONTRACT.id}`)

    expect(await screen.findByText(`Due ${formatDeadline('2020-01-06')}`)).toBeVisible()
  })

  it('says so when the task doesn’t exist or isn’t in the user’s teams', async () => {
    api({ [`GET ${TASK}`]: reply(404) })
    renderApp(`/tasks/${CONTRACT.id}`)

    expect(await screen.findByRole('alert')).toHaveTextContent('doesn’t exist')
  })
})

describe('who can change what', () => {
  it('lets the organizer change the status, owner and deadline', async () => {
    api({})
    renderApp(`/tasks/${CONTRACT.id}`)

    expect(await statusButton('Done')).toBeEnabled()
    expect(await screen.findByRole('button', { name: 'Change owner' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Change deadline' })).toBeVisible()
    expect(screen.getByText(/because you organize this series/)).toBeVisible()
  })

  it('lets the owner change only the status', async () => {
    api({
      ...NOT_ORGANIZER,
      [`GET ${TASK}`]: () => Response.json({ ...CONTRACT, ownerId: ANA.id, ownerName: ANA.name }),
    })
    renderApp(`/tasks/${CONTRACT.id}`)

    expect(await statusButton('Done')).toBeEnabled()
    expect(await screen.findByText(/because this is your task/)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Change owner' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Change deadline' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete this task' })).not.toBeInTheDocument()
  })

  it('shows other members the task read-only', async () => {
    api(NOT_ORGANIZER)
    renderApp(`/tasks/${CONTRACT.id}`)

    expect(await screen.findByText('Status: Open')).toBeVisible()
    expect(await screen.findByText(/Only the task’s owner and the series organizer/)).toBeVisible()
    expect(screen.queryByRole('button', { name: /Change|Done|Delete/ })).not.toBeInTheDocument()
  })
})

describe('changing a task', () => {
  it('changes the status and adds it to the history', async () => {
    let task = CONTRACT
    let history = [CREATED]
    const fetchMock = api({
      [`GET ${TASK}`]: () => Response.json(task),
      [`GET ${TASK}/changes`]: () => Response.json(history),
      [`PUT ${TASK}/status`]: () => {
        task = { ...CONTRACT, status: 'DONE' }
        history = [
          {
            ...CREATED,
            oldStatus: 'OPEN',
            newStatus: 'DONE',
            newOwnerName: null,
            newDeadline: null,
          },
          CREATED,
        ]
        return Response.json(task)
      },
    })
    renderApp(`/tasks/${CONTRACT.id}`)

    fireEvent.click(await statusButton('Done'))

    await waitFor(async () =>
      expect(await statusButton('Done')).toHaveAttribute('aria-pressed', 'true'),
    )
    expect(requestBody(fetchMock, `PUT ${TASK}/status`)).toEqual({ status: 'DONE' })
    expect(await screen.findByText(/changed status Open → Done/)).toBeVisible()
  })

  it('lets the organizer reassign the task to any team member', async () => {
    let task = CONTRACT
    const fetchMock = api({
      [`GET ${TASK}`]: () => Response.json(task),
      [`PUT ${TASK}/owner`]: () => {
        task = { ...CONTRACT, ownerId: DI.id, ownerName: DI.name }
        return Response.json(task)
      },
    })
    renderApp(`/tasks/${CONTRACT.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Change owner' }))
    const owner = screen.getByRole('combobox', { name: 'Owner' })
    expect(owner).toHaveValue(BO.id)
    expect(
      within(owner)
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toEqual([ANA.name, BO.name, DI.name])
    fireEvent.change(owner, { target: { value: DI.id } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(screen.queryByRole('combobox', { name: 'Owner' })).not.toBeInTheDocument(),
    )
    expect(await screen.findByText('Di Vega')).toBeVisible()
    expect(requestBody(fetchMock, `PUT ${TASK}/owner`)).toEqual({ ownerId: DI.id })
  })

  it('lets the organizer move the deadline', async () => {
    let task = CONTRACT
    const fetchMock = api({
      [`GET ${TASK}`]: () => Response.json(task),
      [`PUT ${TASK}/deadline`]: () => {
        task = { ...CONTRACT, deadline: '2999-12-01' }
        return Response.json(task)
      },
    })
    renderApp(`/tasks/${CONTRACT.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Change deadline' }))
    fireEvent.change(screen.getByLabelText('Deadline'), { target: { value: '2999-12-01' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText(`Due ${formatDeadline('2999-12-01')}`)).toBeVisible()
    expect(requestBody(fetchMock, `PUT ${TASK}/deadline`)).toEqual({ deadline: '2999-12-01' })
  })

  it('turns read-only and says why when the organizer role has moved', async () => {
    let series = WEEKLY
    api({
      [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(series),
      [`PUT ${TASK}/status`]: () => {
        series = { ...WEEKLY, organizerId: DI.id, organizerName: DI.name }
        return reply(403)()
      },
    })
    renderApp(`/tasks/${CONTRACT.id}`)

    fireEvent.click(await statusButton('Done'))

    expect(await screen.findByRole('alert')).toHaveTextContent('can no longer change this task')
    expect(await screen.findByText('Status: Open')).toBeVisible()
  })

  it('says so when a change can’t be saved', async () => {
    api({ [`PUT ${TASK}/status`]: reply(500) })
    renderApp(`/tasks/${CONTRACT.id}`)

    fireEvent.click(await statusButton('Done'))

    expect(await screen.findByRole('alert')).toHaveTextContent('couldn’t be saved')
  })
})

describe('correcting a task while its meeting runs', () => {
  it('lets the organizer reword it', async () => {
    let task = CONTRACT
    const fetchMock = api({
      [`GET ${TASK}`]: () => Response.json(task),
      [`PUT ${TASK}/description`]: () => {
        task = { ...CONTRACT, description: 'Sign the Temporis contract' }
        return Response.json(task)
      },
    })
    renderApp(`/tasks/${CONTRACT.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Change description' }))
    const box = screen.getByRole('textbox', { name: 'Description' })
    expect(box).toHaveValue('Sign the contract')
    fireEvent.change(box, { target: { value: '  Sign the Temporis contract  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('heading', { name: 'Sign the Temporis contract' })).toBeVisible()
    expect(requestBody(fetchMock, `PUT ${TASK}/description`)).toEqual({
      description: 'Sign the Temporis contract',
    })
  })

  it('asks before deleting, starts on the safe choice and returns to the meeting', async () => {
    const fetchMock = api({
      [`DELETE ${TASK}`]: () => new Response(null, { status: 204 }),
      [`GET /api/meetings/${RUNNING.id}/agenda-items`]: () => Response.json([]),
      [`GET /api/meetings/${RUNNING.id}/tasks`]: () => Response.json([]),
    })
    const router = renderApp(`/tasks/${CONTRACT.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Delete this task' }))
    const keep = screen.getByRole('button', { name: 'Keep task' })
    expect(keep).toHaveFocus()
    fireEvent.click(keep)
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)

    fireEvent.click(await screen.findByRole('button', { name: 'Delete this task' }))
    fireEvent.click(screen.getByRole('button', { name: 'Delete task' }))

    await waitFor(() => expect(router.state.location.pathname).toBe(`/meetings/${RUNNING.id}`))
  })

  it('offers rewording and deleting while a later meeting runs', async () => {
    api({
      [`GET /api/meetings/${RUNNING.id}`]: () => Response.json(CLOSED),
      [SERIES_MEETINGS]: () => Response.json([{ ...RUNNING, id: 'later' }, CLOSED]),
    })
    renderApp(`/tasks/${CONTRACT.id}`)

    expect(await screen.findByRole('button', { name: 'Change description' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Delete this task' })).toBeVisible()
  })

  it('shows a task deleted during a later meeting read-only, with its history', async () => {
    api({
      [`GET ${TASK}`]: () =>
        Response.json({
          ...CONTRACT,
          deletion: {
            byName: ANA.name,
            at: '2026-11-09T09:00:00.000Z',
            meetingScheduledAt: '2026-11-09T08:30:00.000Z',
          },
        }),
      [`GET ${TASK}/changes`]: () =>
        Response.json([{ ...CREATED, newStatus: null, deleted: true }, CREATED]),
    })
    renderApp(`/tasks/${CONTRACT.id}`)

    expect(await screen.findByText('This task was deleted.')).toBeVisible()
    expect(screen.getByText(/Deleted by Ana Costa during the meeting of/)).toBeVisible()
    expect(await screen.findByText(/Ana Costa deleted the task/)).toBeVisible()
    expect(await screen.findByRole('link', { name: /Weekly ops sync,/ })).toBeVisible()
    expect(screen.queryByRole('button', { name: /Change|Delete|Done/ })).not.toBeInTheDocument()
  })

  it('isn’t offered between meetings, though the status still is', async () => {
    api({
      [`GET /api/meetings/${RUNNING.id}`]: () => Response.json(CLOSED),
      [SERIES_MEETINGS]: () => Response.json([CLOSED]),
    })
    renderApp(`/tasks/${CONTRACT.id}`)

    expect(await statusButton('Done')).toBeEnabled()
    expect(await screen.findByRole('link', { name: /Weekly ops sync,/ })).toBeVisible()
    await screen.findByRole('button', { name: 'Change deadline' })
    expect(screen.queryByRole('button', { name: 'Change description' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete this task' })).not.toBeInTheDocument()
  })

  it('says why when deleting is rejected because the meeting closed', async () => {
    let meetings = [RUNNING]
    api({
      [SERIES_MEETINGS]: () => Response.json(meetings),
      [`DELETE ${TASK}`]: () => {
        meetings = [CLOSED]
        return reply(409)()
      },
    })
    renderApp(`/tasks/${CONTRACT.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Delete this task' }))
    fireEvent.click(screen.getByRole('button', { name: 'Delete task' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('was closed meanwhile')
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Delete this task' })).not.toBeInTheDocument(),
    )
    expect(await heading()).toBeVisible()
  })
})
