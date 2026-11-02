import '@testing-library/jest-dom/vitest'
import { cleanup, configure, screen } from '@testing-library/react'
import { afterEach, beforeAll, vi } from 'vitest'

// The default of 1 second is too tight for the first test of a file. Its first render and first
// role query are slow until the code warms up, and more so while all the test files start at once:
// together they were measured at up to 1.9 seconds (MF-40). This only makes a failing test take
// longer to report. Keep it below testTimeout in vite.config.ts, so failures show this message.
configure({ asyncUtilTimeout: 5000 })

// The first role query of a file is by far the slowest (up to 877 ms, then about 10 ms), so pay for
// it here, outside the time limit of the first test (MF-40).
beforeAll(() => {
  document.body.innerHTML = '<a href="/">Warm-up</a>'
  screen.getByRole('link', { name: 'Warm-up' })
  document.body.innerHTML = ''
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})
