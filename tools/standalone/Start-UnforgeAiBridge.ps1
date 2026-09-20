param(
    [int] $WebSocketPort = 18901,
    [int] $HttpPort = 18900,
    [string] $OpenAiBaseUrl = 'https://api.openai.com'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:UNFORGE_BRIDGE_BIND = '127.0.0.1'
$env:UNFORGE_BRIDGE_WS_PORT = $WebSocketPort.ToString()
$env:UNFORGE_BRIDGE_HTTP_PORT = $HttpPort.ToString()
$env:UNFORGE_OPENAI_BASE_URL = $OpenAiBaseUrl

$apiKeyConfigured = -not [string]::IsNullOrWhiteSpace($env:UNFORGE_OPENAI_API_KEY) -or -not [string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)
Write-Host "Starting Unforge Codex bridge on 127.0.0.1:$WebSocketPort (HTTP $HttpPort)"
Write-Host "Codex endpoint: $OpenAiBaseUrl"
Write-Host "OpenAI API key configured: $apiKeyConfigured"
Write-Host 'No public bind or VPS connection is configured.'
Push-Location $root
try {
    & .\gradlew.bat :bridge:run --no-daemon --max-workers=1 --console=plain
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
