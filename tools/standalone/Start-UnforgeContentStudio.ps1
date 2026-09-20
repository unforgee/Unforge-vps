[CmdletBinding()]
param(
    [int]$Port = 18930,
    [string]$DevinCli = "",
    # Extra content source, e.g. "cw=C:\path\to\kronos-server\data|ro" — repeatable.
    [string[]]$Source = @()
)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$urlFile = Join-Path $repoRoot 'work\content-editor\server.url'

function Test-EditorPort {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $client.Connect('127.0.0.1', $Port)
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

# If the editor is already running, just reopen its URL (it contains the API token).
if ((Test-Path $urlFile) -and (Test-EditorPort)) {
    $existingUrl = (Get-Content $urlFile -Raw).Trim()
    Write-Host "Content Studio on jo käynnissä -> $existingUrl"
    if ($existingUrl) { Start-Process $existingUrl }
    exit 0
}

# Drop a stale marker so we only open the URL the new instance writes.
Remove-Item $urlFile -ErrorAction SilentlyContinue

# Watch for server.url in the background and open the browser once the editor is up.
$watcher = Start-Job -ScriptBlock {
    param($File, $Port)
    $deadline = (Get-Date).AddSeconds(240)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $File) {
            try {
                $url = (Get-Content $File -Raw).Trim()
                if ($url) {
                    $client = New-Object System.Net.Sockets.TcpClient
                    $client.Connect('127.0.0.1', $Port)
                    $client.Close()
                    Start-Process $url
                    return
                }
            } catch { }
        }
        Start-Sleep -Seconds 2
    }
} -ArgumentList $urlFile, $Port

$argsValue = "--repo-root `"$repoRoot`" --port $Port"
if ($DevinCli) { $argsValue += " --devin-cli `"$DevinCli`"" }
foreach ($src in $Source) { $argsValue += " --source `"$src`"" }

Push-Location $repoRoot
try {
    & .\gradlew.bat :tools:content-editor:run "--args=$argsValue" --no-daemon --max-workers=1 --console=plain
    $code = $LASTEXITCODE
}
finally {
    Pop-Location
    Remove-Job $watcher -Force -ErrorAction SilentlyContinue
}
exit $code
