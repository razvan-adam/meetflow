import { expect, test } from 'vitest'

// Deliberately failing test to confirm that a red check blocks merging into main (MF-33). Never merge.
test('fails on purpose', () => {
  expect(1).toBe(2)
})
