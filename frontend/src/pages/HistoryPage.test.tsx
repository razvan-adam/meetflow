import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { HistoryPage, HistorySeries } from '../api/history.ts'
import { ANA, mockApi, noSeries, renderApp } from '../test/renderApp.tsx'

const TEAM_ID = '6d0c1a52-3c1e-4f0b-9a57-2d4b8e7f1c10'
const PATH = `/teams/${TEAM_ID}/history`
const REQUEST = `GET /api${PATH}`
const WEEKLY_NEWEST = '7e6d5c4b-3a29-4180-9f8e-7d6c5b4a3928'
const WEEKLY_OLDEST = '8f7e6d5c-4b3a-4291-8f0e-8e7d6c5b4a39'
const CARRIERS = '9a8f7e6d-5c4b-4a3a-9b1f-9f8e7d6c5b4a'

const HISTORY: HistorySeries[] = [
  {
    id: 'weekly',
    name: 'Weekly ops sync',
    meetings: [
      {
        id: WEEKLY_NEWEST,
        date: '2026-10-19',
        decisions: ['Move the dock 3 scanners to the new firmware', 'Require an RMA number'],
        taskCount: 2,
      },
      { id: WEEKLY_OLDEST, date: '2026-10-12', decisions: [], taskCount: 1 },
    ],
  },
  {
    id: 'monthly',
    name: 'Monthly carrier review',
    meetings: [{ id: CARRIERS, date: '2026-10-01', decisions: ['Drop Rapidex'], taskCount: 0 }],
  },
]

const PALLETS: HistorySeries[] = [
  {
    id: 'weekly',
    name: 'Weekly ops sync',
    meetings: [
      {
        id: WEEKLY_OLDEST,
        date: '2026-10-12',
        decisions: ['Bring pallet costs under €18', 'Hire two drivers'],
        taskCount: 1,
      },
    ],
  },
]

const page = (series: HistorySeries[], next: string | null = null): HistoryPage => ({
  series,
  next,
})

function api(responses: Record<string, () => Response>) {
  return mockApi({
    'GET /api/users/me': () => Response.json(ANA),
    'GET /api/teams': () => Response.json([{ id: TEAM_ID, name: 'Ops team', role: 'MEMBER' }]),
    [`GET /api/teams/${TEAM_ID}`]: () =>
      Response.json({ id: TEAM_ID, name: 'Ops team', role: 'MEMBER' }),
    [`GET /api/teams/${TEAM_ID}/meeting-series`]: noSeries,
    ...responses,
  })
}

const group = (name: string) => screen.getByRole('region', { name })

