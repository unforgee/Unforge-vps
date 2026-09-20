<#
    One command to bring the whole UnForge development environment up.

    Starts, in order, whatever is not already listening:
      8088   client config server
      43594  game server
      18901  AI bridge (WebSocket)
      18930  content editor
      8787   AI4FUN Studio (which also opens 18902 for the studio agent)

    Then launches the client, which hosts the development dock.

    Usage
    -----
      .\Start-UnforgeDev.ps1                 # everything, then the client
      .\Start-UnforgeDev.ps1 -NoClient       # services only
      .\Start-UnforgeDev.ps1 -SkipStudio     # skip the studio (no AI tools)
      .\Start-UnforgeDev.ps1 -Status         # just print what is running
#>

param(
    [switch] $NoClient,
    [switch] $SkipStudio,
    [switch] $SkipAiBridge,
    [switch] $SkipTypeVerification,
    [switch] $Status,
    [string] $StudioRoot = 'C:\Unforge\tools\rsps-dev\app'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$logDirectory = Join-Path $root 'build\local-shadow-logs'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

$configScript = Join-Path $PSScriptRoot 'Serve-UnforgeClientConfig.ps1'
$serverScript = Join-Path $PSScriptRoot 'Start-Unforge239.ps1'
$clientScript = Join-Path $PSScriptRoot 'Start-Unforge239-Client.ps1'
$bridgeScript = Join-Path $PSScriptRoot 'Start-UnforgeAiBridge.ps1'
$editorScript = Join-Path $PSScriptRoot 'Start-UnforgeContentStudio.ps1'
$editorUrlFile = Join-Path $root 'work\content-editor\server.url'

function Test-LocalPort([int] $Port) {
    return $null -ne (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Start-Hidden([string] $script, [string] $logName, [string[]] $extraArguments) {
    $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $script) + $extraArguments
    Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -WorkingDirectory $root `
        -ArgumentList $arguments `
        -RedirectStandardOutput (Join-Path $logDirectory "$logName.out.log") `
        -RedirectStandardError (Join-Path $logDirectory "$logName.err.log") | Out-Null
}

function Wait-ForPort([int] $Port, [int] $TimeoutSeconds = 90) {
    for ($i = 0; $i -lt ($TimeoutSeconds * 2); $i++) {
        if (Test-LocalPort $Port) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Get-EditorToken {
    if (-not (Test-Path -LiteralPath $editorUrlFile)) { return $null }
    try {
        $url = (Get-Content -LiteralPath $editorUrlFile -Raw).Trim()
    } catch {
        return $null
    }
    if ($url -match 'token=([A-Za-z0-9._-]+)') { return $Matches[1] }
    return $url
}

function Show-Status {
    $ports = @(
        @{ Port = 8088;  Name = 'Client config' },
        @{ Port = 43594; Name = 'Game server' },
        @{ Port = 18900; Name = 'Bridge HTTP' },
        @{ Port = 18901; Name = 'Bridge WebSocket' },
        @{ Port = 18902; Name = 'Studio agent' },
        @{ Port = 18930; Name = 'Content editor' },
        @{ Port = 8787;  Name = 'Studio UI' }
    )

    Write-Host ''
    Write-Host 'UnForge development environment' -ForegroundColor Cyan
    foreach ($entry in $ports) {
        $up = Test-LocalPort $entry.Port
        $mark = if ($up) { '[up]  ' } else { '[down]' }
        $colour = if ($up) { 'Green' } else { 'DarkGray' }
        Write-Host ("  {0} {1,-6} {2}" -f $mark, $entry.Port, $entry.Name) -ForegroundColor $colour
    }

    # Only offer the token when the editor is actually listening, otherwise the file
    # from a previous run would be shown as if it were current.
    if (Test-LocalPort 18930) {
        $token = Get-EditorToken
        if ($token) {
            Write-Host ''
            Write-Host '  Content editor token (paste into the dock plugin settings):' -ForegroundColor Yellow
            Write-Host "    $token" -ForegroundColor Yellow
        }
    }

    Write-Host ''
}

if ($Status) {
    Show-Status
    exit 0
}

# --------------------------------------------------------------- services

if (-not (Test-LocalPort 8088)) {
    Write-Host 'Starting client config server on 8088...'
    Start-Hidden $configScript 'client-config' @()
}

if (-not (Test-LocalPort 43594)) {
    Write-Host 'Starting game server on 43594...'
    $serverArguments = @()
    if ($SkipTypeVerification) { $serverArguments += '--skip-type-verification' }
    Start-Hidden $serverScript 'game-server' $serverArguments
}

if (-not $SkipAiBridge -and -not (Test-LocalPort 18901)) {
    Write-Host 'Starting AI bridge on 18901...'
    Start-Hidden $bridgeScript 'ai-bridge' @()
}

if ((Test-Path -LiteralPath $editorScript) -and -not (Test-LocalPort 18930)) {
    Write-Host 'Starting content editor on 18930...'
    Start-Hidden $editorScript 'content-editor' @()
}

if (-not $SkipStudio -and -not (Test-LocalPort 8787)) {
    if (Test-Path -LiteralPath $StudioRoot) {
        Write-Host 'Starting AI4FUN Studio on 8787...'
        Start-Process -FilePath 'cmd.exe' -WindowStyle Hidden -WorkingDirectory $StudioRoot `
            -ArgumentList @('/c', 'npm run studio:unforge') `
            -RedirectStandardOutput (Join-Path $logDirectory 'studio.out.log') `
            -RedirectStandardError (Join-Path $logDirectory 'studio.err.log') | Out-Null
    } else {
        Write-Host "Studio not found at $StudioRoot - skipping (use -StudioRoot to point at it)." -ForegroundColor Yellow
    }
}

# -------------------------------------------------------------- readiness

if (-not (Wait-ForPort 8088 90)) { Write-Warning 'Client config server did not come up on 8088.' }
if (-not (Wait-ForPort 43594 180)) { Write-Warning 'Game server did not come up on 43594.' }
if (-not $SkipAiBridge -and -not (Wait-ForPort 18901 60)) { Write-Warning 'AI bridge did not come up on 18901.' }
if (-not $SkipStudio -and -not (Wait-ForPort 8787 60)) { Write-Warning 'Studio did not come up on 8787.' }
if (-not (Wait-ForPort 18930 90)) { Write-Warning 'Content editor did not come up on 18930.' }

Show-Status

if ($NoClient) {
    Write-Host 'Services are up. Re-run without -NoClient to launch the client.' -ForegroundColor Cyan
    exit 0
}

Write-Host 'Starting the client (it hosts the Unforge Dev dock)...' -ForegroundColor Cyan
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $clientScript
exit $LASTEXITCODE
