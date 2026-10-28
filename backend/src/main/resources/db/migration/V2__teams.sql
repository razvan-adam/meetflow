-- teams module: teams, memberships and invitations. See docs/data-model.md.

CREATE TABLE team (
    id         uuid        PRIMARY KEY,
    name       text        NOT NULL CHECK (char_length(name) <= 100),
    created_by uuid        NOT NULL REFERENCES app_user (id),
    created_at timestamptz NOT NULL
);

CREATE TABLE team_membership (
    team_id   uuid        NOT NULL REFERENCES team (id),
    user_id   uuid        NOT NULL REFERENCES app_user (id),
    role      text        NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
    joined_at timestamptz NOT NULL,
    PRIMARY KEY (team_id, user_id)
);

-- A user's teams are looked up on every request for access control.
CREATE INDEX team_membership_user_idx ON team_membership (user_id);

CREATE TABLE invitation (
    id          uuid        PRIMARY KEY,
    team_id     uuid        NOT NULL REFERENCES team (id),
    email       text        NOT NULL CHECK (email = lower(email) AND char_length(email) <= 254),
    token_hash  text        NOT NULL UNIQUE,
    invited_by  uuid        NOT NULL REFERENCES app_user (id),
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    accepted_at timestamptz,
    accepted_by uuid        REFERENCES app_user (id),
    CHECK ((accepted_at IS NULL) = (accepted_by IS NULL))
);

CREATE INDEX invitation_team_idx ON invitation (team_id);
