@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Setup-UnforgeInstaller.ps1" -PayloadPath "%~dp0payload.zip"
exit /b %ERRORLEVEL%
