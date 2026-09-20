@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-Unforge239.ps1" %*
exit /b %ERRORLEVEL%
