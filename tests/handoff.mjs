import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {readFileSync, writeFileSync, mkdirSync} from 'node:fs';

// Only this disposable project may receive SQL fixture adjustments for time boundaries.
const base = process.env.MUAYTHAI_TEST_URL;
const project = process.env.MUAYTHAI_TEST_COMPOSE_PROJECT;
assert.equal(base, 'http://127.0.0.1:8180', 'Use the isolated handoff gateway');
assert.ok(['khang-handoff', 'khang-acceptance'].includes(project), 'Use a disposable acceptance Compose project');
const run = randomUUID().slice(0, 8);
mkdirSync('android/app/build/handoff', {recursive:true});
const results = [];
let calls = 0, admin;
const settings = Object.fromEntries(readFileSync('deploy/.env','utf8').split(/\r?\n/)
  .filter(line=>line.startsWith('DEMO_ADMIN_PASSWORD='))
  .map(line=>[line.slice(0,line.indexOf('=')),line.slice(line.indexOf('=')+1)]));
const day = minutes => new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh'})
  .format(new Date(Date.now()+minutes*60000));
const instant = (days,hour=10) => day(days*1440)+'T'+String(hour).padStart(2,'0')+':00:00+07:00';
const reason = {reason:'Acceptance verification'};
async function api(method,path,token,body,status=200,code){
 calls++;
 const response=await fetch(base+path,{method,signal:AbortSignal.timeout(20000),
  headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{}),
   'Idempotency-Key':randomUUID()},body:body?JSON.stringify(body):undefined});
 const data=response.headers.get('content-type')?.includes('json')?await response.json():null;
 assert.equal(response.status,status,method+' '+path+' -> '+(data?.code||response.status));
 if(code)assert.equal(data?.code,code,path);
 return data;
}
function sql(database,statement){
 assert.ok(['schedule_db','checkin_db'].includes(database));
 const output=execFileSync('docker',['compose','--env-file','deploy/.env','-p',project,'-f','deploy/compose.yaml',
  'exec','-T','db','psql','-X','-v','ON_ERROR_STOP=1','-U','auth_app','-d',database,'-At','-c',statement],
  {encoding:'utf8',timeout:20000,stdio:['ignore','pipe','pipe']});
 return output.trim();
}
const id = value => { assert.ok(Number.isSafeInteger(value)&&value>0); return value; };
const login = (username,password)=>api('POST','/api/auth/login',null,{username,password});
async function account(directory,label){
 const created=await api('POST','/api/'+directory,admin,{username:label+'-'+run+'@example.test',displayName:'Acceptance '+label},201);
 let session=await login(created.profile.username,created.initialPassword);
 if(session.mustChangePassword)session=await api('POST','/api/auth/change-password',session.token,
  {currentPassword:created.initialPassword,newPassword:randomUUID()});
 return {...created.profile,token:session.token};
}
const shifts = trainer => api('PUT','/api/shifts/'+trainer.id,admin,{shifts:[1,2,3,4,5,6,7]
 .map(weekday=>({weekday,startsAt:'00:00',endsAt:'23:59'}))});
async function pack(learner,type,trainer=null,classId=null,totalSessions=20){
 return api('POST','/api/packages',admin,{learnerId:learner.id,type,trainerId:trainer?.id,classId,
  startDate:day(-1440),endDate:day(90*1440),totalSessions:type==='MEMBERSHIP'?null:totalSessions},201);
}
async function pt(learner,trainer,days=2,hour=10){
 const p=await pack(learner,'PT',trainer);
 const [session]=await api('POST','/api/pt/direct',admin,{learnerId:learner.id,packageId:p.id,weeks:1,startAt:instant(days,hour)},201);
 return {pack:p,session};
}
const time = (session,start,end) => sql('schedule_db',`UPDATE sessions SET start_at=now()+interval '${start} minutes',end_at=now()+interval '${end} minutes' WHERE id=${id(session.id)}`);
const confirm = (p,session,token=admin,status=201,code) => api('POST','/api/checkins/confirm',token,
 {packageId:p.id,sessionId:session?.id??null,method:'QR'},status,code);
