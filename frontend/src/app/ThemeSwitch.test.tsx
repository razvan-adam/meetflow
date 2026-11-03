import { act, fireEvent, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ANA, mockApi, noTeams, renderApp } from '../test/renderApp.tsx'
import indexHtml from '../../index.html?raw'

const root = document.documentElement

function logIn() {
  mockApi({ 'GET /api/users/me': () => Response.json(ANA), 'GET /api/teams': noTeams })
  renderApp('/')
}

// A system setting that a test can change, as when the computer moves to dark mode at night.
function stubSystem(dark: boolean) {
  const listeners = new Set<() => void>()
  const query = {
    matches: dark,
    addEventListener: (_type: string, listener: () => void) => listeners.add(listener),
    removeEventListener: (_type: string, listener: () => void) => listeners.delete(listener),
  }
  vi.stubGlobal('matchMedia', () => query)
  return (nowDark: boolean) => {
    query.matches = nowDark
    act(() => listeners.forEach((listener) => listener()))
  }
}

// Runs the script at the top of index.html, as the browser does before the first paint.
function runIndexScript() {
  const script = /<script>([\s\S]*?)<\/script>/.exec(indexHtml)?.[1] ?? ''
  new Function(script)()
}

describe('the theme switch', () => {
  it('follows a light system setting until the user chooses', async () => {
    logIn()

    const button = await screen.findByRole('button', { name: 'Switch to dark mode' })

    expect(button).toHaveTextContent('Dark mode')
    expect(root.dataset.theme).toBeUndefined()
    expect(localStorage.getItem('meetflow-theme')).toBeNull()
  })

  it('follows a dark system setting until the user chooses', async () => {
    stubSystem(true)
    logIn()

    const button = await screen.findByRole('button', { name: 'Switch to light mode' })

    expect(button).toHaveTextContent('Light mode')
    expect(root.dataset.theme).toBeUndefined()
  })

  it('switches at once, both ways, and remembers the latest choice', async () => {
    logIn()

    fireEvent.click(await screen.findByRole('button', { name: 'Switch to dark mode' }))

    expect(root.dataset.theme).toBe('dark')
    expect(localStorage.getItem('meetflow-theme')).toBe('dark')

    fireEvent.click(screen.getByRole('button', { name: 'Switch to light mode' }))

    expect(root.dataset.theme).toBe('light')
    expect(localStorage.getItem('meetflow-theme')).toBe('light')
    expect(screen.getByRole('button', { name: 'Switch to dark mode' })).toBeVisible()
  })

  it('keeps the choice over a dark system setting', async () => {
    stubSystem(true)
    logIn()

    fireEvent.click(await screen.findByRole('button', { name: 'Switch to light mode' }))

    expect(root.dataset.theme).toBe('light')
    expect(screen.getByRole('button', { name: 'Switch to dark mode' })).toBeVisible()
  })

  it('moves with the system setting while the user hasn’t chosen', async () => {
    const setSystemDark = stubSystem(false)
    logIn()
    await screen.findByRole('button', { name: 'Switch to dark mode' })

    setSystemDark(true)

    expect(screen.getByRole('button', { name: 'Switch to light mode' })).toBeVisible()
  })

  it('stays with the user’s choice when the system setting changes', async () => {
    const setSystemDark = stubSystem(false)
    logIn()
    fireEvent.click(await screen.findByRole('button', { name: 'Switch to dark mode' }))
    fireEvent.click(screen.getByRole('button', { name: 'Switch to light mode' }))

    setSystemDark(true)

    expect(root.dataset.theme).toBe('light')
    expect(screen.getByRole('button', { name: 'Switch to dark mode' })).toBeVisible()
  })

  it('follows a choice made in another tab', async () => {
    logIn()
    await screen.findByRole('button', { name: 'Switch to dark mode' })

    act(() => {
      window.dispatchEvent(new StorageEvent('storage', { key: 'meetflow-theme', newValue: 'dark' }))
    })

    expect(root.dataset.theme).toBe('dark')
    expect(screen.getByRole('button', { name: 'Switch to light mode' })).toBeVisible()
  })

  it('still switches when the browser blocks storage', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementationOnce(() => {
      throw new Error('Blocked')
    })
    logIn()

    fireEvent.click(await screen.findByRole('button', { name: 'Switch to dark mode' }))

    expect(root.dataset.theme).toBe('dark')
    expect(screen.getByRole('button', { name: 'Switch to light mode' })).toBeVisible()
  })
})

describe('the script that applies the theme before the first paint', () => {
  it('applies the theme saved by the switch', async () => {
    logIn()
    fireEvent.click(await screen.findByRole('button', { name: 'Switch to dark mode' }))
    delete root.dataset.theme

    runIndexScript()

    expect(root.dataset.theme).toBe('dark')
  })

  it('leaves the system setting in charge when nothing was chosen', () => {
    runIndexScript()

    expect(root.dataset.theme).toBeUndefined()
  })

  it('ignores a saved value that isn’t a theme', () => {
    localStorage.setItem('meetflow-theme', 'purple')

    runIndexScript()

    expect(root.dataset.theme).toBeUndefined()
  })
})
