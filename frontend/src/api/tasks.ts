import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useReloadAfterChange, type AgendaContext } from './agenda.ts'
import { ApiError, apiFetch } from './client.ts'
import { meetingKey, seriesMeetingsKey } from './meetings.ts'

export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE'

export interface Task {
  id: string
  teamId: string
  seriesId: string
  // The meeting where it was created.
  meetingId: string
  agendaItemId: string
  // Null for a task that came straight from the agenda item.
  decisionId: string | null
  description: string
  ownerId: string
  ownerName: string
  // A calendar date: YYYY-MM-DD.
  deadline: string
  status: TaskStatus
  // Null unless deleted. A task deleted after its meeting closed stays on that meeting's record.
  deletion: TaskDeletion | null
}

// A task on its owner's dashboard, with the names of where it belongs.
export interface PendingTask {
  id: string
  description: string
  deadline: string
  status: TaskStatus
  teamId: string
  teamName: string
  seriesId: string
  seriesName: string
  meetingId: string
  meetingScheduledAt: string
}

export interface TaskDeletion {
  byName: string
  at: string
  // The meeting it was deleted in; null if deleted outside a meeting.
  meetingScheduledAt: string | null
}

export interface NewTask {
  description: string
  ownerId: string
  deadline: string
}

// What a task is created from: an agenda item or a decision.
export type TaskOrigin = { agendaItemId: string } | { decisionId: string }

// One line of a task's history. Only what changed is filled in; the creation has every new value
// and no old ones.
export interface TaskHistoryEntry {
  changedAt: string
  changedByName: string
  duringMeeting: boolean
  oldStatus: TaskStatus | null
  newStatus: TaskStatus | null
  oldOwnerName: string | null
  newOwnerName: string | null
  oldDeadline: string | null
  newDeadline: string | null
  oldDescription: string | null
  newDescription: string | null
  deleted: boolean
}

// One thing to change on a task; the field's name is also the end of its URL's path.
export type TaskFieldChange =
  { status: TaskStatus } | { ownerId: string } | { deadline: string } | { description: string }

const tasksKey = (meetingId: string) => [...meetingKey(meetingId), 'tasks']
const taskKey = (taskId: string) => ['tasks', taskId]
const PENDING_TASKS = ['pending-tasks']
const taskPath = (taskId: string) => `/tasks/${encodeURIComponent(taskId)}`
const JSON_HEADERS = { 'Content-Type': 'application/json' }

function changePath(change: TaskFieldChange): string {
  if ('status' in change) {
    return 'status'
  }
  if ('ownerId' in change) {
    return 'owner'
  }
  return 'deadline' in change ? 'deadline' : 'description'
}

function originPath(origin: TaskOrigin): string {
  return 'decisionId' in origin
    ? `/decisions/${encodeURIComponent(origin.decisionId)}/tasks`
    : `/agenda-items/${encodeURIComponent(origin.agendaItemId)}/tasks`
}

// What the current user still has to do across their teams, soonest deadline first.
export function usePendingTasks() {
  return useQuery({
    queryKey: PENDING_TASKS,
    queryFn: () => apiFetch<PendingTask[]>('/users/me/tasks'),
  })
}

// The tasks created in a meeting, oldest first. Every member of the team can read them.
export function useTasks(meetingId: string) {
  return useQuery({
    queryKey: tasksKey(meetingId),
    queryFn: () => apiFetch<Task[]>(`/meetings/${encodeURIComponent(meetingId)}/tasks`),
  })
}

// The tasks carried over to a meeting, soonest deadline first. Their key sits under the meeting's
// tasks, so whatever reloads those reloads these too.
export function useCarriedOverTasks(meetingId: string) {
  return useQuery({
    queryKey: [...tasksKey(meetingId), 'carried-over'],
    queryFn: () =>
      apiFetch<Task[]>(`/meetings/${encodeURIComponent(meetingId)}/carried-over-tasks`),
  })
}

export function useTask(taskId: string | undefined) {
  return useQuery({
    queryKey: taskKey(taskId ?? ''),
    queryFn: () => apiFetch<Task>(taskPath(taskId ?? '')),
    enabled: Boolean(taskId),
  })
}

// Latest first.
export function useTaskHistory(taskId: string) {
  return useQuery({
    queryKey: [...taskKey(taskId), 'changes'],
    queryFn: () => apiFetch<TaskHistoryEntry[]>(`${taskPath(taskId)}/changes`),
  })
}

// The server decides who may change what: the owner the status; the organizer that, the owner and
// the deadline, and the description while a meeting of the series runs. The context is the task's
// meeting, whose list of tasks reloads, even after a failure, since the page may be out of date. A
// 409 may mean the running meeting closed, so the series' meetings reload too.
export function useChangeTask(context: AgendaContext) {
  const queryClient = useQueryClient()
  const reload = useReloadAfterChange(context, tasksKey(context.meetingId))
  return useMutation({
    mutationFn: ({ taskId, change }: { taskId: string; change: TaskFieldChange }) =>
      apiFetch<Task>(`${taskPath(taskId)}/${changePath(change)}`, {
        method: 'PUT',
        headers: JSON_HEADERS,
        body: JSON.stringify(change),
      }),
    onSettled: async (_data, error, { taskId }) => {
      await queryClient.invalidateQueries({ queryKey: taskKey(taskId) })
      await queryClient.invalidateQueries({ queryKey: PENDING_TASKS })
      if (error instanceof ApiError && error.status === 409) {
        await queryClient.invalidateQueries({ queryKey: seriesMeetingsKey(context.seriesId) })
      }
      await reload(error)
    },
  })
}

// For the organizer, while a meeting of the task's series runs. The task itself reloads now only
// after a failure: deleted during its own meeting it answers 404, which the page leaving it
// shouldn't flash first. A 409 may mean the running meeting closed, so the series' meetings reload
// too.
export function useDeleteTask(context: AgendaContext) {
  const queryClient = useQueryClient()
  const reload = useReloadAfterChange(context, tasksKey(context.meetingId))
  return useMutation({
    mutationFn: (taskId: string) => apiFetch<void>(taskPath(taskId), { method: 'DELETE' }),
    onSettled: async (_data, error, taskId) => {
      await queryClient.invalidateQueries({
        queryKey: taskKey(taskId),
        refetchType: error ? 'active' : 'none',
      })
      if (error instanceof ApiError && error.status === 409) {
        await queryClient.invalidateQueries({ queryKey: seriesMeetingsKey(context.seriesId) })
      }
      await reload(error)
    },
  })
}

// For the series organizer only, while the meeting runs. A 400 means the owner is no longer a
// participant, so the meeting reloads too and the owner list catches up.
export function useAddTask(context: AgendaContext) {
  const queryClient = useQueryClient()
  const reload = useReloadAfterChange(context, tasksKey(context.meetingId))
  return useMutation({
    mutationFn: ({ origin, task }: { origin: TaskOrigin; task: NewTask }) =>
      apiFetch<Task>(originPath(origin), {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify(task),
      }),
    onSettled: async (_data, error) => {
      if (error instanceof ApiError && error.status === 400) {
        await queryClient.invalidateQueries({
          queryKey: meetingKey(context.meetingId),
          exact: true,
        })
      }
      await reload(error)
    },
  })
}
