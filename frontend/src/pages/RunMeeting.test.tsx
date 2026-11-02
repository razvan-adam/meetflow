import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { AgendaItem } from '../api/agenda.ts'
import type { Decision } from '../api/decisions.ts'
import type { Meeting } from '../api/meetings.ts'
import type { Series } from '../api/series.ts'
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
