import { describe, expect, it } from 'vitest'
import css from './global.css?raw'

const MINIMUM = 4.5
const TEXT = ['ink', 'muted', 'wire', 'overdue', 'done']
const BACKGROUNDS = ['paper', 'frame', 'fill', 'wire-soft', 'overdue-soft']

// The first :root rule holds the light theme.
function lightTokens(): Record<string, string> {
  const rule = /:root\s*{([^}]*)}/.exec(css)?.[1] ?? ''
  return Object.fromEntries(
    [...rule.matchAll(/--([\w-]+):\s*(#[0-9a-f]{6})/g)].map(([, name, value]) => [name, value]),
  )
}

function luminance(hex: string): number {
  const [red, green, blue] = [1, 3, 5].map((start) => {
    const channel = parseInt(hex.slice(start, start + 2), 16) / 255
    return channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

function contrast(first: string, second: string): number {
  const [lighter, darker] = [luminance(first), luminance(second)].sort((a, b) => b - a)
  return (lighter + 0.05) / (darker + 0.05)
}

describe('the light theme', () => {
  const tokens = lightTokens()

  it.each(TEXT.flatMap((text) => BACKGROUNDS.map((background) => [text, background])))(
    'keeps %s readable on %s',
    (text, background) => {
      expect(contrast(tokens[text], tokens[background])).toBeGreaterThanOrEqual(MINIMUM)
    },
  )

  it('keeps white text readable on the accent color', () => {
    expect(contrast('#ffffff', tokens.wire)).toBeGreaterThanOrEqual(MINIMUM)
  })
})
