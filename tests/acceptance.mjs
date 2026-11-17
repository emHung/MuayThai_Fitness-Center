import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

const settings = Object.fromEntries(readFileSync(new URL('../deploy/.env', import.meta.url), 'utf8')
  .split(/\r?\n/).filter(line => /^[A-Z_]+=/.test(line))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const base = process.env.MUAYTHAI_TEST_URL || 'http://127.0.0.1:8080';
const run = randomUUID().slice(0, 8);
const localDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });
const date = instant => localDate.format(instant);
const later = (minutes = 0) => new Date(Date.now() + minutes * 60000);

async function request(method, path, { token, body, key, status = 200, code } = {}) {
  const result = await fetch(base + path, {
    method,
    headers: {
      ...(token && { Authorization: 'Bearer ' + token }),
      ...(key && { 'Idempotency-Key': key }),
      ...(body !== undefined && { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(20000),
  });
  const response = result.headers.get('content-type')?.includes('json') ? await result.json() : null;
  assert.equal(result.status, status, method + ' ' + path + ' returned ' + (response?.code || result.status));
  if (code) assert.equal(response?.code, code);
  return response;
}

function login(username, password) {
  return request('POST', '/api/auth/login', { body: { username, password } });
}

function create(token, directory, role, extra = {}) {
  return request('POST', '/api/' + directory, {
    token, status: 201, body: { username: role + '-' + run + '@example.test',
      displayName: 'QA ' + role + ' ' + run, ...extra },
  });
}

async function calendarStream(token, stop) {
  const response = await fetch(base + '/api/sessions/events', {
    headers: { Authorization: 'Bearer ' + token, Accept: 'text/event-stream' },
    signal: AbortSignal.any([stop.signal, AbortSignal.timeout(20000)]),
  });
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /text\/event-stream/);
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffered = '';
  return async function expectEvent(event) {
    while (true) {
      const end = buffered.indexOf('\n\n');
      if (end !== -1) {
        const current = buffered.slice(0, end);
        buffered = buffered.slice(end + 2);
        if (current.split('\n').some(line => /^event:\s*/.test(line) && line.split(':')[1].trim() === event)) return;
        continue;
      }
      const chunk = await reader.read();
      assert.equal(chunk.done, false, 'Luồng lịch kết thúc trước sự kiện ' + event);
      buffered += decoder.decode(chunk.value, { stream: true });
      buffered = buffered.replaceAll('\r\n', '\n');
    }
  };
}

async function main() {
  assert.equal((await request('GET', '/actuator/health')).status, 'UP');
  const { token: admin } = await login('admin@example.test', settings.DEMO_ADMIN_PASSWORD);
  const temporaryPassword = randomUUID();
  const [learner, trainer] = await Promise.all([
    create(admin, 'learners', 'learner'), create(admin, 'trainers', 'trainer', { temporaryPassword }),
  ]);
  assert.match(learner.profile.memberCode, /^HV/);
  const { token: student } = await login(learner.profile.username, learner.initialPassword);
  const unused = await create(admin, 'trainers', 'unused');
  const removal = '/api/trainers/' + unused.profile.id;
  const unusedSession = await login(unused.profile.username, unused.initialPassword);
  await request('DELETE', removal, { token: student, status: 403 });
  await request('DELETE', removal, { token: admin, status: 204 });
  await request('DELETE', removal, { token: admin, status: 204 });
  await request('GET', removal, { token: admin, status: 404 });
  await request('POST', '/api/auth/login', { status: 401, code: 'BAD_CREDENTIALS',
    body: { username: unused.profile.username, password: unused.initialPassword } });
  await request('GET', '/api/users/me', { token: unusedSession.token, status: 401 });
  const recreated = await create(admin, 'trainers', 'unused');
  assert.notEqual(recreated.profile.id, unused.profile.id);

  const racing = await create(admin, 'trainers', 'racing');
  const firstShift = { shifts: [{ weekday: 1, startsAt: '09:00', endsAt: '17:00' }] };
  const adminHeaders = { Authorization: 'Bearer ' + admin, 'Content-Type': 'application/json' };
  const outcomes = await Promise.all([
    fetch(base + '/api/trainers/' + racing.profile.id, {
      method: 'DELETE', headers: adminHeaders, signal: AbortSignal.timeout(20000),
    }),
    fetch(base + '/api/shifts/' + racing.profile.id, {
      method: 'PUT', headers: adminHeaders, body: JSON.stringify(firstShift), signal: AbortSignal.timeout(20000),
    }),
  ]);
  assert.ok(outcomes[0].status === 204 && outcomes[1].status === 404
    || outcomes[0].status === 409 && outcomes[1].status === 200,
  'Chỉ một thao tác xóa hoặc đặt ca đầu tiên được thành công.');

  const temporary = await login(trainer.profile.username, temporaryPassword);
  assert.equal(temporary.mustChangePassword, true);
  await request('GET', '/api/sessions', { token: temporary.token, status: 403, code: 'PASSWORD_CHANGE_REQUIRED' });
  const { token: coach } = await request('POST', '/api/auth/change-password', {
    token: temporary.token, body: { currentPassword: trainer.initialPassword, newPassword: randomUUID() },
  });

  await request('PUT', '/api/shifts/' + trainer.profile.id, { token: admin,
    body: { shifts: [1,2,3,4,5,6,7].map(weekday => ({weekday, startsAt: '00:00', endsAt: '23:59'})) } });
  await request('DELETE', '/api/trainers/' + trainer.profile.id, {
    token: admin, status: 409, code: 'TRAINER_HAS_DATA',
  });

  const pt = { learnerId: learner.profile.id, trainerId: trainer.profile.id, type: 'PT',
    totalSessions: 5, startDate: date(later(-1440)), endDate: date(later(43200)) };
  const [pack, secondPack] = await Promise.all([
    request('POST', '/api/packages', { token: admin, body: pt, status: 201 }),
    request('POST', '/api/packages', { token: admin, body: pt, status: 201 }),
  ]);
  assert.notEqual(pack.code, secondPack.code);
  const upcoming = later(10);
  const appointment = await request('POST', '/api/pt/bookings', {
    token: student, body: { packageId: pack.id, startAt: upcoming.toISOString() }, status: 201,
  });
  await request('POST', '/api/pt/bookings', {
    token: student, body: { packageId: secondPack.id, startAt: upcoming.toISOString() },
    status: 409, code: 'SCHEDULE_CONFLICT',
  });
  await request('POST', '/api/pt/' + appointment.id + '/confirm', { token: coach, body: {} });
  await request('POST', '/api/checkins/preview', {
    token: admin, body: { code: secondPack.code, codeKind: 'QR' }, status: 409, code: 'WRONG_PACKAGE',
  });
  const preview = await request('POST', '/api/checkins/preview', {
    token: admin, body: { code: pack.code, codeKind: 'QR' },
  });
  assert.equal(preview.session.id, appointment.id);
  const key = randomUUID();
  const body = { sessionId: appointment.id, packageId: pack.id, method: 'QR' };
  const calls = await Promise.all([1, 2].map(() => request('POST', '/api/checkins/confirm', {
    token: admin, key, body, status: 201,
  })));
  const entry = calls[0];
  assert.equal(entry.id, calls[1].id);
  assert.equal(entry.remainingAfterEvent, 4);
  await request('POST', '/api/checkins/confirm', {
    token: admin, key: randomUUID(), body, status: 409, code: 'ALREADY_CHECKED_IN',
  });
  await request('POST', '/api/sessions/' + appointment.id + '/cancel', {
    token: admin, body: { reason: 'QA thử hủy sau điểm danh' }, status: 409, code: 'CHECKIN_EXISTS',
  });
  const adjustKey = randomUUID();
  const adjustments = await Promise.all([1, 2].map(() => request('POST', '/api/checkins/' + entry.id + '/adjustments', {
    token: admin, key: adjustKey, body: { reason: 'QA kiểm thử hoàn lượt' }, status: 201,
  })));
  assert.equal(adjustments[0].id, adjustments[1].id);
  assert.equal(adjustments[0].remainingAfter, 5);
  const ledger = await request('GET', '/api/checkins?learnerId=' + learner.profile.id, { token: student });
  assert.equal(ledger[0].status, 'ADJUSTED');
  const packages = await request('GET', '/api/packages/mine', { token: student });
  assert.equal(packages.find(item => item.id === pack.id).remainingSessions, 5);

  const [guardLearner, guardTrainer] = await Promise.all([
    create(admin, 'learners', 'cancel-race-learner'), create(admin, 'trainers', 'cancel-race-trainer'),
  ]);
  await request('PUT', '/api/shifts/' + guardTrainer.profile.id, { token: admin,
    body: { shifts: [1,2,3,4,5,6,7].map(weekday => ({weekday, startsAt: '00:00', endsAt: '23:59'})) } });
  const guardPack = await request('POST', '/api/packages', { token: admin, status: 201,
    body: { ...pt, learnerId: guardLearner.profile.id, trainerId: guardTrainer.profile.id } });
  const [guardSession] = await request('POST', '/api/pt/direct', { token: admin, status: 201,
    body: { learnerId: guardLearner.profile.id, packageId: guardPack.id, weeks: 1, startAt: later(10).toISOString() } });
  const cancelRace = await Promise.all([
    fetch(base + '/api/sessions/' + guardSession.id + '/cancel', {
      method: 'POST', headers: adminHeaders, body: JSON.stringify({ reason: 'QA tranh chấp với điểm danh' }),
      signal: AbortSignal.timeout(20000),
    }),
    fetch(base + '/api/checkins/confirm', {
      method: 'POST', headers: { ...adminHeaders, 'Idempotency-Key': randomUUID() },
      body: JSON.stringify({ sessionId: guardSession.id, packageId: guardPack.id, method: 'QR' }),
      signal: AbortSignal.timeout(20000),
    }),
  ]);
  const canceled = cancelRace[0].status === 200;
  assert.deepEqual(cancelRace.map(item => item.status), canceled ? [200, 409] : [409, 201]);
  const afterRace = await request('GET', '/api/sessions/' + guardSession.id, { token: admin });
  const raceLedger = await request('GET', '/api/checkins?sessionId=' + guardSession.id, { token: admin });
  const racePacks = await request('GET', '/api/packages?learnerId=' + guardLearner.profile.id, { token: admin });
  assert.equal(afterRace.status, canceled ? 'CANCELLED' : 'CONFIRMED');
  assert.equal(raceLedger.length, canceled ? 0 : 1);
  assert.equal(racePacks.find(item => item.id === guardPack.id).remainingSessions, canceled ? 5 : 4);

  const member = await request('POST', '/api/packages', { token: admin, status: 201,
    body: { learnerId: learner.profile.id, type: 'MEMBERSHIP', startDate: pt.startDate, endDate: pt.endDate } });
  const walkIn = await request('POST', '/api/checkins/preview', {
    token: admin, body: { code: member.code, codeKind: 'QR' },
  });
  assert.equal(walkIn.session, null);
  const membership = { packageId: member.id, method: 'QR' };
  const freeEntry = await request('POST', '/api/checkins/confirm', {
    token: admin, status: 201, key: randomUUID(), body: membership,
  });
  assert.equal(freeEntry.remainingAfterEvent, null);
  await request('POST', '/api/checkins/confirm', {
    token: admin, status: 409, code: 'ALREADY_CHECKED_IN', key: randomUUID(), body: membership,
  });

  const tomorrow = later(1440);
  const weekday = (new Date(date(tomorrow) + 'T00:00:00Z').getUTCDay() + 6) % 7 + 1;
  const group = await request('POST', '/api/classes30', {
    token: admin, status: 201, body: { title: 'QA lớp 30 ' + run, trainerId: trainer.profile.id,
      weekdays: [weekday], startsAt: '13:00', endsAt: '14:00' },
  });
  const sessions = await request('GET', '/api/sessions', { token: coach });
  assert.ok(sessions.some(session => session.classId === group.id));
  const group10 = await request('POST', '/api/classes10', {
    token: coach, status: 201, body: { title: 'QA lớp 10 ' + run,
      weekdays: [weekday], startsAt: '15:00', endsAt: '16:00' },
  });
  assert.equal(group10.status, 'PENDING');
  await request('POST', '/api/classes10/' + group10.id + '/approve', { token: coach, status: 403 });
  assert.equal((await request('POST', '/api/classes10/' + group10.id + '/approve', { token: admin })).status, 'OPEN');
  const smallGroupPack = await request('POST', '/api/packages', {
    token: admin, status: 201, body: { ...pt, type: 'GROUP10' },
  });
  const smallGroupSessions = (await request('GET', '/api/sessions', { token: student }))
    .filter(session => session.classId === group10.id && Date.parse(session.startAt) > Date.now() + 10800000);
  const [original, replacement] = smallGroupSessions;
  assert.ok(original && replacement, 'Cần hai buổi lớp 10 ở tương lai');
  await request('PUT', '/api/sessions/' + original.id + '/signup', { token: student,
    body: { packageId: smallGroupPack.id } });
  assert.ok((await request('GET', '/api/checkins/roster?sessionId=' + original.id, { token: coach }))
    .some(row => row.learnerId === learner.profile.id && row.status === 'WAITING'));
  await request('POST', '/api/sessions/' + original.id + '/move-signup', { token: student,
    body: { packageId: smallGroupPack.id, targetSessionId: replacement.id } });
  const roster = id => request('GET', '/api/checkins/roster?sessionId=' + id, { token: coach });
  assert.equal((await roster(original.id)).length, 0);
  assert.ok((await roster(replacement.id)).some(row => row.learnerId === learner.profile.id));
  await request('PUT', '/api/checkins/roster/' + replacement.id + '/absent/' + learner.profile.id, {
    token: coach, status: 409, code: 'SESSION_NOT_ENDED',
  });
  await request('DELETE', '/api/sessions/' + replacement.id + '/signup', { token: student, status: 204 });
  assert.equal((await roster(replacement.id)).length, 0);

  const fixed = await request('PUT', '/api/classes10/' + group10.id + '/fixed/' + learner.profile.id,
    { token: admin, body: { packageId: smallGroupPack.id } });
  assert.ok(fixed.length > 0, 'Cần ít nhất một buổi đã gán theo lịch cố định.');
  assert.equal((await roster(original.id)).length, 0);
  assert.equal((await roster(replacement.id)).length, 0);
  assert.ok((await roster(fixed[0].id)).some(row => row.learnerId === learner.profile.id));

  if (process.env.MUAYTHAI_TEST_CALENDAR === '1') {
    await request('PUT', '/api/classes10/' + group10.id, {
      token: admin, body: { title: group10.title, trainerId: trainer.profile.id,
        weekdays: [weekday], startsAt: '16:30', endsAt: '17:30' },
    });
    const newSlots = await request('GET', '/api/sessions', { token: coach });
    const shifted = newSlots.find(session => session.classId === group10.id && session.status === 'CONFIRMED'
      && Date.parse(session.startAt) === Date.parse(date(tomorrow) + 'T16:30:00+07:00'));
    assert.ok(shifted, 'Cần một buổi vừa sinh từ lịch mới.');
    const deadline = Date.now() + 15000;
    let reserved = false;
    do {
      reserved = (await roster(shifted.id)).some(row => row.learnerId === learner.profile.id);
      if (!reserved) await new Promise(resolve => setTimeout(resolve, 500));
    } while (!reserved && Date.now() < deadline);
    assert.ok(reserved, 'Lịch cố định cần tự giữ chỗ cho buổi vừa sinh.');
    console.log('PASS gán lịch cố định vào buổi lớp 10 mới sinh.');
  }

  const oldTime = new Date(date(later(2880)) + 'T10:00:00+07:00');
  const newTime = new Date(date(later(2880)) + 'T11:00:00+07:00');
  const booking = await request('POST', '/api/pt/bookings', {
    token: student, status: 201, body: { packageId: secondPack.id, startAt: oldTime.toISOString() },
  });
  await request('POST', '/api/pt/' + booking.id + '/confirm', { token: coach, body: {} });
  const change = await request('POST', '/api/pt/' + booking.id + '/changes', {
    token: student, status: 201, body: { proposedStart: newTime.toISOString() },
  });
  const current = () => request('GET', '/api/sessions/' + booking.id, { token: student });
  assert.equal(Date.parse((await current()).startAt), oldTime.valueOf());
  await request('POST', '/api/pt/changes/' + change.id + '/accept', { token: coach });
  assert.equal(Date.parse((await current()).startAt), newTime.valueOf());

  const announcement = 'QA thông báo ' + run;
  await request('POST', '/api/notifications', { token: admin, status: 201,
    body: { title: announcement, message: 'Bài kiểm tra thông báo trung tâm' } });
  const inbox = () => request('GET', '/api/notifications', { token: student });
  const published = (await inbox()).find(item => item.notice.title === announcement);
  assert.equal(published?.read, false);
  await request('PUT', '/api/notifications/read?id=' + encodeURIComponent(published.notice.id), {
    token: student, status: 204,
  });
  assert.equal((await inbox()).find(item => item.notice.id === published.notice.id)?.read, true);

  if (process.env.MUAYTHAI_TEST_CALENDAR === '1' && date(later(80)) === date(later())) {
    const [routingLearner, routingTrainer] = await Promise.all([
      create(admin, 'learners', 'routing-learner'), create(admin, 'trainers', 'routing-trainer'),
    ]);
    const { token: visitor } = await login(routingLearner.profile.username, routingLearner.initialPassword);
    const trainerId = routingTrainer.profile.id;
    const todayWeekday = (new Date(date(later()) + 'T00:00:00Z').getUTCDay() + 6) % 7 + 1;
    await request('PUT', '/api/shifts/' + trainerId, {
      token: admin, body: { shifts: [{ weekday: todayWeekday, startsAt: '00:00', endsAt: '23:59' }] },
    });
    const routingPack = await request('POST', '/api/packages', { token: admin, status: 201,
      body: { ...pt, learnerId: routingLearner.profile.id, trainerId, type: 'GROUP10' } });
    const hour = new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Ho_Chi_Minh',
    });
    const openClass = (label, from, to) => request('POST', '/api/classes10', {
      token: admin, status: 201, body: { trainerId, title: label + run, weekdays: [todayWeekday],
        startsAt: hour.format(later(from)), endsAt: hour.format(later(to)) },
    });
    const [earlier, bookedClass] = await Promise.all([
      openClass('QA walk-in ', 8, 23), openClass('QA giờ được đặt ', 45, 74),
    ]);
    const choices = await request('GET', '/api/sessions', { token: visitor });
    const earlierSession = choices.find(session => session.classId === earlier.id);
    const bookedSession = choices.find(session => session.classId === bookedClass.id);
    assert.ok(earlierSession && bookedSession);
    await request('PUT', '/api/sessions/' + bookedSession.id + '/signup', {
      token: visitor, body: { packageId: routingPack.id },
    });
    const scan = { code: routingPack.code, codeKind: 'QR' };
    await request('POST', '/api/checkins/preview', { token: admin, body: scan, status: 409, code: 'NO_SESSION_NOW' });
    const confirm = { sessionId: earlierSession.id, packageId: routingPack.id, method: 'QR' };
    await request('POST', '/api/checkins/confirm', {
      token: admin, body: confirm, key: randomUUID(), status: 409, code: 'NO_SESSION_NOW',
    });
    await request('POST', '/api/sessions/' + bookedSession.id + '/cancel', {
      token: admin, body: { reason: 'QA hủy lịch để thử walk-in' },
    });
    const location = await request('POST', '/api/checkins/preview', { token: admin, body: scan });
    assert.equal(location.session.id, earlierSession.id);
    const visit = await request('POST', '/api/checkins/confirm', {
      token: admin, body: confirm, key: randomUUID(), status: 201,
    });
    assert.equal(visit.remainingAfterEvent, 4);
    const retained = await openClass('QA giữ lượt đăng ký ', 26, 39);
    const next = (await request('GET', '/api/sessions', { token: visitor }))
      .find(session => session.classId === retained.id);
    assert.ok(next);
    await request('PUT', '/api/sessions/' + next.id + '/signup', { token: visitor, body: { packageId: routingPack.id } });
    const spare = await create(admin, 'trainers', 'routing-substitute');
    await request('PUT', '/api/shifts/' + spare.profile.id, {
      token: admin, body: { shifts: [{ weekday: todayWeekday, startsAt: '00:00', endsAt: '23:59' }] },
    });
    const substituteGroup = await request('PUT', '/api/sessions/' + next.id + '/trainer', {
      token: admin, body: { trainerId: spare.profile.id, reason: 'QA thay HLV cho buổi lớp 10' },
    });
    assert.equal(substituteGroup.assignedTrainerId, trainerId);
    assert.equal(substituteGroup.trainerId, spare.profile.id);
    const transferredPack = await request('PUT', '/api/packages/' + routingPack.id + '/trainer', {
      token: admin, body: { trainerId: guardTrainer.profile.id, reason: 'QA đổi HLV gói lớp 10' },
    });
    assert.equal(transferredPack.remainingSessions, 4);
    assert.equal((await request('GET', '/api/sessions/' + next.id, { token: visitor })).id, next.id);
    const retainedEntry = await request('POST', '/api/checkins/confirm', {
      token: admin, body: { ...confirm, sessionId: next.id }, key: randomUUID(), status: 201,
    });
    assert.equal(retainedEntry.remainingAfterEvent, 3);
    assert.equal(retainedEntry.trainerId, spare.profile.id);
    console.log('PASS chờ đúng cửa sổ đã hẹn và xác nhận lớp 10 walk-in.');
  }
  await request('PUT', '/api/goals/' + learner.profile.id, {
    token: coach, body: { month: date(later()).slice(0, 7), title: 'QA mục tiêu', description: 'Tập đúng lịch', progress: 25 },
  });
  assert.equal((await request('GET', '/api/goals', { token: student }))[0].progress, 25);

  const substitute = '/api/sessions/' + booking.id + '/trainer';
  const substituteBody = { trainerId: guardTrainer.profile.id, reason: 'QA thay HLV cho buổi đã đặt' };
  await request('PUT', substitute, { token: student, body: substituteBody, status: 403 });
  await request('PUT', substitute, { token: admin, body: { ...substituteBody, trainerId: recreated.profile.id },
    status: 409, code: 'TRAINER_CLOSED' });
  const [occupied] = await request('POST', '/api/pt/direct', {
    token: admin, status: 201, body: { learnerId: guardLearner.profile.id, packageId: guardPack.id,
      weeks: 1, startAt: newTime.toISOString() },
  });
  await request('PUT', substitute, { token: admin, body: substituteBody, status: 409, code: 'SCHEDULE_CONFLICT' });
  assert.equal((await current()).trainerId, trainer.profile.id);
  await request('POST', '/api/sessions/' + occupied.id + '/cancel', {
    token: admin, body: { reason: 'QA kết thúc kiểm HLV bận' },
  });
  await request('GET', '/api/sessions/events', { status: 401 });
  const stopStreams = new AbortController();
  try {
    const streams = await Promise.all([admin, coach, student].map(token => calendarStream(token, stopStreams)));
    await Promise.all(streams.map(stream => stream('ready')));
    const transferred = await request('PUT', substitute, { token: admin, body: substituteBody });
    assert.equal(transferred.trainerId, guardTrainer.profile.id);
    assert.equal(transferred.packageId, secondPack.id);
    assert.equal(Date.parse(transferred.startAt), newTime.valueOf());
    await Promise.all(streams.map(stream => stream('calendar')));
    await request('GET', '/api/sessions/' + booking.id, { token: coach, status: 403 });
    assert.equal((await current()).trainerId, guardTrainer.profile.id);
    const acting = await login(guardTrainer.profile.username, guardTrainer.initialPassword);
    const { token: replacementCoach } = await request('POST', '/api/auth/change-password', {
      token: acting.token, body: { currentPassword: guardTrainer.initialPassword, newPassword: randomUUID() },
    });
    assert.equal((await request('GET', '/api/sessions/' + booking.id, { token: replacementCoach })).id, booking.id);
    const display = await request('GET', substitute, { token: student });
    assert.deepEqual(display, { id: guardTrainer.profile.id, displayName: guardTrainer.profile.displayName });

    const changePack = '/api/packages/' + secondPack.id + '/trainer';
    const packChange = { trainerId: recreated.profile.id, reason: 'QA đổi HLV của gói PT' };
    await request('PUT', changePack, { token: student, body: packChange, status: 403 });
    const updatedPack = await request('PUT', changePack, { token: admin, body: packChange });
    assert.equal(updatedPack.trainerId, recreated.profile.id);
    assert.equal(updatedPack.code, secondPack.code);
    assert.equal(updatedPack.remainingSessions, secondPack.remainingSessions);
    await Promise.all(streams.map(stream => stream('calendar')));
    assert.equal((await current()).trainerId, guardTrainer.profile.id);
  } finally {
    stopStreams.abort();
  }
  await request('PUT', '/api/shifts/' + recreated.profile.id, { token: admin,
    body: { shifts: [1,2,3,4,5,6,7].map(weekday => ({weekday, startsAt: '08:00', endsAt: '18:00'})) } });
  const newBooking = await request('POST', '/api/pt/bookings', { token: student, status: 201,
    body: { packageId: secondPack.id, startAt: new Date(date(later(4320)) + 'T10:00:00+07:00').toISOString() } });
  assert.equal(newBooking.trainerId, recreated.profile.id);
  console.log('PASS hủy cạnh tranh điểm danh, đổi HLV của buổi và gói, đồng bộ lịch qua SSE cho cả 3 vai trò.');
  if (process.env.MUAYTHAI_TEST_RECOVERY === '1') {
    assert.equal(base, 'http://127.0.0.1:8180', 'Chỉ chạy kiểm thử gián đoạn trên cổng QA.');
    const compose = (...args) => execFileSync('docker',
      ['compose', '-p', 'khang-acceptance', '-f', 'deploy/compose.yaml', ...args], { stdio: 'pipe' });
    const path = '/api/sessions/' + newBooking.id;
    compose('pause', 'checkin');
    try {
      await request('POST', path + '/cancel', {
        token: admin, body: { reason: 'QA gián đoạn dịch vụ điểm danh' }, status: 503, code: 'DEPENDENCY_UNAVAILABLE',
      });
      assert.equal((await request('GET', path, { token: admin })).status, 'PENDING_TRAINER');
    } finally {
      compose('unpause', 'checkin');
    }
    const deadline = Date.now() + 25000;
    let restored;
    do {
      const latest = await request('GET', path, { token: admin });
      restored = latest.status === 'CANCELLED';
      if (!restored) await new Promise(resolve => setTimeout(resolve, 500));
    } while (!restored && Date.now() < deadline);
    assert.ok(restored, 'Yêu cầu hủy cần tự hoàn tất khi Check-in hoạt động lại.');
    console.log('PASS phục hồi yêu cầu hủy sau gián đoạn Check-in.');
  }
  console.log('PASS hồ sơ, 3 vai trò, xóa HLV, đổi mật khẩu, nhóm 30, duyệt và đổi lớp 10, gói, PT, QR, tập tự do, ghi đồng thời, hoàn lượt, đổi lịch PT, thông báo, mục tiêu.');
}

main().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
