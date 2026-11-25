-- migration 0002: tasks and action_items tables with sync_id UUIDs

-- Drop old tables (if they exist)
DROP TABLE IF EXISTS action_items;
DROP TABLE IF EXISTS tasks;

-- Create new tables with UUID sync_id and proper timestamps
CREATE TABLE tasks (
    id TEXT PRIMARY KEY,                    -- sync_id (UUID)
    task_name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    priority INTEGER NOT NULL,
    context TEXT NOT NULL DEFAULT 'No Context',
    achieved INTEGER NOT NULL DEFAULT 0,
    creation_time TEXT NOT NULL,              -- ISO-8601 UTC timestamp
    updated_at INTEGER NOT NULL,              -- client-supplied version for LWW
    deleted_at INTEGER,                       -- tombstone
    server_updated_at INTEGER NOT NULL,       -- monotonic per-user download stamp
    origin_device TEXT NOT NULL DEFAULT 'server'  -- LWW tie-break
);

CREATE TABLE action_items (
    id TEXT PRIMARY KEY,                    -- sync_id (UUID)
    task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,  -- parent syncId
    description TEXT NOT NULL,
    achieved INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,             -- client-supplied version for LWW
    deleted_at INTEGER,                       -- tombstone
    server_updated_at INTEGER NOT NULL,       -- monotonic per-user download stamp
    origin_device TEXT NOT NULL DEFAULT 'server'  -- LWW tie-break
);

-- Indexes for performance
CREATE UNIQUE INDEX ix_tasks_sync_id ON tasks(sync_id);
CREATE INDEX ix_tasks_deleted ON tasks(deleted_at);
CREATE INDEX ix_tasks_server ON tasks(server_updated_at, id);

CREATE UNIQUE INDEX ix_action_items_sync_id ON action_items(sync_id);
CREATE INDEX ix_action_items_deleted ON action_items(deleted_at);
CREATE INDEX ix_action_items_task ON action_items(task_id);
CREATE INDEX ix_action_items_server ON action_items(server_updated_at, id);
CREATE INDEX ix_action_items_task ON action_items(task_id);

-- idempotency keys
CREATE TABLE idempotency_keys (
    key TEXT PRIMARY KEY,
    response_json TEXT NOT NULL,
    created_at INTEGER NOT NULL
);