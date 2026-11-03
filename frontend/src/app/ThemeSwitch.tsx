import styles from './AppLayout.module.css'
import { useTheme } from './useTheme.ts'

// Says which mode pressing it leads to, so it never reads as a statement of the current one.
export function ThemeSwitch() {
  const { theme, chooseTheme } = useTheme()
  const next = theme === 'dark' ? 'light' : 'dark'

  return (
    <button
      type="button"
      className={styles.barButton}
      aria-label={`Switch to ${next} mode`}
      onClick={() => chooseTheme(next)}
    >
      {next === 'dark' ? 'Dark mode' : 'Light mode'}
    </button>
  )
}
