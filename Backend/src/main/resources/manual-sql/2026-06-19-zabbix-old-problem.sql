CREATE TABLE IF NOT EXISTS zabbix_old_problem (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    problem_id VARCHAR(255) NOT NULL,
    host_id BIGINT NOT NULL,
    host VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    ip VARCHAR(255),
    port INTEGER,
    source VARCHAR(64) NOT NULL DEFAULT 'Zabbix',
    event_id BIGINT NOT NULL,
    started_at BIGINT NOT NULL,
    resolved_at BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN'
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_zabbix_old_problem_event_id
    ON zabbix_old_problem (event_id);
