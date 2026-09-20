param(
    # Maximum heap for the game server in MB. `0` keeps the historical `-Xms1g` default
    # with no explicit cap (the JVM then takes its usual quarter-of-RAM maximum).
    [int] $HeapMb = 0,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $ServerArguments
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$distribution = Join-Path $root 'server\app\build\install\app'
$libraryPath = Join-Path $distribution 'lib\*'

if (-not (Test-Path (Join-Path $distribution 'lib'))) {
    throw "Server distribution is missing. Run .\gradlew.bat :server:app:installDist first."
}

if ([string]::IsNullOrWhiteSpace($env:UNFORGE_HOME)) {
    $env:UNFORGE_HOME = $distribution
}
if ([string]::IsNullOrWhiteSpace($env:UNFORGE_DATA)) {
    $env:UNFORGE_DATA = Join-Path $root '.data'
}

# `java` is not on PATH on every machine (AGENTS.md keeps the JDK under the Gradle toolchain
# dir). Prefer the project JDK, then JAVA_HOME, then PATH - whichever provides java.exe first.
$projectJdk = Join-Path $env:USERPROFILE '.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin\java.exe'
$java = $null
if (Test-Path $projectJdk) {
    $java = $projectJdk
} elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
} elseif (Get-Command java -ErrorAction SilentlyContinue) {
    $java = (Get-Command java).Source
} else {
    throw "No Java runtime found. Expected the project JDK at $projectJdk or java on PATH."
}
Write-Host "Using Java: $java"
$heapArguments =
    if ($HeapMb -gt 0) {
        @("-Xms$([int] [math]::Max(256, $HeapMb / 2))m", "-Xmx${HeapMb}m")
    } else {
        @('-Xms1g')
    }
$javaArguments = @(
    '-ea'
) + $heapArguments + @(
    '-XX:AutoBoxCacheMax=65535',
    '-cp', $libraryPath,
    'org.rsmod.server.app.GameServerKt'
) + $ServerArguments

Write-Host "Starting UnForge revision 239 directly on port 43594..."
Write-Host "UNFORGE_HOME=$env:UNFORGE_HOME"
Write-Host "UNFORGE_DATA=$env:UNFORGE_DATA"
Write-Host "Java heap arguments: $($heapArguments -join ' ')"
& $java @javaArguments
exit $LASTEXITCODE
