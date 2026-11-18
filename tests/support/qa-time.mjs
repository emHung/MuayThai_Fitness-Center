import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';

// Book through the real API first, then prepare the check-in window only in disposable QA data.
// A one-hour booking near midnight would otherwise depend on the trainer's daily shift boundary.
export function requireDisposableQa(base, project) {
  const endpoint = new URL(base);
  assert.equal(endpoint.hostname, '127.0.0.1', 'Time fixtures require localhost');
  assert.ok(endpoint.port && endpoint.port !== '8080', 'Never adjust demo attendance data');
  assert.ok(['khang-acceptance', 'khang-handoff', 'khang-coverage', 'muaythai-qa'].includes(project)
    || /^khang-qa-[a-z0-9]+$/.test(project || ''), 'Set the disposable QA Compose project');
}

export function prepareAttendanceWindow(sessionId, base, project) {
  requireDisposableQa(base, project);
  assert.ok(Number.isSafeInteger(sessionId) && sessionId > 0);
  execFileSync('docker', ['compose', '--env-file', 'deploy/.env', '-p', project, '-f', 'deploy/compose.yaml',
    'exec', '-T', 'db', 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', 'auth_app', '-d', 'schedule_db', '-c',
    `UPDATE sessions SET start_at=now()+interval '10 minutes',end_at=now()+interval '70 minutes' WHERE id=${sessionId}`],
  { timeout: 20000, stdio: 'pipe' });
}
