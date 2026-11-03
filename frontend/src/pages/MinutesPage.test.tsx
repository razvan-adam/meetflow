import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Minutes, MinutesTask } from '../api/minutes.ts'
import { ANA, mockApi, renderApp } from '../test/renderApp.tsx'

const MEETING_ID = '7e6d5c4b-3a29-4180-9f8e-7d6c5b4a3928'
const TEAM_ID = '6d0c1a52-3c1e-4f0b-9a57-2d4b8e7f1c10'
const SERIES_ID = '3b5d7f9a-1c2e-4a6b-8d0f-2e4a6c8e0a1b'
const PATH = `/meetings/${MEETING_ID}/minutes`
const REQUEST = `GET /api${PATH}`

const TASK: MinutesTask = {
  id: '44444444-4444-4444-8444-444444444444',
  description: 'Renegotiate rates',
  ownerName: 'Bo Lind',
  deadline: '2026-10-23',
  statusBefore: 'OPEN',
  statusAtClose: 'IN_PROGRESS',
  daysOverdue: 3,
  removed: false,
  removedSince: false,
  currentStatus: 'IN_PROGRESS',
}

const MINUTES: Minutes = {
  meetingId: MEETING_ID,
  teamId: TEAM_ID,
  seriesId: SERIES_ID,
  seriesName: 'Weekly ops sync',
  meetingDate: '2026-10-26',
  scheduledDate: '2026-10-26',
  closedAt: '2026-10-26T10:12:00.000Z',
  closedByName: 'Ana Costa',
  participants: [
    { id: ANA.id, name: 'Ana Costa', organizer: true },
    { id: 'bo', name: 'Bo Lind', organizer: false },
  ],
  commitments: [TASK],
  agenda: [
    {
      title: 'Holiday cover',
      notes: 'Two people off',
      covered: true,
      carriedOver: false,
      decisions: ['Cover from Madrid'],
    },
    { title: 'Returns backlog', notes: null, covered: false, carriedOver: true, decisions: [] },
  ],
  newTasks: [
    {
      ...TASK,
      id: '55555555-5555-4555-8555-555555555555',
      description: 'Confirm the cover',
      deadline: '2026-10-30',
      statusBefore: 'OPEN',
      statusAtClose: 'OPEN',
      currentStatus: 'OPEN',
      daysOverdue: 0,
    },
  ],
  emailRecipients: 2,
  emailsSent: 2,
}

function api(respond: () => Response) {
  return mockApi({
    'GET /api/users/me': () => Response.json(ANA),
    'GET /api/teams': () => Response.json([{ id: TEAM_ID, name: 'Ops team', role: 'ADMIN' }]),
    [`GET /api/teams/${TEAM_ID}`]: () =>
      Response.json({ id: TEAM_ID, name: 'Ops team', role: 'ADMIN' }),
    [`GET /api/meeting-series/${SERIES_ID}`]: () =>
      Response.json({
        id: SERIES_ID,
        teamId: TEAM_ID,
        name: 'Weekly ops sync',
        organizerId: ANA.id,
        organizerName: 'Ana Costa',
      }),
    [REQUEST]: respond,
  })
}

const rowOf = (table: string, task: string) =>
  within(screen.getByRole('table', { name: table }))
    .getByRole('link', { name: task })
    .closest('tr') as HTMLElement

