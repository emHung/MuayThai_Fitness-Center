# ========================================================================
# Script: start-server.ps1
# Muc dich: Tu dong kiem tra moi truong, khoi dong Docker, chay Backend,
#           bat Tailscale Funnel va tu dong lay dia chi HTTPS cua tung may.
# ========================================================================

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Write-Header([string]$text) {
    Write-Host "`n========================================================================" -ForegroundColor Cyan
    Write-Host "  $text" -ForegroundColor Cyan
    Write-Host "========================================================================" -ForegroundColor Cyan
}

function Write-Success([string]$text) {
    Write-Host "  [OK] $text" -ForegroundColor Green
}

function Write-Info([string]$text) {
    Write-Host "  [..] $text" -ForegroundColor Gray
}

function Write-Warn([string]$text) {
    Write-Host "  [!] $text" -ForegroundColor Yellow
}

function Write-Failure([string]$text) {
    Write-Host "  [X] $text" -ForegroundColor Red
}

function Open-WebUrl([string]$url) {
    $chromePath = @(
        "C:\Program Files\Google\Chrome\Application\chrome.exe",
        "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1

    try {
        if ($chromePath) {
            Start-Process -FilePath $chromePath -ArgumentList $url
        } else {
            Start-Process $url
        }
    } catch {
        # Fallback
    }
}

$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
if (-not $scriptDir) { $scriptDir = (Join-Path (Get-Location) "deploy") }
$rootDir = Split-Path -Parent $scriptDir
Set-Location $rootDir

Write-Header "MUAYTHAI VA FITNESS CENTER - TU DONG KHOI DONG HE THONG"

# ------------------------------------------------------------------------
# 1. KIEM TRA VA TAO CAU HINH deploy/.env
# ------------------------------------------------------------------------
Write-Host "`n1. Kiem tra cau hinh moi truong (.env)..." -ForegroundColor White
$envFile = Join-Path $rootDir "deploy\.env"

if (-not (Test-Path $envFile)) {
    Write-Info "Chua tim thay deploy\.env. Dang tu dong khoi tao du lieu mau..."
    & (Join-Path $rootDir "deploy\init-demo.ps1")
    if (-not (Test-Path $envFile)) {
        Write-Failure "Khong the tao file deploy\.env. Vui long kiem tra lai quyen truy cap."
        exit 1
    }
    Write-Success "Da tao thanh cong file deploy\.env voi mat khau mau an toan."
} else {
    Write-Success "File cau hinh deploy\.env da san sang."
}

# Doc thong tin tai khoan demo tu .env
$envLines = Get-Content $envFile -ErrorAction SilentlyContinue
$adminPw = ($envLines | Where-Object { $_ -match '^DEMO_ADMIN_PASSWORD=(.+)$' } | ForEach-Object { $matches[1] })
$trainerPw = ($envLines | Where-Object { $_ -match '^DEMO_TRAINER_PASSWORD=(.+)$' } | ForEach-Object { $matches[1] })
$learnerPw = ($envLines | Where-Object { $_ -match '^DEMO_LEARNER_PASSWORD=(.+)$' } | ForEach-Object { $matches[1] })

# ------------------------------------------------------------------------
# 2. KIEM TRA VA TU DONG BAT DOCKER DESKTOP
# ------------------------------------------------------------------------
Write-Host "`n2. Kiem tra Docker Desktop..." -ForegroundColor White

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Failure "Khong tim thay lenh docker. Vui long cai dat Docker Desktop tu: https://www.docker.com/"
    exit 1
}

$dockerRunning = $false
try {
    $null = docker info 2>&1
    if ($LASTEXITCODE -eq 0) {
        $dockerRunning = $true
    }
} catch {
    $dockerRunning = $false
}