describe('the meeting history', () => {
  it('lists past meetings by series, newest first, each opening its minutes', async () => {
    api({ [REQUEST]: () => Response.json(page(HISTORY)) })
    renderApp(PATH)

    expect(await screen.findByRole('heading', { level: 1, name: 'Meeting history' })).toBeVisible()
    expect((await screen.findAllByRole('heading', { level: 2 })).map((h) => h.textContent)).toEqual(
      ['Weekly ops sync', 'Monthly carrier review'],
    )
    const weekly = within(group('Weekly ops sync'))
    const links = weekly.getAllByRole('link')
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      `/meetings/${WEEKLY_NEWEST}/minutes`,
      `/meetings/${WEEKLY_OLDEST}/minutes`,
    ])
    expect(links[0]).toHaveTextContent('19')
    const newest = links[0].closest('li') as HTMLElement
    expect(
      within(newest)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual(['Move the dock 3 scanners to the new firmware', 'Require an RMA number'])
    expect(newest).toHaveTextContent('2 decisions · 2 tasks')
    expect(links[1].closest('li')).toHaveTextContent('No decisions')
    expect(links[1].closest('li')).toHaveTextContent('0 decisions · 1 task')
    expect(within(group('Monthly carrier review')).getByText('1 decision · 0 tasks')).toBeVisible()
  })

  it('is linked from the top bar for the current team', async () => {
    api({ [REQUEST]: () => Response.json(page(HISTORY)) })
    renderApp(`/teams/${TEAM_ID}`)

    fireEvent.click(await screen.findByRole('link', { name: 'History' }))

    expect(await screen.findByRole('heading', { name: 'Meeting history' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'History' })).toHaveClass(/active/)
  })

  it('searches series names and decisions, and highlights the words found', async () => {
    const fetchMock = api({
      [REQUEST]: () => Response.json(page(HISTORY)),
      [`${REQUEST}?q=pallet+cost`]: () => Response.json(page(PALLETS)),
    })
    const router = renderApp(PATH)
    await screen.findByRole('region', { name: 'Monthly carrier review' })

    const search = screen.getByRole('searchbox')
    for (const typed of ['p', 'pal', 'pallet', 'pallet cost']) {
      fireEvent.change(search, { target: { value: typed } })
    }

    expect(await screen.findByText('Hire two drivers')).toBeVisible()
    expect(screen.queryByRole('region', { name: 'Monthly carrier review' })).toBeNull()
    expect([...document.querySelectorAll('mark')].map((mark) => mark.textContent)).toEqual([
      'pallet',
      'cost',
    ])
    // Only the finished search goes to the server, and it stays in the address.
    expect(
      fetchMock.mock.calls.filter(([url]) => String(url).startsWith(`/api${PATH}?`)),
    ).toHaveLength(1)
    expect(router.state.location.search).toBe('?q=pallet+cost')
  })

  it('opens with the search from the address', async () => {
    api({ [`${REQUEST}?q=pallet`]: () => Response.json(page(PALLETS)) })
    renderApp(`${PATH}?q=pallet`)

    expect(await screen.findByText('Hire two drivers')).toBeVisible()
    expect(screen.getByRole('searchbox')).toHaveValue('pallet')
  })

  it('says when nothing matches', async () => {
    api({ [`${REQUEST}?q=bilbao`]: () => Response.json(page([])) })
    renderApp(`${PATH}?q=bilbao`)

    expect(await screen.findByText(/^No meetings or decisions match/)).toBeVisible()
  })

  it('says when the team has no past meetings yet', async () => {
    api({ [REQUEST]: () => Response.json(page([])) })
    renderApp(PATH)

    expect(await screen.findByText(/^No past meetings yet/)).toBeVisible()
  })

  it('says when the team isn’t one of the user’s', async () => {
    api({ [REQUEST]: () => new Response(null, { status: 404 }) })
    renderApp(PATH)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This team doesn’t exist or you’re not a member of it.',
    )
  })

  it('shows older meetings on request, continuing a series from the page before', async () => {
    const older: HistorySeries[] = [
      {
        id: 'monthly',
        name: 'Monthly carrier review',
        meetings: [
          { id: 'sep-carriers', date: '2026-09-01', decisions: ['Keep Translog'], taskCount: 0 },
        ],
      },
      {
        id: 'quarterly',
        name: 'Quarterly safety review',
        meetings: [{ id: 'q3-safety', date: '2026-08-15', decisions: [], taskCount: 3 }],
      },
    ]
    api({
      [REQUEST]: () => Response.json(page(HISTORY, CARRIERS)),
      [`${REQUEST}?after=${CARRIERS}`]: () => Response.json(page(older)),
    })
    renderApp(PATH)

    fireEvent.click(await screen.findByRole('button', { name: 'Show older meetings' }))

    expect(await screen.findByText('Keep Translog')).toBeVisible()
    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual([
      'Weekly ops sync',
      'Monthly carrier review',
      'Quarterly safety review',
    ])
    expect(
      within(group('Monthly carrier review'))
        .getAllByRole('link')
        .map((link) => link.getAttribute('href')),
    ).toEqual([`/meetings/${CARRIERS}/minutes`, '/meetings/sep-carriers/minutes'])
    expect(screen.queryByRole('button', { name: 'Show older meetings' })).toBeNull()
  })

  it('keeps the meetings shown when older ones can’t be loaded', async () => {
    api({
      [REQUEST]: () => Response.json(page(HISTORY, CARRIERS)),
      [`${REQUEST}?after=${CARRIERS}`]: () => new Response(null, { status: 500 }),
    })
    renderApp(PATH)

    fireEvent.click(await screen.findByRole('button', { name: 'Show older meetings' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Older meetings couldn’t be loaded.')
    expect(screen.getByText('Drop Rapidex')).toBeVisible()
  })

  it('pages through search results with the search', async () => {
    const fetchMock = api({
      [`${REQUEST}?q=pallet`]: () => Response.json(page(PALLETS, WEEKLY_OLDEST)),
      [`${REQUEST}?q=pallet&after=${WEEKLY_OLDEST}`]: () => Response.json(page([])),
    })
    renderApp(`${PATH}?q=pallet`)

    fireEvent.click(await screen.findByRole('button', { name: 'Show older meetings' }))

    await waitFor(() => expect(screen.queryByRole('button', { name: /older meetings/ })).toBeNull())
    expect(fetchMock).toHaveBeenCalledWith(
      `/api${PATH}?q=pallet&after=${WEEKLY_OLDEST}`,
      expect.anything(),
    )
  })
})
