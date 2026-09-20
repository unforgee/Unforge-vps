$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gradle = Join-Path $root 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Gradle wrapper is missing: $gradle"
}

function Get-LocalServerTargets {
    $connections = @(Get-NetTCPConnection -LocalPort 43594 -State Listen -ErrorAction SilentlyContinue)
    $targets = @()

    foreach ($connection in $connections) {
        $targetPid = [int] $connection.OwningProcess
        if ($targets.Where({ $_.ProcessId -eq $targetPid }).Count -gt 0) {
            continue
        }

        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$targetPid" -ErrorAction Stop
        if ($null -eq $process) {
            throw "Could not inspect the process listening on port 43594 (PID $targetPid)."
        }

        $commandLine = [string] $process.CommandLine
        $isGameServer = $commandLine -match 'org\.rsmod\.server\.app\.GameServerKt'
        $isThisProject = $commandLine -like "*$root*"
        if (-not $isGameServer -or -not $isThisProject) {
            throw "Refusing to stop PID ${targetPid}: port 43594 is not owned by the verified UnForge project server."
        }

        $targets += [pscustomobject] @{
            ProcessId = $targetPid
            CommandLine = $commandLine
        }
    }

    return $targets
}

function Stop-VerifiedLocalServer {
    $targets = @(Get-LocalServerTargets)
    if ($targets.Count -eq 0) {
        Write-Host 'No local UnForge server is listening on port 43594. Starting a fresh server.'
        return
    }

    foreach ($target in $targets) {
        Write-Host "Stopping verified UnForge server PID $($target.ProcessId)..."
        Stop-Process -Id $target.ProcessId -ErrorAction Stop
    }

    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        $remaining = @(Get-LocalServerTargets)
        if ($remaining.Count -eq 0) {
            Write-Host 'Previous UnForge server stopped.'
            return
        }
        Start-Sleep -Milliseconds 250
    }

    throw 'The previous UnForge server did not stop in time; no new server was started.'
}

Stop-VerifiedLocalServer

Write-Host 'Rebuilding the current UnForge server and starting it again...'
Write-Host 'Gradle is rerunning the server build tasks so the newest source is active.'
Push-Location $root
try {
    & $gradle ':server:app:run' '--rerun-tasks' '--console=plain'
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
