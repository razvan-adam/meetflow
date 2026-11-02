import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import {
  useAddAgendaItem,
  useAgendaItems,
  useMarkAgendaItem,
  useSaveNotes,
  type AgendaItem,
  type AgendaItemStatus,
} from '../api/agenda.ts'
import { ApiError } from '../api/client.ts'
import {
  useAddDecision,
  useDecisions,
  useEditDecision,
  useRemoveDecision,
  type Decision,
} from '../api/decisions.ts'
import type { Meeting } from '../api/meetings.ts'
import {
  useAddTask,
  useChangeTask,
  useTasks,
  type NewTask,
  type Task,
  type TaskOrigin,
} from '../api/tasks.ts'
import { useCurrentUser } from '../api/users.ts'
import { ItemDecisions } from './ItemDecisions.tsx'
import { toDateAndTime } from './meetingTime.ts'
import { NewTaskForm } from './NewTaskForm.tsx'
import { taskChangeFailure } from './taskDisplay.ts'
import { TaskList } from './TaskList.tsx'
import pageStyles from './TeamPage.module.css'
import styles from './LiveAgenda.module.css'

interface Props {
  meetingId: string
  seriesId: string
  scheduledAt: string
  participants: Meeting['participants']
  // The series organizer, while the meeting runs.
  canEdit: boolean
  // The series organizer, also once the meeting is closed: task status stays open to changes.
  organizes: boolean
  closed: boolean
}

const STATUS_LABELS: Record<AgendaItemStatus, string> = {
  PENDING: 'Not marked yet',
  COVERED: 'Covered',
  NOT_COVERED: 'Not covered',
}

const STATUS_SYMBOLS: Record<AgendaItemStatus, string> = {
  PENDING: '○',
  COVERED: '✓',
  NOT_COVERED: '–',
}

const SAVE_NOTES_AFTER_MS = 1000

function failureMessage(failure: Error, closed: boolean): string {
  // Only a new task can get this far with a 400: the form has checked everything else.
  if (failure instanceof ApiError && failure.status === 400) {
    return 'That owner is no longer a participant of this meeting, so the task wasn’t saved. Choose another owner.'
  }
  if (failure instanceof ApiError && failure.status === 403) {
    return 'You’re no longer the organizer of this series, so your last change wasn’t saved.'
  }
  if (failure instanceof ApiError && failure.status === 409 && closed) {
    return 'This meeting was closed meanwhile, so your last change wasn’t saved.'
  }
  if (failure instanceof ApiError && (failure.status === 409 || failure.status === 404)) {
    return 'The agenda changed meanwhile. Check it and try again.'
  }
  return 'Your last change couldn’t be saved. Try again in a few minutes.'
}

