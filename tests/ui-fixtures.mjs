import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';

const base = process.env.MUAYTHAI_TEST_URL || 'http://127.0.0.1:8180';
assert.equal(new URL(base).hostname, '127.0.0.1');
assert.notEqual(new URL(base).port, '8080', 'UI fixtures require a disposable QA stack');
const output = process.env.MUAYTHAI_UI_FIXTURE_OUT;
assert.ok(output?.replaceAll('\\', '/').startsWith('android/app/build/'), 'Store credentials only in ignored build output');
const settings = Object.fromEntries(readFileSync('deploy/.env', 'utf8').split(/\r?\n/)
  .filter(line => line.startsWith('DEMO_ADMIN_PASSWORD='))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const run = randomUUID().slice(0, 8);

async function api(path, token, method = 'GET', body, expected = 200) {
  const response = await fetch(base + path, {
    method, signal: AbortSignal.timeout(20000),
    headers: { ...(token ? { Authorization: 'Bearer ' + token } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (response.status !== expected) throw Error(method + ' ' + path + ' HTTP ' + response.status);
  return response.status === 204 ? null : response.json();
}
const login = (username, password) => api('/api/auth/login', null, 'POST', { username, password });
function day(value) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en', { timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(value).map(part => [part.type, part.value]));
  return `${parts.year}-${parts.month}-${parts.day}`;
}

const admin = await login('admin@example.test', settings.DEMO_ADMIN_PASSWORD);
const accounts = { ADMIN: { username: 'admin@example.test', password: settings.DEMO_ADMIN_PASSWORD,
  token: admin.token, id: admin.user.id } };
for (const [directory, role, displayName] of [['trainers', 'TRAINER', 'HLV Minh Quân QA'],
  ['learners', 'LEARNER', 'Nguyễn Minh An QA'], ['receptionists', 'RECEPTION', 'Lễ tân Thanh QA']]) {
  const username = role.toLowerCase() + '-ui-' + run + '@example.test';
  const created = await api('/api/' + directory, admin.token, 'POST', { username, displayName }, 201);
  let session = await login(username, created.initialPassword);
  const password = randomUUID();
  session = await api('/api/auth/change-password', session.token, 'POST', {
    currentPassword: created.initialPassword, newPassword: password,
  });
  accounts[role] = { username, password, token: session.token, id: session.user.id };
}
const coach = accounts.TRAINER;
const learner = accounts.LEARNER;
await api('/api/shifts/' + coach.id, admin.token, 'PUT', {
  shifts: [1, 2, 3, 4, 5, 6, 7].map(weekday => ({ weekday, startsAt: '00:00', endsAt: '23:59' })),
});
const startDate = day(new Date(Date.now() - 86400000));
const endDate = day(new Date(Date.now() + 30 * 86400000));
const pack = await api('/api/packages', admin.token, 'POST', { learnerId: learner.id,
  trainerId: coach.id, type: 'PT', totalSessions: 10, startDate, endDate }, 201);
const confirmed = await api('/api/pt/bookings', learner.token, 'POST', {
  packageId: pack.id, startAt: new Date(Date.now() + 10 * 60000).toISOString(),
}, 201);
await api('/api/pt/' + confirmed.id + '/confirm', coach.token, 'POST', {});
const checkin = await fetch(base + '/api/checkins/confirm', { method: 'POST',
  headers: { Authorization: 'Bearer ' + accounts.RECEPTION.token, 'Content-Type': 'application/json',
    'Idempotency-Key': randomUUID() }, signal: AbortSignal.timeout(20000),
  body: JSON.stringify({ sessionId: confirmed.id, packageId: pack.id, method: 'QR' }),
});
assert.equal(checkin.status, 201, 'QA check-in');
await api('/api/pt/bookings', learner.token, 'POST', {
  packageId: pack.id, startAt: new Date(Date.now() + 90 * 60000).toISOString(),
}, 201);
await api('/api/packages', admin.token, 'POST', { learnerId: learner.id, type: 'MEMBERSHIP', startDate, endDate }, 201);
writeFileSync(output, JSON.stringify({ accounts, sessionId: confirmed.id, packageId: pack.id }, null, 2));
console.log('PASS isolated UI fixtures for four roles, PT, pending request, packages and recent check-in');
