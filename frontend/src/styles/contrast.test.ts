import { describe, expect, it } from 'vitest'
import css from './global.css?raw'

const MINIMUM = 4.5
const TEXT = ['ink', 'muted', 'wire', 'overdue', 'done']
const BACKGROUNDS = ['paper', 'frame', 'fill', 'wire-soft', 'overdue-soft']

function tokensIn(rule: string): Record<string, string> {
  return Object.fromEntries(
    [...rule.matchAll(/--([\w-]+):\s*(#[0-9a-f]{6})/g)].map(([, name, value]) => [name, value]),
  )
}

function ruleBody(selector: RegExp): string {
  return new RegExp(selector.source + /\s*{([^}]*)}/.source).exec(css)?.[1] ?? ''
}

// The first :root rule holds the light theme. The dark theme appears twice: for the system setting
// and for the user's choice.
const light = tokensIn(ruleBody(/:root/))
const darkFromSystem = tokensIn(ruleBody(/:root:not\(\[data-theme='light'\]\)/))
const darkChosen = tokensIn(ruleBody(/:root\[data-theme='dark'\]/))

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

describe.each([
  ['light', light],
  ['dark', darkChosen],
])('the %s theme', (_theme, tokens) => {
  it.each(TEXT.flatMap((text) => BACKGROUNDS.map((background) => [text, background])))(
    'keeps %s readable on %s',
    (text, background) => {
      expect(contrast(tokens[text], tokens[background])).toBeGreaterThanOrEqual(MINIMUM)
    },
  )

  // Inputs have a frame background.
  it('keeps placeholders readable in inputs', () => {
    expect(contrast(tokens.placeholder, tokens.frame)).toBeGreaterThanOrEqual(MINIMUM)
  })

  // Filled buttons use the frame color for their label.
  it('keeps button labels readable on the accent color', () => {
    expect(contrast(tokens.frame, tokens.wire)).toBeGreaterThanOrEqual(MINIMUM)
  })
})

it('uses the same dark colors for the system setting and the theme switch', () => {
  expect(Object.keys(darkChosen).length).toBeGreaterThan(0)
  expect(darkFromSystem).toEqual(darkChosen)
})