// The agenda of a meeting that has started (S7). The organizer works through it one item at a time
// while the meeting runs; everyone else, and everyone once it is closed, reads all of it. This stays
// on the page when it turns read-only, so a change that was rejected can still say why.
export function LiveAgenda({
  meetingId,
  seriesId,
  scheduledAt,
  participants,
  canEdit,
  organizes,
  closed,
}: Props) {
  const items = useAgendaItems(meetingId)
  const add = useAddAgendaItem({ meetingId, seriesId })
  const saveNotes = useSaveNotes({ meetingId, seriesId })
  const mark = useMarkAgendaItem({ meetingId, seriesId })
  const decisions = useDecisions(meetingId)
  const addDecision = useAddDecision({ meetingId, seriesId })
  const editDecision = useEditDecision({ meetingId, seriesId })
  const removeDecision = useRemoveDecision({ meetingId, seriesId })
  const tasks = useTasks(meetingId)
  const addTask = useAddTask({ meetingId, seriesId })
  const changeTask = useChangeTask({ meetingId, seriesId })
  const { data: user } = useCurrentUser()
  const [selectedId, setSelectedId] = useState<string | null>(null)

  if (!items.data) {
    return items.isError ? (
      <p role="alert" className={pageStyles.error}>
        The agenda couldn’t be loaded. Try again in a few minutes.
      </p>
    ) : null
  }
  const agenda = items.data
  const selected = agenda.find((item) => item.id === selectedId) ?? agenda[0]
  const failure =
    add.error ??
    saveNotes.error ??
    mark.error ??
    addDecision.error ??
    editDecision.error ??
    removeDecision.error ??
    addTask.error
  const decisionsOf = (item: AgendaItem) =>
    (decisions.data ?? []).filter((decision) => decision.agendaItemId === item.id)
  const tasksOfDecision = (decision: Decision) =>
    (tasks.data ?? []).filter((task) => task.decisionId === decision.id)
  // The tasks that came straight from the item, not from one of its decisions.
  const tasksOfItem = (item: AgendaItem) =>
    (tasks.data ?? []).filter((task) => task.agendaItemId === item.id && !task.decisionId)
  const taskList = (list: Task[], label: string) => (
    <TaskList
      tasks={list}
      label={label}
      overdueOn={toDateAndTime(scheduledAt).date}
      canChangeStatus={(task) => organizes || task.ownerId === user?.id}
      pending={changeTask.isPending}
      onChangeStatus={(task, status) => changeTask.mutate({ taskId: task.id, change: { status } })}
    />
  )

  function resetRecordErrors() {
    addDecision.reset()
    editDecision.reset()
    removeDecision.reset()
    addTask.reset()
  }

  function handleAddTask(origin: TaskOrigin, task: NewTask, onSuccess: () => void) {
    resetRecordErrors()
    addTask.mutate({ origin, task }, { onSuccess })
  }

  function handleAdd(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const title = String(new FormData(form).get('title')).trim()
    if (title) {
      add.mutate(title, {
        onSuccess: (created) => {
          form.reset()
          setSelectedId(created.id)
        },
      })
    }
  }

  function handleMark(item: AgendaItem, status: AgendaItemStatus) {
    // Pressing the current mark again takes it back.
    mark.mutate({ itemId: item.id, status: item.status === status ? 'PENDING' : status })
  }

  return (
    <section aria-labelledby="agenda-title">
      <h2 id="agenda-title" className={pageStyles.sectionLabel}>
        Agenda items
      </h2>
      {canEdit ? (
        <div className={styles.live}>
          <div className={styles.side}>
            {agenda.length > 0 && (
              <ol className={styles.outline}>
                {agenda.map((item) => (
                  <li key={item.id}>
                    <button
                      className={styles.outlineItem}
                      type="button"
                      aria-current={item.id === selected?.id || undefined}
                      aria-label={`${item.title}: ${STATUS_LABELS[item.status]}`}
                      data-status={item.status}
                      onClick={() => setSelectedId(item.id)}
                    >
                      <span aria-hidden="true" className={styles.symbol}>
                        {STATUS_SYMBOLS[item.status]}
                      </span>
                      <span className={styles.outlineTitle}>{item.title}</span>
                    </button>
                  </li>
                ))}
              </ol>
            )}
            <form className={pageStyles.addRow} onSubmit={handleAdd}>
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
          </div>
          {selected ? (
            <div className={pageStyles.card}>
              <div className={styles.itemHead}>
                <h3 className={pageStyles.cardTitle}>{selected.title}</h3>
                <div className={styles.marks} role="group" aria-label="Was this item covered?">
                  {(['COVERED', 'NOT_COVERED'] as const).map((status) => (
                    <button
                      key={status}
                      className={styles.mark}
                      type="button"
                      aria-pressed={selected.status === status}
                      onClick={() => handleMark(selected, status)}
                    >
                      {STATUS_LABELS[status]}
                    </button>
                  ))}
                </div>
              </div>
              {selected.carriedOver && <p className={pageStyles.meta}>Not covered last time.</p>}
              <NotesEditor
                key={selected.id}
                item={selected}
                onSave={(notes, onFailure) =>
                  saveNotes.mutate({ itemId: selected.id, notes }, { onError: onFailure })
                }
              />
              <ItemDecisions
                key={`decisions-${selected.id}`}
                decisions={decisionsOf(selected)}
                pending={
                  addDecision.isPending || editDecision.isPending || removeDecision.isPending
                }
                tasksOf={tasksOfDecision}
                taskList={(decision) =>
                  taskList(tasksOfDecision(decision), `Tasks from: ${decision.text}`)
                }
                newTaskForm={(decision) => (
                  <NewTaskForm
                    label={`Add task from decision: ${decision.text}`}
                    participants={participants}
                    pending={addTask.isPending}
                    onAdd={(task, onSuccess) =>
                      handleAddTask({ decisionId: decision.id }, task, onSuccess)
                    }
                  />
                )}
                onAdd={(text, onSuccess) => {
                  resetRecordErrors()
                  addDecision.mutate({ itemId: selected.id, text }, { onSuccess })
                }}
                onEdit={(decisionId, text, onSuccess) => {
                  resetRecordErrors()
                  editDecision.mutate({ decisionId, text }, { onSuccess })
                }}
                onRemove={(decisionId, onSettled) => {
                  resetRecordErrors()
                  removeDecision.mutate(decisionId, { onSettled })
                }}
              />
              {taskList(tasksOfItem(selected), `Tasks on ${selected.title}`)}
              <NewTaskForm
                key={`task-${selected.id}`}
                label={`Add task on ${selected.title}`}
                participants={participants}
                pending={addTask.isPending}
                onAdd={(task, onSuccess) =>
                  handleAddTask({ agendaItemId: selected.id }, task, onSuccess)
                }
              />
            </div>
          ) : (
            <p className={pageStyles.empty}>
              No items yet. Add the first topic to take notes on it.
            </p>
          )}
        </div>
      ) : agenda.length === 0 ? (
        <p className={pageStyles.empty}>No agenda items.</p>
      ) : (
        <ol className={pageStyles.list}>
          {agenda.map((item) => (
            <li key={item.id} className={`${pageStyles.row} ${styles.record}`}>
              <div className={styles.itemHead}>
                <span className={styles.recordTitle}>{item.title}</span>
                {(closed || item.status !== 'PENDING') && (
                  <span className={styles.tag} data-status={item.status}>
                    {STATUS_LABELS[item.status]}
                  </span>
                )}
              </div>
              {item.notes && <p className={styles.notes}>{item.notes}</p>}
              {decisionsOf(item).length > 0 && (
                <ul className={styles.decisions} aria-label={`Decisions on ${item.title}`}>
                  {decisionsOf(item).map((decision) => (
                    <li key={decision.id} className={styles.decision}>
                      <p className={styles.decisionText}>{decision.text}</p>
                      {taskList(tasksOfDecision(decision), `Tasks from: ${decision.text}`)}
                    </li>
                  ))}
                </ul>
              )}
              {taskList(tasksOfItem(item), `Tasks on ${item.title}`)}
            </li>
          ))}
        </ol>
      )}
      {decisions.isError && (
        <p role="alert" className={`${pageStyles.error} ${pageStyles.below}`}>
          The decisions couldn’t be loaded. Try again in a few minutes.
        </p>
      )}
      {tasks.isError && (
        <p role="alert" className={`${pageStyles.error} ${pageStyles.below}`}>
          The tasks couldn’t be loaded. Try again in a few minutes.
        </p>
      )}
      {changeTask.error && (
        <p role="alert" className={`${pageStyles.error} ${pageStyles.below}`}>
          {taskChangeFailure(changeTask.error)}
        </p>
      )}
      {failure && (
        <p role="alert" className={`${pageStyles.error} ${pageStyles.below}`}>
          {failureMessage(failure, closed)}
        </p>
      )}
    </section>
  )
}

