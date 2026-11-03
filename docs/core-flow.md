# MeetFlow – Core Meeting Flow

How a team uses MeetFlow across one meeting cycle: **before → during → after → next meeting**. The flow is built around commitment tracking: every task agreed in a meeting has an owner and a deadline, and it stays visible until it is done.

This document is the input for the MVP user stories (roadmap step 2) and the wireframes (roadmap step 3).

## Actors

- **Team admin** – manages the team: invites and removes members, makes other members admins, reassigns the organizer of a series and reassigns or removes the open tasks of members being removed. The user who creates a team is its first admin, and a team always has at least one admin.
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
3. The organizer adds new agenda items, reorders them and can remove agenda items, including carried-over ones, until the meeting starts. Open tasks cannot be taken off the agenda on their own; they leave only when they are done or the task itself is removed (see *Commitment lifecycle*).
4. Participants can see the meeting and its agenda in advance.

**MVP features:** 2 – Meeting creation.

## 2. During the meeting

The organizer runs the meeting in the app and works through the agenda:

1. **Review open commitments first.** For each carried-over task, the owner reports progress and the owner or the organizer updates the status (open, in progress, done). Only the organizer can change a task's owner or deadline.
2. **Discuss each agenda item** and take short notes.
3. **Record decisions**: what was agreed, linked to the agenda item. The organizer can edit a decision until the meeting closes, and remove it until a task comes from it.
4. **Create tasks** from decisions or agenda items. Each task requires:
   - a description
   - an owner, who must be a participant of this meeting
   - a deadline

   The organizer can reword or remove a task during its own meeting and any later meeting of the series.
5. Each agenda item is marked as covered or not covered. Items not covered carry over, and so do items left unmarked when the meeting closes.
6. The organizer closes the meeting. After closing, its notes and decisions can no longer be changed, and tasks can no longer be added to it. A task's status, owner and deadline can still be updated, under the rules in *Commitment lifecycle*, and it can be reworded or removed during a later meeting.

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
- **Overdue** is not a separate status: it is any task that is not done and whose deadline has passed. It is highlighted in the dashboard, the agenda and the minutes. The screens say how many days overdue it is, counted to today in the viewer's time zone; the minutes are a record, so they use the date the meeting was held.
- Only the task owner and the organizer of the task's meeting series can update a task. The owner can change its status; the organizer can change its status, owner and deadline.
- A task can never be left without an owner. Before a member is removed from the team, a team admin must reassign each of their open tasks to another team member or remove it, and reassign the organizer of any series the member runs. The removal cannot be completed until this is done.
- Every change to a task's status, owner, deadline or description is stored with who made it and when (status history), so the minutes and the meeting history can show how a commitment evolved. The one exception is rewording during the task's own meeting, which is a correction: nobody has been sent the task yet.

## Permissions summary

| Action | Team admin | Organizer | Task owner | Other team members |
|---|---|---|---|---|
| Invite / remove team members, appoint admins | ✓ | | | |
| Reassign the organizer of a series | ✓ | | | |
| Reassign or remove open tasks of a member being removed | ✓ | | | |
| Create a meeting series | ✓ | ✓ | ✓ | ✓ |
| Create meetings and edit the agenda of a series | | ✓ | | |
| Record decisions and create tasks during a meeting | | ✓ | | |
| Update task status | | ✓ | ✓ (own tasks) | |
| Change task owner or deadline | | ✓ | | |
| Reword a task during its own or a later meeting | | ✓ | | |
| Remove a task during its own or a later meeting | | ✓ | | |
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
8. **Removed members** – before a member is removed, a team admin must reassign or remove each of their open tasks and reassign any series they organize.
9. **Closed meetings** – notes, decisions and tasks are locked once a meeting is closed; task status remains updatable.

Resolved during roadmap step 4 (MF-30, see `docs/data-model.md`):

10. **One open meeting per series** – the next meeting of a series can only be created once the previous one is closed.
11. **Unmarked agenda items** – items not marked as covered when a meeting closes count as not covered and carry over.
12. **Removed tasks** – removed tasks are hidden but kept, so their history stays intact.
13. **Account deletion** – a deleted user is anonymized, not removed from meeting records.

Resolved while building MF-16:

14. **Removing agenda items** – only before the meeting starts. Once it has started, items can still be added, renamed and reordered until it closes. Decisions and tasks are only created during a meeting, so a removable item never has any pointing at it, and no notes can be lost.

Resolved while building MF-17:

15. **Removing decisions** – a decision that tasks came from can't be removed, so no commitment loses its origin. It can still be edited.

Resolved while building MF-18:

16. **Correcting tasks** – the organizer can reword or remove a task while any meeting of its series runs, but not between meetings. Rewording during the task's own meeting stays out of the history, because it is a correction made before anyone is sent the task; rewording during a later meeting is recorded, because people have seen the old wording. A task removed during its own meeting was a mistake: it disappears and lets go of its decision, so a decision whose tasks were all removed can be removed. A task removed during a later meeting leaves its owner's list and later agendas, but stays on its closed meeting's page under its decision, struck through with who removed it and in which meeting, and its screen stays readable with its history. It can no longer be changed.
17. **Status changes** – any status can follow any other, so a done task can be reopened and a slip of the hand corrected.

Resolved while building MF-19:

18. **Done and reopened tasks on the agenda** – a task marked done before the next meeting starts leaves that meeting's agenda. One marked done during the meeting stays on its list, showing as done, because that is the review the meeting records. A reopened task joins the series' next meeting if it is scheduled or running, unless that is the task's own meeting.
19. **Removed tasks on the agenda** – a removed task never carries over again. The meeting it is removed in, and every closed meeting that reviewed it, show it in a separate "Removed commitments" section, struck through, with who removed it and when.
20. **What carries over with an agenda item** – only its title. Its notes stay on the meeting they were taken in.

Resolved while building MF-22:

21. **What the minutes email holds** – a short summary with a link to the full minutes: decisions, new tasks, open commitments, tasks removed during the meeting and what wasn't covered. Notes and commitments marked done stay in the app.
22. **The record and today** – the minutes keep each task's wording, owner, deadline and status as they were at closing. They also say how a task stands today ("Now done", "Removed since"), so nobody acts on stale information. A closed meeting's page shows a task as it read at closing too, and says if it has been reworded since.
23. **The meeting's date in the minutes** – the date it actually started, in the time zone of the organizer who closed it, so everyone reads the same date and the same overdue days wherever they are. Overdue days count to that date: a task due before a meeting held late was overdue when it was reviewed, whatever the plan said. If the meeting was held on another day than planned, the minutes also show the planned date.
24. **Saying the minutes were emailed** – the minutes page says "emailed" only once every participant's email has gone out, and shows how many have been sent until then.
