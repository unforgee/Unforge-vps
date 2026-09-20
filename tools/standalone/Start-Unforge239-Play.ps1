param(
    [switch] $SkipTypeVerification,
    [switch] $SkipAiBridge,
    # Server heap in MB; `0` keeps the default. See `Start-Unforge239.ps1 -HeapMb`.
    [int] $ServerHeapMb = 0,
    # Client heap in MB; `0` keeps the default. See `Start-Unforge239-Client.ps1 -HeapMb`.
    [int] $ClientHeapMb = 0
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$configScript = Join-Path $PSScriptRoot 'Serve-UnforgeClientConfig.ps1'
$serverScript = Join-Path $PSScriptRoot 'Start-Unforge239.ps1'
$clientScript = Join-Path $PSScriptRoot 'Start-Unforge239-Client.ps1'
$bridgeScript = Join-Path $PSScriptRoot 'Start-UnforgeAiBridge.ps1'
$logDirectory = Join-Path $root 'build\local-shadow-logs'

function Test-LocalPort([int] $Port) {
    return $null -ne (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

if (-not (Test-LocalPort 8088)) {
    Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -WorkingDirectory $root -ArgumentList @(
        '-NoProfile',
        '-ExecutionPolicy',
        'Bypass',
        '-File',
        $configScript
    ) | Out-Null
}

if (-not (Test-LocalPort 43594)) {
    $serverArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $serverScript)
    if ($ServerHeapMb -gt 0) {
        $serverArguments += @('-HeapMb', "$ServerHeapMb")
    }
    if ($SkipTypeVerification) {
        $serverArguments += '--skip-type-verification'
    }
    Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -WorkingDirectory $root -ArgumentList $serverArguments | Out-Null
}

if (-not $SkipAiBridge -and -not (Test-LocalPort 18901)) {
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -WorkingDirectory $root -ArgumentList @(
        '-NoProfile',
        '-ExecutionPolicy',
        'Bypass',
        '-File',
        $bridgeScript
    ) -RedirectStandardOutput (Join-Path $logDirectory 'ai-bridge.out.log') -RedirectStandardError (Join-Path $logDirectory 'ai-bridge.err.log') | Out-Null
    Write-Host 'Starting the local Unforge AI bridge on 127.0.0.1:18901...'
}

# The game server has to load the cache before it binds, which can take well over a minute
# on a cold start - wait properly instead of giving up after a few seconds.
$configReady = $false
$serverReady = $false
for ($i = 0; $i -lt 360; $i++) {
    if (-not $configReady) {
        $configReady = Test-LocalPort 8088
    }
    if (-not $serverReady) {
        $serverReady = Test-LocalPort 43594
    }
    if ($configReady -and $serverReady) {
        break
    }
    Start-Sleep -Milliseconds 500
}

if (-not $configReady -or -not $serverReady) {
    $missing = @()
    if (-not $configReady) { $missing += '8088 (client config)' }
    if (-not $serverReady) { $missing += '43594 (game server)' }
    throw "UnForge did not become ready: $($missing -join ', ')."
}

if (-not $SkipAiBridge -and -not (Test-LocalPort 18901)) {
    Write-Host 'AI bridge is still starting; the client will reconnect automatically.'
}

$clientArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $clientScript)
if ($ClientHeapMb -gt 0) {
    $clientArguments += @('-HeapMb', "$ClientHeapMb")
}
& powershell.exe @clientArguments
exit $LASTEXITCODE
