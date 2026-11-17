[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)] [string] $Destination,
    [ValidatePattern('^[a-z0-9][a-z0-9_-]*$')] [string] $Project = 'deploy'
)

$ErrorActionPreference = 'Stop'
$archive = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Destination)
if (Test-Path -LiteralPath $archive) {
    throw "Tệp sao lưu đã tồn tại."
}
if (-not $PSCmdlet.ShouldProcess($archive, "Sao lưu PostgreSQL từ Compose project $Project")) {
    return
}
$null = New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($archive)) -Force
$partial = $archive + '.' + [Guid]::NewGuid().ToString('N') + '.partial'
$command = [Diagnostics.ProcessStartInfo]::new()
$command.FileName = (Get-Command docker -CommandType Application | Select-Object -First 1).Source
$command.RedirectStandardOutput = $true
$command.RedirectStandardError = $true
$command.UseShellExecute = $false
$compose = Join-Path $PSScriptRoot 'compose.yaml'
$command.Arguments = 'compose -p {0} -f "{1}" exec -T db pg_dumpall -U auth_app --no-role-passwords' -f $Project, $compose

try {
    $process = [Diagnostics.Process]::Start($command)
    try {
        $errors = $process.StandardError.ReadToEndAsync()
        $output = [IO.File]::Open($partial, [IO.FileMode]::CreateNew)
        try {
            $process.StandardOutput.BaseStream.CopyTo($output)
        } finally {
            $output.Dispose()
        }
        $process.WaitForExit()
        $stderr = $errors.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            throw "PostgreSQL báo lỗi khi sao lưu. $stderr"
        }
        if ($stderr) { Write-Warning $stderr }
    } finally {
        $process.Dispose()
    }
    [IO.File]::Move($partial, $archive)
    Get-Item -LiteralPath $archive
} finally {
    if (Test-Path -LiteralPath $partial) {
        Remove-Item -LiteralPath $partial
    }
}