const preview = (p,status=200,code) => api('POST','/api/checkins/preview',admin,{code:p.code,codeKind:'QR'},status,code);
const remaining = p=>api('GET','/api/packages/'+p.id,admin).then(p=>p.remainingSessions);
async function group(type,label){
 const trainer=await account('trainers',label+'-coach'); await shifts(trainer);
 const course=await api('POST','/api/'+(type==='GROUP10'?'classes10':'classes30'),admin,
  {title:'Acceptance '+label,trainerId:trainer.id,weekdays:[1,2,3,4,5,6,7],startsAt:'10:00',endsAt:'11:00'},201);
 const sessions=await api('GET','/api/sessions?q='+encodeURIComponent(course.title),admin);
 const session=sessions.find(s=>s.classId===course.id&&Date.parse(s.startAt)>Date.now()+86400000);
 assert.ok(session,'Future group session required');
 return {trainer,course,session};
}
async function test(name,fn){
 await fn(); results.push({name,status:'PASS'}); console.log('PASS '+name);
}

async function profileAndAccess(){
 const learner=await account('learners','profile');
 const outsider=await account('learners','outsider');
 const desk=await account('receptionists','desk');
 const edited=await api('PUT','/api/users/me',learner.token,{displayName:'Acceptance updated',phone:'0900000000'});
 assert.equal(edited.displayName,'Acceptance updated');
 await api('PUT','/api/users/me',learner.token,{displayName:''},400);
 await api('GET','/api/learners/'+learner.id,outsider.token,undefined,403);
 const membership=await pack(learner,'MEMBERSHIP');
 await api('PATCH','/api/users/'+learner.id+'/status',admin,{status:'LOCKED',...reason});
 await api('GET','/api/users/me',learner.token,undefined,403,'ACCOUNT_LOCKED');
 await preview(membership,409,'ACCOUNT_LOCKED');
 await api('PATCH','/api/users/'+learner.id+'/status',admin,{status:'ACTIVE',...reason});
 await api('GET','/api/users/me',learner.token,undefined,401);
 const fresh=await login(learner.username, (await api('POST','/api/users/'+learner.id+'/password-reset',admin,reason)).password);
 assert.equal(fresh.mustChangePassword,true);
 await api('GET','/api/notes',desk.token,undefined,403);
 await api('GET','/api/goals',desk.token,undefined,403);
 await api('POST','/api/auth/logout',outsider.token,undefined,204);
 await api('GET','/api/users/me',outsider.token,undefined,401);
}
async function group10Capacity(){
 const g=await group('GROUP10','capacity10'); const learners=[];
 for(let i=0;i<11;i++){ const learner=await account('learners','g10-'+i); learners.push({learner,pack:await pack(learner,'GROUP10',g.trainer)}); }
 for(const row of learners.slice(0,9))await api('PUT','/api/sessions/'+g.session.id+'/signup',row.learner.token,{packageId:row.pack.id});
 const race=await Promise.all(learners.slice(9).map(async row=>{
  const r=await fetch(base+'/api/sessions/'+g.session.id+'/signup',{method:'PUT',headers:{Authorization:'Bearer '+row.learner.token,'Content-Type':'application/json'},body:JSON.stringify({packageId:row.pack.id})});
  const body=await r.json();return {status:r.status,code:body.code,row};
 }));
 assert.deepEqual(race.map(r=>r.status).sort(),[200,409]);
 assert.equal(race.find(r=>r.status===409).code,'CLASS_FULL');
 const roster=await api('GET','/api/checkins/roster?sessionId='+g.session.id,admin); assert.equal(roster.length,10);
 const loser=race.find(r=>r.status===409).row;
 time(g.session,10,70);
 await confirm(loser.pack,g.session,admin,409,'CLASS_FULL');
 assert.equal(await remaining(loser.pack),20);
 const registered=learners[0];
 await confirm(registered.pack,g.session); assert.equal(await remaining(registered.pack),19);
 time(g.session,40,100); await confirm(learners[1].pack,g.session,admin,409,'NO_SESSION_NOW');
 time(g.session,-21,39); await confirm(loser.pack,g.session,admin,409,'NO_SESSION_NOW');
 time(g.session,-90,-30); await confirm(learners[1].pack,g.session,admin,409,'NO_SESSION_NOW');
}
async function group30Capacity(){
 const g=await group('GROUP30','capacity30'); const rows=[];
 for(let i=0;i<31;i++){const learner=await account('learners','g30-'+i);rows.push({learner,pack:await pack(learner,'GROUP30',null,g.course.id)});}
 time(g.session,10,70);
 for(const row of rows.slice(0,29))await confirm(row.pack,g.session);
 const attempts=await Promise.all(rows.slice(29).map(async row=>{
  const r=await fetch(base+'/api/checkins/confirm',{method:'POST',headers:{Authorization:'Bearer '+admin,'Content-Type':'application/json','Idempotency-Key':randomUUID()},body:JSON.stringify({packageId:row.pack.id,sessionId:g.session.id,method:'QR'})});
  const data=await r.json();return {status:r.status,code:data.code,row};
 }));
 assert.deepEqual(attempts.map(r=>r.status).sort(),[201,409]); assert.equal(attempts.find(r=>r.status===409).code,'CLASS_FULL');
 assert.equal((await api('GET','/api/checkins?sessionId='+g.session.id,admin)).length,30);
 assert.equal(await remaining(attempts.find(r=>r.status===409).row.pack),20);
 const other=await group('GROUP30','other-class');
 await confirm(rows[0].pack,other.session,admin,409,'WRONG_PACKAGE');
 assert.ok(!(await api('GET','/api/sessions',rows[0].learner.token)).some(s=>s.classId===other.course.id));
}
async function rescheduleAndCancellation(){
 const trainer=await account('trainers','reschedule-coach'); await shifts(trainer);
 const learner=await account('learners','reschedule');
 const {session,pack:p}=await pt(learner,trainer,3);
 const proposal=await api('POST','/api/pt/'+session.id+'/changes',trainer.token,{proposedStart:instant(3,12)},201);
 assert.equal(proposal.recipientId,learner.id);
 assert.ok((await api('GET','/api/notifications',learner.token)).some(n=>n.notice.sessionId===session.id));
 const before=(await api('GET','/api/sessions/'+session.id,learner.token)).startAt;
 await api('POST','/api/pt/changes/'+proposal.id+'/reject',learner.token);
 assert.equal((await api('GET','/api/sessions/'+session.id,learner.token)).startAt,before);
 const expired=await api('POST','/api/pt/'+session.id+'/changes',learner.token,{proposedStart:instant(3,13)},201);
 sql('schedule_db',`UPDATE pt_changes SET expires_at=now()-interval '1 minute' WHERE id=${id(expired.id)}`);
 assert.equal((await api('GET','/api/pt/changes',learner.token)).find(p=>p.id===expired.id).status,'EXPIRED');
 await api('POST','/api/pt/changes/'+expired.id+'/accept',trainer.token,undefined,409,'CHANGE_CLOSED');
 assert.equal((await api('GET','/api/sessions/'+session.id,learner.token)).startAt,before);
 time(session,719,779); await api('POST','/api/sessions/'+session.id+'/cancel',learner.token,reason,409,'CANCEL_TOO_LATE');
 time(session,721,781); await api('POST','/api/sessions/'+session.id+'/cancel',learner.token,reason);
 assert.equal((await api('GET','/api/sessions/'+session.id,admin)).reason,reason.reason);
 assert.equal(await remaining(p),20);
 const g=await group('GROUP10','cancel3'); const gp=await pack(learner,'GROUP10',g.trainer);
 await api('PUT','/api/sessions/'+g.session.id+'/signup',learner.token,{packageId:gp.id});
 time(g.session,179,239); await api('DELETE','/api/sessions/'+g.session.id+'/signup',learner.token,undefined,409,'CANCEL_TOO_LATE');
 time(g.session,181,241); await api('DELETE','/api/sessions/'+g.session.id+'/signup',learner.token,undefined,204);
 assert.equal(await remaining(gp),20);
}
async function notesGoalsAndAbsence(){
 const trainer=await account('trainers','notes-coach'); await shifts(trainer);
 const outsider=await account('trainers','notes-outsider');
 const learner=await account('learners','notes'); const other=await account('learners','notes-other');
 const {session,pack:p}=await pt(learner,trainer,4);
 const note={content:'Kỹ thuật cơ bản',comment:'Hoàn thành buổi tập',rating:4};
 await api('PUT','/api/notes/session/'+session.id,trainer.token,note,409,'SESSION_NOT_ENDED');
 time(session,-120,-60);
 await api('PUT','/api/notes/session/'+session.id,outsider.token,note,403);
 await api('PUT','/api/notes/session/'+session.id,trainer.token,{...note,rating:6},400);
 await api('PUT','/api/notes/session/'+session.id,trainer.token,note);
 assert.equal((await api('GET','/api/notes/session/'+session.id,learner.token)).rating,4);
 await api('GET','/api/notes/session/'+session.id,other.token,undefined,403);
 assert.ok((await api('GET','/api/notes?learnerId='+learner.id,admin)).some(n=>n.sessionId===session.id));
 const older=await pt(learner,trainer,5); time(older.session,-240,-180);
 await api('PUT','/api/notes/session/'+older.session.id,trainer.token,note);
 const timeline=await api('GET','/api/notes',learner.token);
 assert.deepEqual(timeline.map(n=>n.sessionId),[session.id,older.session.id]);
 await api('GET','/api/notes?learnerId='+learner.id,outsider.token,undefined,403);
 await api('PUT',`/api/checkins/roster/${session.id}/absent/${learner.id}`,trainer.token);
 assert.equal((await api('GET','/api/checkins/roster?sessionId='+session.id,admin)).find(r=>r.learnerId===learner.id).status,'ABSENT');
 assert.equal(await remaining(p),20);
 const groupNote=await group('GROUP10','notes-group'); time(groupNote.session,-180,-120);
 const savedGroup=await api('PUT','/api/notes/session/'+groupNote.session.id,groupNote.trainer.token,{content:'Nội dung buổi nhóm',comment:'Đã hoàn thành'});
 assert.equal(savedGroup.rating,null);
 assert.equal((await api('GET','/api/notes/session/'+groupNote.session.id,admin)).content,'Nội dung buổi nhóm');
 const goal={month:day(0).slice(0,7),title:'Mục tiêu',description:'Hoàn thiện kỹ thuật',progress:100};
 await api('PUT','/api/goals/'+learner.id,outsider.token,goal,403);
 assert.equal((await api('PUT','/api/goals/'+learner.id,trainer.token,goal)).status,'ACHIEVED');
 assert.equal((await api('GET','/api/goals',learner.token))[0].progress,100);
 await api('GET','/api/goals?learnerId='+learner.id,other.token,undefined,403);
}
async function packageAndCare(){
 const learner=await account('learners','care'); const p=await pack(learner,'MEMBERSHIP');
 sql('checkin_db',`UPDATE packages SET start_date=current_date-30,end_date=current_date-1 WHERE id=${id(p.id)}`);
 await preview(p,409,'PACKAGE_EXPIRED');
 sql('checkin_db',`UPDATE packages SET end_date=current_date+60 WHERE id=${id(p.id)}`);
 assert.ok((await api('GET','/api/care/inactive',admin)).some(r=>r.learnerId===learner.id));
 await confirm(p,null);
 assert.ok(!(await api('GET','/api/care/inactive',admin)).some(r=>r.learnerId===learner.id));
 sql('checkin_db',`UPDATE checkins SET checked_in_at=now()-interval '13 days 23 hours' WHERE package_id=${id(p.id)}`);
 assert.ok(!(await api('GET','/api/care/inactive',admin)).some(r=>r.learnerId===learner.id));
 sql('checkin_db',`UPDATE checkins SET checked_in_at=now()-interval '14 days 1 hour' WHERE package_id=${id(p.id)}`);
 assert.ok((await api('GET','/api/care/inactive',admin)).some(r=>r.learnerId===learner.id));
 sql('checkin_db',`UPDATE packages SET end_date=(now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date+6 WHERE id=${id(p.id)}`);
 assert.ok((await api('GET','/api/care/renewals',admin)).some(r=>r.learnerId===learner.id));
 assert.ok((await api('GET','/api/notifications',learner.token)).some(n=>n.notice.id.startsWith('package-'+p.id+'-')));
 const trainer=await account('trainers','exhaust-coach'); const exhausted=await pack(learner,'PT',trainer, null,1);
 sql('checkin_db',`UPDATE packages SET used_sessions=total_sessions WHERE id=${id(exhausted.id)}`);
 await preview(exhausted,409,'PACKAGE_EXHAUSTED');
 const thresholdLearner=await account('learners','care-threshold');
 const thresholdPack=await pack(thresholdLearner,'PT',trainer);
 sql('checkin_db',"UPDATE packages SET used_sessions=16 WHERE id="+id(thresholdPack.id));
 assert.ok(!(await api('GET','/api/care/renewals',admin)).some(r=>r.learnerId===thresholdLearner.id));
 sql('checkin_db',"UPDATE packages SET used_sessions=17 WHERE id="+id(thresholdPack.id));
 assert.ok((await api('GET','/api/care/renewals',admin)).some(r=>r.learnerId===thresholdLearner.id));
 assert.ok((await api('GET','/api/notifications',thresholdLearner.token)).some(n=>n.notice.id.startsWith('package-'+thresholdPack.id+'-')));
}
async function schedulingAndClassLifecycle(){
 const g=await group('GROUP30','lifecycle');
 const replacement=await account('trainers','group30-substitute');await shifts(replacement);
 const replaced=await api('PUT','/api/sessions/'+g.session.id+'/trainer',admin,{trainerId:replacement.id,...reason});
 assert.equal(replaced.trainerId,replacement.id);
 await api('GET','/api/sessions/'+g.session.id,g.trainer.token,undefined,403);
 assert.equal((await api('GET','/api/sessions/'+g.session.id,replacement.token)).id,g.session.id);
 assert.ok((await api('GET','/api/notifications',g.trainer.token)).some(n=>n.notice.title==='Buổi tập đã đổi HLV'));
 assert.ok((await api('GET','/api/notifications',replacement.token)).some(n=>n.notice.sessionId===g.session.id));
 const changed=await api('PUT','/api/classes30/'+g.course.id,admin,{title:g.course.title+' updated',trainerId:g.trainer.id,weekdays:[1,2,3,4,5,6,7],startsAt:'12:00',endsAt:'13:00'});
 assert.ok(changed.title.endsWith('updated'));
 assert.equal((await api('GET','/api/sessions/'+g.session.id,admin)).status,'CANCELLED');
 const future=await api('GET','/api/sessions?q='+encodeURIComponent(changed.title),admin);
 assert.ok(future.some(s=>s.classId===g.course.id&&s.status==='CONFIRMED'));
 await api('POST','/api/classes30/'+g.course.id+'/stop',admin);
 assert.ok((await api('GET','/api/sessions?q='+encodeURIComponent(changed.title),admin))
  .filter(s=>s.classId===g.course.id&&Date.parse(s.startAt)>Date.now()+1800000).every(s=>s.status==='CANCELLED'));
 const proposed=await api('POST','/api/classes10',g.trainer.token,{title:'Acceptance rejected '+run,weekdays:[1],startsAt:'14:00',endsAt:'15:00'},201);
 assert.equal(proposed.status,'PENDING');
 assert.equal((await api('POST','/api/classes10/'+proposed.id+'/reject',admin)).status,'REJECTED');
 const learner=await account('learners','slots');const p=await pack(learner,'PT',g.trainer);
 const query='/api/pt/slots?packageId='+p.id+'&date='+day(5*1440);
 const before=await api('GET',query,learner.token); assert.ok(before.some(s=>s.available));
 const closed=await api('POST','/api/shifts/'+g.trainer.id+'/blocks',admin,{startAt:instant(5,10),endAt:instant(5,11),...reason},201);
 assert.ok((await api('GET',query,learner.token)).some(s=>Date.parse(s.startAt)===Date.parse(instant(5,10))&&!s.available));
 await api('POST','/api/pt/bookings',learner.token,{packageId:p.id,startAt:instant(5,10)},409,'TRAINER_CLOSED');
 await api('DELETE',`/api/shifts/${g.trainer.id}/blocks/${closed.id}`,admin,undefined,204);
 const request=await api('POST','/api/pt/bookings',learner.token,{packageId:p.id,startAt:instant(5,10)},201);
 assert.ok((await api('GET',query,learner.token)).some(s=>Date.parse(s.startAt)===Date.parse(instant(5,10))&&!s.available));
 assert.equal((await api('POST','/api/pt/'+request.id+'/reject',g.trainer.token,reason)).status,'REJECTED');
 assert.ok((await api('GET',query,learner.token)).some(s=>Date.parse(s.startAt)===Date.parse(instant(5,10))&&s.available));
 await api('GET','/api/shifts/'+g.trainer.id,learner.token,undefined,403);
 assert.equal((await api('GET','/api/shifts/'+g.trainer.id,g.trainer.token)).length,7);
 await api('PUT','/api/shifts/'+g.trainer.id,admin,{shifts:[]});
 assert.equal((await api('GET',query,learner.token)).length,0);
}
async function manualAttendanceAndScope(){
 const trainer=await account('trainers','manual-coach');await shifts(trainer);
 const outsider=await account('trainers','manual-outsider');
 const learner=await account('learners','manual');const other=await account('learners','manual-other');
 const desk=await account('receptionists','manual-desk');
 const {session,pack:p}=await pt(learner,trainer,6);
 time(session,10,70);
 const body={sessionId:session.id,packageId:p.id,reason:'Người dùng xác nhận tại quầy'};
 await api('POST','/api/checkins/manual',outsider.token,body,403);
 await api('POST','/api/checkins/manual',desk.token,body,403);
 await api('POST','/api/checkins/manual',trainer.token,{...body,reason:''},400);
 const entry=await api('POST','/api/checkins/manual',trainer.token,body,201);
 await api('POST','/api/sessions/'+session.id+'/cancel',admin,reason,409,'CHECKIN_EXISTS');
 assert.equal(entry.method,'MANUAL');assert.equal(entry.actorId,trainer.id);assert.equal(await remaining(p),19);
 assert.equal((await api('GET','/api/checkins/'+entry.id,learner.token)).id,entry.id);
 await api('GET','/api/checkins/'+entry.id,other.token,undefined,403);
 await api('GET','/api/checkins/'+entry.id,outsider.token,undefined,403);
 await api('GET','/api/checkins/'+entry.id,desk.token,undefined,403);
 assert.equal((await api('GET','/api/checkins?sessionId='+session.id,admin)).length,1);
 assert.equal((await api('GET','/api/checkins?sessionId='+session.id,outsider.token)).length,0);
 await api('GET','/api/checkins/roster?sessionId='+session.id,outsider.token,undefined,403);
 assert.equal((await api('GET','/api/checkins/roster?sessionId='+session.id,trainer.token))[0].status,'RECORDED');
 await api('POST','/api/checkins/'+entry.id+'/adjustments',admin,reason,201);
 assert.equal(await remaining(p),20);
 assert.equal((await api('GET','/api/checkins/roster?sessionId='+session.id,trainer.token))[0].status,'ADJUSTED');
 await api('POST','/api/sessions/'+session.id+'/cancel',admin,reason,409,'CHECKIN_EXISTS');
 assert.equal((await api('GET','/api/sessions/'+session.id,admin)).status,'CONFIRMED');
 assert.equal(await remaining(p),20);
}

