# MeetFlow – MVP Backlog Order

The agreed build order for the MVP user stories. **This document is the source of truth for priority**; the order of issues in the Jira backlog is not maintained.

Stories and their acceptance criteria live in Jira (project MF). The flow they implement is described in `docs/core-flow.md`.

| Rank | Key | Story | Epic |
|---|---|---|---|
| 1 | MF-8 | Sign up, log in and log out | MF-5 Core |
| 2 | MF-9 | Create a team | MF-5 Core |
| 3 | MF-10 | Invite colleagues to a team | MF-5 Core |
| 4 | MF-12 | Create a meeting series | MF-5 Core |
| 5 | MF-13 | Schedule a meeting with participants | MF-5 Core |
| 6 | MF-14 | Prepare the meeting agenda | MF-5 Core |
| 7 | MF-15 | Run a meeting: notes per agenda item and closing | MF-5 Core |
| 8 | MF-16 | Record decisions during a meeting | MF-5 Core |
| 9 | MF-17 | Create tasks with owner and deadline | MF-6 Commitment tracking |
| 10 | MF-18 | Update a task's status, owner and deadline | MF-6 Commitment tracking |
| 11 | MF-19 | Carry over open tasks and uncovered items to the next meeting | MF-6 Commitment tracking |
| 12 | MF-21 | Pending-tasks dashboard | MF-6 Commitment tracking |
| 13 | MF-22 | Generate and email minutes when a meeting is closed | MF-7 Minutes and history |
| 14 | MF-20 | Email task owners their tasks after the meeting | MF-6 Commitment tracking |
| 15 | MF-23 | Browse and search meeting history | MF-7 Minutes and history |
| 16 | MF-11 | Manage team members and admins | MF-5 Core |

## Rationale

- **Ranks 1–8** provide the minimum needed to hold a meeting.
- **Ranks 9–12** deliver commitment tracking, MeetFlow's differentiator, as early as possible. Completing rank 12 is the milestone for a first internal demo: a team can run a meeting, assign tasks and see them carry over.
- **Ranks 13–16** complete the MVP. They are valuable, but the core loop can be tried without them.

## Maintaining this order

Update this table when stories are added, removed or reprioritized, and reference the related Jira issue in the commit message.