if (-not $dockerRunning) {
    Write-Warn "Docker Desktop chua chay. Dang tu dong tim va khoi dong..."
    
    $candidatePaths = @(
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
        "C:\Program Files\Docker\Docker\Docker Desktop.exe",
        "$env:LOCALAPPDATA\Programs\Docker Desktop\Docker Desktop.exe"
    )
    
    $dockerExe = $candidatePaths | Where-Object { Test-Path $_ } | Select-Object -First 1

    if ($dockerExe) {
        Start-Process -FilePath $dockerExe
        Write-Info "Da gui lenh mo Docker Desktop ($dockerExe)."
        Write-Info "Dang doi Docker Engine san sang (toi da 90 giay)..."

        $timeoutSeconds = 90
        $elapsed = 0
        while ($elapsed -lt $timeoutSeconds) {
            Start-Sleep -Seconds 3
            $elapsed += 3
            Write-Host -NoNewline "." -ForegroundColor Gray
            
            $null = docker info 2>&1
            if ($LASTEXITCODE -eq 0) {
                Write-Host ""
                $dockerRunning = $true
                break
            }
        }
    } else {
        Write-Failure "Khong tim thay duong dan Docker Desktop.exe. Vui long tu mo Docker Desktop bang tay."
        exit 1
    }

    if (-not $dockerRunning) {
        Write-Host ""
        Write-Failure "Docker Desktop khoi dong qua lau hoac gap loi. Vui long kiem tra Docker Desktop roi chay lai."
        exit 1
    }
}

Write-Success "Docker Engine da san sang hoat dong."

# ------------------------------------------------------------------------
# 3. KHOI DONG CAC SERVICE BACKEND QUA DOCKER COMPOSE
# ------------------------------------------------------------------------
Write-Host "`n3. Khoi dong cac dich vu Backend (Docker Compose)..." -ForegroundColor White
Write-Info "Dang dong bo containers (Gateway, Auth, Schedule, Checkin, Progress, PostgreSQL)..."

docker compose --env-file deploy/.env -p deploy -f deploy/compose.yaml up -d
if ($LASTEXITCODE -ne 0) {
    Write-Failure "Khoi dong Docker Compose that bai. Vui long kiem tra log loi o tren."
    exit 1
}

Write-Info "Dang cho API Gateway khoi dong hoan tat (kiem tra http://127.0.0.1:8080/actuator/health)..."
$backendReady = $false
$maxHealthCheckWait = 45
$waited = 0

while ($waited -lt $maxHealthCheckWait) {
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:8080/actuator/health" -Method Get -TimeoutSec 3 -ErrorAction Stop
        if ($health.status -eq "UP") {
            $backendReady = $true
            break
        }
    } catch {
        # Dang khoi dong
    }
    Start-Sleep -Seconds 2
    $waited += 2
    Write-Host -NoNewline "." -ForegroundColor Gray
}
Write-Host ""

if ($backendReady) {
    Write-Success "Backend Microservices da hoat dong tot tai cong 8080 (Status: UP)."
} else {
    Write-Warn "Backend dang mat nhieu thoi gian hon du kien de khoi dong. Ban van co the tiep tuc cho."
}

# ------------------------------------------------------------------------
# 4. KIEM TRA TAILSCALE, BAT FUNNEL VA TU DONG MO CHROME NEU CHUA BAT
# ------------------------------------------------------------------------
Write-Host "`n4. Kiem tra Tailscale va Kich hoat HTTPS tu xa..." -ForegroundColor White
$tailscaleAvailable = $false
$tailscaleHttpsUrl = $null
$funnelActive = $false

