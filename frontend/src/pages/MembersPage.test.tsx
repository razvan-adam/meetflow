import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Series } from '../api/series.ts'
import type { OpenTask } from '../api/tasks.ts'
import type { Member } from '../api/teams.ts'
import { ANA, mockApi, renderApp, requestBody } from '../test/renderApp.tsx'

const TEAM_ID = '6d0c1a52-3c1e-4f0b-9a57-2d4b8e7f1c10'
const PATH = `/teams/${TEAM_ID}/members`
const BO = '1b2c3d4e-5f60-4718-8293-a4b5c6d7e8f9'
const DI = '2c3d4e5f-6071-4829-93a4-b5c6d7e8f9a0'
const SERIES_ID = '3b5d7f9a-1c2e-4a6b-8d0f-2e4a6c8e0a1b'

const MEMBERS: Member[] = [
  { id: ANA.id, name: 'Ana Costa', role: 'ADMIN' },
  { id: BO, name: 'Bo Lind', role: 'MEMBER' },
  { id: DI, name: 'Di Moreau', role: 'MEMBER' },
]

const SERIES: Series[] = [
  {
    id: SERIES_ID,
    teamId: TEAM_ID,
    name: 'Monthly carrier review',
    organizerId: BO,
    organizerName: 'Bo Lind',
  },
]

const TASK: OpenTask = {
  id: '44444444-4444-4444-8444-444444444444',
  description: 'Fix scanner sync errors',
  ownerId: BO,
  deadline: '2026-10-28',
  status: 'OPEN',
}

function api(role: Member['role'], overrides: Record<string, () => Response> = {}) {
  const team = { id: TEAM_ID, name: 'Ops team', role }
  return mockApi({
    'GET /api/users/me': () => Response.json(ANA),
    'GET /api/teams': () => Response.json([team]),
    [`GET /api/teams/${TEAM_ID}`]: () => Response.json(team),
    [`GET /api/teams/${TEAM_ID}/members`]: () => Response.json(MEMBERS),
    [`GET /api/teams/${TEAM_ID}/meeting-series`]: () => Response.json(SERIES),
    [`GET /api/teams/${TEAM_ID}/open-tasks`]: () => Response.json([TASK]),
    ...overrides,
  })
}

const rowOf = (name: string) =>
  within(screen.getByRole('list', { name: 'Members' }))
    .getByText(name, { exact: false })
    .closest('li') as HTMLElement

