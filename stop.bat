@echo off
chcp 65001 >nul
title MuayThai ^& Fitness Center - Server Stopper
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy\stop-server.ps1"

if %ERRORLEVEL% neq 0 (
    echo.
    echo [!] Da co loi xay ra trong qua trinh dung he thong.
)

echo.
echo Nhan phim bat ky de dong cua so nay...
pause >nul
