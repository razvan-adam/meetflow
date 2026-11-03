import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { PendingTask } from '../api/tasks.ts'
import type { Team } from '../api/teams.ts'
import { ANA, mockApi, renderApp, requestBody } from '../test/renderApp.tsx'

const OPS: Team = { id: '6d0c1a52-3c1e-4f0b-9a57-2d4b8e7f1c10', name: 'Ops team', role: 'MEMBER' }
const LEADERSHIP: Team = {
  id: '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d',
  name: 'Leadership',
  role: 'ADMIN',
}
const CONTRACT: PendingTask = {
  id: '44444444-4444-4444-8444-444444444444',
  description: 'Sign the contract',
  deadline: '2999-11-20',
  status: 'OPEN',
  teamId: OPS.id,
  teamName: OPS.name,
  seriesId: '3b5d7f9a-1c2e-4a6b-8d0f-2e4a6c8e0a1b',
  seriesName: 'Weekly ops sync',
  meetingId: '7e6d5c4b-3a29-4180-9f8e-7d6c5b4a3928',
  meetingScheduledAt: '2026-11-02T12:00:00.000Z',
}
const BUDGET: PendingTask = {
  ...CONTRACT,
  id: '55555555-5555-4555-8555-555555555555',
  description: 'Send the budget draft',
  deadline: '2020-01-01',
  status: 'IN_PROGRESS',
  teamId: LEADERSHIP.id,
  teamName: LEADERSHIP.name,
  seriesName: 'Monthly budget review',
}
const PENDING = 'GET /api/users/me/tasks'
const STATUS = `PUT /api/tasks/${CONTRACT.id}/status`

function api(overrides: Record<string, () => Response>) {
  return mockApi({
    'GET /api/users/me': () => Response.json(ANA),
    'GET /api/teams': () => Response.json([LEADERSHIP, OPS]),
    [PENDING]: () => Response.json([BUDGET, CONTRACT]),
    ...overrides,
  })
}

const list = () => screen.findByRole('list', { name: 'My pending tasks' })
const statusOf = (description: string) =>
  screen.findByRole('combobox', { name: `Status of ${description}` })

