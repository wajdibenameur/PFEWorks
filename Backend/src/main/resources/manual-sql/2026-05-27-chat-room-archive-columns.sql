-- Manual migration for chat room archive support.
ALTER TABLE chat_rooms
    ADD COLUMN IF NOT EXISTS archived BIT(1) NOT NULL DEFAULT b'0',
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP NULL;

-- Compatibility migration:
-- Some environments still have status as ENUM('OPEN','LOCKED'),
-- which fails when persisting CLOSED.
-- Convert status to VARCHAR and normalize legacy LOCKED values.
ALTER TABLE chat_rooms
    MODIFY COLUMN status VARCHAR(20) NOT NULL;

UPDATE chat_rooms
SET status = 'CLOSED'
WHERE status = 'LOCKED';

CREATE INDEX IF NOT EXISTS idx_chat_rooms_status_closed_at
    ON chat_rooms (status, closed_at);

CREATE INDEX IF NOT EXISTS idx_chat_rooms_status_archived_at
    ON chat_rooms (status, archived_at);
