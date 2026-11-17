[CmdletBinding(SupportsShouldProcess)]
param([ValidateRange(1024, 65535)][int] $Port = 8186, [string] $CoverageDirectory)

$ErrorActionPreference = 'Stop'
$project = 'khang-prod-smoke-' + [Guid]::NewGuid().ToString('N').Substring(0,8)
if (-not $PSCmdlet.ShouldProcess($project, 'Build and verify an isolated production-style stack')) { return }

function New-TestSecret {
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    [BitConverter]::ToString($bytes).Replace('-', '')
}

function Invoke-Compose {
    & docker @compose @args
    if ($LASTEXITCODE -ne 0) { throw "Docker compose failed with exit code $LASTEXITCODE." }
}

$root = Split-Path -Parent $PSScriptRoot
$config = Join-Path ([IO.Path]::GetTempPath()) "$project.env"
$bootstrapPassword = New-TestSecret
$variables = @(
    "PRODUCTION_DB_VOLUME=$project-db", "GATEWAY_PORT=$Port", 'ATTENDANCE_RETENTION=P1Y',
    'ATTENDANCE_RETENTION_CRON=*/2 * * * * *',
    'BOOTSTRAP_ADMIN_ENABLED=true', 'BOOTSTRAP_ADMIN_EMAIL=pilot-admin@example.test',
    "BOOTSTRAP_ADMIN_PASSWORD=$bootstrapPassword"
)
foreach ($key in 'POSTGRES_PASSWORD','JWT_SECRET','INTERNAL_KEY','AUTH_DB_PASSWORD','SCHEDULE_DB_PASSWORD',
    'CHECKIN_DB_PASSWORD','PROGRESS_DB_PASSWORD','AUTH_MIGRATION_PASSWORD','SCHEDULE_MIGRATION_PASSWORD',
    'CHECKIN_MIGRATION_PASSWORD','PROGRESS_MIGRATION_PASSWORD') {
    $variables += "$key=$(New-TestSecret)"
}
[IO.File]::WriteAllLines($config, $variables, [Text.UTF8Encoding]::new($false))
$compose = @('compose','--env-file',$config,'-p',$project,'-f',(Join-Path $root 'deploy/compose.yaml'),
    '-f',(Join-Path $root 'deploy/production.compose.yaml'))
$previousPort = $env:GATEWAY_PORT
$previousCoverage = $env:MUAYTHAI_COVERAGE_PATH

