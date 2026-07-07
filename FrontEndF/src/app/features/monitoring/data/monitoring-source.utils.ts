import { MonitoringProblem } from '../../../core/models/monitoring-problem.model';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import { ZabbixProblem } from '../../../core/models/zabbix-problem.model';

export function matchesMonitoringSource(
  source: string | null | undefined,
  expectedSource: string
): boolean {
  const actual = normalizeMonitoringSource(source);
  const expected = normalizeMonitoringSource(expectedSource);
  return actual === expected;
}

function normalizeMonitoringSource(source: string | null | undefined): string {
  const normalized = (source ?? '').toUpperCase();
  return normalized === 'SNMP' ? 'SNMP' : normalized;
}

export function toZabbixProblem(problem: MonitoringProblem): ZabbixProblem {
  return {
    problemId: problem.problemId ?? problem.id,
    host: problem.hostName ?? problem.hostId ?? 'UNKNOWN',
    port: problem.port ?? null,
    hostId: problem.hostId ?? null,
    description: problem.description ?? 'No description',
    severity: problem.severity ?? 'UNKNOWN',
    active: problem.active,
    source: problem.source,
    eventId: problem.eventId ?? 0,
    ip: problem.ip ?? null,
    startedAt: problem.startedAt ?? null,
    startedAtFormatted: problem.startedAtFormatted ?? null,
    resolvedAt: problem.resolvedAt ?? null,
    resolvedAtFormatted: problem.resolvedAtFormatted ?? null,
    status: problem.status ?? (problem.active ? 'ACTIVE' : 'RESOLVED')
  };
}

export function findSourceAvailability(
  entries: SourceAvailability[],
  source: string
): SourceAvailability | null {
  return entries.find((entry) => matchesMonitoringSource(entry.source, source)) ?? null;
}

