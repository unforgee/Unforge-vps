[CmdletBinding()]
param(
    [int]$PollSeconds = 3,
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
if (-not $keyPath) { throw 'Tripo API key file was not found.' }
$raw = (Get-Content -LiteralPath $keyPath -Raw).Trim()
$apiKey = if ($raw -match '=') { ($raw -split '=', 2)[1].Trim().Trim('"', "'") } else { $raw }
if ([string]::IsNullOrWhiteSpace($apiKey)) { throw 'Tripo API key file is empty.' }

$headers = @{ Authorization = "Bearer $apiKey" }
$assets = @(
    @{
        Name = 'unforge_starter_melee_weapon'
        Prompt = 'A single modular fantasy melee weapon for an OSRS-style private server called Unforge Starter Steelblade: compact steel longsword with a simple dark grip, subtle gold guard, readable silhouette, low-poly game asset, no character, no stand, no text, neutral studio lighting, centered, PBR materials, suitable for separating into an inventory icon and player wield model.'
    },
    @{
        Name = 'unforge_starter_range_set_and_bow'
        Prompt = 'A complete modular fantasy ranged starter set for an OSRS-style private server called Unforge Rangersteel: lightweight steel-and-leather ranger hood, body, chaps, gloves, boots, and a matching shortbow, coherent low-poly game asset, each equipment piece clearly separated, no character, no stand, no text, neutral studio lighting, centered, PBR materials, suitable for separating into individual inventory and player models.'
    },
    @{
        Name = 'unforge_starter_magic_set_and_staff'
        Prompt = 'A complete modular fantasy magic starter set for an OSRS-style private server called Unforge Arcanesteel: blue-steel and deep blue mage hood, robe top, robe bottoms, gloves, boots, and a matching crystal-tipped staff, coherent low-poly game asset, each equipment piece clearly separated, no character, no stand, no text, neutral studio lighting, centered, PBR materials, suitable for separating into individual inventory and player models.'
    },
    @{
        Name = 'unforge_starter_melee_npc'
        Prompt = 'A hostile starter-island melee training NPC for an OSRS-style private server called Unforge: small armored steel recruit with a battered helmet, simple shield and sword, readable low-poly humanoid game character, neutral combat-ready stance, no base, no text, centered, PBR materials, suitable for later rigging and RuneScape-style animation.'
    },
    @{
        Name = 'unforge_starter_ranger_npc'
        Prompt = 'A hostile starter-island ranged training NPC for an OSRS-style private server called Unforge: leather-and-steel scout with hood, shortbow and quiver, readable low-poly humanoid game character, neutral combat-ready stance, no base, no text, centered, PBR materials, suitable for later rigging and RuneScape-style animation.'
    },
    @{
        Name = 'unforge_starter_mage_npc'
        Prompt = 'A hostile starter-island magic training NPC for an OSRS-style private server called Unforge: apprentice battle mage wearing blue steel and dark blue robes, holding a simple staff with a glowing crystal, readable low-poly humanoid game character, neutral combat-ready stance, no base, no text, centered, PBR materials, suitable for later rigging and RuneScape-style animation.'
    }
)

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$manifest = [ordered]@{
    generatedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    apiKeySource = [IO.Path]::GetFileName($keyPath)
    model = 'P1-20260311'
    assets = @()
}

foreach ($asset in $assets) {
    $body = @{
        prompt = $asset.Prompt
        model = 'P1-20260311'
        texture = $true
        pbr = $true
        texture_quality = 'detailed'
        face_limit = 5000
    } | ConvertTo-Json -Depth 5

    Write-Output "Creating Tripo task: $($asset.Name)"
    $create = Invoke-RestMethod -Method Post -Uri 'https://openapi.tripo3d.ai/v3/generation/text-to-model' -Headers $headers -ContentType 'application/json' -Body $body
    if ($create.code -ne 0 -or -not $create.data.task_id) {
        throw "Tripo task creation failed for $($asset.Name) with code $($create.code)."
    }

    $taskId = $create.data.task_id
    $deadline = (Get-Date).AddMinutes($TimeoutMinutes)
    do {
        Start-Sleep -Seconds $PollSeconds
        $task = Invoke-RestMethod -Method Get -Uri "https://openapi.tripo3d.ai/v3/tasks/$taskId" -Headers $headers
        $state = $task.data.status
        Write-Output ("{0}: {1} ({2}%)" -f $asset.Name, $state, $task.data.progress)
        if ($state -eq 'success') {
            $modelUrl = $task.data.output.model_url
            if (-not $modelUrl) { throw "Tripo succeeded but returned no model URL for $($asset.Name)." }
            $modelPath = Join-Path $outputDir ($asset.Name + '.glb')
            Invoke-WebRequest -Uri $modelUrl -OutFile $modelPath
            $resultPath = Join-Path $outputDir ($asset.Name + '.result.json')
            $task | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $resultPath -Encoding utf8
            $manifest.assets += [ordered]@{ name = $asset.Name; taskId = $taskId; file = $modelPath; status = 'success' }
            break
        }
        if ($state -in @('failed', 'cancelled', 'banned')) {
            throw "Tripo task $taskId for $($asset.Name) ended in state '$state'."
        }
    } while ((Get-Date) -lt $deadline)

    if (-not ($manifest.assets | Where-Object { $_.name -eq $asset.Name })) {
        throw "Timed out waiting for Tripo task $taskId for $($asset.Name)."
    }
}

$manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $outputDir 'starter-content-manifest.json') -Encoding utf8
Write-Output "Generated $($manifest.assets.Count) Tripo assets in $outputDir"