describe('the pending-tasks dashboard', () => {
  it('shows each task with its team, series, deadline, status and meeting', async () => {
    api({})
    renderApp('/')

    const row = within(await list())
      .getByRole('link', { name: 'Sign the contract' })
      .closest('li') as HTMLElement
    expect(within(row).getByRole('link', { name: 'Sign the contract' })).toHaveAttribute(
      'href',
      `/tasks/${CONTRACT.id}`,
    )
    expect(within(row).getByText(/Ops team · Weekly ops sync/)).toBeVisible()
    expect(within(row).getByRole('link', { name: /from the meeting of/ })).toHaveAttribute(
      'href',
      `/meetings/${CONTRACT.meetingId}`,
    )
    expect(within(row).getByText(/^Due /)).toBeVisible()
    expect(within(row).getByRole('combobox')).toHaveValue('OPEN')
  })

  it('keeps the soonest deadline first and highlights overdue tasks', async () => {
    api({})
    renderApp('/')

    const rows = within(await list()).getAllByRole('listitem')
    expect(rows[0]).toHaveTextContent('Send the budget draft')
    expect(rows[1]).toHaveTextContent('Sign the contract')
    expect(rows[0]).toHaveAttribute('data-overdue')
    expect(rows[0]).toHaveTextContent(/days overdue/)
    expect(rows[1]).not.toHaveAttribute('data-overdue')
    expect(screen.getByText('pending')).toHaveTextContent('2 pending')
    expect(screen.getByText('overdue')).toHaveTextContent('1 overdue')
  })

  it('keeps a task marked done in place, so it can be set back', async () => {
    let pending = [BUDGET, CONTRACT]
    let changes = 0
    const fetchMock = api({
      [PENDING]: () => Response.json(pending),
      [STATUS]: () => {
        changes += 1
        pending = changes === 1 ? [BUDGET] : [BUDGET, CONTRACT]
        return Response.json({ ...CONTRACT, status: changes === 1 ? 'DONE' : 'OPEN' })
      },
    })
    renderApp('/')

    fireEvent.change(await statusOf('Sign the contract'), { target: { value: 'DONE' } })

    expect(await screen.findByText('Done. Leaves your list.')).toBeVisible()
    expect(requestBody(fetchMock, STATUS)).toEqual({ status: 'DONE' })
    await waitFor(async () => expect(await statusOf('Sign the contract')).toBeEnabled())
    expect(await statusOf('Sign the contract')).toHaveValue('DONE')
    expect(screen.getByText('pending')).toHaveTextContent('1 pending')

    fireEvent.change(await statusOf('Sign the contract'), { target: { value: 'OPEN' } })

    await waitFor(() =>
      expect(screen.queryByText('Done. Leaves your list.')).not.toBeInTheDocument(),
    )
    await waitFor(() => expect(changes).toBe(2))
    expect(await statusOf('Sign the contract')).toHaveValue('OPEN')
    expect(screen.getByText('pending')).toHaveTextContent('2 pending')
  })

  it('drops a done task once the filter changes', async () => {
    let pending = [BUDGET, CONTRACT]
    api({
      [PENDING]: () => Response.json(pending),
      [STATUS]: () => {
        pending = [BUDGET]
        return Response.json({ ...CONTRACT, status: 'DONE' })
      },
    })
    renderApp('/')

    fireEvent.change(await statusOf('Sign the contract'), { target: { value: 'DONE' } })
    await waitFor(async () => expect(await statusOf('Sign the contract')).toBeEnabled())

    fireEvent.click(screen.getByRole('button', { name: 'All teams' }))

    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'Sign the contract' })).not.toBeInTheDocument(),
    )
  })

  it('shows the status as it was, and why, when a change fails', async () => {
    api({ [STATUS]: () => new Response(null, { status: 404 }) })
    renderApp('/')

    fireEvent.change(await statusOf('Sign the contract'), { target: { value: 'DONE' } })

    expect(await screen.findByRole('alert')).toHaveTextContent('This task was removed meanwhile.')
    expect(await statusOf('Sign the contract')).toHaveValue('OPEN')
    expect(screen.queryByText('Done. Leaves your list.')).not.toBeInTheDocument()
  })

  it('filters by team', async () => {
    api({})
    renderApp('/')

    expect(await list()).toBeVisible()
    fireEvent.click(await screen.findByRole('button', { name: 'Ops team' }))

    expect(screen.getByRole('button', { name: 'Ops team' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('link', { name: 'Sign the contract' })).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Send the budget draft' })).not.toBeInTheDocument()
    expect(screen.getByText('pending')).toHaveTextContent('1 pending')

    fireEvent.click(screen.getByRole('button', { name: 'All teams' }))
    expect(screen.getByRole('link', { name: 'Send the budget draft' })).toBeVisible()
  })

  it('says so when a filtered team has nothing open', async () => {
    api({ [PENDING]: () => Response.json([BUDGET]) })
    renderApp('/')

    expect(await list()).toBeVisible()
    fireEvent.click(await screen.findByRole('button', { name: 'Ops team' }))

    expect(screen.getByText('You have no open tasks in Ops team.')).toBeVisible()
  })

  it('offers no filter to someone in one team', async () => {
    api({
      'GET /api/teams': () => Response.json([OPS]),
      [PENDING]: () => Response.json([CONTRACT]),
    })
    renderApp('/')

    expect(await list()).toBeVisible()
    expect(screen.queryByRole('group', { name: 'Filter by team' })).not.toBeInTheDocument()
  })

  it('says so when nothing is open', async () => {
    api({ [PENDING]: () => Response.json([]) })
    renderApp('/')

    expect(await screen.findByText(/You have no open tasks\./)).toBeVisible()
    expect(screen.queryByRole('list', { name: 'My pending tasks' })).not.toBeInTheDocument()
  })

  it('prompts someone with no team to create one', async () => {
    api({ 'GET /api/teams': () => Response.json([]), [PENDING]: () => Response.json([]) })
    renderApp('/')

    expect(await screen.findByText(/You’re not in a team yet/)).toBeVisible()
    expect(screen.queryByText(/You have no open tasks/)).not.toBeInTheDocument()
  })

  it('says so when the tasks can’t be loaded', async () => {
    api({ [PENDING]: () => new Response(null, { status: 500 }) })
    renderApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('Your tasks couldn’t be loaded.')
  })
})
