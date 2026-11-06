-- minutes module: the meeting history lists a team's minutes (MF-23). See docs/data-model.md.

CREATE INDEX minutes_team_idx ON minutes (team_id);
