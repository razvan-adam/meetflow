import { expect, it } from 'vitest'
import { formatDeadline } from './taskDeadline.ts'

it('shows a deadline on the day it was set, in the user’s own time zone', () => {
  const sameDay = new Intl.DateTimeFormat(undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(2026, 10, 20))

  expect(formatDeadline('2026-11-20')).toBe(sameDay)
})
