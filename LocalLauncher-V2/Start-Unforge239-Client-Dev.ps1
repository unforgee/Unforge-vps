$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$clientRoot = Join-Path $root 'client-src'
$gradle = Join-Path $clientRoot 'gradlew.bat'
$clientScript = Join-Path $root 'tools\standalone\Start-Unforge239-Client.ps1'

if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Client Gradle wrapper is missing: $gradle"
}
if (-not (Test-Path -LiteralPath $clientScript)) {
    throw "Client start script is missing: $clientScript"
}

Write-Host 'Building the current r239 client (:client:shadowJar)...'
Push-Location $clientRoot
try {
    & $gradle ':client:shadowJar' '--console=plain'
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}

Write-Host 'Starting the freshly built r239 client...'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $clientScript
exit $LASTEXITCODE
