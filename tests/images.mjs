import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';

const base = process.env.MUAYTHAI_TEST_URL || 'http://127.0.0.1:8180';
assert.equal(new URL(base).hostname, '127.0.0.1', 'Image tests require a local QA server');
assert.notEqual(new URL(base).port, '8080', 'Use an isolated QA server');
const settings = Object.fromEntries(readFileSync('deploy/.env', 'utf8').split(/\r?\n/)
  .filter(line => line.startsWith('DEMO_ADMIN_PASSWORD='))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const run = randomUUID().slice(0, 8);
const image = readFileSync(process.env.MUAYTHAI_TEST_IMAGE || new URL('./fixtures/course.jpg', import.meta.url));

async function request(method, path, token, body, expected = 200) {
  const headers = {};
  if (token) headers.Authorization = 'Bearer ' + token;
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const response = await fetch(base + path, {
    method, headers, body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(20000),
  });
  if (response.status !== expected) {
    const error = await response.json().catch(() => ({}));
    assert.fail(method + ' ' + path + ' HTTP ' + response.status + ' ' + (error.code || '') + ' ' + (error.message || ''));
  }
  return response;
}

async function login(username, password) {
  return (await request('POST', '/api/auth/login', null, { username, password })).json();
}

async function upload(path, token, bytes = image, expected = 200) {
  const form = new FormData();
  form.append('file', new Blob([bytes], { type: 'image/jpeg' }), 'photo.jpg');
  return request('PUT', path, token, form, expected);
}

async function main() {
  const admin = await login('admin@example.test', settings.DEMO_ADMIN_PASSWORD);
  const accounts = { ADMIN: { username: 'admin@example.test', password: settings.DEMO_ADMIN_PASSWORD,
    token: admin.token, id: admin.user.id } };
  for (const [directory, role] of [['learners', 'LEARNER'], ['trainers', 'TRAINER'], ['receptionists', 'RECEPTION']]) {
    const username = role.toLowerCase() + '-images-' + run + '@example.test';
    const created = await (await request('POST', '/api/' + directory, admin.token,
      { username, displayName: 'Ảnh thử ' + role }, 201)).json();
    let session = await login(username, created.initialPassword);
    const password = randomUUID();
    session = await (await request('POST', '/api/auth/change-password', session.token,
      { currentPassword: created.initialPassword, newPassword: password })).json();
    accounts[role] = { username, password, token: session.token, id: session.user.id };
  }
  const learner = accounts.LEARNER;
  const trainer = accounts.TRAINER;
  await request('GET', '/api/users/me/image', null, undefined, 401);
  for (const account of Object.values(accounts)) {
    await request('DELETE', '/api/users/me/image', account.token, undefined, 204);
    await request('GET', '/api/users/me/image', account.token, undefined, 404);
    await upload('/api/users/me/image', account.token);
    const response = await request('GET', '/api/users/me/image', account.token);
    assert.match(response.headers.get('content-type'), /^image\/jpeg/);
    assert.ok(response.headers.get('etag'));
    const first = new Uint8Array(await response.arrayBuffer());
    assert.equal(first[0], 255);
    assert.equal(first[1], 216);
    const version = response.headers.get('etag');
    await upload('/api/users/me/image', account.token);
    assert.notEqual((await request('GET', '/api/users/me/image', account.token)).headers.get('etag'), version);
    await request('DELETE', '/api/users/me/image', account.token, undefined, 204);
    await request('DELETE', '/api/users/me/image', account.token, undefined, 204);
    await request('GET', '/api/users/me/image', account.token, undefined, 404);
    await upload('/api/users/me/image', account.token);
  }
  console.log('PASS avatar create, replace, version, read and idempotent delete for four roles');
  await upload('/api/users/' + trainer.id + '/image', learner.token, image, 403);
  await request('DELETE', '/api/users/' + admin.user.id + '/image', accounts.RECEPTION.token, undefined, 403);
  await request('GET', '/api/users/' + learner.id + '/image', accounts.RECEPTION.token);
  await request('GET', '/api/users/' + trainer.id + '/image', admin.token);
  await upload('/api/users/me/image', learner.token, Buffer.from('not an image'), 415);
  await upload('/api/users/me/image', learner.token, Buffer.alloc(5 * 1024 * 1024 + 1), 413);
  const hugeDimensions = Buffer.from(image);
  const frame = hugeDimensions.indexOf(Buffer.from([255, 192]));
  assert.ok(frame > 0, 'JPEG fixture must contain a baseline frame');
  hugeDimensions.writeUInt16BE(20000, frame + 5);
  hugeDimensions.writeUInt16BE(20000, frame + 7);
  await upload('/api/users/me/image', learner.token, hugeDimensions, 413);
  console.log('PASS oversized uploads and decompression dimensions are rejected');
  await upload('/api/users/' + admin.user.id + '/image', learner.token, image, 403);
  await request('GET', '/api/users/' + admin.user.id + '/image', learner.token, undefined, 403);
  await upload('/api/users/me/image', learner.token, Buffer.from('not an image'), 415);
  await upload('/api/users/me/image', learner.token, image.subarray(0, 25), 400);
  await upload('/api/users/me/image', learner.token, Buffer.alloc(6 * 1024 * 1024), 413);
  for (const type of ['PT', 'GROUP10', 'GROUP30', 'MEMBERSHIP']) {
    const path = '/api/packages/types/' + type + '/image';
    await upload(path, learner.token, image, 403);
    await upload(path, admin.token);
    await request('GET', path, learner.token);
    await request('GET', path, accounts.RECEPTION.token);
  }
  await upload('/api/packages/types/UNKNOWN/image', admin.token, image, 404);
  console.log('PASS package images and unauthorized or malformed upload rejection');
  const today = new Date().toISOString().slice(0, 10);
  const endDate = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
  await request('PUT', '/api/shifts/' + trainer.id, admin.token, {
    shifts: [1, 2, 3, 4, 5, 6, 7].map(weekday => ({ weekday, startsAt: '08:00', endsAt: '21:00' })),
  });
  await request('POST', '/api/packages', admin.token, {
    learnerId: learner.id, type: 'MEMBERSHIP', startDate: today, endDate,
  }, 201);
  const group = await (await request('POST', '/api/classes30', admin.token, {
    title: 'Muay Thai căn bản ' + run, trainerId: trainer.id, weekdays: [1, 3, 5],
    startsAt: '17:00', endsAt: '18:00',
  }, 201)).json();
  await request('POST', '/api/packages', admin.token, {
    learnerId: learner.id, type: 'GROUP30', classId: group.id, totalSessions: 12,
    startDate: today, endDate,
  }, 201);
  const classPath = '/api/classes30/' + group.id + '/image';
  await upload(classPath, trainer.token, image, 403);
  await upload(classPath, admin.token);
  await request('GET', classPath, learner.token);
  await request('GET', classPath, trainer.token);
  await request('DELETE', classPath, admin.token, undefined, 204);
  await request('GET', classPath, learner.token, undefined, 404);
  await upload(classPath, admin.token);
  console.log('PASS class image access follows existing class permissions');
  if (process.env.MUAYTHAI_IMAGE_FIXTURE_OUT) {
    writeFileSync(process.env.MUAYTHAI_IMAGE_FIXTURE_OUT, JSON.stringify({ accounts, classId: group.id }, null, 2));
  }
  await request('POST', '/api/auth/logout', learner.token, undefined, 204);
  await request('GET', '/api/users/me/image', learner.token, undefined, 401);
  console.log('PASS revoked sessions cannot read protected images');
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
