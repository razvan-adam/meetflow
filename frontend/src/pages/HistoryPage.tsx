import { useEffect, useState, type ReactNode } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useHistory, type HistoryMeeting } from '../api/history.ts'
import { Breadcrumb } from './Breadcrumb.tsx'
import styles from './HistoryPage.module.css'
import pageStyles from './TeamPage.module.css'
import { formatDeadline } from './taskDisplay.ts'

// Waits for a pause in typing before searching, so each keystroke isn't a request.
const SEARCH_DELAY_MS = 250

// Letters and digits make words, as the server splits the search.
const WORD = /[\p{L}\p{N}]+/gu

// A team's meeting history (S10): past meetings by series, searchable by keyword across series
// names and decision text (MF-23). The search is kept in the address, so going back keeps it.
export function HistoryPage() {
  const { teamId = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const search = params.get('q') ?? ''
  const [typed, setTyped] = useState(search)
  const history = useHistory(teamId, search.trim())

  useEffect(() => {
    if (typed === search) {
      return
    }
    const timer = setTimeout(
      () => setParams(typed ? { q: typed } : {}, { replace: true }),
      SEARCH_DELAY_MS,
    )
    return () => clearTimeout(timer)
  }, [typed, search, setParams])

  if (history.isError) {
    return (
      <main className={pageStyles.page}>
        <p role="alert" className={pageStyles.empty}>
          {history.error instanceof ApiError && history.error.status === 404
            ? 'This team doesn’t exist or you’re not a member of it.'
            : 'Something went wrong. Try again in a few minutes.'}
        </p>
      </main>
    )
  }
  const words = wordsIn(search)
  return (
    <main className={pageStyles.page}>
      <div>
        <Breadcrumb teamId={teamId} />
        <h1 className={pageStyles.title}>Meeting history</h1>
      </div>
      <input
        className={`${pageStyles.input} ${styles.search}`}
        type="search"
        aria-label="Search meeting history"
        placeholder="Search series names and decisions"
        value={typed}
        onChange={(event) => setTyped(event.target.value)}
      />
      {history.data &&
        (history.data.length === 0 ? (
          <p className={pageStyles.empty}>
            {words.length > 0
              ? 'No meetings or decisions match. Search covers series names and decision text in this team.'
              : 'No past meetings yet. A meeting appears here once it’s closed, with its decisions and minutes.'}
          </p>
        ) : (
          history.data.map((series) => (
            <section key={series.id} aria-labelledby={`series-${series.id}`}>
              <h2 id={`series-${series.id}`} className={pageStyles.sectionLabel}>
                <Highlighted text={series.name} words={words} />
              </h2>
              <ul className={pageStyles.list}>
                {series.meetings.map((meeting) => (
                  <MeetingRow key={meeting.id} meeting={meeting} words={words} />
                ))}
              </ul>
            </section>
          ))
        ))}
    </main>
  )
}

function MeetingRow({ meeting, words }: { meeting: HistoryMeeting; words: string[] }) {
  return (
    <li className={`${pageStyles.row} ${styles.meeting}`}>
      <Link className={styles.date} to={`/meetings/${meeting.id}/minutes`}>
        {formatDeadline(meeting.date)}
      </Link>
      {meeting.decisions.length > 0 ? (
        <ul className={styles.decisions} aria-label="Decisions">
          {meeting.decisions.map((decision, index) => (
            <li key={index}>
              <Highlighted text={decision} words={words} />
            </li>
          ))}
        </ul>
      ) : (
        <span className={pageStyles.meta}>No decisions</span>
      )}
      <span className={`${pageStyles.meta} ${styles.count}`}>
        {countText(meeting.decisions.length, 'decision')} · {countText(meeting.taskCount, 'task')}
      </span>
    </li>
  )
}

function countText(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? '' : 's'}`
}

function wordsIn(search: string): string[] {
  return [...search.toLocaleLowerCase().matchAll(WORD)].map(([word]) => word)
}

// Marks the start of each word that a search word begins, as the server matches them.
function Highlighted({ text, words }: { text: string; words: string[] }) {
  if (words.length === 0) {
    return text
  }
  const parts: ReactNode[] = []
  let shown = 0
  for (const match of text.matchAll(WORD)) {
    const lower = match[0].toLocaleLowerCase()
    const longest = Math.max(
      0,
      ...words.filter((word) => lower.startsWith(word)).map((w) => w.length),
    )
    if (longest > 0) {
      parts.push(
        text.slice(shown, match.index),
        <mark key={match.index} className={styles.mark}>
          {match[0].slice(0, longest)}
        </mark>,
      )
      shown = match.index + longest
    }
  }
  parts.push(text.slice(shown))
  return parts
}
