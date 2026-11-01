import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Meeting } from '../api/meetings.ts'
import type { Series } from '../api/series.ts'
import type { Member, Team } from '../api/teams.ts'
import { ANA, mockApi, renderApp, requestBody } from '../test/renderApp.tsx'
import { formatMeetingTime } from './meetingTime.ts'

const OPS: Team = { id: '6d0c1a52-3c1e-4f0b-9a57-2d4b8e7f1c10', name: 'Ops team', role: 'MEMBER' }
const BO = { id: '9c8b7a6d-5e4f-4321-8765-0fedcba98765', name: 'Bo Lind' }
const DEE = { id: '5a4b3c2d-1e0f-4a9b-8c7d-6e5f4a3b2c1d', name: 'Dee Moss' }
const WEEKLY: Series = {
  id: '3b5d7f9a-1c2e-4a6b-8d0f-2e4a6c8e0a1b',
  teamId: OPS.id,
  name: 'Weekly ops sync',
  organizerId: ANA.id,
  organizerName: ANA.name,
}
const MEMBERS: Member[] = [
  { id: ANA.id, name: ANA.name, role: 'ADMIN' },
  { id: BO.id, name: BO.name, role: 'MEMBER' },
  { id: DEE.id, name: DEE.name, role: 'MEMBER' },
]
// The tests run in UTC (vite.config.ts), so 09:30 in the form is 09:30Z.
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

const loggedIn = () => Response.json(ANA)
const teamApi = {
  'GET /api/users/me': loggedIn,
  'GET /api/teams': () => Response.json([OPS]),
  [`GET /api/teams/${OPS.id}`]: () => Response.json(OPS),
  [`GET /api/teams/${OPS.id}/members`]: () => Response.json(MEMBERS),
}

