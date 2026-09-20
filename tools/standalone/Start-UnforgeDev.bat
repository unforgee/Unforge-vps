@echo off
rem One-command launcher for the whole UnForge development environment.
rem Passes every argument through, e.g. Start-UnforgeDev.bat -NoClient
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-UnforgeDev.ps1" %*
exit /b %ERRORLEVEL%