async function customerFeedback(){
 for(const directory of ['trainers','learners','receptionists']){
  const payload={username:directory+'-password-'+run+'@example.test',displayName:'Acceptance password',temporaryPassword:'123456'};
  const invalid=await api('POST','/api/'+directory,admin,payload,400);
  assert.match(invalid.message,/8/,'Explain password minimum');
  const password=randomUUID();
  const created=await api('POST','/api/'+directory,admin,{...payload,temporaryPassword:password},201);
  assert.equal(created.initialPassword,password);
  assert.ok((await login(created.profile.username,password)).token,'Chosen password works');
 }
 const learner=await account('learners','archived-learner');
 const desk=await account('receptionists','archived-desk');
 const p=await pack(learner,'MEMBERSHIP');
 const entry=await confirm(p,null,desk.token);
 await api('DELETE','/api/learners/'+learner.id,desk.token,undefined,403);
 await api('DELETE','/api/receptionists/'+desk.id,learner.token,undefined,403);
 for(const [directory,user] of [['learners',learner],['receptionists',desk]]){
  await api('DELETE','/api/'+directory+'/'+user.id,admin,undefined,204);
  await api('DELETE','/api/'+directory+'/'+user.id,admin,undefined,204);
  await api('GET','/api/users/me',user.token,undefined,401);
  await api('GET','/api/'+directory+'/'+user.id,admin,undefined,404);
  const rows=await api('GET','/api/'+directory+'?q='+encodeURIComponent(user.username),admin);
  assert.equal(rows.items.length,0);
 }
 assert.equal((await api('GET','/api/checkins/'+entry.id,admin)).id,entry.id,'Keep archived account history');
 assert.equal((await api('GET','/api/packages/'+p.id,admin)).learnerId,learner.id);
 const g=await group('GROUP10','class-bound');
 const student=await account('learners','class-bound-student');
 const bound=await pack(student,'GROUP10',null,g.course.id);
 assert.equal(bound.classId,g.course.id); assert.equal(bound.trainerId,g.trainer.id);
 const enrolled=await api('PUT','/api/classes10/'+g.course.id+'/fixed/'+student.id,admin,{packageId:bound.id});
 assert.ok(enrolled.length>0);
 const repeat=await api('PUT','/api/classes10/'+g.course.id+'/fixed/'+student.id,admin,{packageId:bound.id});
 assert.equal(repeat.length,enrolled.length,'Retry fixed enrollment without duplicates');
 const upcoming=await account('learners','future-class-student');
 const future=await api('POST','/api/packages',admin,{learnerId:upcoming.id,type:'GROUP10',classId:g.course.id,
  startDate:day(7*1440),endDate:day(14*1440),totalSessions:10},201);
 const futureSessions=await api('PUT','/api/classes10/'+g.course.id+'/fixed/'+upcoming.id,admin,{packageId:future.id});
 assert.ok(futureSessions.length>0,'Allow assigning a future package now');
 assert.ok(futureSessions.every(s=>s.startAt.slice(0,10)>=future.startDate));
 const outsider=await account('trainers','other-group-coach'); await shifts(outsider);
 const other=await api('POST','/api/classes10',admin,{title:'Other class '+run,trainerId:outsider.id,
  weekdays:[1,2,3,4,5,6,7],startsAt:'10:00',endsAt:'11:00'},201);
 await api('PUT','/api/classes10/'+other.id+'/fixed/'+student.id,admin,{packageId:bound.id},409,'WRONG_PACKAGE');
 await api('PUT','/api/packages/'+bound.id+'/trainer',admin,{trainerId:outsider.id,...reason},400);
 await api('POST','/api/packages',admin,{learnerId:student.id,type:'GROUP10',trainerId:outsider.id,classId:g.course.id,
  startDate:day(0),endDate:day(14400),totalSessions:10},400);
 const group30=await group('GROUP30','wrong-class-kind');
 await api('POST','/api/packages',admin,{learnerId:student.id,type:'GROUP10',classId:group30.course.id,
  startDate:day(0),endDate:day(14400),totalSessions:10},400);
}

