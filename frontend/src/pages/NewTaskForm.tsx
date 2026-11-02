import { useState, type FormEvent } from 'react'
import type { Meeting } from '../api/meetings.ts'
import type { NewTask } from '../api/tasks.ts'
import styles from './LiveAgenda.module.css'
import pageStyles from './TeamPage.module.css'

interface Props {
  // Says what the task comes from, since a screen can offer several of these.
  label: string
  participants: Meeting['participants']
  pending: boolean
  onAdd: (task: NewTask, onSuccess: () => void) => void
}

// Stays closed until needed, because every decision on the open item has one of these. Every task
// needs a description, an owner and a deadline, and the owner must be a participant (MF-17).
export function NewTaskForm({ label, participants, pending, onAdd }: Props) {
  const [open, setOpen] = useState(false)
  const [incomplete, setIncomplete] = useState(false)

  function close() {
    setOpen(false)
    setIncomplete(false)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const fields = new FormData(event.currentTarget)
    const task = {
      description: String(fields.get('description')).trim(),
      ownerId: String(fields.get('ownerId')),
      deadline: String(fields.get('deadline')),
    }
    const complete = Boolean(task.description && task.ownerId && task.deadline)
    setIncomplete(!complete)
    if (complete) {
      onAdd(task, close)
    }
  }

  if (!open) {
    return (
      <button
        className={`${pageStyles.secondary} ${styles.addTask}`}
        type="button"
        aria-label={label}
        onClick={() => setOpen(true)}
      >
        Add task
      </button>
    )
  }
  return (
    <form className={styles.newTask} aria-label={label} onSubmit={handleSubmit} noValidate>
      <label className={styles.field}>
        New task
        <input
          className={`${pageStyles.input} ${styles.taskInput}`}
          name="description"
          placeholder="What needs to be done"
          maxLength={500}
          autoFocus
        />
      </label>
      <label className={styles.field}>
        Owner
        <select className={`${pageStyles.input} ${styles.taskInput}`} name="ownerId">
          <option value="">Participant…</option>
          {participants.map((participant) => (
            <option key={participant.id} value={participant.id}>
              {participant.name}
            </option>
          ))}
        </select>
      </label>
      <label className={styles.field}>
        Deadline
        <input className={`${pageStyles.input} ${styles.taskInput}`} name="deadline" type="date" />
      </label>
      <div className={styles.newTaskActions}>
        <button className={pageStyles.primary} type="submit" disabled={pending}>
          Save task
        </button>
        <button className={pageStyles.secondary} type="button" onClick={close}>
          Cancel
        </button>
      </div>
      {incomplete && (
        <p role="alert" className={`${pageStyles.error} ${styles.newTaskError}`}>
          Add a description, an owner and a deadline. Every task needs all three.
        </p>
      )}
    </form>
  )
}
