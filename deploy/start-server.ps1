# ========================================================================
# Script: start-server.ps1
# Mục đích: Tự động kiểm tra môi trường, khởi động Docker, chạy Backend,
#           bật Tailscale Funnel và tự động lấy địa chỉ HTTPS của từng máy.
# ========================================================================

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Write-Header($text) {
    Write-Host "`n========================================================================" -ForegroundColor Cyan
    Write-Host "  $text" -ForegroundColor Cyan
    Write-Host "========================================================================" -ForegroundColor Cyan
}

function Write-Success($text) {
    Write-Host "  [OK] $text" -ForegroundColor Green
}

function Write-Info($text) {
    Write-Host "  [..] $text" -ForegroundColor Gray
}

function Write-Warn($text) {
    Write-Host "  [!] $text" -ForegroundColor Yellow
}

function Write-Failure($text) {
    Write-Host "  [X] $text" -ForegroundColor Red
}

$rootDir = Split-Path -Parent $PSScriptRoot
Set-Location $rootDir

Write-Header "MUAYTHAI & FITNESS CENTER - TỰ ĐỘNG KHỞI ĐỘNG HỆ THỐNG"

# ------------------------------------------------------------------------
# 1. KIỂM TRA VÀ TẠO CẤU HÌNH deploy/.env
# ------------------------------------------------------------------------
Write-Host "`n1. Kiểm tra cấu hình môi trường (.env)..." -ForegroundColor White
$envFile = Join-Path $rootDir "deploy\.env"

if (-not (Test-Path $envFile)) {
    Write-Info "Chưa tìm thấy deploy\.env. Đang tự động khởi tạo dữ liệu mẫu..."
    & (Join-Path $rootDir "deploy\init-demo.ps1")
    if (-not (Test-Path $envFile)) {
        Write-Failure "Không thể tạo file deploy\.env. Vui lòng kiểm tra lại quyền truy cập."
        exit 1
    }
    Write-Success "Đã tạo thành công file deploy\.env với mật khẩu mẫu an toàn."
} else {
    Write-Success "File cấu hình deploy\.env đã sẵn sàng."
}

# Đọc thông tin tài khoản demo từ .env
$envLines = Get-Content $envFile -ErrorAction SilentlyContinue
$adminPw = ($envLines | Where-Object { $_ -match '^DEMO_ADMIN_PASSWORD=(.+)$' } | ForEach-Object { $matches[1] })
$trainerPw = ($envLines | Where-Object { $_ -match '^DEMO_TRAINER_PASSWORD=(.+)$' } | ForEach-Object { $matches[1] })
$learnerPw = ($envLines | Where-Object { $_ -match '^DEMO_LEARNER_PASSWORD=(.+)$' } | ForEach-Object { $matches[1] })

# ------------------------------------------------------------------------
# 2. KIỂM TRA VÀ TỰ ĐỘNG BẬT DOCKER DESKTOP
# ------------------------------------------------------------------------
Write-Host "`n2. Kiểm tra Docker Desktop..." -ForegroundColor White

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Failure "Không tìm thấy lệnh 'docker'. Vui lòng cài đặt Docker Desktop từ: https://www.docker.com/"
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
    Write-Warn "Docker Desktop chưa chạy. Đang tự động tìm và khởi động..."
    
    $candidatePaths = @(
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
        "C:\Program Files\Docker\Docker\Docker Desktop.exe",
        "$env:LOCALAPPDATA\Programs\Docker Desktop\Docker Desktop.exe"
    )
    
    $dockerExe = $candidatePaths | Where-Object { Test-Path $_ } | Select-Object -First 1

    if ($dockerExe) {
        Start-Process -FilePath $dockerExe
        Write-Info "Đã gửi lệnh mở Docker Desktop ($dockerExe)."
        Write-Info "Đang đợi Docker Engine sẵn sàng (tối đa 90 giây)..."

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
        Write-Failure "Không tìm thấy đường dẫn 'Docker Desktop.exe'. Vui lòng tự mở Docker Desktop bằng tay."
        exit 1
    }

    if (-not $dockerRunning) {
        Write-Host ""
        Write-Failure "Docker Desktop khởi động quá lâu hoặc gặp lỗi. Vui lòng kiểm tra Docker Desktop rồi chạy lại."
        exit 1
    }
}

Write-Success "Docker Engine đã sẵn sàng hoạt động."

# ------------------------------------------------------------------------
# 3. KHỞI ĐỘNG CÁC SERVICE BACKEND QUA DOCKER COMPOSE
# ------------------------------------------------------------------------
Write-Host "`n3. Khởi động các dịch vụ Backend (Docker Compose)..." -ForegroundColor White
Write-Info "Đang đồng bộ containers (Gateway, Auth, Schedule, Checkin, Progress, PostgreSQL)..."

docker compose --env-file deploy/.env -p deploy -f deploy/compose.yaml up -d
if ($LASTEXITCODE -ne 0) {
    Write-Failure "Khởi động Docker Compose thất bại. Vui lòng kiểm tra log lỗi ở trên."
    exit 1
}

