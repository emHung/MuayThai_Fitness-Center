[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^https://github\.com/[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+\.git$')]
    [string] $RepositoryUrl
)

$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$git = (Get-Command git.exe -CommandType Application).Source
$origin = @(& $git -C $workspace remote get-url --push --all origin)
if ($LASTEXITCODE -ne 0 -or $origin.Count -ne 1 -or $origin[0] -cne $RepositoryUrl) {
    throw 'Unexpected Git push URL for origin.'
}
$head = & $git -C $workspace rev-parse --verify refs/heads/main
if ($LASTEXITCODE -ne 0 -or $head -notmatch '^[0-9a-f]{40}$') {
    throw 'No valid committed main branch.'
}
if (-not $PSCmdlet.ShouldProcess($RepositoryUrl, "Push main commit $head")) {
    return
}

$logDirectory = Join-Path $env:LOCALAPPDATA 'MuayThaiFitness\DailyPublish'
$null = New-Item -ItemType Directory -Path $logDirectory -Force
$logPath = Join-Path $logDirectory 'history.jsonl'
$promptSetting = $env:GIT_TERMINAL_PROMPT
$operation = 'lookup_failed'
try {
    $env:GIT_TERMINAL_PROMPT = '0'
    $remote = @(& $git -C $workspace ls-remote --refs origin refs/heads/main)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot check the GitHub branch.' }
    if ($remote.Count -eq 1 -and ($remote[0] -split '\s+')[0] -eq $head) {
        $operation = 'up_to_date'
        return
    }
    $operation = 'push_failed'
    & $git -C $workspace push origin ("${head}:refs/heads/main")
    if ($LASTEXITCODE -ne 0) { throw 'Git push failed.' }
    $operation = 'verification_failed'
    $verified = @(& $git -C $workspace ls-remote --refs origin refs/heads/main)
    if ($LASTEXITCODE -ne 0 -or $verified.Count -ne 1 -or ($verified[0] -split '\s+')[0] -ne $head) {
        throw 'Could not verify main on GitHub after pushing.'
    }
    $operation = 'pushed'
} finally {
    $env:GIT_TERMINAL_PROMPT = $promptSetting
    [ordered]@{ timestamp = [DateTimeOffset]::Now.ToString('o'); action = 'daily_git_push'; commit = $head;
        result = $operation } | ConvertTo-Json -Compress | Add-Content -LiteralPath $logPath -Encoding UTF8
}
