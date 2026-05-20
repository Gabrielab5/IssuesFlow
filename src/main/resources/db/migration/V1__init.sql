-- =============================================================================
-- V1__init.sql  –  IssueFlow initial schema
-- =============================================================================

-- ---------------------------------------------------------------------------
-- USERS  (hard-delete only – no @SQLDelete on the entity)
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255),
    role          VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT chk_users_role    CHECK  (role IN ('ADMIN', 'DEVELOPER'))
);

-- ---------------------------------------------------------------------------
-- PROJECTS  (soft-deletable)
-- ---------------------------------------------------------------------------
CREATE TABLE projects (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id    BIGINT       NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_projects_owner_id  ON projects (owner_id);
CREATE INDEX idx_projects_active    ON projects (id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- TICKETS  (soft-deletable, optimistic-lock via version)
-- ---------------------------------------------------------------------------
CREATE TABLE tickets (
    id          BIGSERIAL    PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    priority    VARCHAR(20)  NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    project_id  BIGINT       NOT NULL REFERENCES projects (id),
    assignee_id BIGINT                REFERENCES users (id),
    due_date    TIMESTAMPTZ,
    is_overdue  BOOLEAN      NOT NULL DEFAULT FALSE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT chk_tickets_status   CHECK (status   IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE')),
    CONSTRAINT chk_tickets_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_tickets_type     CHECK (type     IN ('BUG', 'FEATURE', 'TECHNICAL'))
);

CREATE INDEX idx_tickets_project_deleted ON tickets (project_id, deleted_at);
CREATE INDEX idx_tickets_assignee        ON tickets (assignee_id) WHERE assignee_id IS NOT NULL;
CREATE INDEX idx_tickets_status_active   ON tickets (status)      WHERE deleted_at IS NULL;
CREATE INDEX idx_tickets_due_date        ON tickets (due_date)    WHERE deleted_at IS NULL AND due_date IS NOT NULL;

-- ---------------------------------------------------------------------------
-- COMMENTS  (no soft-delete per spec, optimistic-lock via version)
-- ---------------------------------------------------------------------------
CREATE TABLE comments (
    id         BIGSERIAL   PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES tickets (id),
    author_id  BIGINT      NOT NULL REFERENCES users   (id),
    content    TEXT        NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_comments_ticket_id ON comments (ticket_id);
CREATE INDEX idx_comments_author_id ON comments (author_id);

-- ---------------------------------------------------------------------------
-- COMMENT MENTIONS  (no soft-delete)
-- ---------------------------------------------------------------------------
CREATE TABLE comment_mentions (
    id                BIGSERIAL   PRIMARY KEY,
    comment_id        BIGINT      NOT NULL REFERENCES comments (id),
    mentioned_user_id BIGINT      NOT NULL REFERENCES users    (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT uq_comment_mentions UNIQUE (comment_id, mentioned_user_id)
);

CREATE INDEX idx_comment_mentions_user ON comment_mentions (mentioned_user_id);

-- ---------------------------------------------------------------------------
-- TICKET DEPENDENCIES  (no soft-delete, no self-dependency)
-- ---------------------------------------------------------------------------
CREATE TABLE ticket_dependencies (
    id            BIGSERIAL   PRIMARY KEY,
    ticket_id     BIGINT      NOT NULL REFERENCES tickets (id),
    blocked_by_id BIGINT      NOT NULL REFERENCES tickets (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT uq_ticket_dependencies    UNIQUE (ticket_id, blocked_by_id),
    CONSTRAINT chk_no_self_dependency    CHECK  (ticket_id <> blocked_by_id)
);

CREATE INDEX idx_ticket_deps_ticket     ON ticket_dependencies (ticket_id);
CREATE INDEX idx_ticket_deps_blocked_by ON ticket_dependencies (blocked_by_id);

-- ---------------------------------------------------------------------------
-- ATTACHMENTS  (no soft-delete per spec)
-- ---------------------------------------------------------------------------
CREATE TABLE attachments (
    id           BIGSERIAL    PRIMARY KEY,
    ticket_id    BIGINT       NOT NULL REFERENCES tickets (id),
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(127) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ
);

CREATE INDEX idx_attachments_ticket ON attachments (ticket_id);

-- ---------------------------------------------------------------------------
-- AUDIT LOGS  (append-only; performedBy / entityId are bare IDs, no FK)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id           BIGSERIAL   PRIMARY KEY,
    action       VARCHAR(30) NOT NULL,
    entity_type  VARCHAR(50) NOT NULL,
    entity_id    BIGINT,
    performed_by BIGINT,
    actor        VARCHAR(10) NOT NULL,
    payload      JSONB,
    timestamp    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT chk_audit_action CHECK (action IN ('CREATE','UPDATE','DELETE','RESTORE','AUTO_ASSIGN','AUTO_ESCALATE','LOGIN','LOGOUT')),
    CONSTRAINT chk_audit_actor  CHECK (actor  IN ('USER','SYSTEM'))
);

CREATE INDEX idx_audit_timestamp  ON audit_logs (timestamp DESC);
CREATE INDEX idx_audit_entity     ON audit_logs (entity_type, entity_id) WHERE entity_id    IS NOT NULL;
CREATE INDEX idx_audit_performer  ON audit_logs (performed_by)           WHERE performed_by IS NOT NULL;

-- ---------------------------------------------------------------------------
-- REVOKED TOKENS  (JWT deny-list)
-- ---------------------------------------------------------------------------
CREATE TABLE revoked_tokens (
    id         BIGSERIAL    PRIMARY KEY,
    jti        VARCHAR(36)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_revoked_tokens_jti UNIQUE (jti)
);

CREATE INDEX idx_revoked_tokens_expires ON revoked_tokens (expires_at);
