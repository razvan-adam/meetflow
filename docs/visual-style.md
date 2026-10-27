# MeetFlow – Basic Visual Style

The starting visual style for MeetFlow (roadmap step 3, MF-26), as used in the wireframes (`docs/wireframes/index.html`). It is deliberately small: enough to build the MVP consistently, to be refined after the user tests (MF-27).

## Principles

- **Commitments first.** Wherever a task appears it shows the same four things in the same place: description, owner, deadline, status.
- **Overdue is the loudest thing on the screen.** Red is reserved for overdue tasks and destructive actions; nothing else uses it.
- **Light and calm.** Near-white surfaces, faint borders, one accent color for anything you can click.
- **Light and dark themes.** Every color is a token with a value for each theme; the app follows the system setting.

## Colors

| Token | Role | Light | Dark |
|---|---|---|---|
| `paper` | Page background | `#f8fbfa` | `#18201e` |
| `frame` | Surfaces (cards, panels) | `#ffffff` | `#212b28` |
| `ink` | Main text | `#1f2a27` | `#eef4f2` |
| `muted` | Secondary text, metadata | `#6b7773` | `#a3b1ac` |
| `line` | Borders, dividers | `#e6ecea` | `#35433f` |
| `fill` | Subtle blocks, read-only inputs | `#f5f9f7` | `#27322f` |
| `wire` | Accent: buttons, links, selection | `#0f8a72` | `#5fdcbc` |
| `wire-soft` | Accent tint: selected chips, callouts | `#eaf8f3` | `#1d4a40` |
| `overdue` | Overdue tasks, destructive actions | `#e0484d` | `#ff6b6b` |
| `overdue-soft` | Overdue row background | `#fff4f4` | `#3a1c1e` |
| `done` | Done confirmation | `#0f8a72` | `#5fdcbc` |

The lilac `note` colors in the wireframes are only for design notes and are not part of the app.

## Typography

- **Atkinson Hyperlegible** (400, 700) for all app text. Chosen for legibility of small metadata such as owners and dates. Fallback: `"Segoe UI", system-ui, sans-serif`.
- **JetBrains Mono** (400, 600) only for small section labels and tags. Fallback: `ui-monospace, Consolas, monospace`.
- Scale: 22px page titles, 16px section titles, 15px body, 14px controls and lists, 13px metadata, 11–12px labels and tags.
- Dates and counts use tabular figures so columns line up.

## Spacing and shape

- Spacing steps: 4, 6, 8, 10, 12, 14, 16, 18, 24 px. Groups are laid out with `gap`, not margins.
- Page padding: 16px on phones, 28px from 640px wide.
- Radius: 6px for controls, rows and cards; 8px for the app frame; fully rounded for chips and role badges.
- Borders are 1px `line`. No shadows except the 3px red bar on overdue rows.

## Core components

- **Task row:** description (bold, links to the task), metadata line (owner, status or origin), deadline and status on the right. Overdue: `overdue-soft` background, 3px `overdue` left bar, deadline in bold red with "overdue". Done: struck through and muted.
- **Buttons:** primary (filled `wire`, one per screen), secondary (outlined), danger (red outline, for close and remove).
- **Status control:** a select in lists; a segmented control (Open / In progress / Done) on the task page.
- **Chips:** filters such as teams; selected chips use `wire-soft` with a `wire` border.
- **Tags:** small mono labels, such as "not covered on 26 Oct" (accent tint) or "read-only" (grey).
- **Lock badge:** dashed outline, "stays until done", on carried-over tasks that cannot be removed from the agenda.
- **Callouts:** `wire-soft` for information (carry-over preview, permissions); `overdue-soft` for problems (expired invitation).
- **Confirmation panels:** inline, never a browser dialog. They list what will happen (for example, closing a meeting) before the action button.
