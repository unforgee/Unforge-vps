$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $root
try {
    Write-Host 'Running Unforge bridge unit and loopback WebSocket smoke tests...'
    & .\gradlew.bat :bridge:test --no-daemon --max-workers=1 --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host 'UNFORGE_BRIDGE_SMOKE_PASS'
}
finally {
    Pop-Location
}
