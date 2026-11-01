import { useState, type DragEvent, type FormEvent } from 'react'
import {
  useAddAgendaItem,
  useAgendaItems,
  useRemoveAgendaItem,
  useRenameAgendaItem,
  useReorderAgenda,
  type AgendaItem,
} from '../api/agenda.ts'
import { ApiError } from '../api/client.ts'
import pageStyles from './TeamPage.module.css'
import styles from './Agenda.module.css'

interface Props {
  meetingId: string
  seriesId: string
  // The series organizer, until the meeting is closed.
  canEdit: boolean
}

function failureMessage(failure: Error): string {
  if (failure instanceof ApiError && failure.status === 403) {
    return 'You’re no longer the organizer of this series, so the agenda is now read-only.'
  }
  if (failure instanceof ApiError && (failure.status === 409 || failure.status === 404)) {
    return 'The agenda or the meeting changed meanwhile. Check the agenda and try again.'
  }
  return 'The agenda couldn’t be saved. Try again in a few minutes.'
}

// The agenda of a meeting (S6): read-only for everyone but the organizer. MF-19 adds the open
// commitments above the items; they can't be removed.
export function Agenda({ meetingId, seriesId, canEdit }: Props) {
  const items = useAgendaItems(meetingId)
  const add = useAddAgendaItem({ meetingId, seriesId })
  const rename = useRenameAgendaItem({ meetingId, seriesId })
  const remove = useRemoveAgendaItem({ meetingId, seriesId })
  const reorder = useReorderAgenda({ meetingId, seriesId })
  const [editingId, setEditingId] = useState<string | null>(null)
  const [removingId, setRemovingId] = useState<string | null>(null)
  const [draggedId, setDraggedId] = useState<string | null>(null)

  if (!items.data) {
    return items.isError ? (
      <p role="alert" className={pageStyles.error}>
        The agenda couldn’t be loaded. Try again in a few minutes.
      </p>
    ) : null
  }
  const agenda = items.data
  const failure = add.error ?? rename.error ?? remove.error ?? reorder.error

  function resetErrors() {
    add.reset()
    rename.reset()
    remove.reset()
    reorder.reset()
  }

  function moveTo(itemId: string, index: number) {
    const moved = agenda.find((item) => item.id === itemId)
    if (!moved || agenda[index]?.id === itemId) {
      return
    }
    const others = agenda.filter((item) => item.id !== itemId)
    resetErrors()
    reorder.mutate([...others.slice(0, index), moved, ...others.slice(index)])
  }

  function handleAdd(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const title = String(new FormData(form).get('title')).trim()
    if (title) {
      resetErrors()
      add.mutate(title, { onSuccess: () => form.reset() })
    }
  }

  function handleRename(itemId: string, title: string) {
    resetErrors()
    rename.mutate({ itemId, title }, { onSuccess: () => setEditingId(null) })
  }

  function handleRemove(itemId: string) {
    resetErrors()
    remove.mutate(itemId, { onSettled: () => setRemovingId(null) })
  }

  function handleDrop(event: DragEvent, index: number) {
    event.preventDefault()
    if (draggedId) {
      moveTo(draggedId, index)
    }
    setDraggedId(null)
  }

  return (
    <section aria-labelledby="agenda-title">
      <h2 id="agenda-title" className={pageStyles.sectionLabel}>
        Agenda items
      </h2>
      {agenda.length === 0 ? (
        <p className={pageStyles.empty}>
          {canEdit ? 'No items yet. Add the first topic below.' : 'No agenda items yet.'}
        </p>
      ) : (
        <ol className={pageStyles.list}>
          {agenda.map((item, index) => (
            <li
              key={item.id}
              className={`${pageStyles.row} ${styles.item}`}
              data-dragged={item.id === draggedId || undefined}
              onDragOver={canEdit ? (event) => event.preventDefault() : undefined}
              onDrop={canEdit ? (event) => handleDrop(event, index) : undefined}
            >
              {canEdit && editingId === item.id ? (
                <RenameForm
                  item={item}
                  pending={rename.isPending}
                  onSave={(title) => handleRename(item.id, title)}
                  onCancel={() => setEditingId(null)}
                />
              ) : canEdit && removingId === item.id ? (
                <RemoveConfirmation
                  item={item}
                  pending={remove.isPending}
                  onConfirm={() => handleRemove(item.id)}
                  onCancel={() => setRemovingId(null)}
                />
              ) : (
                <>
                  {canEdit && (
                    <span
                      className={styles.handle}
                      aria-hidden="true"
                      draggable
                      onDragStart={(event) => {
                        // Firefox only starts a drag that carries data.
                        event.dataTransfer.setData('text/plain', item.title)
                        setDraggedId(item.id)
                      }}
                      onDragEnd={() => setDraggedId(null)}
                    >
                      ⋮⋮
                    </span>
                  )}
                  <span className={styles.title}>
                    {item.title}
                    {item.carriedOver && (
                      <span className={styles.carried}>not covered last time</span>
                    )}
                  </span>
                  {canEdit && (
                    <span className={styles.actions}>
                      {/* Dragging needs a mouse: these also work by keyboard and on touch screens. */}
                      <button
                        className={styles.action}
                        type="button"
                        aria-label={`Move ${item.title} up`}
                        disabled={index === 0 || reorder.isPending}
                        onClick={() => moveTo(item.id, index - 1)}
                      >
                        ↑
                      </button>
                      <button
                        className={styles.action}
                        type="button"
                        aria-label={`Move ${item.title} down`}
                        disabled={index === agenda.length - 1 || reorder.isPending}
                        onClick={() => moveTo(item.id, index + 1)}
                      >
                        ↓
                      </button>
                      <button
                        className={styles.action}
                        type="button"
                        aria-label={`Edit ${item.title}`}
                        onClick={() => {
                          resetErrors()
                          setRemovingId(null)
                          setEditingId(item.id)
                        }}
                      >
                        Edit
                      </button>
                      <button
                        className={styles.action}
                        type="button"
                        aria-label={`Remove ${item.title}`}
                        disabled={remove.isPending}
                        onClick={() => {
                          resetErrors()
                          setEditingId(null)
                          setRemovingId(item.id)
                        }}
                      >
                        ×
                      </button>
                    </span>
                  )}
                </>
              )}
            </li>
          ))}
        </ol>
      )}
      {canEdit && (
        <form className={`${pageStyles.addRow} ${pageStyles.below}`} onSubmit={handleAdd}>
          <input
            className={pageStyles.input}
            name="title"
            aria-label="New agenda item"
            placeholder="Add an agenda item"
            maxLength={200}
            required
          />
          <button className={pageStyles.secondary} type="submit" disabled={add.isPending}>
            Add
          </button>
        </form>
      )}
      {failure && (
        <p role="alert" className={`${pageStyles.error} ${pageStyles.below}`}>
          {failureMessage(failure)}
        </p>
      )}
    </section>
  )
}

