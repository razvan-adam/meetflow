# MeetFlow – User Journeys

The screen-by-screen journeys each actor follows in MeetFlow (roadmap step 3, MF-25). They turn the core flow (`docs/core-flow.md`) and the MVP stories (MF-8 to MF-23, ordered in `docs/mvp-backlog.md`) into the input for the wireframes (MF-26).

Screens are referred to by the IDs in *Key screens* below. Example data used throughout: a logistics company's **Ops team**, whose **Weekly ops sync** series is organized by Marta.

## Journeys

### J1. Set up a team

**Actor:** a new user who becomes team admin. **Stories:** MF-8, MF-9, MF-10, MF-12.

1. Signs up with name, email and password (S1) and lands on an empty dashboard (S2) that prompts them to create a team.
2. Creates the team "Ops team" and becomes its first admin (S3).
3. Invites colleagues by email from the team page and sees the pending invitations (S3).
4. Creates the meeting series "Weekly ops sync" and becomes its organizer (S3 → S4).

### J2. Join a team from an invitation

**Actor:** an invited colleague. **Stories:** MF-10, MF-8.

1. Opens the invitation email (E1) and follows the link.
2. Signs up, or logs in if they already have an account (S1), and joins the team.
3. Lands on the dashboard (S2) and can see the team's series and meetings (S3, S4).
4. If the link is expired or already used, sees a clear message and asks an admin for a new one.

### J3. Prepare a meeting

**Actor:** organizer. **Stories:** MF-13, MF-14, MF-19.

1. Opens the series (S4) and schedules the next meeting: date, time and participants from the team (S5).
2. Opens the agenda (S6). It is already pre-filled with:
   - every open or in-progress task of the series, overdue ones highlighted, which cannot be removed
   - agenda items not covered last time, which can be removed
3. Adds new agenda items and reorders them.
4. Participants and other team members can open the meeting and its agenda in advance (S6, read-only).

### J4. Run and close a meeting

**Actor:** organizer, with the participants in the room. **Stories:** MF-15, MF-16, MF-17, MF-18.

1. Starts the meeting (S7). Carried-over tasks come first.
2. For each carried-over task, the owner reports progress and the owner or the organizer updates its status; the organizer can also change owner or deadline (S7, S8).
3. Works through the agenda items: takes short notes, records decisions linked to the item, and creates tasks from a decision or item with description, owner (a participant) and deadline (S7).
4. Marks items that were not discussed as not covered, so they carry over.
5. Closes the meeting after a confirmation step. Notes, decisions and tasks are locked; task status stays updatable. Closing generates the minutes and sends the emails (J5).

### J5. Follow up after the meeting

**Actor:** participant and task owner. **Stories:** MF-22, MF-20, MF-21, MF-18.

1. Receives the minutes by email (E2) and can open them in the app, read-only (S9).
2. Each task owner receives one email listing their tasks from the meeting, with links (E3). No further reminders are sent.
3. Logs in and lands on the pending-tasks dashboard (S2): their open and in-progress tasks across all teams, soonest deadline first, overdue highlighted, filterable by team.
4. Updates a task's status directly from the dashboard, or opens the task (S8) to see its details and history.
5. At the next meeting the task reappears on the agenda (J3) until it is marked done.

### J6. Look back at what was decided

**Actor:** any team member. **Stories:** MF-23, MF-22.

1. Opens the team's meeting history (S10): past meetings grouped by series, newest first.
2. Searches by keyword across series names and decision text.
3. Opens a past meeting's minutes (S9) and sees its decisions and tasks with their current status.

### J7. Remove a member

**Actor:** team admin. **Stories:** MF-11, MF-12.

1. Opens the team's members (S11) and chooses to remove a member.
2. Before the removal can be confirmed, reassigns each of the member's open tasks to another team member or deletes it, and reassigns the organizer of any series they run.
3. Confirms the removal; the member immediately loses access to the team.
4. Separately, can make members admins or remove admin rights, keeping at least one admin.

## Story coverage

| Story | Journeys |
|---|---|
| MF-8 Sign up, log in and log out | J1, J2 |
| MF-9 Create a team | J1 |
| MF-10 Invite colleagues to a team | J1, J2 |
| MF-11 Manage team members and admins | J7 |
| MF-12 Create a meeting series | J1, J7 |
| MF-13 Schedule a meeting with participants | J3 |
| MF-14 Prepare the meeting agenda | J3 |
| MF-15 Run a meeting: notes per agenda item and closing | J4 |
| MF-16 Record decisions during a meeting | J4 |
| MF-17 Create tasks with owner and deadline | J4 |
| MF-18 Update a task's status, owner and deadline | J4, J5 |
| MF-19 Carry over open tasks and uncovered items | J3, J5 |
| MF-20 Email task owners their tasks after the meeting | J5 |
| MF-21 Pending-tasks dashboard | J5 |
| MF-22 Generate and email minutes | J5, J6 |
| MF-23 Browse and search meeting history | J6 |

## Key screens

The input for the wireframes (MF-26). Screens marked **key** are the ones named in roadmap step 3 and are wireframed first.

| ID | Screen | Used in | Key |
|---|---|---|---|
| S1 | Sign up / log in | J1, J2 | |
| S2 | Pending-tasks dashboard (landing page after login) | J1, J2, J5 | key |
| S3 | Team: series, members and invitations | J1, J2 | |
| S4 | Meeting series: next meeting, open tasks, past meetings | J1, J3 | |
| S5 | Schedule a meeting: date, time, participants | J3 | key |
| S6 | Agenda | J3 | key |
| S7 | Live meeting: notes, decisions and tasks, closing | J4 | key |
| S8 | Task: status, owner, deadline, history | J4, J5 | key |
| S9 | Minutes (read-only) | J5, J6 | key |
| S10 | Meeting history and search | J6 | key |
| S11 | Team members: roles and removal | J7 | |
| E1 | Invitation email | J2 | |
| E2 | Minutes email | J5 | |
| E3 | Task email to owners | J5 | |

Wireframes: `docs/wireframes/index.html`.
