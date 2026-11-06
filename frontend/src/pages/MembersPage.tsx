import { useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useTeamSeries, type Series } from '../api/series.ts'
import { useTeamOpenTasks, type OpenTask } from '../api/tasks.ts'
import {
  useChangeRole,
  useRemoveMember,
  useTeam,
  useTeamMembers,
  type Member,
} from '../api/teams.ts'
import { useCurrentUser } from '../api/users.ts'
import { Breadcrumb } from './Breadcrumb.tsx'
import styles from './MembersPage.module.css'
import pageStyles from './TeamPage.module.css'
import { formatDeadline } from './taskDisplay.ts'

const REMOVE_TASK = 'remove'

// The team's members (S11): everyone sees who is in the team and their role; admins make members
// admins, take admin rights and remove members once their work is handed over (MF-11).
export function MembersPage() {
  const { teamId = '' } = useParams()
  const team = useTeam(teamId)
  const members = useTeamMembers(teamId)
  const series = useTeamSeries(teamId)
  const openTasks = useTeamOpenTasks(teamId)
  const { data: user } = useCurrentUser()
  const changeRole = useChangeRole(teamId)
  const [removingId, setRemovingId] = useState<string | null>(null)
  const [removedName, setRemovedName] = useState<string | null>(null)

  if (team.isError) {
    return (
      <main className={pageStyles.page}>
        <p role="alert" className={pageStyles.empty}>
          {team.error instanceof ApiError && team.error.status === 404
            ? 'This team doesn’t exist or you’re not a member of it.'
            : 'Something went wrong. Try again in a few minutes.'}
        </p>
      </main>
    )
  }
  if (!team.data || !members.data || !series.data || !openTasks.data) {
    return null
  }
  const isAdmin = team.data.role === 'ADMIN'
  const admins = members.data.filter((member) => member.role === 'ADMIN').length
  const removing = members.data.find((member) => member.id === removingId)
  const tasksOf = (member: Member) => openTasks.data.filter((task) => task.ownerId === member.id)
  const seriesOf = (member: Member) =>
    series.data.filter((found) => found.organizerId === member.id)

  return (
    <main className={pageStyles.page}>
      <div>
        <Breadcrumb teamId={teamId} />
        <h1 className={pageStyles.title}>Members</h1>
        <p className={pageStyles.sub}>
          {isAdmin
            ? 'A team always keeps at least one admin.'
            : 'Team admins manage who is in the team.'}
        </p>
      </div>
      {removedName && (
        <p role="status" className={styles.callout}>
          {removedName} was removed from {team.data.name}.
        </p>
      )}
      {changeRole.isError && (
        <p role="alert" className={pageStyles.error}>
          {changeRole.error instanceof ApiError && changeRole.error.status === 409
            ? 'A team always keeps at least one admin.'
            : 'The role couldn’t be changed. Try again in a few minutes.'}
        </p>
      )}
      <ul className={pageStyles.list} aria-label="Members">
        {members.data.map((member) => {
          const lastAdmin = member.role === 'ADMIN' && admins === 1
          return (
            <li key={member.id} className={pageStyles.row}>
              <div>
                <div>
                  {member.name}
                  {member.id === user?.id && ' (you)'}
                </div>
                <div className={pageStyles.meta}>{workText(seriesOf(member), tasksOf(member))}</div>
              </div>
              <div className={styles.actions}>
                <span className={styles.role} data-admin={member.role === 'ADMIN' || undefined}>
                  {member.role === 'ADMIN' ? 'admin' : 'member'}
                </span>
                {isAdmin && (
                  <>
                    <button
                      className={pageStyles.secondary}
                      type="button"
                      disabled={lastAdmin || changeRole.isPending}
                      title={lastAdmin ? 'A team always keeps at least one admin.' : undefined}
                      aria-label={
                        member.role === 'ADMIN'
                          ? `Take admin rights from ${member.name}`
                          : `Make ${member.name} an admin`
                      }
                      onClick={() =>
                        changeRole.mutate({
                          memberId: member.id,
                          role: member.role === 'ADMIN' ? 'MEMBER' : 'ADMIN',
                        })
                      }
                    >
                      {member.role === 'ADMIN' ? 'Remove admin' : 'Make admin'}
                    </button>
                    <button
                      className={pageStyles.danger}
                      type="button"
                      disabled={lastAdmin}
                      title={lastAdmin ? 'A team always keeps at least one admin.' : undefined}
                      aria-label={`Remove ${member.name}`}
                      onClick={() => {
                        setRemovedName(null)
                        setRemovingId(member.id)
                      }}
                    >
                      Remove
                    </button>
                  </>
                )}
              </div>
            </li>
          )
        })}
      </ul>
      {isAdmin && removing && (
        <RemovalPanel
          key={removing.id}
          teamId={teamId}
          teamName={team.data.name}
          member={removing}
          others={members.data.filter((member) => member.id !== removing.id)}
          tasks={tasksOf(removing)}
          series={seriesOf(removing)}
          isSelf={removing.id === user?.id}
          onCancel={() => setRemovingId(null)}
          onRemoved={() => {
            setRemovingId(null)
            setRemovedName(removing.name)
          }}
        />
      )}
    </main>
  )
}

