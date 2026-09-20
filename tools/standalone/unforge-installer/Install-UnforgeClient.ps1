[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $SourceRoot
)

$ErrorActionPreference = 'Stop'
$installRoot = Join-Path $env:LOCALAPPDATA 'Unforge'
$desktop = [Environment]::GetFolderPath('Desktop')
$desktopJar = Join-Path $desktop 'unforge.jar'
$installedJar = Join-Path $installRoot 'unforge.jar'
$runtimeSource = Join-Path $SourceRoot 'runtime'
$runtimeTarget = Join-Path $installRoot 'runtime'

if (-not (Test-Path -LiteralPath (Join-Path $SourceRoot 'unforge.jar') -PathType Leaf)) {
    throw 'Paketti ei sisällä unforge.jar-tiedostoa.'
}
if (-not (Test-Path -LiteralPath (Join-Path $runtimeSource 'bin\javaw.exe') -PathType Leaf)) {
    throw 'Paketti ei sisällä Java 21 -runtimea.'
}

if (Test-Path -LiteralPath $installRoot) {
    Remove-Item -LiteralPath $installRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $installRoot -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $SourceRoot 'unforge.jar') -Destination $installedJar -Force
Copy-Item -LiteralPath $runtimeSource -Destination $runtimeTarget -Recurse -Force
Copy-Item -LiteralPath $installedJar -Destination $desktopJar -Force

$javaw = Join-Path $runtimeTarget 'bin\javaw.exe'
$shortcutPath = Join-Path $desktop 'UnForge Client.lnk'
if (Test-Path -LiteralPath $shortcutPath) {
    Remove-Item -LiteralPath $shortcutPath -Force
}
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $javaw
$shortcut.Arguments = '-jar "' + $desktopJar + '"'
$shortcut.WorkingDirectory = $installRoot
$shortcut.IconLocation = $javaw + ',0'
$shortcut.Description = 'UnForge revision 239 client'
$shortcut.Save()

Write-Host 'UnForge asennettu.'
Write-Host ("Client-JAR: {0}" -f $desktopJar)
Write-Host ("Suora käynnistyskuvake: {0}" -f $shortcutPath)
Write-Host 'Server: 94.237.118.174:43594'
