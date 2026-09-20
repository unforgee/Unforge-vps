@echo off
rem Frees RAM by stopping stuck Gradle/Kotlin build JVMs. Never touches a running
rem UnForge service or the game client. Pass -DryRun or -IncludeStaleServers through.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Free-UnforgeMemory.ps1" %*
exit /b %ERRORLEVEL%