describe('scheduling a meeting', () => {
  it('lets the organizer schedule the next meeting with participants from the team', async () => {
    let meetings: Meeting[] = []
    const fetchMock = mockApi({
      ...teamApi,
      [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(WEEKLY),
      [`GET /api/meeting-series/${WEEKLY.id}/meetings`]: () => Response.json(meetings),
      [`POST /api/meeting-series/${WEEKLY.id}/meetings`]: () => {
        meetings = [MONDAY]
        return Response.json(MONDAY, { status: 201 })
      },
      [`GET /api/meetings/${MONDAY.id}`]: () => Response.json(MONDAY),
    })
    const router = renderApp(`/series/${WEEKLY.id}`)

    fireEvent.click(await screen.findByRole('link', { name: 'Schedule next meeting' }))
    expect(await screen.findByRole('heading', { name: 'Schedule the next meeting' })).toBeVisible()
    const organizer = await screen.findByRole('checkbox', { name: /Ana Costa/ })
    expect(organizer).toBeChecked()
    expect(organizer).toBeDisabled()
    // Everyone in the team is ticked to start with.
    fireEvent.click(screen.getByRole('checkbox', { name: 'Dee Moss' }))
    fireEvent.change(screen.getByLabelText('Date'), { target: { value: '2026-11-02' } })
    fireEvent.change(screen.getByLabelText('Time'), { target: { value: '09:30' } })
    fireEvent.click(screen.getByRole('button', { name: 'Schedule meeting' }))

    expect(
      await screen.findByRole('heading', { name: formatMeetingTime(MONDAY.scheduledAt) }),
    ).toBeVisible()
    expect(router.state.location.pathname).toBe(`/meetings/${MONDAY.id}`)
    expect(requestBody(fetchMock, `POST /api/meeting-series/${WEEKLY.id}/meetings`)).toEqual({
      scheduledAt: '2026-11-02T09:30:00.000Z',
      participantIds: [ANA.id, BO.id],
    })
    const participants = screen.getByRole('region', { name: 'Participants (2)' })
    expect(within(participants).getByText('Bo Lind')).toBeVisible()
    expect(within(participants).getByText('· organizer')).toBeVisible()
  })

  it('shows the next meeting on the series page, and no scheduling to other members', async () => {
    const byBo = { ...WEEKLY, organizerId: BO.id, organizerName: BO.name }
    mockApi({
      ...teamApi,
      [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(byBo),
      [`GET /api/meeting-series/${WEEKLY.id}/meetings`]: () => Response.json([MONDAY]),
    })
    renderApp(`/series/${WEEKLY.id}`)

    const next = await screen.findByRole('region', { name: 'Next meeting' })
    expect(
      within(next).getByRole('link', { name: formatMeetingTime(MONDAY.scheduledAt) }),
    ).toHaveAttribute('href', `/meetings/${MONDAY.id}`)
    expect(within(next).getByText('2 participants')).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Schedule next meeting' })).not.toBeInTheDocument()
  })

  it('tells other members that only the organizer schedules meetings', async () => {
    mockApi({
      ...teamApi,
      [`GET /api/meeting-series/${WEEKLY.id}`]: () =>
        Response.json({ ...WEEKLY, organizerId: BO.id, organizerName: BO.name }),
    })
    renderApp(`/series/${WEEKLY.id}/schedule`)

    expect(
      await screen.findByText('Only the organizer, Bo Lind, can schedule meetings.'),
    ).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Schedule meeting' })).not.toBeInTheDocument()
  })
})

describe('the meeting page', () => {
  it('lets the organizer change the date and participants', async () => {
    let meeting = MONDAY
    const fetchMock = mockApi({
      ...teamApi,
      [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(WEEKLY),
      [`GET /api/meeting-series/${WEEKLY.id}/meetings`]: () => Response.json([meeting]),
      [`GET /api/meetings/${MONDAY.id}`]: () => Response.json(meeting),
      [`PUT /api/meetings/${MONDAY.id}`]: () => {
        meeting = {
          ...MONDAY,
          scheduledAt: '2026-11-03T15:00:00.000Z',
          participants: [...MONDAY.participants, DEE],
        }
        return Response.json(meeting)
      },
    })
    renderApp(`/meetings/${MONDAY.id}`)

    fireEvent.click(await screen.findByRole('link', { name: 'Edit date and participants' }))
    const date = await screen.findByLabelText('Date')
    expect(date).toHaveValue('2026-11-02')
    expect(screen.getByLabelText('Time')).toHaveValue('09:30')
    expect(screen.getByRole('checkbox', { name: 'Bo Lind' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Dee Moss' })).not.toBeChecked()
    fireEvent.change(date, { target: { value: '2026-11-03' } })
    fireEvent.change(screen.getByLabelText('Time'), { target: { value: '15:00' } })
    fireEvent.click(screen.getByRole('checkbox', { name: 'Dee Moss' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(
      await screen.findByRole('heading', { name: formatMeetingTime('2026-11-03T15:00:00Z') }),
    ).toBeVisible()
    expect(screen.getByRole('region', { name: 'Participants (3)' })).toBeVisible()
    expect(requestBody(fetchMock, `PUT /api/meetings/${MONDAY.id}`)).toEqual({
      scheduledAt: '2026-11-03T15:00:00.000Z',
      participantIds: [ANA.id, BO.id, DEE.id],
    })
  })

  it('says so when the meeting was closed meanwhile', async () => {
    mockApi({
      ...teamApi,
      [`GET /api/meeting-series/${WEEKLY.id}`]: () => Response.json(WEEKLY),
      [`GET /api/meetings/${MONDAY.id}`]: () => Response.json(MONDAY),
      [`PUT /api/meetings/${MONDAY.id}`]: () => Response.json({ status: 409 }, { status: 409 }),
    })
    renderApp(`/meetings/${MONDAY.id}/edit`)

    fireEvent.click(await screen.findByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('was closed meanwhile')
  })

  it('shows the meeting read-only to members who don’t organize it', async () => {
    mockApi({
      ...teamApi,
      [`GET /api/meeting-series/${WEEKLY.id}`]: () =>
        Response.json({ ...WEEKLY, organizerId: BO.id, organizerName: BO.name }),
      [`GET /api/meetings/${MONDAY.id}`]: () => Response.json(MONDAY),
    })
    renderApp(`/meetings/${MONDAY.id}`)

    expect(await screen.findByRole('link', { name: 'Weekly ops sync' })).toHaveAttribute(
      'href',
      `/series/${WEEKLY.id}`,
    )
    expect(screen.getByRole('combobox', { name: 'Team' })).toHaveValue(OPS.id)
    expect(
      screen.queryByRole('link', { name: 'Edit date and participants' }),
    ).not.toBeInTheDocument()
  })

  it('says so when the meeting is not in one of the user’s teams', async () => {
    mockApi({
      'GET /api/users/me': loggedIn,
      'GET /api/teams': () => Response.json([OPS]),
      [`GET /api/meetings/${MONDAY.id}`]: () => Response.json({ status: 404 }, { status: 404 }),
    })
    renderApp(`/meetings/${MONDAY.id}`)

    expect(await screen.findByRole('alert')).toHaveTextContent('isn’t in one of your teams')
  })
})
