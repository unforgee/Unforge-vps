<#
    Frees RAM by stopping stuck Java build leftovers.

    It stops only JVMs that are disposable Gradle/Kotlin build machinery:

      * Gradle daemons (any project on this machine)
      * Kotlin compile daemons
      * orphaned Gradle wrapper / launcher JVMs

    It NEVER stops:

      * anything LISTENING on a UnForge development port - that covers the game
        server (43594), client config (8088), AI bridge (18900/18901), studio
        (8787/18902) and content editor (18930), and
      * the RuneLite game client.

    So it is safe to run while a world is up.

    Usage
    -----
      .\Free-UnforgeMemory.ps1                    # stop the build leftovers
      .\Free-UnforgeMemory.ps1 -DryRun            # list what would be stopped
      .\Free-UnforgeMemory.ps1 -IncludeStaleServers
                                                  # also stop game-server JVMs that are NOT
                                                  # bound to 43594 (a server that failed to boot).
                                                  # Do not use this while a server is starting up.
#>

param(
    [switch] $DryRun,
    [switch] $IncludeStaleServers
)

$ErrorActionPreference = 'Continue'

$developmentPorts = @(
    @{ Port = 8088;  Name = 'Client config' },
    @{ Port = 43594; Name = 'Game server' },
    @{ Port = 18900; Name = 'Bridge HTTP' },
    @{ Port = 18901; Name = 'Bridge WebSocket' },
    @{ Port = 18902; Name = 'Studio agent' },
    @{ Port = 18930; Name = 'Content editor' },
    @{ Port = 8787;  Name = 'Studio UI' }
)

function Get-FreeMemoryMb {
    $operatingSystem = Get-CimInstance Win32_OperatingSystem
    return [int] [math]::Round($operatingSystem.FreePhysicalMemory / 1024)
}

# Anything owning a development port is live software - never touch it.
$protected = @{}
foreach ($entry in $developmentPorts) {
    $connections = Get-NetTCPConnection -LocalPort $entry.Port -State Listen -ErrorAction SilentlyContinue
    foreach ($connection in $connections) {
        $protected[[int] $connection.OwningProcess] = $entry.Name
    }
}

$junkPattern = 'GradleDaemon|KotlinCompileDaemon|GradleWrapperMain|org\.gradle\.launcher|org\.jetbrains\.kotlin\.daemon'
$serverPattern = 'org\.rsmod\.server\.app\.GameServerKt'

$beforeMb = Get-FreeMemoryMb

Write-Host ''
Write-Host 'UnForge memory cleanup' -ForegroundColor Cyan
Write-Host ("  Free RAM before : {0} MB" -f $beforeMb)
if ($protected.Count -gt 0) {
    Write-Host ("  Live services   : {0}" -f (($protected.Values | Sort-Object -Unique) -join ', ')) -ForegroundColor Green
}

$candidates = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'")
$stopped = 0
$failed = 0

foreach ($process in $candidates) {
    $processId = [int] $process.ProcessId
    $commandLine = [string] $process.CommandLine
    if ([string]::IsNullOrWhiteSpace($commandLine)) { continue }

    $isProtected = $protected.ContainsKey($processId)
    $isJunk = $commandLine -match $junkPattern
    $isStaleServer = $IncludeStaleServers -and ($commandLine -match $serverPattern) -and (-not $isProtected)

    if (-not $isJunk -and -not $isStaleServer) { continue }

    if ($isProtected) {
        # Cannot happen for build junk, but guard anyway: never stop a listener.
        Write-Host ("  [keep]    PID {0} - serving {1}" -f $processId, $protected[$processId]) -ForegroundColor DarkGray
        continue
    }

    $label =
        if ($isStaleServer) { 'stale game server' }
        else { 'build leftover' }

    if ($DryRun) {
        Write-Host ("  [would stop] PID {0} ({1})" -f $processId, $label) -ForegroundColor Yellow
        continue
    }

    try {
        Stop-Process -Id $processId -Force -ErrorAction Stop
        Write-Host ("  [stopped] PID {0} ({1})" -f $processId, $label) -ForegroundColor Green
        $stopped++
    } catch {
        Write-Host ("  [failed]  PID {0}: {1}" -f $processId, $_.Exception.Message) -ForegroundColor Red
        $failed++
    }
}

if ($stopped -gt 0 -and -not $DryRun) {
    Start-Sleep -Milliseconds 800
}

$afterMb = Get-FreeMemoryMb
$delta = $afterMb - $beforeMb
$deltaText =
    if ($delta -ge 0) { "+{0}" -f $delta }
    else { "{0}" -f $delta }

Write-Host ''
if ($DryRun) {
    Write-Host 'Dry run: nothing was stopped.' -ForegroundColor Cyan
} elseif ($stopped -eq 0 -and $failed -eq 0) {
    Write-Host 'Nothing to clean up - no stuck build JVMs found.' -ForegroundColor Cyan
} else {
    Write-Host ("  Stopped {0} process(es)." -f $stopped) -ForegroundColor Cyan
}
Write-Host ("  Free RAM after  : {0} MB ({1} MB)" -f $afterMb, $deltaText)
Write-Host ''

exit 0
