[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $PayloadPath
)

$ErrorActionPreference = 'Stop'
$stage = Join-Path $env:TEMP ('UnforgeInstall-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage -Force | Out-Null

try {
    Expand-Archive -LiteralPath $PayloadPath -DestinationPath $stage -Force
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $stage 'Install-UnforgeClient.ps1') -SourceRoot $stage
    if ($LASTEXITCODE -ne 0) {
        throw "UnForge-asennus epäonnistui: exit code $LASTEXITCODE"
    }
}
finally {
    if (Test-Path -LiteralPath $stage) {
        Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
    }
}
