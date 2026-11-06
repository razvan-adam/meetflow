# MeetFlow – Basic Visual Style

The starting visual style for MeetFlow (roadmap step 3, MF-26), as used in the wireframes (`docs/wireframes/index.html`). It is deliberately small: enough to build the MVP consistently, to be refined after the user tests (MF-27).

## Principles

- **Commitments first.** Wherever a task appears it shows the same four things in the same place: description, owner, deadline, status.
- **Overdue is the loudest thing on the screen.** Red is reserved for overdue tasks and destructive actions; nothing else uses it.
- **Light and calm.** Near-white surfaces, faint borders, one accent color for anything you can click.
- **Light and dark themes.** Every color is a token with a value for each theme. The app follows the system setting until the user picks a theme with the switch in the top bar. The choice is remembered in that browser, not on the account, and also applies to the logged-out pages.

## Colors

| Token | Role | Light | Dark |
|---|---|---|---|
| `paper` | Page background | `#f8fbfa` | `#18201e` |
| `frame` | Surfaces (cards, panels) | `#ffffff` | `#212b28` |
| `ink` | Main text | `#1f2a27` | `#eef4f2` |
| `muted` | Secondary text, metadata | `#67726e` | `#a3b1ac` |
| `line` | Borders, dividers | `#e6ecea` | `#35433f` |
| `fill` | Subtle blocks, read-only inputs | `#f5f9f7` | `#27322f` |
| `wire` | Accent: buttons, links, selection | `#0e7e68` | `#5fdcbc` |
| `wire-soft` | Accent tint: selected chips, callouts | `#eaf8f3` | `#1a433a` |
| `overdue` | Overdue tasks, destructive actions | `#c64044` | `#ff8080` |
| `overdue-soft` | Overdue row background | `#fff4f4` | `#3a1c1e` |
| `done` | Done confirmation | `#0e7e68` | `#5fdcbc` |
| `placeholder` | Placeholder text in inputs | `#757575` | `#8f9c97` |

In both themes, `ink`, `muted`, `wire`, `overdue` and `done` reach a contrast of at least 4.5:1 on every background (`paper`, `frame`, `fill`, `wire-soft`, `overdue-soft`), `placeholder` reaches it on `frame` (the input background), and `frame` reaches it on `wire` (the label of filled buttons). `src/styles/contrast.test.ts` checks this, and that the two copies of the dark colors in `global.css` match, so a new color can't quietly fall below it (MF-43, MF-44). Disabled controls are faded on purpose and are exempt.

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
- **Theme switch:** a secondary button in the top bar, named after the theme it leads to ("Dark mode" in light mode).
- **Status control:** a select in lists; a segmented control (Open / In progress / Done) on the task page.
- **Chips:** filters such as teams; selected chips use `wire-soft` with a `wire` border.
- **Tags:** small mono labels, such as "not covered on 26 Oct" (accent tint) or "read-only" (grey).
- **Lock badge:** dashed outline, "stays until done", on carried-over tasks that cannot be removed from the agenda.
- **Callouts:** `wire-soft` for information (carry-over preview, permissions); `overdue-soft` for problems (expired invitation).
- **Search highlight:** words found by a search are marked with a `wire-soft` background and keep their text color.
- **Show more:** a long list shows its newest entries and ends with a secondary button that loads the next ones ("Show older meetings"); if they can't load, an error appears below and the list stays.
- **Confirmation panels:** inline, never a browser dialog. They list what will happen (for example, closing a meeting) before the action button.
