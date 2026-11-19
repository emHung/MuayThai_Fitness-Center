@echo off
chcp 65001 >nul
title MuayThai ^& Fitness Center - Server Launcher
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy\start-server.ps1"

if %ERRORLEVEL% neq 0 (
    echo.
    echo [!] Da co loi xay ra hoac tien trinh bi dung.
)

echo.
echo Nhan phim bat ky de dong cua so nay...
pause >nul
