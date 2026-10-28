# MeetFlow – Core Meeting Flow

How a team uses MeetFlow across one meeting cycle: **before → during → after → next meeting**. The flow is built around commitment tracking: every task agreed in a meeting has an owner and a deadline, and it stays visible until it is done.

This document is the input for the MVP user stories (roadmap step 2) and the wireframes (roadmap step 3).

## Actors

- **Team admin** – manages the team: invites and removes members, makes other members admins, reassigns the organizer of a series and reassigns or deletes the open tasks of members being removed. The user who creates a team is its first admin, and a team always has at least one admin.
- **Team member** – belongs to a team and can see its meetings, decisions and tasks.
- **Organizer** – the team member who runs a meeting series. Each series has exactly one organizer.
- **Participant** – a team member invited to a meeting.
- **Task owner** – the person responsible for a task. Every task has exactly one owner.

All actors are authenticated users. A user can only see the meetings, decisions and tasks of the teams they belong to.

## Meeting series

A team can have several **meeting series**: groups of recurring meetings with the same purpose, for example a weekly ops sync and a monthly budget review. Every meeting belongs to exactly one series, and carry-over works within a series: open tasks and uncovered agenda items reappear at the next meeting **of the same series**.

Each series has one organizer. A team admin can reassign the organizer to another team member, for example when the organizer leaves the team.

## 0. Setup (once per team)

1. A user signs up with name, email and password, and creates a team, becoming its first team admin. The final authentication method is decided with the tech stack (roadmap step 4).
2. The admin invites colleagues to the team by email. Invitation links are single-use and expire after 7 days.
3. Invited colleagues sign up (or log in) and join the team as members.
4. A team member creates a meeting series (e.g. "Weekly ops sync") and becomes its organizer.

**MVP features:** 1 – User and team sign-up, 2 – Meeting creation.

## 1. Before the meeting

1. The organizer creates the next meeting in a series: date and time, participants (team members).
2. The agenda is pre-filled with **carried-over items** from the previous meeting of the same series:
   - every task still open or in progress
   - agenda items marked as not covered
3. The organizer adds new agenda items, reorders them and can remove carried-over agenda items. Open tasks cannot be removed from the agenda; they leave only when they are done (see *Commitment lifecycle*).
4. Participants can see the meeting and its agenda in advance.

**MVP features:** 2 – Meeting creation.

## 2. During the meeting

The organizer runs the meeting in the app and works through the agenda:

1. **Review open commitments first.** For each carried-over task, the owner reports progress and the owner or the organizer updates the status (open, in progress, done). Only the organizer can change a task's owner or deadline.
2. **Discuss each agenda item** and take short notes.
3. **Record decisions**: what was agreed, linked to the agenda item.
4. **Create tasks** from decisions or agenda items. Each task requires:
   - a description
   - an owner, who must be a participant of this meeting
   - a deadline
5. Agenda items not covered are marked as such, so they carry over.
6. The organizer closes the meeting. After closing, its notes, decisions and tasks can no longer be added or edited; task status can still be updated.

**MVP features:** 3 – Recording of agreements, 4 – Task assignment with owners.

## 3. After the meeting

1. When the meeting is closed, **minutes** are generated automatically: participants, agenda, notes, decisions, new tasks (owner, deadline) and the status of carried-over tasks.
2. The minutes are emailed to all participants and stay available in the app. Minutes cannot be edited once they are generated.
3. Each task owner receives **one email** after the meeting listing their tasks from it. There are no further reminders (no deadline or overdue emails), to avoid spamming users. This email cannot be turned off.
4. Each user sees their tasks on the **pending-tasks dashboard**, across all their teams, sorted by deadline, with overdue tasks highlighted. The dashboard is the landing page after login and can be filtered by team.
5. Task owners update status between meetings (open → in progress → done), including directly from the dashboard.
6. Past meetings, their decisions and minutes are listed in the team's **meeting history**, grouped by series and searchable by keyword across series names and decision text.

**MVP features:** 5 – Pending-tasks dashboard, 6 – Meeting history, 7 – Minutes generation.

## 4. Next meeting

