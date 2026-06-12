-- Legacy databases may still have role.name defined as an ENUM that does not include SYSTEM.
-- This causes "Data truncated for column 'name'" when the monitoring ticket flow creates the
-- internal SYSTEM role for automatic ticketing and notifications.

ALTER TABLE role
    MODIFY COLUMN name VARCHAR(50) NOT NULL;