interface RemoveConfirmationProps {
  item: AgendaItem
  pending: boolean
  onConfirm: () => void
  onCancel: () => void
}

// Focus starts on the safe choice, so pressing Enter by accident doesn't remove the item.
function RemoveConfirmation({ item, pending, onConfirm, onCancel }: RemoveConfirmationProps) {
  return (
    <div className={styles.confirmation} role="group" aria-label={`Confirm removing ${item.title}`}>
      <p className={styles.confirmationText}>
        Remove “{item.title}” from the agenda? This can’t be undone.
      </p>
      <button className={pageStyles.danger} type="button" disabled={pending} onClick={onConfirm}>
        Remove item
      </button>
      <button className={pageStyles.secondary} type="button" onClick={onCancel} autoFocus>
        Keep item
      </button>
    </div>
  )
}

interface RenameFormProps {
  item: AgendaItem
  pending: boolean
  onSave: (title: string) => void
  onCancel: () => void
}

function RenameForm({ item, pending, onSave, onCancel }: RenameFormProps) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const title = String(new FormData(event.currentTarget).get('title')).trim()
    if (title) {
      onSave(title)
    }
  }

  return (
    <form className={`${pageStyles.addRow} ${styles.renameForm}`} onSubmit={handleSubmit}>
      <input
        className={pageStyles.input}
        name="title"
        aria-label={`New title for ${item.title}`}
        defaultValue={item.title}
        maxLength={200}
        required
        autoFocus
      />
      <button className={pageStyles.primary} type="submit" disabled={pending}>
        Save
      </button>
      <button className={pageStyles.secondary} type="button" onClick={onCancel}>
        Cancel
      </button>
    </form>
  )
}
