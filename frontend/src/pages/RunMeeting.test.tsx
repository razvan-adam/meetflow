import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { AgendaItem } from '../api/agenda.ts'
import type { Decision } from '../api/decisions.ts'
import type { Meeting } from '../api/meetings.ts'
import type { Series } from '../api/series.ts'
import type { Task } from '../api/tasks.ts'
import type { Team } from '../api/teams.ts'
import { ANA, mockApi, renderApp, requestBody } from '../test/renderApp.tsx'
import { formatDeadline } from './taskDisplay.ts'

const OPS: Team = { id: '6d0c1a52-3c1e-4f0b-9a57-2d4b8e7f1c10', name: 'Ops team', role: 'MEMBER' }
const BO = { id: '9c8b7a6d-5e4f-4321-8765-0fedcba98765', name: 'Bo Lind' }
const WEEKLY: Series = {
  id: '3b5d7f9a-1c2e-4a6b-8d0f-2e4a6c8e0a1b',
  teamId: OPS.id,
  name: 'Weekly ops sync',
  organizerId: ANA.id,
  organizerName: ANA.name,
}
const SCHEDULED: Meeting = {
  id: '7e6d5c4b-3a29-4180-9f8e-7d6c5b4a3928',
  teamId: OPS.id,
  seriesId: WEEKLY.id,
  scheduledAt: '2026-11-02T09:30:00.000Z',
  status: 'SCHEDULED',
  closedAt: null,
  participants: [
    { id: ANA.id, name: ANA.name },
    { id: BO.id, name: BO.name },
  ],
}
const RUNNING: Meeting = { ...SCHEDULED, status: 'IN_PROGRESS' }
const CLOSED: Meeting = { ...SCHEDULED, status: 'CLOSED', closedAt: '2026-11-02T10:14:00.000Z' }
const BUDGET: AgendaItem = {
  id: '11111111-1111-4111-8111-111111111111',
  title: 'Budget',
  notes: null,
  status: 'PENDING',
  carriedOver: false,
}
const STAFFING: AgendaItem = {
  id: '22222222-2222-4222-8222-222222222222',
  title: 'Staffing',
  notes: 'Agency quotes received',
  status: 'PENDING',
  carriedOver: false,
}

const MEETING = `/api/meetings/${SCHEDULED.id}`
const AGENDA = `${MEETING}/agenda-items`
const DECISIONS = `${MEETING}/decisions`
const TASKS = `${MEETING}/tasks`
const CONFLICT = () => Response.json({ status: 409 }, { status: 409 })

