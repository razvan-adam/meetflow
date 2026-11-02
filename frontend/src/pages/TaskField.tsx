import { useState, type FormEvent, type ReactNode } from 'react'
import pageStyles from './TeamPage.module.css'
import styles from './TaskPage.module.css'

interface Props {
  label: string
  value: ReactNode
  canChange: boolean
  pending: boolean
  // The control to change it, named "value".
  input: ReactNode
  onSave: (value: string, onSuccess: () => void) => void
}

// A line of the task screen (S8) that the user may be allowed to change, in place.
export function TaskField({ label, value, canChange, pending, input, onSave }: Props) {
  const [editing, setEditing] = useState(false)
  const changeLabel = `Change ${label.toLowerCase()}`

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const entered = String(new FormData(event.currentTarget).get('value')).trim()
    if (entered) {
      onSave(entered, () => setEditing(false))
    }
  }

  return (
    <>
      <dt>{label}</dt>
      <dd>
        {editing ? (
          <form className={styles.fieldForm} aria-label={changeLabel} onSubmit={handleSubmit}>
            {input}
            <button className={pageStyles.primary} type="submit" disabled={pending}>
              Save
            </button>
            <button
              className={pageStyles.secondary}
              type="button"
              onClick={() => setEditing(false)}
            >
              Cancel
            </button>
          </form>
        ) : (
          <>
            <span>{value}</span>
            {canChange && (
              <>
                {' · '}
                <button
                  className={styles.change}
                  type="button"
                  aria-label={changeLabel}
                  onClick={() => setEditing(true)}
                >
                  Change
                </button>
              </>
            )}
          </>
        )}
      </dd>
    </>
  )
}