function workText(series: Series[], tasks: OpenTask[]): string {
  const parts =
    series.length > 0 ? [`Organizes ${series.map((found) => found.name).join(', ')}`] : []
  parts.push(
    tasks.length === 0
      ? 'No open tasks'
      : `${tasks.length} open task${tasks.length === 1 ? '' : 's'}`,
  )
  return parts.join(' · ')
}

// Removing a member: each open task they own goes to another member or is removed, and each series
// they organize gets a new organizer, before the removal can be confirmed.
function RemovalPanel({
  teamId,
  teamName,
  member,
  others,
  tasks,
  series,
  isSelf,
  onCancel,
  onRemoved,
}: {
  teamId: string
  teamName: string
  member: Member
  others: Member[]
  tasks: OpenTask[]
  series: Series[]
  isSelf: boolean
  onCancel: () => void
  onRemoved: () => void
}) {
  const remove = useRemoveMember(teamId)
  const navigate = useNavigate()
  const [taskChoices, setTaskChoices] = useState<Record<string, string>>({})
  const [organizers, setOrganizers] = useState<Record<string, string>>({})
  // Only choices still on offer count: for a task or series that is gone after a reload, or a
  // member who left the team meanwhile, the item is left to resolve again.
  const otherIds = new Set(others.map((other) => other.id))
  const taskChoice = (taskId: string) => {
    const choice = taskChoices[taskId] ?? ''
    return choice === REMOVE_TASK || otherIds.has(choice) ? choice : ''
  }
  const organizerChoice = (seriesId: string) => {
    const choice = organizers[seriesId] ?? ''
    return otherIds.has(choice) ? choice : ''
  }
  const left =
    tasks.filter((task) => !taskChoice(task.id)).length +
    series.filter((found) => !organizerChoice(found.id)).length

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const handover = {
      tasks: tasks.map((task) => ({
        taskId: task.id,
        newOwnerId: taskChoice(task.id) === REMOVE_TASK ? null : taskChoice(task.id),
      })),
      series: series.map((found) => ({
        seriesId: found.id,
        organizerId: organizerChoice(found.id),
      })),
    }
    remove.mutate(
      { memberId: member.id, handover },
      // Someone who removes themselves can no longer see the team.
      { onSuccess: () => (isSelf ? navigate('/') : onRemoved()) },
    )
  }

  return (
    <form
      className={`${pageStyles.card} ${styles.removal}`}
      aria-labelledby="removal-title"
      onSubmit={handleSubmit}
    >
      <div>
        <h2 id="removal-title" className={pageStyles.cardTitle}>
          Remove {member.name} from {teamName}
        </h2>
        <p className={pageStyles.sub}>
          {tasks.length + series.length > 0
            ? 'Hand over their work first. Nothing can be left without an owner.'
            : `${isSelf ? 'You have' : 'They have'} no open tasks or series to hand over.`}{' '}
          {isSelf ? 'You' : 'They'} lose access to the team’s meetings, tasks and minutes at once.
        </p>
      </div>
      {(tasks.length > 0 || series.length > 0) && (
        <div className={styles.resolve}>
          {tasks.map((task) => (
            <label key={task.id} className={styles.resolveRow}>
              <span>
                <b>Task:</b> {task.description}{' '}
                <span className={pageStyles.meta}>· due {formatDeadline(task.deadline)}</span>
              </span>
              <select
                className={pageStyles.input}
                aria-label={`What to do with “${task.description}”`}
                value={taskChoice(task.id)}
                onChange={(event) =>
                  setTaskChoices({ ...taskChoices, [task.id]: event.target.value })
                }
              >
                <option value="">Choose…</option>
                {others.map((other) => (
                  <option key={other.id} value={other.id}>
                    Reassign to {other.name}
                  </option>
                ))}
                <option value={REMOVE_TASK}>Remove task</option>
              </select>
            </label>
          ))}
          {series.map((found) => (
            <label key={found.id} className={styles.resolveRow}>
              <span>
                <b>Organizer of:</b> {found.name}
              </span>
              <select
                className={pageStyles.input}
                aria-label={`New organizer of ${found.name}`}
                value={organizerChoice(found.id)}
                onChange={(event) =>
                  setOrganizers({ ...organizers, [found.id]: event.target.value })
                }
              >
                <option value="">Choose new organizer…</option>
                {others.map((other) => (
                  <option key={other.id} value={other.id}>
                    {other.name}
                  </option>
                ))}
              </select>
            </label>
          ))}
        </div>
      )}
      {left > 0 && (
        <p className={pageStyles.sub}>
          {left === 1 ? '1 item left to resolve.' : `${left} items left to resolve.`}
        </p>
      )}
      {remove.isError && (
        <p role="alert" className={pageStyles.error}>
          {removalError(remove.error)}
        </p>
      )}
      <div className={pageStyles.addRow}>
        <button className={pageStyles.danger} type="submit" disabled={left > 0 || remove.isPending}>
          Remove {member.name}
        </button>
        <button className={pageStyles.secondary} type="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}

function removalError(error: Error): string {
  if (error instanceof ApiError && error.status === 409) {
    return 'Their tasks, series or role changed while you were choosing. The lists above are up to date now: check them and try again.'
  }
  if (error instanceof ApiError && error.status === 400) {
    return 'Work can only go to another member of the team.'
  }
  return 'The member couldn’t be removed. Try again in a few minutes.'
}
