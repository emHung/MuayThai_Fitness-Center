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
# 4. KIEM TRA TAILSCALE, BAT FUNNEL VA TU DONG MO DUONG DAN NEU CHUA BAT
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
            
            Write-Success "Tailscale da dang nhap: $rawDns"
            Write-Info "Dang tu dong kich hoat che do Funnel (HTTPS cong khai)..."
            
            # Kich hoat Funnel proxy vao cong 8080 va ghi nhan output
            $funnelOutput = tailscale funnel --bg --yes --https=443 http://127.0.0.1:8080 2>&1 | Out-String
            
            # Kiem tra xem Funnel da thuc su bat thanh cong chua (kiem tra JSON va text)
            $statusJson = tailscale funnel status --json 2>$null | ConvertFrom-Json -ErrorAction SilentlyContinue
            if ($statusJson -and $statusJson.AllowFunnel) {
                foreach ($prop in $statusJson.AllowFunnel.PSObject.Properties) {
                    if ($prop.Value -eq $true) {
                        $funnelActive = $true
                        break
                    }
                }
            }
            if (-not $funnelActive) {
                $rawStatus = tailscale funnel status 2>&1 | Out-String
                if ($rawStatus -match 'Funnel on') {
                    $funnelActive = $true
                }
            }
            
            if ($funnelActive) {
                Write-Success "Da mo cong HTTPS thanh cong: $tailscaleHttpsUrl"
            } else {
                Write-Warn "Tailscale Funnel chua duoc bat tren may chu hoac tai khoan nay!"
                
                # Tim duong dan Tailscale tra ve neu co link
                $openUrl = $null
                if ($funnelOutput -match '(https?://(login|console)\.tailscale\.com/[^\s''"<>]+)') {
                    $openUrl = $matches[1]
                } else {
                    $openUrl = "https://login.tailscale.com/admin/dns"
                }
                
                Write-Info "Dang tu dong mo trinh duyet den trang cau hinh Tailscale de ban bat Funnel..."
                Write-Host "  Duong dan cau hinh: $openUrl" -ForegroundColor Cyan
                try {
                    Start-Process $openUrl
                } catch {
                    # Bo qua neu loi mo trinh duyet
                }
                
                Write-Host "`n  [*] CAC BUOC BAT FUNNEL TRONG TAILSCALE ADMIN CONSOLE (Chi can lam 1 lan):" -ForegroundColor Yellow
                Write-Host "      1. Bat 'MagicDNS' va nhan 'Enable HTTPS' tai:" -ForegroundColor White
                Write-Host "         https://login.tailscale.com/admin/dns" -ForegroundColor Cyan
                Write-Host "      2. Neu chua cap quyen, them 'funnel' vao ACL (Access Controls) tai:" -ForegroundColor White
                Write-Host "         https://login.tailscale.com/admin/acls" -ForegroundColor Cyan
                Write-Host '         "nodeAttrs": [{"target": ["autogroup:member"], "attr": ["funnel"]}]' -ForegroundColor DarkGray
                Write-Host "      3. Sau khi bat xong tren web, hay chay lai: start.bat`n" -ForegroundColor Green
            }
        } else {
            Write-Warn "Ung dung Tailscale dang o trang thai: $($tsJson.BackendState)"
            Write-Info "Dang lay duong dan dang nhap..."
            $upOutput = tailscale up 2>&1 | Out-String
            $loginUrl = $null
            if ($upOutput -match '(https?://login\.tailscale\.com/a/[^\s''"<>]+)') {
                $loginUrl = $matches[1]
            } else {
                $loginUrl = "https://login.tailscale.com/admin"
            }
            Write-Info "Dang tu dong mo trinh duyet de ban dang nhap Tailscale..."
            Write-Host "  Duong dan dang nhap: $loginUrl" -ForegroundColor Cyan
            try {
                Start-Process $loginUrl
            } catch {
                # Bo qua
            }
            Write-Warn "Vui long dang nhap tren trinh duyet roi chay lai: start.bat"
        }
    } catch {
        Write-Warn "Khong the lay thong tin tu Tailscale ($($_.Exception.Message))."
    }
} else {
    Write-Warn "Chua tim thay cong cu tailscale. May chu van hoat dong o mang cuc bo (localhost:8080)."
    Write-Info "Dang tu dong mo trinh duyet den trang tai Tailscale..."
    Write-Host "  Duong dan tai: https://tailscale.com/download" -ForegroundColor Cyan
    try {
        Start-Process "https://tailscale.com/download"
    } catch {
        # Bo qua
    }
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
    Write-Host "      * Tu xa qua Internet (4G / Mang ngoai): (Chua bat Funnel - Hay cau hinh tren web vua mo)" -ForegroundColor Yellow
    Write-Host "        Duong dan cau hinh:                  https://login.tailscale.com/admin/dns" -ForegroundColor Cyan
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
        Write-Host "      (Meo: Sau khi bat Funnel tren trang web vua mo, chay lai start.bat de lay link HTTPS)" -ForegroundColor Gray
    }
}

Write-Host "`n========================================================================" -ForegroundColor Cyan
Write-Host "  Meo: Ban co the thu nho cua so nay de may chu tiep tuc chay." -ForegroundColor Gray
Write-Host "  Khi muon dung he thong va giai phong RAM, hay chay: stop.bat" -ForegroundColor Gray
Write-Host "========================================================================`n" -ForegroundColor Cyan
