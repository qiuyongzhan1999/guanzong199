@echo off
chcp 65001 >nul
title gz199 backend
cd /d "%~dp0"
echo.
echo Restarting Java backend...
echo Keep this window open.
echo When you see Started Gz199Application, it is ready.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\scripts\restart-backend.ps1"
echo.
echo Backend stopped. Press any key to close.
pause >nul
