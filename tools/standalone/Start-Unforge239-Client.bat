@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-Unforge239-Client.ps1" %*
exit /b %ERRORLEVEL%
