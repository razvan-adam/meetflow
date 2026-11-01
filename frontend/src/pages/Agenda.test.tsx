import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { AgendaItem } from '../api/agenda.ts'
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
const MONDAY: Meeting = {
  id: '7e6d5c4b-3a29-4180-9f8e-7d6c5b4a3928',
  teamId: OPS.id,
  seriesId: WEEKLY.id,
  scheduledAt: '2026-11-02T09:30:00.000Z',
  status: 'SCHEDULED',
  participants: [
    { id: ANA.id, name: ANA.name },
    { id: BO.id, name: BO.name },
  ],
}
const BUDGET: AgendaItem = {
  id: '11111111-1111-4111-8111-111111111111',
  title: 'Budget',
  carriedOver: false,
}
const RETURNS: AgendaItem = {
  id: '22222222-2222-4222-8222-222222222222',
  title: 'Returns backlog',
  carriedOver: true,
}

const AGENDA = `/api/meetings/${MONDAY.id}/agenda-items`

function api(overrides: Record<string, () => Response>) {
  return mockApi({
    'GET /api/users/me': () => Response.json(ANA),
    'GET /api/teams': () => Response.json([OPS]),
    [`GET /api/teams/${OPS.id}`]: () => Response.json(OPS),
    [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(WEEKLY),
    [`GET /api/meetings/${MONDAY.id}`]: () => Response.json(MONDAY),
    ...overrides,
  })
}

async function itemTitles() {
  const region = await screen.findByRole('region', { name: 'Agenda items' })
  return within(region)
    .getAllByRole('listitem')
    .map((item) => item.textContent)
}

describe('the agenda', () => {
  it('lets the organizer add an item', async () => {
    let agenda: AgendaItem[] = []
    const fetchMock = api({
      [`GET ${AGENDA}`]: () => Response.json(agenda),
      [`POST ${AGENDA}`]: () => {
        agenda = [BUDGET]
        return Response.json(BUDGET, { status: 201 })
      },
    })
    renderApp(`/meetings/${MONDAY.id}`)

    expect(await screen.findByText('No items yet. Add the first topic below.')).toBeVisible()
    const input = await screen.findByRole('textbox', { name: 'New agenda item' })
    fireEvent.change(input, { target: { value: '  Budget  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Budget')).toBeVisible()
    expect(requestBody(fetchMock, `POST ${AGENDA}`)).toEqual({ title: 'Budget' })
    await waitFor(() => expect(input).toHaveValue(''))
  })

  it('lets the organizer rename an item', async () => {
    let agenda = [BUDGET]
    const fetchMock = api({
      [`GET ${AGENDA}`]: () => Response.json(agenda),
      [`PUT /api/agenda-items/${BUDGET.id}`]: () => {
        agenda = [{ ...BUDGET, title: 'Budget 2027' }]
        return Response.json(agenda[0])
      },
    })
    renderApp(`/meetings/${MONDAY.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Edit Budget' }))
    fireEvent.change(screen.getByRole('textbox', { name: 'New title for Budget' }), {
      target: { value: 'Budget 2027' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Budget 2027')).toBeVisible()
    expect(requestBody(fetchMock, `PUT /api/agenda-items/${BUDGET.id}`)).toEqual({
      title: 'Budget 2027',
    })
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
  })

  it('tags carried-over items and lets the organizer remove them', async () => {
    let agenda = [RETURNS, BUDGET]
    api({
      [`GET ${AGENDA}`]: () => Response.json(agenda),
      [`DELETE /api/agenda-items/${RETURNS.id}`]: () => {
        agenda = [BUDGET]
        return new Response(null, { status: 204 })
      },
    })
    renderApp(`/meetings/${MONDAY.id}`)

    expect(await screen.findByText('not covered last time')).toBeVisible()
    fireEvent.click(await screen.findByRole('button', { name: 'Remove Returns backlog' }))
    fireEvent.click(screen.getByRole('button', { name: 'Remove item' }))

    await waitFor(() => expect(screen.queryByText(/Returns backlog/)).not.toBeInTheDocument())
    expect(screen.getByText('Budget')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Remove item' })).not.toBeInTheDocument()
  })

  it('asks before removing an item and keeps it if the organizer changes their mind', async () => {
    const fetchMock = api({ [`GET ${AGENDA}`]: () => Response.json([BUDGET]) })
    renderApp(`/meetings/${MONDAY.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Remove Budget' }))

    expect(screen.getByText(/Remove “Budget” from the agenda\?/)).toBeVisible()
    const keep = screen.getByRole('button', { name: 'Keep item' })
    expect(keep).toHaveFocus()
    fireEvent.click(keep)

    expect(await screen.findByRole('button', { name: 'Remove Budget' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Remove item' })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)
  })

  it('closes the confirmation and says so when removing fails', async () => {
    let agenda = [BUDGET, RETURNS]
    api({
      [`GET ${AGENDA}`]: () => Response.json(agenda),
      [`DELETE /api/agenda-items/${RETURNS.id}`]: () => {
        agenda = [BUDGET]
        return Response.json({ status: 404 }, { status: 404 })
      },
    })
    renderApp(`/meetings/${MONDAY.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Remove Returns backlog' }))
    fireEvent.click(screen.getByRole('button', { name: 'Remove item' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('changed meanwhile')
    expect(screen.queryByRole('button', { name: 'Remove item' })).not.toBeInTheDocument()
  })

  it('moves an item with the buttons, which work without a mouse', async () => {
    const fetchMock = api({
      [`GET ${AGENDA}`]: () => Response.json([BUDGET, RETURNS]),
      [`PUT ${AGENDA}/order`]: () => Response.json([RETURNS, BUDGET]),
    })
    renderApp(`/meetings/${MONDAY.id}`)

    const down = await screen.findByRole('button', { name: 'Move Budget down' })
    expect(screen.getByRole('button', { name: 'Move Budget up' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Move Returns backlog down' })).toBeDisabled()
    fireEvent.click(down)

    await waitFor(() =>
      expect(requestBody(fetchMock, `PUT ${AGENDA}/order`)).toEqual({
        itemIds: [RETURNS.id, BUDGET.id],
      }),
    )
  })

  it('moves an item by dragging its handle onto another', async () => {
    const fetchMock = api({
      [`GET ${AGENDA}`]: () => Response.json([BUDGET, RETURNS]),
      [`PUT ${AGENDA}/order`]: () => Response.json([RETURNS, BUDGET]),
    })
    renderApp(`/meetings/${MONDAY.id}`)

    const handles = await waitFor(() => {
      const found = screen.getAllByText('⋮⋮')
      expect(found).toHaveLength(2)
      return found
    })
    const target = screen.getByText('Budget').closest('li') as HTMLElement
    fireEvent.dragStart(handles[1], { dataTransfer: { setData: () => undefined } })
    fireEvent.dragOver(target)
    fireEvent.drop(target)

    await waitFor(() =>
      expect(requestBody(fetchMock, `PUT ${AGENDA}/order`)).toEqual({
        itemIds: [RETURNS.id, BUDGET.id],
      }),
    )
  })

  it('reloads the agenda and says so when it changed meanwhile', async () => {
    let agenda = [BUDGET, RETURNS]
    api({
      [`GET ${AGENDA}`]: () => Response.json(agenda),
      [`PUT ${AGENDA}/order`]: () => {
        agenda = [BUDGET]
        return Response.json({ status: 409 }, { status: 409 })
      },
    })
    renderApp(`/meetings/${MONDAY.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Move Budget down' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('changed meanwhile')
    await waitFor(() => expect(screen.queryByText('Returns backlog')).not.toBeInTheDocument())
  })

  it('turns read-only and says why when the organizer role moved to someone else', async () => {
    let series = WEEKLY
    api({
      [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(series),
      [`GET ${AGENDA}`]: () => Response.json([BUDGET, RETURNS]),
      [`PUT /api/agenda-items/${BUDGET.id}`]: () => {
        series = { ...WEEKLY, organizerId: BO.id, organizerName: BO.name }
        return Response.json({ status: 403 }, { status: 403 })
      },
    })
    renderApp(`/meetings/${MONDAY.id}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Edit Budget' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('no longer the organizer')
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument(),
    )
    expect(screen.getByText('Budget')).toBeVisible()
    expect(screen.queryByRole('textbox', { name: 'New agenda item' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Remove|Move|Edit/ })).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('read-only')
  })

  it('shows the agenda read-only to members who don’t organize the series', async () => {
    api({
      [`GET /api/meeting-series/${WEEKLY.id}`]: () =>
        Response.json({ ...WEEKLY, organizerId: BO.id, organizerName: BO.name }),
      [`GET ${AGENDA}`]: () => Response.json([RETURNS, BUDGET]),
    })
    renderApp(`/meetings/${MONDAY.id}`)

    expect(await screen.findByRole('link', { name: 'Weekly ops sync' })).toBeVisible()
    await waitFor(async () => expect(await itemTitles()).toHaveLength(2))
    expect(screen.getByText('not covered last time')).toBeVisible()
    expect(screen.queryByRole('textbox', { name: 'New agenda item' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Remove|Move|Edit/ })).not.toBeInTheDocument()
    expect(screen.queryByText('⋮⋮')).not.toBeInTheDocument()
  })

  it('shows the agenda of a closed meeting read-only to the organizer', async () => {
    api({
      [`GET /api/meetings/${MONDAY.id}`]: () => Response.json({ ...MONDAY, status: 'CLOSED' }),
      [`GET ${AGENDA}`]: () => Response.json([BUDGET]),
    })
    renderApp(`/meetings/${MONDAY.id}`)

    expect(await screen.findByText('Budget')).toBeVisible()
    expect(await screen.findByRole('link', { name: 'Weekly ops sync' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Remove Budget' })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'New agenda item' })).not.toBeInTheDocument()
  })
})
