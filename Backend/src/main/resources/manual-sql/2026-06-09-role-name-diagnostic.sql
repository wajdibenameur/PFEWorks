-- Diagnostic helper for the legacy role.name issue.
-- Run this before or after the compatibility migration to verify the column type.

SELECT
    TABLE_NAME,
    COLUMN_NAME,
    COLUMN_TYPE,
    DATA_TYPE,
    CHARACTER_MAXIMUM_LENGTH,
    IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'role'
  AND COLUMN_NAME = 'name';

SELECT id, name
FROM role
ORDER BY id;
