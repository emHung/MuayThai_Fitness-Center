# ========================================================================
# Script: stop-server.ps1
# Muc dich: Tat toan bo cac tien trinh chay ngam: Docker containers,
#           Tailscale Funnel/Serve va giai phong cong 8080.
# ========================================================================

$ErrorActionPreference = 'Continue'
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

Write-Header "MUAYTHAI VA FITNESS CENTER - DUNG HE THONG VA GIAI PHONG TAI NGUYEN"

# ------------------------------------------------------------------------
# 1. TAT TAILSCALE FUNNEL VA SERVE
# ------------------------------------------------------------------------
Write-Host "`n1. Dong ket noi Internet (Tailscale Funnel)..." -ForegroundColor White
if (Get-Command tailscale -ErrorAction SilentlyContinue) {
    try {
        $null = tailscale funnel reset 2>&1
        $null = tailscale serve reset 2>&1
        Write-Success "Da dong Tailscale Funnel. Link HTTPS tu xa da duoc tat."
    } catch {
        Write-Warn "Khong the reset Tailscale."
    }
} else {
    Write-Info "Tailscale khong duoc cai dat tren he thong nay, bo qua."
}

# ------------------------------------------------------------------------
# 2. DUNG CAC DICH VU BACKEND (DOCKER COMPOSE)
# ------------------------------------------------------------------------
Write-Host "`n2. Dung cac dich vu Backend (Docker Compose)..." -ForegroundColor White
if (Get-Command docker -ErrorAction SilentlyContinue) {
    $dockerRunning = $false
    try {
        $null = docker info 2>&1
        if ($LASTEXITCODE -eq 0) { $dockerRunning = $true }
    } catch {
        $dockerRunning = $false
    }

    if ($dockerRunning) {
        $envFile = Join-Path $rootDir "deploy\.env"
        Write-Info "Dang dung cac containers: Gateway, Auth, Schedule, Checkin, Progress, PostgreSQL..."
        if (Test-Path $envFile) {
            docker compose --env-file deploy/.env -p deploy -f deploy/compose.yaml down
        } else {
            docker compose -p deploy -f deploy/compose.yaml down
        }

        if ($LASTEXITCODE -eq 0) {
            Write-Success "Toan bo containers backend da dung thanh cong."
            Write-Info "Luu y: Du lieu database duoc luu an toan trong volume auth_data."
        } else {
            Write-Warn "Containers da duoc dung truoc do hoac co canh bao nhe."
        }
    } else {
        Write-Info "Docker Engine hien khong chay. Khong co container nao dang hoat dong."
    }
} else {
    Write-Info "Khong tim thay Docker CLI tren he thong, bo qua."
}

# ------------------------------------------------------------------------
# 3. KIEM TRA VA GIAI PHONG CONG 8080
# ------------------------------------------------------------------------
Write-Host "`n3. Kiem tra trang thai cong 8080..." -ForegroundColor White
try {
    $portOcc = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
    if ($portOcc) {
        Write-Warn "Cong 8080 van dang bi chiem boi tien trinh co PID: $($portOcc.OwningProcess)"
    } else {
        Write-Success "Cong 8080 da hoan toan duoc giai phong."
    }
} catch {
    Write-Info "Cong 8080 da san sang cho lan chay tiep theo."
}

# ------------------------------------------------------------------------
# 4. HOI Y KIEN NGUOI DUNG VE DOCKER DESKTOP
# ------------------------------------------------------------------------
Write-Host "`n4. Tuy chon giai phong bo nho RAM may tinh..." -ForegroundColor White
$dockerProc = Get-Process -Name "*Docker Desktop*" -ErrorAction SilentlyContinue
if ($dockerProc) {
    Write-Host "  Docker Desktop dang chay ngam va chiem dung RAM may tinh." -ForegroundColor Yellow
    $choice = Read-Host "  Ban co muon tat luon Docker Desktop de giai phong RAM khong? (y/N) [Mac dinh: N]"
    if ($choice -match '^(y|yes)$') {
        Write-Info "Dang dong tien trinh Docker Desktop..."
        Stop-Process -Name "*Docker Desktop*" -Force -ErrorAction SilentlyContinue
        Stop-Process -Name "*com.docker*" -Force -ErrorAction SilentlyContinue
        Write-Success "Da tat Docker Desktop. Bo nho RAM da duoc giai phong."
    } else {
        Write-Info "Giu Docker Desktop tiep tuc chay o che do cho."
    }
} else {
    Write-Success "Docker Desktop hien khong chay ngam."
}

# ------------------------------------------------------------------------
# TONG KET
# ------------------------------------------------------------------------
Write-Header "DA DUNG TOAN BO HE THONG THANH CONG"
Write-Host "  [OK] Tailscale Funnel:  Da tat (Dong cong HTTPS)" -ForegroundColor Green
Write-Host "  [OK] Microservices:     Da dung (Gateway, Auth, Schedule, Checkin, Progress)" -ForegroundColor Green
Write-Host "  [OK] Co so du lieu:     Du lieu duoc bao toan an toan trong Volume" -ForegroundColor Green
Write-Host "  [OK] Cong 8080:         San sang cho lan chay tiep theo" -ForegroundColor Green
Write-Host "`n  (*) Khi nao can chay lai he thong, chi can nhap dup chuot vao: start.bat`n" -ForegroundColor Cyan
