<#
    Stops everything Start-UnforgeDev.ps1 started.

    Only processes that are actually LISTENING on the development ports are
    stopped, so this cannot take down unrelated software.

    Usage
    -----
      .\Stop-UnforgeDev.ps1            # stop the development services
      .\Stop-UnforgeDev.ps1 -WhatIf    # list what would be stopped
#>

param(
    [switch] $WhatIf
)

$ErrorActionPreference = 'Continue'

$targets = @(
    @{ Port = 8787;  Name = 'Studio UI' },
    @{ Port = 18902; Name = 'Studio agent' },
    @{ Port = 18930; Name = 'Content editor' },
    @{ Port = 18901; Name = 'Bridge WebSocket' },
    @{ Port = 18900; Name = 'Bridge HTTP' },
    @{ Port = 43594; Name = 'Game server' },
    @{ Port = 8088;  Name = 'Client config' }
)

Write-Host ''
Write-Host 'Stopping UnForge development services' -ForegroundColor Cyan

$stopped = 0
foreach ($target in $targets) {
    $connections = Get-NetTCPConnection -LocalPort $target.Port -State Listen -ErrorAction SilentlyContinue
    if (-not $connections) {
        Write-Host ("  [--]  {0,-6} {1} (not running)" -f $target.Port, $target.Name) -ForegroundColor DarkGray
        continue
    }

    $processIds = $connections | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($processId in $processIds) {
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if (-not $process) { continue }

        if ($WhatIf) {
            Write-Host ("  [??]  {0,-6} {1} -> PID {2} ({3})" -f $target.Port, $target.Name, $processId, $process.ProcessName) -ForegroundColor Yellow
            continue
        }

        try {
            Stop-Process -Id $processId -Force -ErrorAction Stop
            Write-Host ("  [ok]  {0,-6} {1} -> stopped PID {2} ({3})" -f $target.Port, $target.Name, $processId, $process.ProcessName) -ForegroundColor Green
            $stopped++
        } catch {
            Write-Host ("  [!!]  {0,-6} {1} -> could not stop PID {2}: {3}" -f $target.Port, $target.Name, $processId, $_.Exception.Message) -ForegroundColor Red
        }
    }
}

Write-Host ''
if ($WhatIf) {
    Write-Host 'WhatIf run: nothing was stopped.' -ForegroundColor Cyan
} else {
    Write-Host "Stopped $stopped process(es)." -ForegroundColor Cyan
}