if (Get-Command tailscale -ErrorAction SilentlyContinue) {
    try {
        $tsJson = tailscale status --json 2>$null | ConvertFrom-Json -ErrorAction Stop
        if ($tsJson.BackendState -eq "Running") {
            $tailscaleAvailable = $true
            $rawDns = $tsJson.Self.DNSName.TrimEnd('.')
            $tailscaleHttpsUrl = "https://$rawDns"
            $nodeId = $tsJson.Self.ID
            $funnelWebUrl = if ($nodeId) { "https://login.tailscale.com/f/funnel?node=$nodeId" } else { "https://login.tailscale.com/admin/dns" }

            Write-Success "Tailscale da dang nhap: $rawDns"

            # Kiem tra xem may nay da duoc bat quyen Funnel tren Tailnet chua
            $hasFunnelCap = ($tsJson.Self.CapMap -and $tsJson.Self.CapMap.funnel -ne $null)
            $hasCertDomains = ($tsJson.CertDomains -ne $null -and $tsJson.CertDomains.Count -gt 0)

            if (-not $hasFunnelCap -or -not $hasCertDomains) {
                Write-Warn "Tailscale Funnel chua duoc bat tren tailnet cua ban!"
                Write-Info "Dang tu dong mo trinh duyet Chrome den trang kich hoat Funnel..."
                Write-Host "  Duong dan kich hoat: $funnelWebUrl" -ForegroundColor Cyan
                Open-WebUrl $funnelWebUrl
                Write-Info "Vui long nhan nut 'Enable Funnel' tren trang Chrome vua mo."
                Write-Info "Dang cho kich hoat (toi da 20 giay)..."
            } else {
                Write-Info "Dang tu dong kich hoat che do Funnel proxy vao cong 8080..."
            }

            # Chay lenh tailscale funnel bang Process de khong bi treo console
            $psi = New-Object System.Diagnostics.ProcessStartInfo
            $psi.FileName = "tailscale.exe"
            $psi.Arguments = "funnel --bg --yes --https=443 http://127.0.0.1:8080"
            $psi.UseShellExecute = $false
            $psi.CreateNoWindow = $true
            $psi.RedirectStandardOutput = $true
            $psi.RedirectStandardError = $true

            $proc = [System.Diagnostics.Process]::Start($psi)
            $maxWaitSec = if (-not $hasFunnelCap -or -not $hasCertDomains) { 20 } else { 8 }
            $waitedSec = 0

            while (-not $proc.HasExited -and $waitedSec -lt $maxWaitSec) {
                Start-Sleep -Seconds 1
                $waitedSec++
                Write-Host -NoNewline "." -ForegroundColor Gray
                
                $statusJson = tailscale funnel status --json 2>$null | ConvertFrom-Json -ErrorAction SilentlyContinue
                if ($statusJson -and $statusJson.AllowFunnel) {
                    $funnelActive = $true
                    break
                }
            }
            Write-Host ""

            if (-not $proc.HasExited) {
                try { $proc.Kill() } catch { }
            }

            # Kiem tra lai trang thai cuoi cung
            if (-not $funnelActive) {
                $rawStatus = tailscale funnel status 2>&1 | Out-String
                if ($rawStatus -match 'Funnel on') {
                    $funnelActive = $true
                }
            }

            if ($funnelActive) {
                Write-Success "Da mo cong HTTPS cong khai thanh cong: $tailscaleHttpsUrl"
            } else {
                Write-Warn "Chua kich hoat duoc Funnel HTTPS. May chu van hoat dong o mang cuc bo."
                Write-Host "  (*) Neu ban vua nhan 'Enable Funnel' tren Chrome, hay chay lai start.bat de he thong nhan dien.`n" -ForegroundColor Yellow
            }
        } else {
            Write-Warn "Ung dung Tailscale dang o trang thai: $($tsJson.BackendState)"
            Write-Info "Dang lay duong dan dang nhap..."
            $upOutput = tailscale up 2>&1 | Out-String
            $loginUrl = if ($upOutput -match '(https?://login\.tailscale\.com/a/\S+)') { $matches[1] } else { "https://login.tailscale.com/admin" }
            Write-Info "Dang tu dong mo Chrome de ban dang nhap Tailscale..."
            Write-Host "  Duong dan dang nhap: $loginUrl" -ForegroundColor Cyan
            Open-WebUrl $loginUrl
            Write-Warn "Vui long dang nhap tren trinh duyet roi chay lai: start.bat"
        }
    } catch {
        Write-Warn "Khong the lay thong tin tu Tailscale ($($_.Exception.Message))."
    }
} else {
    Write-Warn "Chua tim thay cong cu tailscale tren may tinh."
    Write-Info "Dang tu dong mo Chrome den trang tai: https://tailscale.com/download"
    Open-WebUrl "https://tailscale.com/download"
}

