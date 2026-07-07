-- PostgreSQL manual migration for deduplicating monitoring tickets
-- and enforcing uniqueness on (monitoring_source, external_problem_id).
--
-- Safe for tickets without external_problem_id:
-- PostgreSQL UNIQUE constraints allow multiple NULL values, so manual tickets
-- or legacy rows with external_problem_id IS NULL remain allowed.

BEGIN;

CREATE TEMP TABLE ticket_duplicate_pairs AS
SELECT id AS duplicate_id,
       MIN(id) OVER (PARTITION BY monitoring_source, external_problem_id) AS keeper_id
FROM tickets
WHERE monitoring_source IS NOT NULL
  AND external_problem_id IS NOT NULL;

DELETE FROM ticket_duplicate_pairs
WHERE duplicate_id = keeper_id;

-- Re-attach interventions to the kept ticket before removing duplicates.
UPDATE interventions i
SET ticket_id = d.keeper_id
FROM ticket_duplicate_pairs d
WHERE i.ticket_id = d.duplicate_id;

-- If both the kept ticket and a duplicate already have a chat room,
-- keep the oldest room on the kept ticket and delete the conflicting duplicate room.
DELETE FROM chat_rooms cr
USING ticket_duplicate_pairs d,
      chat_rooms keeper_room
WHERE cr.ticket_id = d.duplicate_id
  AND keeper_room.ticket_id = d.keeper_id;

-- Otherwise move the remaining duplicate chat rooms to the kept ticket.
UPDATE chat_rooms cr
SET ticket_id = d.keeper_id
FROM ticket_duplicate_pairs d
WHERE cr.ticket_id = d.duplicate_id;

-- Remove duplicated tickets while keeping the oldest id for each pair.
DELETE FROM tickets t
USING ticket_duplicate_pairs d
WHERE t.id = d.duplicate_id;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_tickets_monitoring_source_external_problem'
    ) THEN
        ALTER TABLE tickets
            ADD CONSTRAINT uk_tickets_monitoring_source_external_problem
            UNIQUE (monitoring_source, external_problem_id);
    END IF;
END
$$;

COMMIT;