describe('the members page', () => {
  it('lists each member with their role and the work they have', async () => {
    api('ADMIN')
    renderApp(PATH)

    expect(await screen.findByRole('heading', { name: 'Members' })).toBeVisible()
    expect(rowOf('Ana Costa')).toHaveTextContent('Ana Costa (you)')
    expect(rowOf('Ana Costa')).toHaveTextContent('admin')
    expect(rowOf('Bo Lind')).toHaveTextContent('Organizes Monthly carrier review · 1 open task')
    expect(rowOf('Di Moreau')).toHaveTextContent('No open tasks')
    // The only admin can't lose their rights or be removed.
    expect(screen.getByRole('button', { name: 'Take admin rights from Ana Costa' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Remove Ana Costa' })).toBeDisabled()
  })

  it('shows members the list without the admin actions', async () => {
    api('MEMBER')
    renderApp(PATH)

    expect(await screen.findByText('Team admins manage who is in the team.')).toBeVisible()
    expect(
      within(screen.getByRole('list', { name: 'Members' })).queryAllByRole('button'),
    ).toHaveLength(0)
  })

  it('is linked from the team page', async () => {
    api('ADMIN', { [`GET /api/teams/${TEAM_ID}/meeting-series`]: () => Response.json(SERIES) })
    renderApp(`/teams/${TEAM_ID}`)

    fireEvent.click(await screen.findByRole('link', { name: 'Manage members' }))

    expect(await screen.findByRole('heading', { name: 'Members' })).toBeVisible()
  })

  it('makes a member an admin', async () => {
    const fetchMock = api('ADMIN', {
      [`PUT /api/teams/${TEAM_ID}/members/${DI}/role`]: () =>
        Response.json({ id: DI, name: 'Di Moreau', role: 'ADMIN' }),
    })
    renderApp(PATH)

    fireEvent.click(await screen.findByRole('button', { name: 'Make Di Moreau an admin' }))

    await screen.findByRole('list', { name: 'Members' })
    await expect.poll(() => fetchMock.mock.calls.length).toBeGreaterThan(6)
    expect(requestBody(fetchMock, `PUT /api/teams/${TEAM_ID}/members/${DI}/role`)).toEqual({
      role: 'ADMIN',
    })
  })

  it('says why a role can’t change when the team would lose its last admin', async () => {
    api('ADMIN', {
      [`PUT /api/teams/${TEAM_ID}/members/${DI}/role`]: () => new Response(null, { status: 409 }),
    })
    renderApp(PATH)

    fireEvent.click(await screen.findByRole('button', { name: 'Make Di Moreau an admin' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A team always keeps at least one admin.',
    )
  })

  it('removes a member only once their tasks and series are handed over', async () => {
    const fetchMock = api('ADMIN', {
      [`POST /api/teams/${TEAM_ID}/members/${BO}/removal`]: () =>
        new Response(null, { status: 204 }),
    })
    renderApp(PATH)

    fireEvent.click(await screen.findByRole('button', { name: 'Remove Bo Lind' }))

    const panel = within(screen.getByRole('form', { name: 'Remove Bo Lind from Ops team' }))
    const confirm = panel.getByRole('button', { name: 'Remove Bo Lind' })
    expect(confirm).toBeDisabled()
    expect(panel.getByText('2 items left to resolve.')).toBeVisible()
    // Work can't go back to the member being removed.
    const taskChoice = panel.getByRole('combobox', {
      name: 'What to do with “Fix scanner sync errors”',
    })
    expect(within(taskChoice).queryByText('Reassign to Bo Lind')).toBeNull()

    fireEvent.change(taskChoice, { target: { value: DI } })
    expect(confirm).toBeDisabled()
    fireEvent.change(
      panel.getByRole('combobox', { name: 'New organizer of Monthly carrier review' }),
      {
        target: { value: ANA.id },
      },
    )
    fireEvent.click(confirm)

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Bo Lind was removed from Ops team.',
    )
    expect(requestBody(fetchMock, `POST /api/teams/${TEAM_ID}/members/${BO}/removal`)).toEqual({
      tasks: [{ taskId: TASK.id, newOwnerId: DI }],
      series: [{ seriesId: SERIES_ID, organizerId: ANA.id }],
    })
  })

  it('can remove a task instead of reassigning it', async () => {
    const fetchMock = api('ADMIN', {
      [`GET /api/teams/${TEAM_ID}/meeting-series`]: () => Response.json([]),
      [`POST /api/teams/${TEAM_ID}/members/${BO}/removal`]: () =>
        new Response(null, { status: 204 }),
    })
    renderApp(PATH)

    fireEvent.click(await screen.findByRole('button', { name: 'Remove Bo Lind' }))
    fireEvent.change(screen.getByRole('combobox', { name: /^What to do with/ }), {
      target: { value: 'remove' },
    })
    fireEvent.click(
      within(screen.getByRole('form')).getByRole('button', { name: 'Remove Bo Lind' }),
    )

    await screen.findByRole('status')
    expect(requestBody(fetchMock, `POST /api/teams/${TEAM_ID}/members/${BO}/removal`)).toEqual({
      tasks: [{ taskId: TASK.id, newOwnerId: null }],
      series: [],
    })
  })

  it('says when the member’s work changed while choosing', async () => {
    api('ADMIN', {
      [`POST /api/teams/${TEAM_ID}/members/${DI}/removal`]: () =>
        new Response(null, { status: 409 }),
    })
    renderApp(PATH)

    fireEvent.click(await screen.findByRole('button', { name: 'Remove Di Moreau' }))
    expect(screen.getByText(/no open tasks or series to hand over/)).toBeVisible()
    fireEvent.click(screen.getAllByRole('button', { name: 'Remove Di Moreau' })[1])

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Their tasks, series or role changed while you were choosing.',
    )
  })

  it('asks again when the chosen member left the team meanwhile', async () => {
    let membersRead = 0
    const fetchMock = api('ADMIN', {
      // Di is removed by another admin while Ana is choosing; the 409 reloads the lists.
      [`GET /api/teams/${TEAM_ID}/members`]: () =>
        Response.json(membersRead++ === 0 ? MEMBERS : MEMBERS.filter((member) => member.id !== DI)),
      [`POST /api/teams/${TEAM_ID}/members/${BO}/removal`]: () =>
        new Response(null, { status: 409 }),
    })
    renderApp(PATH)

    fireEvent.click(await screen.findByRole('button', { name: 'Remove Bo Lind' }))
    const panel = within(screen.getByRole('form', { name: 'Remove Bo Lind from Ops team' }))
    const confirm = panel.getByRole('button', { name: 'Remove Bo Lind' })
    const taskChoice = panel.getByRole('combobox', { name: /^What to do with/ })
    fireEvent.change(taskChoice, { target: { value: DI } })
    fireEvent.change(panel.getByRole('combobox', { name: /^New organizer of/ }), {
      target: { value: ANA.id },
    })
    fireEvent.click(confirm)

    expect(await panel.findByText('1 item left to resolve.')).toBeVisible()
    expect(taskChoice).toHaveValue('')
    expect(within(taskChoice).queryByText('Reassign to Di Moreau')).toBeNull()
    expect(confirm).toBeDisabled()
    expect(
      fetchMock.mock.calls.filter(
        ([url, init]) => String(url).endsWith(`/members/${BO}/removal`) && init?.method === 'POST',
      ),
    ).toHaveLength(1)
  })

  it('takes an admin who removes themselves back to their tasks', async () => {
    const twoAdmins = MEMBERS.map((member) =>
      member.id === DI ? { ...member, role: 'ADMIN' as const } : member,
    )
    api('ADMIN', {
      [`GET /api/teams/${TEAM_ID}/members`]: () => Response.json(twoAdmins),
      [`POST /api/teams/${TEAM_ID}/members/${ANA.id}/removal`]: () =>
        new Response(null, { status: 204 }),
    })
    const router = renderApp(PATH)

    fireEvent.click(await screen.findByRole('button', { name: 'Remove Ana Costa' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Remove Ana Costa' })[1])

    await expect.poll(() => router.state.location.pathname).toBe('/')
  })
})