try {
    $env:GATEWAY_PORT = "$Port"
    if ($CoverageDirectory) {
        $env:MUAYTHAI_COVERAGE_PATH = (Resolve-Path -LiteralPath $CoverageDirectory).Path
        $compose += @('-f', (Join-Path $root 'deploy/coverage.compose.yaml'))
    }
    Invoke-Compose up -d --build --wait --wait-timeout 180
    foreach ($service in 'auth','schedule','checkin','progress') {
        $role = $service + '_app'
        $query = @"
SELECT NOT rolsuper AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolbypassrls
  AND NOT has_schema_privilege(rolname, 'public', 'CREATE')
  AND NOT has_table_privilege(rolname, 'public.flyway_schema_history', 'SELECT,INSERT,UPDATE,DELETE')
  AND (SELECT count(*) FROM pg_database WHERE datname IN ('auth_db','schedule_db','checkin_db','progress_db')
    AND has_database_privilege(rolname, datname, 'CONNECT')) = 1
FROM pg_roles WHERE rolname = '$role';
"@
        $allowed = Invoke-Compose exec -T db psql -XAt -v ON_ERROR_STOP=1 -U db_bootstrap -d ($service + '_db') -c $query
        if ($allowed -cne 't') { throw "Unexpected PostgreSQL rights for $role." }
    }
    $query = "SELECT NOT has_table_privilege('auth_app','password_reset_events','UPDATE,DELETE') AND NOT EXISTS (SELECT FROM users WHERE username IN ('admin@example.test','trainer@example.test','learner@example.test'));"
    $allowed = Invoke-Compose exec -T db psql -XAt -v ON_ERROR_STOP=1 -U db_bootstrap -d auth_db -c $query
    if ($allowed -cne 't') { throw 'Production accounts or audit rights are misconfigured.' }

    $server = "http://127.0.0.1:$Port"
    $loginBody = @{username='pilot-admin@example.test';password=$bootstrapPassword} | ConvertTo-Json -Compress
    $login = Invoke-RestMethod "$server/api/auth/login" -Method Post -TimeoutSec 15 -ContentType 'application/json' -Body $loginBody
    if ($login.user.role -ne 'ADMIN' -or -not $login.mustChangePassword) { throw 'Bootstrap must enforce password change.' }
    $changeBody = @{currentPassword=$bootstrapPassword;newPassword=(New-TestSecret)} | ConvertTo-Json -Compress
    $session = Invoke-RestMethod "$server/api/auth/change-password" -Method Post -TimeoutSec 15 -ContentType 'application/json' -Headers @{Authorization='Bearer '+$login.token} -Body $changeBody
    if ($session.mustChangePassword) { throw 'Bootstrap password change did not complete.' }
    $headers = @{Authorization='Bearer '+$session.token}
    $null = Invoke-RestMethod "$server/api/sessions" -Headers $headers -TimeoutSec 15
    $null = Invoke-RestMethod "$server/api/notifications" -Headers $headers -TimeoutSec 15
    $null = Invoke-RestMethod "$server/api/checkins" -Headers $headers -TimeoutSec 15

    $fixture = @'
WITH package AS (
    INSERT INTO packages(code, learner_id, learner_name, member_code, type, start_date, end_date)
    VALUES (gen_random_uuid(), 900001, 'QA retention', 'QA-RETENTION', 'MEMBERSHIP', current_date - 800, current_date)
    RETURNING id
), visits AS (
    INSERT INTO checkins(package_id, learner_id, learner_name, session_title, service_date,
        actor_id, method, checked_in_at, request_key)
    SELECT id, 900001, 'QA retention', 'QA open gym', current_date - age,
        900002, 'CODE_ENTRY', now() - interval '1 day' * age, gen_random_uuid()
    FROM package CROSS JOIN (VALUES (800), (1)) AS samples(age)
    RETURNING id, checked_in_at
)
INSERT INTO checkin_adjustments(checkin_id, actor_id, reason, adjusted_at, request_key)
SELECT id, 900002, 'QA retention', checked_in_at, gen_random_uuid() FROM visits;
INSERT INTO absence_records(session_id, learner_id, actor_id, recorded_at)
VALUES (900003, 900001, 900002, now() - interval '800 days'),
       (900004, 900001, 900002, now() - interval '1 day');
'@
    $null = Invoke-Compose exec -T db psql -X -v ON_ERROR_STOP=1 -U db_bootstrap -d checkin_db -c "BEGIN; $fixture COMMIT;"
    $retained = @'
SELECT (SELECT count(*) FROM checkins WHERE learner_id=900001 AND checked_in_at >= now() - interval '1 year') = 1
   AND (SELECT count(*) FROM checkins WHERE learner_id=900001) = 1
   AND (SELECT count(*) FROM checkin_adjustments WHERE actor_id=900002) = 1
   AND (SELECT count(*) FROM absence_records WHERE learner_id=900001) = 1
   AND EXISTS (SELECT FROM absence_records WHERE session_id=900004);
'@
    $cleared = 'f'
    for ($attempt = 0; $attempt -lt 12 -and $cleared -ne 't'; $attempt++) {
        $cleared = Invoke-Compose exec -T db psql -XAt -v ON_ERROR_STOP=1 -U db_bootstrap -d checkin_db -c $retained
        if ($cleared -ne 't') { Start-Sleep -Seconds 1 }
    }
    if ($cleared -ne 't') { throw 'Retention did not remove only the old records.' }
    'PASS retention removes old visits, adjustments and absences while preserving recent records.'
    'PASS production-style bootstrap, migrations and PostgreSQL privilege boundaries.'
} finally {
    try { Invoke-Compose down --timeout 30 --volumes }
    finally {
        $env:GATEWAY_PORT = $previousPort
        $env:MUAYTHAI_COVERAGE_PATH = $previousCoverage
        Remove-Item -LiteralPath $config
    }
}
