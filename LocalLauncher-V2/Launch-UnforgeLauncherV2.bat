@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Launch-UnforgeLauncherV2.ps1"
exit /b %ERRORLEVEL%
