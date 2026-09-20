$ErrorActionPreference = 'Stop'

$launcherDirectory = (Resolve-Path $PSScriptRoot).Path
$desktop = [Environment]::GetFolderPath('Desktop')
$shortcutPath = Join-Path $desktop 'UnForge Launcher V2.lnk'

$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = Join-Path $launcherDirectory 'Launch-UnforgeLauncherV2.bat'
$shortcut.WorkingDirectory = $launcherDirectory
$shortcut.WindowStyle = 1
$shortcut.Description = 'UnForge revision 239 launcher V2'
$shortcut.Save()

Write-Host "Shortcut created at $shortcutPath"
