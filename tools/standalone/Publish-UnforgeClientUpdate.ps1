[CmdletBinding()]
param(
    [string] $ClientJar = '',
    [string] $LauncherJar = '',
    [string] $Version = '1.12.34-unforge239',
    [string] $BaseUrl = 'https://rspsunforge.online/downloads'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

if ([string]::IsNullOrWhiteSpace($ClientJar)) {
    $ClientJar = Join-Path $root 'dist\UnForge239-Client\client.jar'
}
if (-not (Test-Path -LiteralPath $ClientJar -PathType Leaf)) {
    throw "Client JAR puuttuu: $ClientJar"
}
if ([string]::IsNullOrWhiteSpace($LauncherJar)) {
    $LauncherJar = Join-Path $root 'dist\unforge-launcher.jar'
}
if (-not (Test-Path -LiteralPath $LauncherJar -PathType Leaf)) {
    throw "Launcher JAR puuttuu: $LauncherJar"
}

$downloadDirectory = Join-Path $root 'website\downloads'
$fileName = "unforge-client-$Version.jar"
$downloadPath = Join-Path $downloadDirectory $fileName
$launcherDownloadPath = Join-Path $downloadDirectory 'unforge-launcher.jar'
$manifestPath = Join-Path $downloadDirectory 'client-manifest.json'

New-Item -ItemType Directory -Path $downloadDirectory -Force | Out-Null
Copy-Item -LiteralPath $ClientJar -Destination $downloadPath -Force
Copy-Item -LiteralPath $LauncherJar -Destination $launcherDownloadPath -Force
$hash = (Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256).Hash.ToLowerInvariant()
$normalizedBaseUrl = $BaseUrl.TrimEnd('/')

$manifest = [ordered]@{
    launcherVersion = '2.0.0'
    clientVersion = $Version
    clientUrl = "$normalizedBaseUrl/$fileName"
    sha256 = $hash
    releaseDate = (Get-Date).ToUniversalTime().ToString('o')
    releaseNotes = @(
        'Modern launcher with verified client auto-update.'
        'Current Unforge 239 client package.'
        'Companions, Item Instances, and progression systems are ready in the live stack.'
    )
}

$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

$downloadInfo = Get-Item -LiteralPath $downloadPath
$launcherInfo = Get-Item -LiteralPath $launcherDownloadPath
$manifestInfo = Get-Item -LiteralPath $manifestPath
Write-Host ("Client package: {0} ({1:N0} MB)" -f $downloadInfo.FullName, ($downloadInfo.Length / 1MB))
Write-Host ("Launcher package: {0} ({1:N0} MB)" -f $launcherInfo.FullName, ($launcherInfo.Length / 1MB))
Write-Host ("Manifest: {0} ({1:N0} bytes)" -f $manifestInfo.FullName, $manifestInfo.Length)
Write-Host "SHA-256: $hash"
Write-Host "Upload both files into public_html/downloads on Namecheap."
