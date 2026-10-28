# meetflow
Web App that manages meetings, agreements and pending tasks which are discussed on that meeting.

## Getting started

Install JDK 25, Node.js 24 LTS and Docker, then:

```sh
cd frontend && npm ci && cd ..
npm run dev
```

The app opens at http://localhost:5173 and the API health check is at `/api/health`. All commands (test, lint, build) are listed in `AGENTS.md`.

## Terminology: minutes

**Minutes** are the written record of a meeting: attendees, agenda, decisions, and the tasks agreed with their owners and deadlines. MeetFlow generates them automatically when a meeting is closed and sends them to all participants.

We keep the term "minutes". These alternatives were considered:

| Name | Feel | Example in the app |
|---|---|---|
| **Meeting summary** | Clear, neutral, needs no explanation | "Meeting summary sent to 6 participants" |
| **Recap** | Short and friendly, common in modern tools | "Monday ops sync – Recap" |
| **Follow-up** | Puts the focus on what happens next, which fits commitment tracking | "Your follow-up from today's meeting: 3 new tasks" |
| **Meeting report** | More formal, suits larger companies | "Download meeting report (PDF)" |
| **Minutes** | Traditional and well known in business, but sounds old-fashioned | "Minutes of the board meeting" |

"Notes" is not used for this document: MeetFlow is not a note-taking app, and "notes" already refers to the short comments taken on each agenda item during a meeting.