Write-Info "Đang chờ API Gateway khởi động hoàn tất (kiểm tra http://127.0.0.1:8080/actuator/health)..."
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
        # Đang khởi động
    }
    Start-Sleep -Seconds 2
    $waited += 2
    Write-Host -NoNewline "." -ForegroundColor Gray
}
Write-Host ""

if ($backendReady) {
    Write-Success "Backend Microservices đã hoạt động tốt tại cổng 8080 (Status: UP)."
} else {
    Write-Warn "Backend đang mất nhiều thời gian hơn dự kiến để khởi động. Bạn vẫn có thể tiếp tục chờ."
}

# ------------------------------------------------------------------------
# 4. KIỂM TRA TAILSCALE, BẬT FUNNEL & TỰ ĐỘNG LẤY ĐỊA CHỈ HTTPS
# ------------------------------------------------------------------------
Write-Host "`n4. Kiểm tra Tailscale & Kích hoạt HTTPS từ xa..." -ForegroundColor White
$tailscaleAvailable = $false
$tailscaleHttpsUrl = $null

if (Get-Command tailscale -ErrorAction SilentlyContinue) {
    try {
        $tsJson = tailscale status --json 2>$null | ConvertFrom-Json -ErrorAction Stop
        if ($tsJson.BackendState -eq "Running") {
            $tailscaleAvailable = $true
            $rawDns = $tsJson.Self.DNSName.TrimEnd('.')
            $tailscaleHttpsUrl = "https://$rawDns"
            
            Write-Success "Tailscale đã đăng nhập: $rawDns"
            Write-Info "Đang tự động bật chế độ Funnel (HTTPS công khai)..."
            
            # Kích hoạt Funnel proxy vào cổng 8080
            $null = tailscale funnel --bg --yes --https=443 http://127.0.0.1:8080 2>&1
            
            Write-Success "Đã mở cổng HTTPS thành công: $tailscaleHttpsUrl"
        } else {
            Write-Warn "Ứng dụng Tailscale đang ở trạng thái: '$($tsJson.BackendState)'."
            Write-Warn "Vui lòng mở Tailscale và đăng nhập để kích hoạt đường link HTTPS từ xa."
        }
    } catch {
        Write-Warn "Không thể lấy thông tin từ Tailscale ($($_.Exception.Message))."
    }
} else {
    Write-Warn "Chưa tìm thấy lệnh 'tailscale'. Máy chủ vẫn hoạt động tốt ở mạng nội bộ (localhost:8080)."
    Write-Info "Nếu muốn truy cập từ xa qua 4G, hãy cài Tailscale từ: https://tailscale.com/download"
}

# ------------------------------------------------------------------------
# 5. BẢNG TỔNG KẾT VÀ HƯỚNG DẪN KẾT NỐI
# ------------------------------------------------------------------------
Write-Header "HỆ THỐNG ĐÃ KHỞI ĐỘNG HOÀN TẤT VÀ SẴN SÀNG"

Write-Host "  [+] ĐỊA CHỈ TRUY CẬP MÁY CHỦ:" -ForegroundColor Yellow
Write-Host "      * Cục bộ (Máy tính / USB / Emulator):  http://127.0.0.1:8080" -ForegroundColor White
if ($tailscaleHttpsUrl) {
    Write-Host "      * Từ xa qua Internet (4G / Mạng ngoài): $tailscaleHttpsUrl" -ForegroundColor Green
} else {
    Write-Host "      * Từ xa qua Internet:                  (Chưa bật Tailscale)" -ForegroundColor DarkGray
}

Write-Host "`n  [+] THÔNG TIN ĐĂNG NHẬP DEMO (Lấy tự động từ deploy/.env):" -ForegroundColor Yellow
Write-Host "      1. QUẢN TRỊ VIÊN (Admin):" -ForegroundColor White
Write-Host "         Email:    admin@example.test" -ForegroundColor Cyan
Write-Host "         Mật khẩu: $adminPw" -ForegroundColor Cyan

Write-Host "`n      2. HUẤN LUYỆN VIÊN (Trainer):" -ForegroundColor White
Write-Host "         Email:    trainer@example.test" -ForegroundColor Cyan
Write-Host "         Mật khẩu: $trainerPw" -ForegroundColor Cyan

Write-Host "`n      3. HỌC VIÊN (Learner):" -ForegroundColor White
Write-Host "         Email:    learner@example.test" -ForegroundColor Cyan
Write-Host "         Mật khẩu: $learnerPw" -ForegroundColor Cyan

if ($tailscaleHttpsUrl) {
    Write-Host "`n  [+] LỆNH BUILD APK ANDROID CHO MÁY NÀY (Chạy qua Internet):" -ForegroundColor Yellow
    Write-Host "      cd android" -ForegroundColor White
    Write-Host "      .\gradlew.bat :app:assembleDebug '-PauthBaseUrl=$tailscaleHttpsUrl/'" -ForegroundColor Green
}

Write-Host "`n========================================================================" -ForegroundColor Cyan
Write-Host "  Mẹo: Bạn có thể thu nhỏ cửa sổ này để máy chủ tiếp tục chạy." -ForegroundColor Gray
Write-Host "========================================================================`n" -ForegroundColor Cyan
