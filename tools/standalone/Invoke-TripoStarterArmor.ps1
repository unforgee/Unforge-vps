[CmdletBinding()]
param(
    [string]$Prompt = "A complete fantasy starter armor set called Unforge Dawnsteel: helmet, chest plate, plate legs, gauntlets and armored boots, cohesive low-poly hard-surface game asset, brushed steel with subtle warm gold trim and a small crown emblem, neutral studio lighting, centered, no character, no weapons, no text",
    [string]$Model = "P2-20260801",
    [int]$PollSeconds = 10,
    [int]$TimeoutMinutes = 20
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$outputDir = Join-Path $repoRoot 'work\tripo'
$keyCandidates = @(
    'C:\Users\Administrator\Desktop\tripo\_api\_key.env',
    'C:\Users\Administrator\Desktop\tripo_api_key.env',
    'C:\Users\Administrator\Desktop\tripo_api.env.txt',
    'C:\Users\Administrator\Desktop\tripo.env.txt'
)

$keyPath = $keyCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $keyPath) { throw 'Tripo API key file was not found on the Desktop.' }
$raw = (Get-Content -LiteralPath $keyPath -Raw).Trim()
$apiKey = if ($raw -match '=') { ($raw -split '=', 2)[1].Trim().Trim('"', "'") } else { $raw }
if ([string]::IsNullOrWhiteSpace($apiKey)) { throw 'Tripo API key file is empty.' }

$headers = @{ Authorization = "Bearer $apiKey" }
$body = @{
    prompt = $Prompt
    model = $Model
    texture = $true
    pbr = $true
    texture_quality = 'detailed'
    face_limit = 5000
    quad = $true
} | ConvertTo-Json -Depth 5

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$requestPath = Join-Path $outputDir 'starter-armor-set.request.json'
@{
    prompt = $Prompt
    model = $Model
    requestedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    apiKeySource = [IO.Path]::GetFileName($keyPath)
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $requestPath -Encoding utf8

$create = Invoke-RestMethod -Method Post -Uri 'https://openapi.tripo3d.ai/v3/generation/text-to-model' -Headers $headers -ContentType 'application/json' -Body $body
if ($create.code -ne 0 -or -not $create.data.task_id) {
    throw "Tripo task creation failed with code $($create.code)."
}

$taskId = $create.data.task_id
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
do {
    Start-Sleep -Seconds $PollSeconds
    $task = Invoke-RestMethod -Method Get -Uri "https://openapi.tripo3d.ai/v3/tasks/$taskId" -Headers $headers
    $state = $task.data.status
    Write-Output ("Tripo task {0}: {1} ({2}%)" -f $taskId, $state, $task.data.progress)
    if ($state -eq 'success') {
        $modelUrl = $task.data.output.model_url
        if (-not $modelUrl) { throw 'Tripo succeeded but returned no model URL.' }
        $modelPath = Join-Path $outputDir 'Unforge-dawnsteel-starter-armor.glb'
        Invoke-WebRequest -Uri $modelUrl -OutFile $modelPath
        $task | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $outputDir 'starter-armor-set.result.json') -Encoding utf8
        Write-Output "Generated: $modelPath"
        exit 0
    }
    if ($state -in @('failed', 'cancelled')) { throw "Tripo task ended in state '$state'." }
} while ((Get-Date) -lt $deadline)

throw "Timed out waiting for Tripo task $taskId."
