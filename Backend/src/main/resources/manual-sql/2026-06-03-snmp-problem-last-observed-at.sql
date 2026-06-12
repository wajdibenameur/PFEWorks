ALTER TABLE snmp_problem
    ADD COLUMN IF NOT EXISTS last_observed_at BIGINT NULL;
