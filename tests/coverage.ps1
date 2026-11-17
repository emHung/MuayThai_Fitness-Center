[CmdletBinding(SupportsShouldProcess)]
param([ValidateRange(1024, 65535)][int]$Port = 8180)

$ErrorActionPreference = 'Stop'
$project = 'khang-coverage'
if (!$PSCmdlet.ShouldProcess($project, 'Run Docker acceptance and write the JaCoCo report')) {
    return
}

if (!$env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK with java and jar.' }
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$jar = Join-Path $env:JAVA_HOME 'bin\jar.exe'
$root = Split-Path -Parent $PSScriptRoot
$reportDir = Join-Path $root 'backend\target\coverage'
$null = New-Item -ItemType Directory -Force -Path $reportDir
$work = Join-Path $reportDir ([Guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $work

function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "Docker exited with code $LASTEXITCODE." }
}

$settings = @{
    GATEWAY_PORT = "$Port"
    CLASS_CALENDAR_CRON = '*/5 * * * * *'
    MUAYTHAI_COVERAGE_PATH = $work
    MUAYTHAI_TEST_COMPOSE_PROJECT = $project
    MUAYTHAI_TEST_URL = "http://127.0.0.1:$Port"
    MUAYTHAI_TEST_CALENDAR = '1'
    MUAYTHAI_TEST_RECOVERY = '1'
}
$previous = @{}
$stack = @('compose', '-p', $project, '-f', 'deploy/compose.yaml', '-f', 'deploy/coverage.compose.yaml')
Push-Location -LiteralPath $root
try {
    foreach ($name in $settings.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name)
        [Environment]::SetEnvironmentVariable($name, $settings[$name])
    }
    $release = '0.8.16'
    $repo = 'https://repo.maven.apache.org/maven2/org/jacoco'
    $cli = Join-Path $work 'jacococli.jar'
    Invoke-WebRequest -UseBasicParsing -TimeoutSec 45 -Uri "$repo/org.jacoco.agent/$release/org.jacoco.agent-$release-runtime.jar" -OutFile (Join-Path $work 'jacocoagent.jar')
    Invoke-WebRequest -UseBasicParsing -TimeoutSec 45 -Uri "$repo/org.jacoco.cli/$release/org.jacoco.cli-$release-nodeps.jar" -OutFile $cli

    try {
        Invoke-Docker @stack up -d --build --wait
        & node tests/acceptance.mjs
        if ($LASTEXITCODE -ne 0) { throw "Acceptance failed with exit code $LASTEXITCODE." }
    } finally {
        Invoke-Docker @stack stop -t 30
    }

    & (Join-Path $PSScriptRoot 'production-smoke.ps1') -Port $Port -CoverageDirectory $work

    $reportArgs = @('report')
    foreach ($service in 'auth','schedule','checkin','progress','gateway') {
        $out = Join-Path $work $service
        $null = New-Item -ItemType Directory -Path $out
        $archive = Join-Path $out 'service.jar'
        Invoke-Docker cp "$project-$service-1:/app/service.jar" $archive
        Push-Location -LiteralPath $out
        try {
            & $jar xf $archive BOOT-INF/classes BOOT-INF/lib/service-common-0.1.0-SNAPSHOT.jar
            if ($LASTEXITCODE -ne 0) { throw "Cannot extract classes for $service." }
        } finally { Pop-Location }
        $reportArgs += @((Join-Path $work "$service.exec"))
        $reportArgs += @('--classfiles', (Join-Path $out 'BOOT-INF\classes'))
    }

    # Use the exact application bytecode loaded by the instrumented JVMs.
    $common = Join-Path $work 'checkin\BOOT-INF\lib\service-common-0.1.0-SNAPSHOT.jar'
    $output = Join-Path $reportDir 'acceptance.xml'
    & $java -jar $cli @reportArgs --classfiles $common --encoding UTF-8 --xml $output
    if ($LASTEXITCODE -ne 0) { throw "JaCoCo failed with exit code $LASTEXITCODE." }
    Get-Item -LiteralPath $output
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name])
    }
    Pop-Location
    $resolved = (Resolve-Path -LiteralPath $work).Path
    if ((Split-Path -Parent $resolved) -eq (Resolve-Path -LiteralPath $reportDir).Path) {
        Remove-Item -LiteralPath $resolved -Recurse
    }
}