interface NotesEditorProps {
  item: AgendaItem
  onSave: (notes: string, onFailure: () => void) => void
}

// Saves on its own: shortly after typing stops, when focus leaves, and when another item is opened,
// so nobody has to think about saving during a meeting. A save that fails is tried again on the next
// change or when focus leaves, and the text stays in the box meanwhile.
function NotesEditor({ item, onSave }: NotesEditorProps) {
  const [draft, setDraft] = useState(item.notes ?? '')
  // Kept in a ref because the save on leaving the item runs after the last render.
  const latest = useRef({ draft, saved: draft as string | null, onSave })

  const save = useCallback(() => {
    const { draft: text, saved, onSave: send } = latest.current
    if (text !== saved) {
      latest.current.saved = text
      send(text, () => {
        latest.current.saved = null
      })
    }
  }, [])

  useEffect(() => {
    latest.current.onSave = onSave
  })

  useEffect(() => {
    const timer = setTimeout(save, SAVE_NOTES_AFTER_MS)
    return () => clearTimeout(timer)
  }, [draft, save])

  useEffect(() => save, [save])

  return (
    <label className={styles.field}>
      Notes
      <textarea
        className={`${pageStyles.input} ${styles.textarea}`}
        value={draft}
        maxLength={10000}
        rows={8}
        onChange={(event) => {
          latest.current.draft = event.target.value
          setDraft(event.target.value)
        }}
        onBlur={save}
      />
    </label>
  )
}
