# MeetFlow – High-Level Project Steps

Ten steps from idea to a launched, sellable product. Steps 1–8 take us to the MVP described in `idea.txt`; steps 9–10 cover launch and the features planned for later.

## 1. Validate the problem and the market
Interview 10–15 potential customers (SMBs; operations, logistics and administration departments; consulting firms) to confirm that losing track of meeting commitments is a real, costly problem. Review competitors (note-taking apps, task managers, meeting tools) and write down how MeetFlow's focus on following up on commitments differs.

**Outcome:** validated problem statement, target customer profile, competitor summary.

## 2. Define the product and the MVP scope
Turn the idea into concrete user stories and the core flow: before → during → after → next meeting. Fix the MVP scope to the seven features in `idea.txt` and explicitly defer AI, Teams integration and advanced analytics.

**Outcome:** prioritized MVP backlog with acceptance criteria.

## 3. Design the user experience
Map the main user journeys and produce wireframes for the key screens: meeting creation, agenda, live meeting notes, decisions and tasks, pending-tasks dashboard, meeting history, minutes. Test the wireframes with a few of the interviewed users.

**Outcome:** validated wireframes and a basic visual style.

## 4. Choose the architecture and tech stack
Pick the frontend, backend, database, authentication, email provider and hosting. Design the data model around the core entities: users, teams, meetings, agenda items, decisions, tasks and their status history.

**Outcome:** architecture document, data model, repository and project skeleton.

## 5. Set up the development foundations
Create the repository, coding conventions, CI pipeline (lint, tests, build), development/staging environments and baseline security (secrets management, HTTPS, access control).

**Outcome:** a "hello world" app deployed automatically to staging.

## 6. Build the core: users, teams and meetings
Implement sign-up and login, team management and invitations, meeting creation with participants and agendas, and recording of topics and decisions during the meeting.

**Outcome:** a team can create and run a meeting in the app.

## 7. Build commitment tracking (the differentiator)
Implement tasks with owners and deadlines, status updates, the per-person pending-tasks dashboard, email reminders as deadlines approach, and automatic carry-over of open items to the next meeting's agenda.

**Outcome:** commitments are followed from the meeting until they are done.

## 8. Complete the MVP: minutes, history and pilot testing
Generate structured minutes and send the summary to participants; add searchable meeting and decision history. Run a pilot with 3–5 real teams, collect feedback and fix the most important issues.

**Outcome:** MVP ready, validated by real pilot users.

## 9. Launch commercially
Set up Free / Pro / Business plans with subscription billing, a landing page, onboarding, terms and privacy policy (GDPR), support channels and usage analytics. Start the sales and marketing strategy targeting the defined customer segments.

**Outcome:** public launch with paying customers.

## 10. Grow with advanced features
Based on customer feedback and usage data, add AI summaries from meeting transcripts, Microsoft Teams and calendar integrations, automations and team statistics for the Business plan, and continue improving retention and conversion.

**Outcome:** a differentiated product roadmap driven by real customer data.
