[CmdletBinding()]
param([string]$CachePath = '', [int]$Port = 18931)
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $CachePath) { $CachePath = Join-Path $repoRoot '.data\cache\game' }
$argsValue = "--cache `"$CachePath`" --port $Port"
$url = "http://127.0.0.1:$Port/"
function Test-EditorPort {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $client.Connect('127.0.0.1', $Port)
        $client.Close()
        return $true
    } catch { return $false }
}
Push-Location $repoRoot
try {
    if (Test-EditorPort) {
        Start-Process $url
        exit 0
    }
    Start-Job -ScriptBlock {
        param($Port, $Url)
        $deadline = (Get-Date).AddSeconds(180)
        while ((Get-Date) -lt $deadline) {
            try {
                $client = New-Object System.Net.Sockets.TcpClient
                $client.Connect('127.0.0.1', $Port)
                $client.Close()
                Start-Process $Url
                return
            } catch { Start-Sleep -Seconds 2 }
        }
    } -ArgumentList $Port, $url | Out-Null
    & .\gradlew.bat :tools:interface-editor:run "--args=$argsValue" --no-daemon --max-workers=1 --console=plain
    exit $LASTEXITCODE
} finally { Pop-Location }
