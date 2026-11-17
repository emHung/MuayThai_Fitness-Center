[CmdletBinding(SupportsShouldProcess)]
param()

$ErrorActionPreference = 'Stop'
$path = Join-Path $PSScriptRoot '.env'
$current = if (Test-Path -LiteralPath $path) { [IO.File]::ReadAllLines($path) } else { @() }

function New-Secret([int] $Length) {
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $bytes = New-Object byte[] $Length
        $generator.GetBytes($bytes)
        [BitConverter]::ToString($bytes).Replace('-', '')
    } finally {
        $generator.Dispose()
    }
}

$defaults = [ordered]@{
    POSTGRES_PASSWORD = (New-Secret 24)
    JWT_SECRET = (New-Secret 32)
    INTERNAL_KEY = (New-Secret 32)
    DEMO_ENABLED = 'true'
    DEMO_ADMIN_PASSWORD = "A!$(New-Secret 12)"
    DEMO_TRAINER_PASSWORD = "T!$(New-Secret 12)"
    DEMO_LEARNER_PASSWORD = "L!$(New-Secret 12)"
    SCHEDULE_DB_PASSWORD = (New-Secret 24)
    CHECKIN_DB_PASSWORD = (New-Secret 24)
    PROGRESS_DB_PASSWORD = (New-Secret 24)
}
$missing = foreach ($key in $defaults.Keys) {
    if (-not ($current -match ('^' + [regex]::Escape($key) + '='))) {
        "$key=$($defaults[$key])"
    }
}
if (-not $missing) {
    Write-Output 'Cấu hình demo đã có đủ giá trị.'
    return
}
if ($PSCmdlet.ShouldProcess($path, 'Thêm thông tin cấu hình còn thiếu')) {
    [IO.File]::WriteAllLines($path, @($current) + @($missing), [Text.UTF8Encoding]::new($false))
    Write-Output 'Đã cập nhật cấu hình demo cục bộ.'
}