# ------------------------------------------------------------------------
# 5. BANG TONG KET VA HUONG DAN KET NOI
# ------------------------------------------------------------------------
Write-Header "HE THONG DA KHOI DONG HOAN TAT VA SAN SANG"

Write-Host "  [+] DIA CHI TRUY CAP MAY CHU:" -ForegroundColor Yellow
Write-Host "      * Cuc bo (May tinh / USB / Emulator):  http://127.0.0.1:8080" -ForegroundColor White
if ($tailscaleHttpsUrl -and $funnelActive) {
    Write-Host "      * Tu xa qua Internet (4G / Mang ngoai): $tailscaleHttpsUrl" -ForegroundColor Green
} elseif ($tailscaleHttpsUrl) {
    Write-Host "      * Tu xa qua Internet (4G / Mang ngoai): (Chua bat Funnel - Hay nhan nut tren Chrome vua mo)" -ForegroundColor Yellow
    Write-Host "        Duong dan kich hoat:                 $funnelWebUrl" -ForegroundColor Cyan
} else {
    Write-Host "      * Tu xa qua Internet:                  (Chua bat / dang nhap Tailscale)" -ForegroundColor DarkGray
}

Write-Host "`n  [+] THONG TIN DANG NHAP DEMO (Lay tu dong tu deploy/.env):" -ForegroundColor Yellow
Write-Host "      1. QUAN TRI VIEN (Admin):" -ForegroundColor White
Write-Host "         Email:    admin@example.test" -ForegroundColor Cyan
Write-Host "         Mat khau: $adminPw" -ForegroundColor Cyan

Write-Host "`n      2. HUAN LUYEN VIEN (Trainer):" -ForegroundColor White
Write-Host "         Email:    trainer@example.test" -ForegroundColor Cyan
Write-Host "         Mat khau: $trainerPw" -ForegroundColor Cyan

Write-Host "`n      3. HOC VIEN (Learner):" -ForegroundColor White
Write-Host "         Email:    learner@example.test" -ForegroundColor Cyan
Write-Host "         Mat khau: $learnerPw" -ForegroundColor Cyan

if ($tailscaleHttpsUrl -and $funnelActive) {
    Write-Host "`n  [+] LENH BUILD APK ANDROID CHO MAY NAY (Chay qua Internet):" -ForegroundColor Yellow
    Write-Host "      cd android" -ForegroundColor White
    $apkParam = "-PauthBaseUrl=$tailscaleHttpsUrl/"
    Write-Host "      .\gradlew.bat :app:assembleDebug $apkParam" -ForegroundColor Green
} else {
    Write-Host "`n  [+] LENH BUILD APK ANDROID CHO MAY NAY (Chay mang cuc bo):" -ForegroundColor Yellow
    Write-Host "      cd android" -ForegroundColor White
    Write-Host "      .\gradlew.bat :app:assembleDebug -PauthBaseUrl=http://10.0.2.2:8080/" -ForegroundColor Green
    if ($tailscaleHttpsUrl) {
        Write-Host "      (Meo: Sau khi nhan Enable Funnel tren Chrome, chay lai start.bat de lay link HTTPS)" -ForegroundColor Gray
    }
}

Write-Host "`n========================================================================" -ForegroundColor Cyan
Write-Host "  Meo: Ban co the thu nho cua so nay de may chu tiep tuc chay." -ForegroundColor Gray
Write-Host "  Khi muon dung he thong va giai phong RAM, hay chay: stop.bat" -ForegroundColor Gray
Write-Host "========================================================================`n" -ForegroundColor Cyan