When the organizer creates the next meeting of the series, the cycle starts again at step 1: open tasks and uncovered agenda items reappear on the agenda automatically. A commitment keeps coming back until it is marked done.

## Commitment lifecycle

```
          create (owner + deadline)
                   │
                   ▼
   ┌──────────► open ──────► in progress ──────► done
   │               ▲               │
   │               └───────────────┘
   │     (reopen)                          done tasks leave the agenda
   └─────────────────────────────── done
```

- **open** – created, not started.
- **in progress** – the owner has started working on it.
- **done** – completed. A done task can be reopened if needed.
- **Overdue** is not a separate status: it is any task that is not done and whose deadline has passed. It is highlighted in the dashboard, the agenda and the minutes.
- Only the task owner and the organizer of the task's meeting series can update a task. The owner can change its status; the organizer can change its status, owner and deadline.
- A task can never be left without an owner. Before a member is removed from the team, a team admin must reassign each of their open tasks to another team member or delete it, and reassign the organizer of any series the member runs. The removal cannot be completed until this is done.
- Every change is stored with who made it and when (status history), so the minutes and the meeting history can show how a commitment evolved.

## Permissions summary

| Action | Team admin | Organizer | Task owner | Other team members |
|---|---|---|---|---|
| Invite / remove team members, appoint admins | ✓ | | | |
| Reassign the organizer of a series | ✓ | | | |
| Reassign or delete open tasks of a member being removed | ✓ | | | |
| Create a meeting series | ✓ | ✓ | ✓ | ✓ |
| Create meetings and edit the agenda of a series | | ✓ | | |
| Update task status | | ✓ | ✓ (own tasks) | |
| Change task owner or deadline | | ✓ | | |
| Edit minutes | – | – | – | – |
| View the team's meetings, tasks and minutes | ✓ | ✓ | ✓ | ✓ |

Being a team admin does not grant organizer rights on a series; a user can hold several roles.

## Feature mapping

| Flow step | MVP feature |
|---|---|
| 0. Setup: sign-up, team, admins, invitations | 1 – User and team sign-up |
| 0. Setup: create series; 1. Before: create meeting, agenda, carry-over | 2 – Meeting creation |
| 2. During: record decisions | 3 – Recording of agreements |
| 2. During: create tasks with owner and deadline | 4 – Task assignment with owners |
| 3. After: personal task list, status updates | 5 – Pending-tasks dashboard |
| 3. After: past meetings and decisions | 6 – Meeting history |
| 3. After: minutes and task email sent to participants | 7 – Minutes generation |

Automatic carry-over is not a separate feature: it is part of commitment tracking (roadmap step 7) and supports features 2 and 5.

## Out of scope for the MVP

AI summaries from transcripts, Microsoft Teams and calendar integrations, advanced automations and analytics, billing, editing minutes, deadline and overdue reminders.

## Decisions

Resolved during roadmap step 2 (MF-2):

1. **Meeting series** – a team can have more than one series; carry-over happens within a series.
2. **Who can update a task** – only the task owner and the organizer.
3. **Task owners** – every task has exactly one owner, who must be a participant of the meeting where the task is created. Unassigned tasks are not allowed.
4. **Emails** – each task owner gets one email after the meeting with their tasks; no deadline or overdue reminders, to avoid spamming. The email cannot be turned off.
5. **Minutes** – cannot be edited in the MVP.
6. **Roles** – teams have admins who manage membership.
7. **Organizer** – one per series; a team admin can reassign it.
8. **Removed members** – before a member is removed, a team admin must reassign or delete each of their open tasks and reassign any series they organize.
9. **Closed meetings** – notes, decisions and tasks are locked once a meeting is closed; task status remains updatable.

Resolved during roadmap step 4 (MF-30, see `docs/data-model.md`):

10. **One open meeting per series** – the next meeting of a series can only be created once the previous one is closed.
11. **Unmarked agenda items** – items not marked as covered when a meeting closes count as not covered and carry over.
12. **Deleted tasks** – tasks deleted by an admin are hidden but kept, so their history stays intact.
13. **Account deletion** – a deleted user is anonymized, not removed from meeting records.
