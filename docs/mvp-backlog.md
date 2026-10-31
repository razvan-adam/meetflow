# MeetFlow – MVP Backlog Order

The agreed build order for the MVP user stories, plus every other issue registered in Jira so the whole project can be followed from here. **This document is the source of truth for priority**; the order of issues in the Jira backlog is not maintained.

Stories and their acceptance criteria live in Jira (project MF). The flow they implement is described in `docs/core-flow.md`. Statuses below mirror Jira as of 2026-10-31.

## MVP stories

| Rank | Key | Story | Epic | Status |
|---|---|---|---|---|
| 1 | MF-8 | Sign up, log in and log out | MF-5 Core | Done |
| 2 | MF-9 | Create a team | MF-5 Core | Done |
| 3 | MF-10 | Invite colleagues to a team | MF-5 Core | To Do |
| 4 | MF-12 | Create a meeting series | MF-5 Core | To Do |
| 5 | MF-13 | Schedule a meeting with participants | MF-5 Core | To Do |
| 6 | MF-14 | Prepare the meeting agenda | MF-5 Core | To Do |
| 7 | MF-15 | Run a meeting: notes per agenda item and closing | MF-5 Core | To Do |
| 8 | MF-16 | Record decisions during a meeting | MF-5 Core | To Do |
| 9 | MF-17 | Create tasks with owner and deadline | MF-6 Commitment tracking | To Do |
| 10 | MF-18 | Update a task's status, owner and deadline | MF-6 Commitment tracking | To Do |
| 11 | MF-19 | Carry over open tasks and uncovered items to the next meeting | MF-6 Commitment tracking | To Do |
| 12 | MF-21 | Pending-tasks dashboard | MF-6 Commitment tracking | To Do |
| 13 | MF-22 | Generate and email minutes when a meeting is closed | MF-7 Minutes and history | To Do |
| 14 | MF-20 | Email task owners their tasks after the meeting | MF-6 Commitment tracking | To Do |
| 15 | MF-23 | Browse and search meeting history | MF-7 Minutes and history | To Do |
| 16 | MF-11 | Manage team members and admins | MF-5 Core | To Do |

## Rationale

- **Ranks 1–8** provide the minimum needed to hold a meeting.
- **Ranks 9–12** deliver commitment tracking, MeetFlow's differentiator, as early as possible. Completing rank 12 is the milestone for a first internal demo: a team can run a meeting, assign tasks and see them carry over.
- **Ranks 13–16** complete the MVP. They are valuable, but the core loop can be tried without them.

## Epics

| Key | Epic | Status |
|---|---|---|
| MF-1 | Define the product and the MVP scope | Done |
| MF-24 | Design the user experience | Done |
| MF-28 | Choose the architecture and tech stack | Done |
| MF-32 | Set up the development foundations | To Do |
| MF-5 | Core: users, teams and meetings | To Do |
| MF-6 | Commitment tracking | To Do |
| MF-7 | Minutes and meeting history | To Do |

## Other tasks

Work outside the MVP stories, grouped by epic.

| Key | Task | Epic | Status |
|---|---|---|---|
| MF-2 | Map the core meeting flow (before → during → after → next meeting) | MF-1 Product and MVP scope | Done |
| MF-3 | Write user stories with acceptance criteria for the 7 MVP features | MF-1 Product and MVP scope | Done |
| MF-4 | Prioritize the MVP backlog and record deferred scope | MF-1 Product and MVP scope | Done |
| MF-25 | Map the main user journeys | MF-24 User experience | Done |
| MF-26 | Wireframe the key screens and define a basic visual style | MF-24 User experience | Done |
| MF-27 | Test the wireframes with interviewed users | MF-24 User experience | Done |
| MF-29 | Choose the tech stack and hosting | MF-28 Architecture and tech stack | Done |
| MF-30 | Design the data model | MF-28 Architecture and tech stack | Done |
| MF-31 | Create the project skeleton | MF-28 Architecture and tech stack | Done |
| MF-33 | Set up the CI pipeline | MF-32 Development foundations | Done |
| MF-34 | Define coding conventions and add a backend linter/formatter | MF-32 Development foundations | Done |
| MF-35 | Fix the review follow-ups from the project skeleton | MF-32 Development foundations | Done |
| MF-36 | Add baseline security | MF-32 Development foundations | Done |
| MF-37 | Set up the staging environment with automatic deploys | MF-32 Development foundations | To Do |

## Maintaining this document

Update the tables when issues are added, removed, reprioritized or change status, and reference the related Jira issue in the commit message.