describe('the minutes page', () => {
  it('shows the series, the date, who attended and that it is read-only', async () => {
    api(() => Response.json(MINUTES))
    renderApp(PATH)

    expect(await screen.findByRole('heading', { level: 1, name: /^Minutes: / })).toHaveTextContent(
      '26',
    )
    expect(screen.getByText(/Closed by Ana Costa/)).toHaveTextContent('Weekly ops sync')
    expect(screen.getByText(/· emailed to 2 participants ·/)).toBeVisible()
    expect(screen.getByText('read-only')).toBeVisible()
    expect(screen.getByText('Ana Costa (organizer), Bo Lind')).toBeVisible()
    const record = within(screen.getByRole('article'))
    expect(record.queryByRole('textbox')).not.toBeInTheDocument()
    expect(record.queryByRole('combobox')).not.toBeInTheDocument()
    expect(record.queryByRole('button')).not.toBeInTheDocument()
  })

  it('shows the agenda with its notes and decisions, and what wasn’t covered', async () => {
    api(() => Response.json(MINUTES))
    renderApp(PATH)

    expect(await screen.findByRole('heading', { name: 'Holiday cover' })).toBeVisible()
    expect(screen.getByText('Two people off')).toBeVisible()
    expect(screen.getByText('Cover from Madrid')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Returns backlog' })).toBeVisible()
    expect(screen.getByText('Not covered. Carried over to the next meeting.')).toBeVisible()
  })

  it('shows status changes as before and after, and highlights overdue tasks', async () => {
    api(() => Response.json(MINUTES))
    renderApp(PATH)

    await screen.findByRole('table', { name: 'Commitments reviewed' })
    const row = rowOf('Commitments reviewed', 'Renegotiate rates')
    expect(row).toHaveAttribute('data-overdue')
    expect(row).toHaveTextContent('3 days overdue')
    expect(row).toHaveTextContent('Open → In progress')
    expect(row).toHaveTextContent('Bo Lind')
  })

  it('lists new tasks with their owner and deadline, not overdue', async () => {
    api(() => Response.json(MINUTES))
    renderApp(PATH)

    await screen.findByRole('table', { name: 'New tasks' })
    const row = rowOf('New tasks', 'Confirm the cover')
    expect(row).not.toHaveAttribute('data-overdue')
    expect(row).toHaveTextContent('Bo Lind')
    expect(row).toHaveTextContent('Open')
    expect(row).not.toHaveTextContent('→')
  })

  it('says how a task stands today when that differs from the record', async () => {
    api(() =>
      Response.json({
        ...MINUTES,
        commitments: [{ ...TASK, currentStatus: 'DONE' }],
        newTasks: [{ ...MINUTES.newTasks[0], removedSince: true }],
      }),
    )
    renderApp(PATH)

    await screen.findByRole('table', { name: 'Commitments reviewed' })
    expect(rowOf('Commitments reviewed', 'Renegotiate rates')).toHaveTextContent('Now done')
    expect(rowOf('New tasks', 'Confirm the cover')).toHaveTextContent('Removed since')
  })

  it('lists tasks removed during the meeting apart, saying removed', async () => {
    api(() =>
      Response.json({
        ...MINUTES,
        commitments: [{ ...TASK, removed: true, daysOverdue: 0 }],
      }),
    )
    renderApp(PATH)

    const heading = await screen.findByRole('heading', { name: 'Removed during this meeting' })
    expect(heading).toBeVisible()
    expect(screen.getByText('Renegotiate rates').tagName).toBe('S')
    expect(screen.queryByRole('table', { name: 'Commitments reviewed' })).not.toBeInTheDocument()
  })

  it('says it was emailed only once every email has gone out', async () => {
    let sent = 1
    api(() => Response.json({ ...MINUTES, emailsSent: sent }))
    renderApp(PATH)

    expect(await screen.findByText(/sending the email: 1 of 2 sent so far/)).toBeVisible()
    expect(screen.queryByText(/emailed to/)).not.toBeInTheDocument()

    sent = 2

    expect(
      await screen.findByText(/emailed to 2 participants/, {}, { timeout: 5000 }),
    ).toBeVisible()
    expect(screen.queryByText(/sending the email/)).not.toBeInTheDocument()
  })

  it('says when a meeting was held on another day than planned', async () => {
    api(() => Response.json({ ...MINUTES, meetingDate: '2026-11-03' }))
    renderApp(PATH)

    expect(await screen.findByText(/^Planned for /)).toHaveTextContent('26')
  })

  it('doesn’t mention the plan when the meeting was held that day', async () => {
    api(() => Response.json(MINUTES))
    renderApp(PATH)

    await screen.findByRole('article')
    expect(screen.queryByText(/^Planned for /)).not.toBeInTheDocument()
  })

  it('links to the meeting page', async () => {
    api(() => Response.json(MINUTES))
    renderApp(PATH)

    expect(await screen.findByRole('link', { name: 'Open the meeting page' })).toHaveAttribute(
      'href',
      `/meetings/${MEETING_ID}`,
    )
  })

  it('says so when the minutes don’t exist or aren’t in the user’s teams', async () => {
    api(() => new Response(null, { status: 404 }))
    renderApp(PATH)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'These minutes don’t exist or aren’t in one of your teams.',
    )
  })

  it('says so when the minutes can’t be loaded', async () => {
    api(() => new Response(null, { status: 500 }))
    renderApp(PATH)

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong.')
  })
})
