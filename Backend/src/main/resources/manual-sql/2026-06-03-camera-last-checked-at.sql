ALTER TABLE camera_list
    ADD COLUMN IF NOT EXISTS last_checked_at DATETIME NULL;
