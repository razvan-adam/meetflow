import { useState, type FormEvent, type ReactNode } from 'react'
import type { Decision } from '../api/decisions.ts'
import type { Task } from '../api/tasks.ts'
import agendaStyles from './Agenda.module.css'
import styles from './LiveAgenda.module.css'
import { TaskList } from './TaskList.tsx'
import pageStyles from './TeamPage.module.css'

interface Props {
  decisions: Decision[]
  pending: boolean
  tasksOf: (decision: Decision) => Task[]
  newTaskForm: (decision: Decision) => ReactNode
  onAdd: (text: string, onSuccess: () => void) => void
  onEdit: (decisionId: string, text: string, onSuccess: () => void) => void
  onRemove: (decisionId: string, onSettled: () => void) => void
}

const textOf = (form: HTMLFormElement) => String(new FormData(form).get('text')).trim()

// The decisions on the open agenda item with their tasks, for the organizer while the meeting runs
// (S7). The changes themselves live in LiveAgenda, so a rejected one can still say why after this
// turns read-only. A decision that tasks came from can't be removed, so it doesn't offer that.
export function ItemDecisions({
  decisions,
  pending,
  tasksOf,
  newTaskForm,
  onAdd,
  onEdit,
  onRemove,
}: Props) {
  const [editingId, setEditingId] = useState<string | null>(null)
  const [removingId, setRemovingId] = useState<string | null>(null)

  function handleAdd(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const text = textOf(form)
    if (text) {
      onAdd(text, () => form.reset())
    }
  }

  return (
    <>
      {decisions.length > 0 && (
        <ul className={styles.decisions} aria-label="Decisions">
          {decisions.map((decision) => (
            <li key={decision.id} className={styles.decision}>
              {editingId === decision.id ? (
                <EditForm
                  decision={decision}
                  pending={pending}
                  onSave={(text) => onEdit(decision.id, text, () => setEditingId(null))}
                  onCancel={() => setEditingId(null)}
                />
              ) : removingId === decision.id ? (
                <div
                  className={agendaStyles.confirmation}
                  role="group"
                  aria-label={`Confirm removing decision: ${decision.text}`}
                >
                  <p className={agendaStyles.confirmationText}>
                    Remove this decision? This can’t be undone.
                  </p>
                  <button
                    className={pageStyles.danger}
                    type="button"
                    disabled={pending}
                    onClick={() => onRemove(decision.id, () => setRemovingId(null))}
                  >
                    Remove decision
                  </button>
                  {/* Focus starts on the safe choice, so Enter doesn't remove it by accident. */}
                  <button
                    className={pageStyles.secondary}
                    type="button"
                    onClick={() => setRemovingId(null)}
                    autoFocus
                  >
                    Keep decision
                  </button>
                </div>
              ) : (
                <div className={styles.decisionRow}>
                  <p className={styles.decisionText}>{decision.text}</p>
                  <span className={agendaStyles.actions}>
                    <button
                      className={agendaStyles.action}
                      type="button"
                      aria-label={`Edit decision: ${decision.text}`}
                      onClick={() => {
                        setRemovingId(null)
                        setEditingId(decision.id)
                      }}
                    >
                      Edit
                    </button>
                    {tasksOf(decision).length === 0 && (
                      <button
                        className={agendaStyles.action}
                        type="button"
                        aria-label={`Remove decision: ${decision.text}`}
                        onClick={() => {
                          setEditingId(null)
                          setRemovingId(decision.id)
                        }}
                      >
                        ×
                      </button>
                    )}
                  </span>
                </div>
              )}
              <TaskList tasks={tasksOf(decision)} label={`Tasks from: ${decision.text}`} />
              {newTaskForm(decision)}
            </li>
          ))}
        </ul>
      )}
      <form className={pageStyles.addRow} onSubmit={handleAdd}>
        <input
          className={pageStyles.input}
          name="text"
          aria-label="New decision"
          placeholder="Record a decision for this item"
          maxLength={2000}
          required
        />
        <button className={pageStyles.secondary} type="submit" disabled={pending}>
          Add decision
        </button>
      </form>
    </>
  )
}

interface EditFormProps {
  decision: Decision
  pending: boolean
  onSave: (text: string) => void
  onCancel: () => void
}

function EditForm({ decision, pending, onSave, onCancel }: EditFormProps) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const text = textOf(event.currentTarget)
    if (text) {
      onSave(text)
    }
  }

  return (
    <form className={styles.decisionForm} onSubmit={handleSubmit}>
      <textarea
        className={`${pageStyles.input} ${styles.textarea}`}
        name="text"
        aria-label="Decision text"
        defaultValue={decision.text}
        maxLength={2000}
        rows={3}
        required
        autoFocus
      />
      <div className={styles.decisionFormActions}>
        <button className={pageStyles.primary} type="submit" disabled={pending}>
          Save
        </button>
        <button className={pageStyles.secondary} type="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}
