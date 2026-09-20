[CmdletBinding()]
param(
    [string]$ShortcutPath = "$env:USERPROFILE\Desktop\Unforge Content Studio.lnk",
    [string]$IconPath = ""
)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$launcher = Join-Path $repoRoot 'tools\standalone\Start-UnforgeContentStudio.ps1'

$ws = New-Object -ComObject WScript.Shell
$lnk = $ws.CreateShortcut($ShortcutPath)
$lnk.TargetPath = 'powershell.exe'
$lnk.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$launcher`""
$lnk.WorkingDirectory = $repoRoot
$lnk.Description = 'Unforge Content Studio - paikallinen sisaltoeditori (portti 18930)'
if ($IconPath -and (Test-Path $IconPath)) {
    $lnk.IconLocation = "$IconPath,0"
}
$lnk.Save()

Write-Host "Pikakuvake luotu: $ShortcutPath"
Write-Host "Kohde: powershell.exe -File $launcher"
