import { useSyncExternalStore } from 'react'

export type Theme = 'light' | 'dark'

// index.html reads this key too, to apply the theme before the first paint.
const STORAGE_KEY = 'meetflow-theme'
const SYSTEM_DARK = '(prefers-color-scheme: dark)'

const listeners = new Set<() => void>()

function isTheme(value: string | null | undefined): value is Theme {
  return value === 'light' || value === 'dark'
}

// The root element's attribute is what the styles use, so it also decides what the button says.
function currentTheme(): Theme {
  const chosen = document.documentElement.dataset.theme
  if (isTheme(chosen)) {
    return chosen
  }
  return window.matchMedia(SYSTEM_DARK).matches ? 'dark' : 'light'
}

function subscribe(listener: () => void) {
  const system = window.matchMedia(SYSTEM_DARK)
  // Keeps tabs in step: this event only fires in the tabs that didn't make the change.
  const onStorage = (event: StorageEvent) => {
    if (event.key === STORAGE_KEY) {
      if (isTheme(event.newValue)) {
        document.documentElement.dataset.theme = event.newValue
      } else {
        delete document.documentElement.dataset.theme
      }
      listener()
    }
  }
  listeners.add(listener)
  system.addEventListener('change', listener)
  window.addEventListener('storage', onStorage)
  return () => {
    listeners.delete(listener)
    system.removeEventListener('change', listener)
    window.removeEventListener('storage', onStorage)
  }
}

function chooseTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // Storage can be blocked; the choice then lasts until the page reloads.
  }
  listeners.forEach((listener) => listener())
}

// The theme in use and a way to choose one. Until the user chooses, it follows the system setting.
export function useTheme() {
  return { theme: useSyncExternalStore(subscribe, currentTheme), chooseTheme }
}