function api(overrides: Record<string, () => Response>) {
  return mockApi({
    'GET /api/users/me': () => Response.json(ANA),
    'GET /api/teams': () => Response.json([OPS]),
    [`GET /api/teams/${OPS.id}`]: () => Response.json(OPS),
    [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(WEEKLY),
    [`GET ${MEETING}`]: () => Response.json(RUNNING),
    [`GET ${AGENDA}`]: () => Response.json([BUDGET, STAFFING]),
    [`GET ${DECISIONS}`]: () => Response.json([]),
    [`GET ${TASKS}`]: () => Response.json([]),
    [`GET ${MEETING}/carried-over-tasks`]: () => Response.json([]),
    ...overrides,
  })
}

const notesBox = () => screen.findByRole('textbox', { name: 'Notes' })

describe('starting a meeting', () => {
  it('lets the organizer start a scheduled meeting and take notes', async () => {
    let meeting = SCHEDULED
    api({
      [`GET ${MEETING}`]: () => Response.json(meeting),
      [`POST ${MEETING}/start`]: () => {
        meeting = RUNNING
        return Response.json(RUNNING)
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    expect(screen.queryByRole('textbox', { name: 'Notes' })).not.toBeInTheDocument()
    fireEvent.click(await screen.findByRole('button', { name: 'Start meeting' }))

    expect(await notesBox()).toBeVisible()
    expect(screen.getByText('In progress')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Start meeting' })).not.toBeInTheDocument()
  })

  it('doesn’t offer starting to members who don’t organize the series', async () => {
    api({
      [`GET /api/meeting-series/${WEEKLY.id}`]: () =>
        Response.json({ ...WEEKLY, organizerId: BO.id, organizerName: BO.name }),
      [`GET ${MEETING}`]: () => Response.json(SCHEDULED),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    expect(await screen.findByRole('link', { name: 'Weekly ops sync' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Start meeting' })).not.toBeInTheDocument()
  })
})

describe('working through the agenda', () => {
  it('opens the first item and switches to another', async () => {
    api({})
    renderApp(`/meetings/${SCHEDULED.id}`)

    expect(await screen.findByRole('heading', { name: 'Budget' })).toBeVisible()
    expect(await notesBox()).toHaveValue('')

    fireEvent.click(screen.getByRole('button', { name: 'Staffing: Not marked yet' }))

    expect(await screen.findByRole('heading', { name: 'Staffing' })).toBeVisible()
    expect(await notesBox()).toHaveValue('Agency quotes received')
  })

  it('saves notes when focus leaves the box', async () => {
    const fetchMock = api({
      [`PUT /api/agenda-items/${BUDGET.id}/notes`]: () =>
        Response.json({ ...BUDGET, notes: 'Up by 35%' }),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    const notes = await notesBox()
    fireEvent.change(notes, { target: { value: 'Up by 35%' } })
    fireEvent.blur(notes)

    await waitFor(() =>
      expect(requestBody(fetchMock, `PUT /api/agenda-items/${BUDGET.id}/notes`)).toEqual({
        notes: 'Up by 35%',
      }),
    )
  })

  it('saves notes shortly after typing stops', async () => {
    const fetchMock = api({
      [`PUT /api/agenda-items/${BUDGET.id}/notes`]: () =>
        Response.json({ ...BUDGET, notes: 'Up by 35%' }),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.change(await notesBox(), { target: { value: 'Up by 35%' } })

    await waitFor(() =>
      expect(requestBody(fetchMock, `PUT /api/agenda-items/${BUDGET.id}/notes`)).toEqual({
        notes: 'Up by 35%',
      }),
    )
  })

  it('saves unsaved notes when another item is opened, and shows them on return', async () => {
    let notes: string | null = null
    const fetchMock = api({
      [`GET ${AGENDA}`]: () => Response.json([{ ...BUDGET, notes }, STAFFING]),
      [`PUT /api/agenda-items/${BUDGET.id}/notes`]: () => {
        notes = 'Up by 35%'
        return Response.json({ ...BUDGET, notes })
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.change(await notesBox(), { target: { value: 'Up by 35%' } })
    fireEvent.click(screen.getByRole('button', { name: 'Staffing: Not marked yet' }))
    expect(await screen.findByRole('heading', { name: 'Staffing' })).toBeVisible()

    await waitFor(() =>
      expect(requestBody(fetchMock, `PUT /api/agenda-items/${BUDGET.id}/notes`)).toEqual({
        notes: 'Up by 35%',
      }),
    )
    fireEvent.click(screen.getByRole('button', { name: 'Budget: Not marked yet' }))
    expect(await notesBox()).toHaveValue('Up by 35%')
  })

  it('keeps the text and says so when notes can’t be saved, then tries again', async () => {
    const fetchMock = api({
      [`PUT /api/agenda-items/${BUDGET.id}/notes`]: () =>
        Response.json({ status: 500 }, { status: 500 }),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    const notes = await notesBox()
    fireEvent.change(notes, { target: { value: 'Up by 35%' } })
    fireEvent.blur(notes)

    expect(await screen.findByRole('alert')).toHaveTextContent('couldn’t be saved')
    expect(notes).toHaveValue('Up by 35%')

    const saves = () =>
      fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith('/notes') && init?.method)
    const before = saves().length
    fireEvent.blur(notes)
    await waitFor(() => expect(saves().length).toBe(before + 1))
  })

  it('marks an item and takes the mark back when pressed again', async () => {
    let status: AgendaItem['status'] = 'PENDING'
    const fetchMock = api({
      [`GET ${AGENDA}`]: () => Response.json([{ ...BUDGET, status }, STAFFING]),
      [`PUT /api/agenda-items/${BUDGET.id}/status`]: () => {
        status = status === 'PENDING' ? 'NOT_COVERED' : 'PENDING'
        return Response.json({ ...BUDGET, status })
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    const notCovered = await screen.findByRole('button', { name: 'Not covered' })
    expect(notCovered).toHaveAttribute('aria-pressed', 'false')
    fireEvent.click(notCovered)

    await waitFor(() => expect(notCovered).toHaveAttribute('aria-pressed', 'true'))
    expect(requestBody(fetchMock, `PUT /api/agenda-items/${BUDGET.id}/status`)).toEqual({
      status: 'NOT_COVERED',
    })
    expect(await screen.findByRole('button', { name: 'Budget: Not covered' })).toBeVisible()

    fireEvent.click(notCovered)

    await waitFor(() => expect(notCovered).toHaveAttribute('aria-pressed', 'false'))
  })

  it('lets the organizer add a topic raised during the meeting and opens it', async () => {
    const RAISED: AgendaItem = { ...BUDGET, id: 'raised', title: 'Raised now' }
    let agenda = [BUDGET]
    api({
      [`GET ${AGENDA}`]: () => Response.json(agenda),
      [`POST ${AGENDA}`]: () => {
        agenda = [BUDGET, RAISED]
        return Response.json(RAISED, { status: 201 })
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.change(await screen.findByRole('textbox', { name: 'New agenda item' }), {
      target: { value: 'Raised now' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByRole('heading', { name: 'Raised now' })).toBeVisible()
  })

  it('shows members the notes and marks of a running meeting, read-only', async () => {
    api({
      [`GET /api/meeting-series/${WEEKLY.id}`]: () =>
        Response.json({ ...WEEKLY, organizerId: BO.id, organizerName: BO.name }),
      [`GET ${AGENDA}`]: () => Response.json([{ ...BUDGET, status: 'COVERED' }, STAFFING]),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    expect(await screen.findByText('Agency quotes received')).toBeVisible()
    expect(screen.getByText('Covered')).toBeVisible()
    await waitFor(() => expect(screen.getByText('Bo Lind')).toBeVisible())
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Close meeting' })).not.toBeInTheDocument()
  })

  it('turns read-only and says why when the organizer role moved to someone else', async () => {
    let series = WEEKLY
    api({
      [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(series),
      [`PUT /api/agenda-items/${BUDGET.id}/status`]: () => {
        series = { ...WEEKLY, organizerId: BO.id, organizerName: BO.name }
        return Response.json({ status: 403 }, { status: 403 })
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Covered' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('no longer the organizer')
    await waitFor(() =>
      expect(screen.queryByRole('textbox', { name: 'Notes' })).not.toBeInTheDocument(),
    )
    expect(screen.queryByRole('button', { name: 'Close meeting' })).not.toBeInTheDocument()
  })

  it('turns read-only and says so when notes are rejected because it was closed', async () => {
    let meeting = RUNNING
    api({
      [`GET ${MEETING}`]: () => Response.json(meeting),
      [`PUT /api/agenda-items/${BUDGET.id}/notes`]: () => {
        meeting = CLOSED
        return CONFLICT()
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    const notes = await notesBox()
    fireEvent.change(notes, { target: { value: 'Too late' } })
    fireEvent.blur(notes)

    expect(await screen.findByRole('alert')).toHaveTextContent('was closed meanwhile')
    expect(screen.getByText('This meeting was closed')).toBeVisible()
    await waitFor(() =>
      expect(screen.queryByRole('textbox', { name: 'Notes' })).not.toBeInTheDocument(),
    )
  })
})

describe('recording decisions', () => {
  const PICKERS: Decision = { id: 'pickers', agendaItemId: BUDGET.id, text: 'Hire 6 pickers' }
  const QUOTES: Decision = { id: 'quotes', agendaItemId: STAFFING.id, text: 'Ask for two quotes' }

  it('records a decision on the open item and clears the box', async () => {
    let decisions: Decision[] = []
    const fetchMock = api({
      [`GET ${DECISIONS}`]: () => Response.json(decisions),
      [`POST /api/agenda-items/${BUDGET.id}/decisions`]: () => {
        decisions = [PICKERS]
        return Response.json(PICKERS, { status: 201 })
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    const box = await screen.findByRole('textbox', { name: 'New decision' })
    fireEvent.change(box, { target: { value: '  Hire 6 pickers  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add decision' }))

    expect(await screen.findByText('Hire 6 pickers')).toBeVisible()
    expect(requestBody(fetchMock, `POST /api/agenda-items/${BUDGET.id}/decisions`)).toEqual({
      text: 'Hire 6 pickers',
    })
    await waitFor(() => expect(box).toHaveValue(''))
  })

  it('shows only the decisions of the open item', async () => {
    api({ [`GET ${DECISIONS}`]: () => Response.json([PICKERS, QUOTES]) })
    renderApp(`/meetings/${SCHEDULED.id}`)

    // Every item shows until the page knows the user organizes the series, so wait for that.
    await notesBox()
    expect(await screen.findByText('Hire 6 pickers')).toBeVisible()
    expect(screen.queryByText('Ask for two quotes')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Staffing: Not marked yet' }))

    expect(await screen.findByText('Ask for two quotes')).toBeVisible()
    expect(screen.queryByText('Hire 6 pickers')).not.toBeInTheDocument()
  })

  it('edits a decision', async () => {
    let decisions = [PICKERS]
    const fetchMock = api({
      [`GET ${DECISIONS}`]: () => Response.json(decisions),
      [`PUT /api/decisions/${PICKERS.id}`]: () => {
        decisions = [{ ...PICKERS, text: 'Hire 8 pickers' }]
        return Response.json(decisions[0])
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Edit decision: Hire 6 pickers' }))
    const box = screen.getByRole('textbox', { name: 'Decision text' })
    expect(box).toHaveValue('Hire 6 pickers')
    fireEvent.change(box, { target: { value: 'Hire 8 pickers' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Hire 8 pickers')).toBeVisible()
    expect(requestBody(fetchMock, `PUT /api/decisions/${PICKERS.id}`)).toEqual({
      text: 'Hire 8 pickers',
    })
    expect(screen.queryByRole('textbox', { name: 'Decision text' })).not.toBeInTheDocument()
  })

  it('asks before removing a decision and starts on the safe choice', async () => {
    let decisions = [PICKERS]
    const fetchMock = api({
      [`GET ${DECISIONS}`]: () => Response.json(decisions),
      [`DELETE /api/decisions/${PICKERS.id}`]: () => {
        decisions = []
        return new Response(null, { status: 204 })
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    const removeButton = () =>
      screen.findByRole('button', { name: 'Remove decision: Hire 6 pickers' })
    fireEvent.click(await removeButton())
    const keep = screen.getByRole('button', { name: 'Keep decision' })
    expect(keep).toHaveFocus()
    fireEvent.click(keep)
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)

    fireEvent.click(await removeButton())
    fireEvent.click(screen.getByRole('button', { name: 'Remove decision' }))

    await waitFor(() => expect(screen.queryByText('Hire 6 pickers')).not.toBeInTheDocument())
  })

  it('keeps the text and says so when a decision can’t be saved', async () => {
    api({
      [`POST /api/agenda-items/${BUDGET.id}/decisions`]: () =>
        Response.json({ status: 500 }, { status: 500 }),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    const box = await screen.findByRole('textbox', { name: 'New decision' })
    fireEvent.change(box, { target: { value: 'Hire 6 pickers' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add decision' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('couldn’t be saved')
    expect(box).toHaveValue('Hire 6 pickers')
  })

  it('turns read-only and says so when a decision is rejected because it was closed', async () => {
    let meeting = RUNNING
    api({
      [`GET ${MEETING}`]: () => Response.json(meeting),
      [`POST /api/agenda-items/${BUDGET.id}/decisions`]: () => {
        meeting = CLOSED
        return CONFLICT()
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.change(await screen.findByRole('textbox', { name: 'New decision' }), {
      target: { value: 'Too late' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Add decision' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('was closed meanwhile')
    await waitFor(() =>
      expect(screen.queryByRole('textbox', { name: 'New decision' })).not.toBeInTheDocument(),
    )
  })

  it('shows members the decisions under each item, read-only', async () => {
    api({
      [`GET /api/meeting-series/${WEEKLY.id}`]: () =>
        Response.json({ ...WEEKLY, organizerId: BO.id, organizerName: BO.name }),
      [`GET ${DECISIONS}`]: () => Response.json([PICKERS, QUOTES]),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    const budget = await screen.findByRole('list', { name: 'Decisions on Budget' })
    expect(within(budget).getByText('Hire 6 pickers')).toBeVisible()
    const staffing = screen.getByRole('list', { name: 'Decisions on Staffing' })
    expect(within(staffing).getByText('Ask for two quotes')).toBeVisible()
    expect(screen.queryByRole('textbox', { name: 'New decision' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /decision/i })).not.toBeInTheDocument()
  })

  it('keeps the decisions on the record of a closed meeting', async () => {
    api({
      [`GET ${MEETING}`]: () => Response.json(CLOSED),
      [`GET ${DECISIONS}`]: () => Response.json([PICKERS]),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    expect(await screen.findByText('Hire 6 pickers')).toBeVisible()
    expect(screen.queryByRole('textbox', { name: 'New decision' })).not.toBeInTheDocument()
  })

  it('says so when the decisions can’t be loaded, rather than showing none', async () => {
    api({ [`GET ${DECISIONS}`]: () => Response.json({ status: 500 }, { status: 500 }) })
    renderApp(`/meetings/${SCHEDULED.id}`)

    expect(await screen.findByRole('alert')).toHaveTextContent('decisions couldn’t be loaded')
  })
})

describe('creating tasks', () => {
  const PICKERS: Decision = { id: 'pickers', agendaItemId: BUDGET.id, text: 'Hire 6 pickers' }
  const CONTRACT: Task = {
    id: 'contract',
    teamId: OPS.id,
    seriesId: WEEKLY.id,
    meetingId: SCHEDULED.id,
    agendaItemId: BUDGET.id,
    decisionId: PICKERS.id,
    description: 'Sign the contract',
    ownerId: BO.id,
    ownerName: BO.name,
    deadline: '2999-11-20',
    status: 'OPEN',
    deletion: null,
  }
  const DRAFT: Task = { ...CONTRACT, id: 'draft', decisionId: null, description: 'Send the draft' }
  const FROM_DECISION = 'Add task from decision: Hire 6 pickers'
  const FROM_ITEM = 'Add task on Budget'

  function fillIn(form: HTMLElement, description: string, ownerId: string, deadline: string) {
    fireEvent.change(within(form).getByRole('textbox', { name: 'New task' }), {
      target: { value: description },
    })
    fireEvent.change(within(form).getByRole('combobox', { name: 'Owner' }), {
      target: { value: ownerId },
    })
    fireEvent.change(within(form).getByLabelText('Deadline'), { target: { value: deadline } })
    fireEvent.click(within(form).getByRole('button', { name: 'Save task' }))
  }

  it('creates a task from a decision and shows it with its owner and deadline', async () => {
    let tasks: Task[] = []
    const fetchMock = api({
      [`GET ${DECISIONS}`]: () => Response.json([PICKERS]),
      [`GET ${TASKS}`]: () => Response.json(tasks),
      [`POST /api/decisions/${PICKERS.id}/tasks`]: () => {
        tasks = [CONTRACT]
        return Response.json(CONTRACT, { status: 201 })
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: FROM_DECISION }))
    fillIn(
      screen.getByRole('form', { name: FROM_DECISION }),
      '  Sign the contract  ',
      BO.id,
      '2999-11-20',
    )

    const list = await screen.findByRole('list', { name: 'Tasks from: Hire 6 pickers' })
    expect(within(list).getByText('Sign the contract')).toBeVisible()
    expect(within(list).getByText('Bo Lind')).toBeVisible()
    expect(within(list).getByRole('combobox', { name: 'Status of Sign the contract' })).toHaveValue(
      'OPEN',
    )
    expect(within(list).getByText(`Due ${formatDeadline('2999-11-20')}`)).toBeVisible()
    expect(requestBody(fetchMock, `POST /api/decisions/${PICKERS.id}/tasks`)).toEqual({
      description: 'Sign the contract',
      ownerId: BO.id,
      deadline: '2999-11-20',
    })
    expect(screen.queryByRole('form', { name: FROM_DECISION })).not.toBeInTheDocument()
  })

  it('creates a task straight from the open agenda item', async () => {
    let tasks: Task[] = []
    const fetchMock = api({
      [`GET ${TASKS}`]: () => Response.json(tasks),
      [`POST /api/agenda-items/${BUDGET.id}/tasks`]: () => {
        tasks = [DRAFT]
        return Response.json(DRAFT, { status: 201 })
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: FROM_ITEM }))
    fillIn(screen.getByRole('form', { name: FROM_ITEM }), 'Send the draft', BO.id, '2999-11-20')

    const list = await screen.findByRole('list', { name: 'Tasks on Budget' })
    expect(within(list).getByText('Send the draft')).toBeVisible()
    expect(requestBody(fetchMock, `POST /api/agenda-items/${BUDGET.id}/tasks`)).toEqual({
      description: 'Send the draft',
      ownerId: BO.id,
      deadline: '2999-11-20',
    })
  })

  it('offers only the meeting’s participants as owners', async () => {
    api({})
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: FROM_ITEM }))

    const owners = within(screen.getByRole('combobox', { name: 'Owner' })).getAllByRole('option')
    expect(owners.map((option) => option.textContent)).toEqual(['Participant…', ANA.name, BO.name])
  })

  it('won’t save a task without a description, an owner and a deadline', async () => {
    const fetchMock = api({})
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: FROM_ITEM }))
    const form = screen.getByRole('form', { name: FROM_ITEM })
    for (const [description, ownerId, deadline] of [
      ['   ', BO.id, '2999-11-20'],
      ['Send the draft', '', '2999-11-20'],
      ['Send the draft', BO.id, ''],
    ]) {
      fillIn(form, description, ownerId, deadline)

      expect(await within(form).findByRole('alert')).toHaveTextContent('Every task needs all three')
    }
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
  })

  it('keeps the form and says why when the owner is no longer a participant', async () => {
    let meeting = RUNNING
    api({
      [`GET ${MEETING}`]: () => Response.json(meeting),
      [`POST /api/agenda-items/${BUDGET.id}/tasks`]: () => {
        meeting = { ...RUNNING, participants: [{ id: ANA.id, name: ANA.name }] }
        return Response.json({ status: 400 }, { status: 400 })
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: FROM_ITEM }))
    const form = screen.getByRole('form', { name: FROM_ITEM })
    fillIn(form, 'Send the draft', BO.id, '2999-11-20')

    expect(await screen.findByRole('alert')).toHaveTextContent('no longer a participant')
    expect(within(form).getByRole('textbox', { name: 'New task' })).toHaveValue('Send the draft')
    await waitFor(() =>
      expect(within(form).queryByRole('option', { name: BO.name })).not.toBeInTheDocument(),
    )
  })

  it('turns read-only and says so when a task is rejected because it was closed', async () => {
    let meeting = RUNNING
    api({
      [`GET ${MEETING}`]: () => Response.json(meeting),
      [`POST /api/agenda-items/${BUDGET.id}/tasks`]: () => {
        meeting = CLOSED
        return CONFLICT()
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: FROM_ITEM }))
    fillIn(screen.getByRole('form', { name: FROM_ITEM }), 'Too late', BO.id, '2999-11-20')

    expect(await screen.findByRole('alert')).toHaveTextContent('was closed meanwhile')
    await waitFor(() =>
      expect(screen.queryByRole('form', { name: FROM_ITEM })).not.toBeInTheDocument(),
    )
  })

  it('doesn’t offer removing a decision that tasks came from, but still editing it', async () => {
    api({
      [`GET ${DECISIONS}`]: () => Response.json([PICKERS]),
      [`GET ${TASKS}`]: () => Response.json([CONTRACT]),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    // Every item shows until the page knows the user organizes the series, so wait for that.
    await notesBox()
    expect(await screen.findByText('Sign the contract')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Edit decision: Hire 6 pickers' })).toBeVisible()
    expect(
      screen.queryByRole('button', { name: 'Remove decision: Hire 6 pickers' }),
    ).not.toBeInTheDocument()
  })

  it('shows members the tasks under each decision and item, read-only', async () => {
    api({
      [`GET /api/meeting-series/${WEEKLY.id}`]: () =>
        Response.json({ ...WEEKLY, organizerId: BO.id, organizerName: BO.name }),
      [`GET ${DECISIONS}`]: () => Response.json([PICKERS]),
      [`GET ${TASKS}`]: () => Response.json([CONTRACT, DRAFT]),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    const fromDecision = await screen.findByRole('list', { name: 'Tasks from: Hire 6 pickers' })
    expect(within(fromDecision).getByText('Sign the contract')).toBeVisible()
    const fromItem = screen.getByRole('list', { name: 'Tasks on Budget' })
    expect(within(fromItem).getByText('Send the draft')).toBeVisible()
    expect(screen.queryByRole('button', { name: /Add task/ })).not.toBeInTheDocument()
  })

  it('keeps the tasks on the record of a closed meeting', async () => {
    api({
      [`GET ${MEETING}`]: () => Response.json(CLOSED),
      [`GET ${TASKS}`]: () => Response.json([DRAFT]),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    expect(await screen.findByText('Send the draft')).toBeVisible()
    expect(screen.queryByRole('button', { name: /Add task/ })).not.toBeInTheDocument()
  })

  it('says so when the tasks can’t be loaded, rather than showing none', async () => {
    api({ [`GET ${TASKS}`]: () => Response.json({ status: 500 }, { status: 500 }) })
    renderApp(`/meetings/${SCHEDULED.id}`)

    expect(await screen.findByRole('alert')).toHaveTextContent('tasks couldn’t be loaded')
  })

  describe('keeping them up to date', () => {
    const STATUS = 'Status of Send the draft'
    const AS_MEMBER = {
      [`GET /api/meeting-series/${WEEKLY.id}`]: () =>
        Response.json({ ...WEEKLY, organizerId: BO.id, organizerName: BO.name }),
    }

    it('lets the organizer change a status from the agenda', async () => {
      let task = DRAFT
      const fetchMock = api({
        [`GET ${TASKS}`]: () => Response.json([task]),
        [`PUT /api/tasks/${DRAFT.id}/status`]: () => {
          task = { ...DRAFT, status: 'IN_PROGRESS' }
          return Response.json(task)
        },
      })
      renderApp(`/meetings/${SCHEDULED.id}`)

      fireEvent.change(await screen.findByRole('combobox', { name: STATUS }), {
        target: { value: 'IN_PROGRESS' },
      })

      await waitFor(() =>
        expect(screen.getByRole('combobox', { name: STATUS })).toHaveValue('IN_PROGRESS'),
      )
      expect(requestBody(fetchMock, `PUT /api/tasks/${DRAFT.id}/status`)).toEqual({
        status: 'IN_PROGRESS',
      })
    })

    it('lets the owner change only their own tasks’ status, during and after the meeting', async () => {
      const mine = { ...DRAFT, ownerId: ANA.id, ownerName: ANA.name }
      for (const meeting of [RUNNING, CLOSED]) {
        api({
          ...AS_MEMBER,
          [`GET ${MEETING}`]: () => Response.json(meeting),
          [`GET ${TASKS}`]: () => Response.json([mine, { ...CONTRACT, decisionId: null }]),
        })
        renderApp(`/meetings/${SCHEDULED.id}`)

        expect(await screen.findByRole('combobox', { name: STATUS })).toBeVisible()
        expect(
          screen.queryByRole('combobox', { name: 'Status of Sign the contract' }),
        ).not.toBeInTheDocument()
        expect(screen.getByText('Bo Lind · Open')).toBeVisible()
        cleanup()
      }
    })

    it('lets the organizer change a status after the meeting closes', async () => {
      api({
        [`GET ${MEETING}`]: () => Response.json(CLOSED),
        [`GET ${TASKS}`]: () => Response.json([DRAFT]),
      })
      renderApp(`/meetings/${SCHEDULED.id}`)

      expect(await screen.findByRole('combobox', { name: STATUS })).toBeEnabled()
    })

    it('links each task to its own screen', async () => {
      api({ [`GET ${TASKS}`]: () => Response.json([DRAFT]) })
      renderApp(`/meetings/${SCHEDULED.id}`)

      expect(await screen.findByRole('link', { name: 'Send the draft' })).toHaveAttribute(
        'href',
        `/tasks/${DRAFT.id}`,
      )
    })

    it('shows how many days overdue a task is, unless it’s done', async () => {
      api({
        [`GET ${TASKS}`]: () =>
          Response.json([
            { ...DRAFT, id: 'late', description: 'Late', deadline: '2020-01-06' },
            {
              ...DRAFT,
              id: 'finished',
              description: 'Finished',
              deadline: '2020-01-06',
              status: 'DONE',
            },
          ]),
      })
      renderApp(`/meetings/${SCHEDULED.id}`)

      // Every item shows until the page knows the user organizes the series, so wait for that.
      await notesBox()
      const due = `Due ${formatDeadline('2020-01-06')}`
      expect(await screen.findByText(new RegExp(`^${due} · \\d+ days overdue$`))).toBeVisible()
      expect(screen.getByText(due)).toBeVisible()
    })

    it('rewords a task right on the agenda', async () => {
      let task = DRAFT
      const fetchMock = api({
        [`GET ${TASKS}`]: () => Response.json([task]),
        [`PUT /api/tasks/${DRAFT.id}/description`]: () => {
          task = { ...DRAFT, description: 'Send the final draft' }
          return Response.json(task)
        },
      })
      renderApp(`/meetings/${SCHEDULED.id}`)

      fireEvent.click(await screen.findByRole('button', { name: 'Edit task: Send the draft' }))
      const box = screen.getByRole('textbox', { name: 'Task description' })
      expect(box).toHaveValue('Send the draft')
      fireEvent.change(box, { target: { value: '  Send the final draft  ' } })
      fireEvent.click(screen.getByRole('button', { name: 'Save' }))

      expect(await screen.findByRole('link', { name: 'Send the final draft' })).toBeVisible()
      expect(requestBody(fetchMock, `PUT /api/tasks/${DRAFT.id}/description`)).toEqual({
        description: 'Send the final draft',
      })
      expect(screen.queryByRole('textbox', { name: 'Task description' })).not.toBeInTheDocument()
    })

    it('keeps the words and says why when rewording fails', async () => {
      api({
        [`GET ${TASKS}`]: () => Response.json([DRAFT]),
        [`PUT /api/tasks/${DRAFT.id}/description`]: () =>
          Response.json({ status: 500 }, { status: 500 }),
      })
      renderApp(`/meetings/${SCHEDULED.id}`)

      fireEvent.click(await screen.findByRole('button', { name: 'Edit task: Send the draft' }))
      const box = screen.getByRole('textbox', { name: 'Task description' })
      fireEvent.change(box, { target: { value: 'Send the final draft' } })
      fireEvent.click(screen.getByRole('button', { name: 'Save' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('couldn’t be saved')
      expect(box).toHaveValue('Send the final draft')
    })

    it('asks before deleting a task right on the agenda, then frees its decision', async () => {
      let tasks = [CONTRACT]
      const fetchMock = api({
        [`GET ${DECISIONS}`]: () => Response.json([PICKERS]),
        [`GET ${TASKS}`]: () => Response.json(tasks),
        [`DELETE /api/tasks/${CONTRACT.id}`]: () => {
          tasks = []
          return new Response(null, { status: 204 })
        },
      })
      renderApp(`/meetings/${SCHEDULED.id}`)

      const deleteButton = () =>
        screen.findByRole('button', { name: 'Remove task: Sign the contract' })
      fireEvent.click(await deleteButton())
      const keep = screen.getByRole('button', { name: 'Keep task' })
      expect(keep).toHaveFocus()
      fireEvent.click(keep)
      expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)

      fireEvent.click(await deleteButton())
      fireEvent.click(screen.getByRole('button', { name: 'Remove task' }))

      await waitFor(() => expect(screen.queryByText('Sign the contract')).not.toBeInTheDocument())
      expect(
        await screen.findByRole('button', { name: 'Remove decision: Hire 6 pickers' }),
      ).toBeVisible()
    })

    it('doesn’t offer rewording or deleting to other members, even a task’s owner', async () => {
      const mine = { ...DRAFT, ownerId: ANA.id, ownerName: ANA.name }
      api({ ...AS_MEMBER, [`GET ${TASKS}`]: () => Response.json([mine]) })
      renderApp(`/meetings/${SCHEDULED.id}`)

      expect(await screen.findByRole('combobox', { name: STATUS })).toBeVisible()
      expect(
        screen.queryByRole('button', { name: /Edit task|Remove task/ }),
      ).not.toBeInTheDocument()
    })

    it('offers rewording and deleting on a closed meeting while a later one runs', async () => {
      let tasks = [DRAFT]
      api({
        [`GET ${MEETING}`]: () => Response.json(CLOSED),
        [`GET ${TASKS}`]: () => Response.json(tasks),
        [`GET /api/meeting-series/${WEEKLY.id}/meetings`]: () =>
          Response.json([{ ...RUNNING, id: 'later' }, CLOSED]),
        [`DELETE /api/tasks/${DRAFT.id}`]: () => {
          tasks = [
            {
              ...DRAFT,
              deletion: {
                byName: ANA.name,
                at: '2026-11-09T09:00:00.000Z',
                meetingScheduledAt: '2026-11-09T08:30:00.000Z',
              },
            },
          ]
          return new Response(null, { status: 204 })
        },
      })
      renderApp(`/meetings/${SCHEDULED.id}`)

      expect(await screen.findByRole('button', { name: 'Edit task: Send the draft' })).toBeVisible()
      fireEvent.click(screen.getByRole('button', { name: 'Remove task: Send the draft' }))
      fireEvent.click(screen.getByRole('button', { name: 'Remove task' }))

      // It stays on the closed meeting's record, marked, with no way left to change it.
      expect(await screen.findByText(/Removed by Ana Costa during the meeting of/)).toBeVisible()
      expect(screen.getByRole('link', { name: 'Send the draft' }).closest('s')).not.toBeNull()
      expect(screen.queryByRole('combobox', { name: STATUS })).not.toBeInTheDocument()
      expect(
        screen.queryByRole('button', { name: /Edit task|Remove task/ }),
      ).not.toBeInTheDocument()
    })

    it('offers neither on a closed meeting when no meeting of the series runs', async () => {
      api({
        [`GET ${MEETING}`]: () => Response.json(CLOSED),
        [`GET ${TASKS}`]: () => Response.json([DRAFT]),
        [`GET /api/meeting-series/${WEEKLY.id}/meetings`]: () => Response.json([CLOSED]),
      })
      renderApp(`/meetings/${SCHEDULED.id}`)

      expect(await screen.findByRole('combobox', { name: STATUS })).toBeVisible()
      await waitFor(() =>
        expect(
          screen.queryByRole('button', { name: /Edit task|Remove task/ }),
        ).not.toBeInTheDocument(),
      )
    })

    it('offers removing a decision again once its tasks are gone', async () => {
      api({ [`GET ${DECISIONS}`]: () => Response.json([PICKERS]) })
      renderApp(`/meetings/${SCHEDULED.id}`)

      expect(
        await screen.findByRole('button', { name: 'Remove decision: Hire 6 pickers' }),
      ).toBeVisible()
    })

    it('says so when a status change can’t be saved', async () => {
      api({
        [`GET ${TASKS}`]: () => Response.json([DRAFT]),
        [`PUT /api/tasks/${DRAFT.id}/status`]: () =>
          Response.json({ status: 500 }, { status: 500 }),
      })
      renderApp(`/meetings/${SCHEDULED.id}`)

      fireEvent.change(await screen.findByRole('combobox', { name: STATUS }), {
        target: { value: 'DONE' },
      })

      expect(await screen.findByRole('alert')).toHaveTextContent('couldn’t be saved')
    })
  })
})

describe('removing an agenda item once the meeting has started', () => {
  it('moves to the live agenda, where items can’t be removed', async () => {
    let meeting = SCHEDULED
    api({
      [`GET ${MEETING}`]: () => Response.json(meeting),
      [`DELETE /api/agenda-items/${BUDGET.id}`]: () => {
        meeting = RUNNING
        return CONFLICT()
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Remove Budget' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Remove item' }))

    expect(await notesBox()).toBeVisible()
    expect(screen.queryByRole('button', { name: /^Remove Budget/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Budget: Not marked yet' })).toBeVisible()
  })
})

describe('closing a meeting', () => {
  it('asks first, says what happens to unmarked items, and starts on the safe choice', async () => {
    const fetchMock = api({
      [`GET ${AGENDA}`]: () => Response.json([{ ...BUDGET, status: 'COVERED' }, STAFFING]),
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Close meeting' }))

    const confirmation = await screen.findByRole('group', { name: 'Confirm closing the meeting' })
    expect(
      within(confirmation).getByText('1 item isn’t marked, so it will count as not covered.'),
    ).toBeVisible()
    const keep = within(confirmation).getByRole('button', { name: 'Keep it open' })
    expect(keep).toHaveFocus()
    fireEvent.click(keep)

    expect(await screen.findByRole('button', { name: 'Close meeting' })).toBeVisible()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/close'))).toBe(false)
  })

  it('doesn’t promise emails, which come with later stories', async () => {
    api({})
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Close meeting' }))

    const confirmation = await screen.findByRole('group', { name: 'Confirm closing the meeting' })
    expect(confirmation).not.toHaveTextContent(/email|minutes/i)
  })

  it('closes the meeting and shows its record, read-only', async () => {
    let meeting = RUNNING
    let agenda = [BUDGET, STAFFING]
    api({
      [`GET ${MEETING}`]: () => Response.json(meeting),
      [`GET ${AGENDA}`]: () => Response.json(agenda),
      [`POST ${MEETING}/close`]: () => {
        meeting = CLOSED
        agenda = [
          { ...BUDGET, status: 'NOT_COVERED' },
          { ...STAFFING, status: 'NOT_COVERED' },
        ]
        return Response.json(CLOSED)
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Close meeting' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Close the meeting' }))

    expect(await screen.findByText('This meeting was closed')).toBeVisible()
    expect(await screen.findByText('Agency quotes received')).toBeVisible()
    expect(await screen.findAllByText('Not covered')).toHaveLength(2)
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Close meeting' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Edit date/ })).not.toBeInTheDocument()
  })

  it('waits for notes still being saved before it can close', async () => {
    let finishSaving = (_response: Response) => {}
    const saving = new Promise<Response>((resolve) => {
      finishSaving = resolve
    })
    api({
      [`PUT /api/agenda-items/${BUDGET.id}/notes`]: () => saving as unknown as Response,
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    const notes = await notesBox()
    fireEvent.change(notes, { target: { value: 'Last words' } })
    fireEvent.blur(notes)
    fireEvent.click(screen.getByRole('button', { name: 'Close meeting' }))

    expect(await screen.findByRole('button', { name: 'Saving notes…' })).toBeDisabled()

    finishSaving(Response.json({ ...BUDGET, notes: 'Last words' }))

    expect(await screen.findByRole('button', { name: 'Close the meeting' })).toBeEnabled()
  })

  it('says why when closing is rejected because the organizer role moved', async () => {
    let series = WEEKLY
    api({
      [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(series),
      [`POST ${MEETING}/close`]: () => {
        series = { ...WEEKLY, organizerId: BO.id, organizerName: BO.name }
        return Response.json({ status: 403 }, { status: 403 })
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Close meeting' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Close the meeting' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('no longer the organizer')
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Close meeting' })).not.toBeInTheDocument(),
    )
  })

  it('shows the closed state when the meeting was already closed elsewhere', async () => {
    let meeting = RUNNING
    api({
      [`GET ${MEETING}`]: () => Response.json(meeting),
      [`POST ${MEETING}/close`]: () => {
        meeting = CLOSED
        return CONFLICT()
      },
    })
    renderApp(`/meetings/${SCHEDULED.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Close meeting' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Close the meeting' }))

    expect(await screen.findByText('This meeting was closed')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Close meeting' })).not.toBeInTheDocument()
  })
})
