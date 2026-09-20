param(
    [int] $Port = 8088,
    # Interface to bind. `127.0.0.1` serves the local loopback configs; pass the public IP
    # (e.g. `-BindAddress 94.237.118.174`) to expose jav_config/world_list to remote clients.
    [string] $BindAddress = '127.0.0.1'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$clientRoot = Join-Path $root 'tools\standalone\client'

if (-not (Test-Path (Join-Path $clientRoot 'jav_local_239.ws'))) {
    throw "Local r239 client configuration is missing: $clientRoot"
}

Write-Host "Serving local r239 client configuration on http://${BindAddress}:$Port/"
Write-Host "This is a static file server, not a game proxy."

# Prefer a real Python interpreter when one exists. On many Windows machines `python` only
# resolves to the Microsoft Store alias stub, which exits immediately without serving anything
# - detect that and fall back to the built-in HttpListener server below.
$python = $null
try {
    $candidate = (Get-Command python -ErrorAction Stop).Source
    $probe = & $candidate --version 2>&1
    if ($LASTEXITCODE -eq 0 -and $probe -match 'Python\s+\d') {
        $python = $candidate
    }
} catch {
    $python = $null
}

if ($null -ne $python) {
    Write-Host "Using Python static file server: $python"
    Push-Location $clientRoot
    try {
        & $python -m http.server $Port --bind $BindAddress
        exit $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}

Write-Host 'Python not available; using the built-in PowerShell file server.'
$mimeTypes = @{
    '.ws'   = 'application/octet-stream'
    '.jar'  = 'application/java-archive'
    '.txt'  = 'text/plain'
    '.html' = 'text/html'
    '.json' = 'application/json'
    '.png'  = 'image/png'
    '.ico'  = 'image/x-icon'
}

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://${BindAddress}:$Port/")
$listener.Start()
Write-Host "Listening for client config requests... (Ctrl+C to stop)"

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        try {
            $relative = $context.Request.Url.LocalPath.TrimStart('/')
            if ([string]::IsNullOrWhiteSpace($relative)) {
                $relative = 'jav_local_239.ws'
            }
            $filePath = Join-Path $clientRoot $relative
            $resolved = [IO.Path]::GetFullPath($filePath)
            if (-not $resolved.StartsWith($clientRoot, [StringComparison]::OrdinalIgnoreCase) -or
                -not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
                $context.Response.StatusCode = 404
                $context.Response.Close()
                continue
            }
            $bytes = [IO.File]::ReadAllBytes($resolved)
            $ext = [IO.Path]::GetExtension($resolved).ToLowerInvariant()
            $context.Response.ContentType = $mimeTypes[$ext]
            if ($null -eq $context.Response.ContentType) {
                $context.Response.ContentType = 'application/octet-stream'
            }
            $context.Response.ContentLength64 = $bytes.Length
            $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
            $context.Response.Close()
        } catch {
            try { $context.Response.StatusCode = 500; $context.Response.Close() } catch {}
        }
    }
}
finally {
    $listener.Stop()
    $listener.Close()
}
