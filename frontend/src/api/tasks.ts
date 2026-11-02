import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useReloadAfterChange, type AgendaContext } from './agenda.ts'
import { ApiError, apiFetch } from './client.ts'
import { meetingKey } from './meetings.ts'

export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE'

export interface Task {
  id: string
  agendaItemId: string
  // Null for a task that came straight from the agenda item.
  decisionId: string | null
  description: string
  ownerId: string
  ownerName: string
  // A calendar date: YYYY-MM-DD.
  deadline: string
  status: TaskStatus
}

export interface NewTask {
  description: string
  ownerId: string
  deadline: string
}

// What a task is created from: an agenda item or a decision.
export type TaskOrigin = { agendaItemId: string } | { decisionId: string }

const tasksKey = (meetingId: string) => [...meetingKey(meetingId), 'tasks']
const JSON_HEADERS = { 'Content-Type': 'application/json' }

function originPath(origin: TaskOrigin): string {
  return 'decisionId' in origin
    ? `/decisions/${encodeURIComponent(origin.decisionId)}/tasks`
    : `/agenda-items/${encodeURIComponent(origin.agendaItemId)}/tasks`
}

// The tasks created in a meeting, oldest first. Every member of the team can read them.
export function useTasks(meetingId: string) {
  return useQuery({
    queryKey: tasksKey(meetingId),
    queryFn: () => apiFetch<Task[]>(`/meetings/${encodeURIComponent(meetingId)}/tasks`),
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
