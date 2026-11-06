-- minutes module: the meeting history pages through a team's minutes, newest meeting first
-- (MF-23). The meeting date is ISO text in the JSON, which sorts like the date. A backward scan of
-- this index serves each page. See docs/data-model.md.

CREATE INDEX minutes_history_idx ON minutes (team_id, (content ->> 'meetingDate'), generated_at, id);
