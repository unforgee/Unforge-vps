$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gradle = Join-Path $root 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Gradle wrapper is missing: $gradle"
}

Write-Host 'Building and starting the current UnForge server (:server:app:run)...'
Push-Location $root
try {
    & $gradle ':server:app:run' '--console=plain'
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
