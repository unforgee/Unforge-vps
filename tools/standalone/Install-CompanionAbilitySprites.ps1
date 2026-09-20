param(
    [string]$RuneliteDir = "$env:USERPROFILE\.runelite\unforge-cache\sprites"
)

$ErrorActionPreference = 'Stop'
$source = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\client-src\runelite-client\src\main\resources\unforge\companion-icons'))
$target = [IO.Path]::GetFullPath($RuneliteDir)
New-Item -ItemType Directory -Force -Path $target | Out-Null
$manifestPath = Join-Path (Split-Path $target -Parent) 'manifest.json'
$manifest = @{}
if (Test-Path $manifestPath) {
    $raw = Get-Content $manifestPath -Raw
    if ($raw.Trim()) { $manifest = $raw | ConvertFrom-Json -AsHashtable }
}
Get-ChildItem $source -Filter '*.png' | ForEach-Object {
    Copy-Item $_.FullName (Join-Path $target $_.Name) -Force
    $spriteId = $_.BaseName.Split('_')[0]
    $manifest["$spriteId/0"] = $_.Name
}
$manifest | ConvertTo-Json | Set-Content $manifestPath -Encoding UTF8
Write-Host "Installed companion ability sprites to $target"