async function learnerConflictAcrossTrainers(){
 const learner=await account('learners','conflict-learner');
 const coaches=[];
 for(let i=0;i<2;i++){const trainer=await account('trainers','conflict-coach'+i);await shifts(trainer);coaches.push({trainer,pack:await pack(learner,'PT',trainer)});}
 const attempts=await Promise.all(coaches.map(async row=>{
  const r=await fetch(base+'/api/pt/bookings',{method:'POST',headers:{Authorization:'Bearer '+learner.token,'Content-Type':'application/json'},body:JSON.stringify({packageId:row.pack.id,startAt:instant(7,9)})});
  return {status:r.status,data:await r.json()};
 }));
 assert.deepEqual(attempts.map(r=>r.status).sort(),[201,409]);
 assert.equal(attempts.find(r=>r.status===409).data.code,'SCHEDULE_CONFLICT');
 const visible=await api('GET','/api/sessions',learner.token);
 assert.equal(visible.filter(s=>Date.parse(s.startAt)===Date.parse(instant(7,9))&&s.status==='PENDING_TRAINER').length,1);
}
async function main(){
 admin=(await login('admin@example.test',settings.DEMO_ADMIN_PASSWORD)).token;
 await test('FR01-07: profile validation, session revocation and privacy',profileAndAccess);
 await test('FR12/33/34/42: group10 last place, full walk-in, time window',group10Capacity);
 await test('FR12/17/34/40: group30 last place, package binding and quota',group30Capacity);
 await test('FR14/15/21/23/55: reject, expire and cancellation boundaries',rescheduleAndCancellation);
 await test('FR36-39/44/47-54: notes, absence, goals and ownership',notesGoalsAndAbsence);
 await test('FR34/45/46: expiration, exhaustion, care thresholds',packageAndCare);
 await test('FR08/13/24/26-28: class lifecycle, PT rejection and slots',schedulingAndClassLifecycle);
 await test('FR02/35-41: manual attendance, quota and record scope',manualAttendanceAndScope);
 await test('FR11: concurrent learner bookings across trainers',learnerConflictAcrossTrainers);
 await test('Customer feedback: chosen passwords, account archive, class10 assignment',customerFeedback);
}
try{await main();}catch(error){results.push({status:'FAIL',message:error.message});process.exitCode=1;console.error(error.message);}
finally{writeFileSync('android/app/build/handoff/extended-results.json',JSON.stringify({run,at:new Date().toISOString(),calls,results},null,2));}
