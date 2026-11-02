import { describe, expect, it } from 'vitest'
import type { Task } from '../api/tasks.ts'
import { dueText, formatDeadline, isOverdue } from './taskDisplay.ts'

const TASK = { deadline: '2026-11-20', status: 'OPEN' } as Task

it('shows a deadline on the day it was set, in the user’s own time zone', () => {
  const sameDay = new Intl.DateTimeFormat(undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(2026, 10, 20))

  expect(formatDeadline('2026-11-20')).toBe(sameDay)
})

describe('overdue', () => {
  it('starts the day after the deadline', () => {
    expect(isOverdue(TASK, '2026-11-20')).toBe(false)
    expect(isOverdue(TASK, '2026-11-21')).toBe(true)
  })

  it('counts for a task in progress but not for a done one', () => {
    expect(isOverdue({ ...TASK, status: 'IN_PROGRESS' }, '2026-12-01')).toBe(true)
    expect(isOverdue({ ...TASK, status: 'DONE' }, '2026-12-01')).toBe(false)
  })

  it('says how many days late the task is', () => {
    expect(dueText(TASK, '2026-11-21')).toMatch(/· 1 day overdue$/)
    expect(dueText(TASK, '2026-11-24')).toMatch(/· 4 days overdue$/)
    expect(dueText(TASK, '2026-12-01')).toMatch(/· 11 days overdue$/)
  })

  it('counts across a clock change and a new year', () => {
    expect(dueText({ ...TASK, deadline: '2026-10-20' }, '2026-11-02')).toMatch(/· 13 days overdue$/)
    expect(dueText({ ...TASK, deadline: '2026-12-30' }, '2027-01-02')).toMatch(/· 3 days overdue$/)
  })

  it('says nothing on the deadline itself or before it', () => {
    expect(dueText(TASK, '2026-11-20')).not.toMatch(/overdue/)
    expect(dueText(TASK, '2026-11-02')).not.toMatch(/overdue/)
  })
})
