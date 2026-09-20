param(
    [string] $ClientJar = "",
    # Maximum heap for the game client in MB. `0` leaves the JVM default in place.
    [int] $HeapMb = 0
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($ClientJar)) {
    $ClientJar = Join-Path $root 'client-src\runelite-client\build\libs\client-1.12.34-SNAPSHOT-shaded.jar'
}

if (-not (Test-Path -LiteralPath $ClientJar)) {
    throw "Client jar is missing. Build it with .\client-src\gradlew.bat :client:shadowJar first."
}

# `java` is not on PATH on every machine (AGENTS.md keeps the JDK under the Gradle toolchain
# dir). Prefer the project JDK, then JAVA_HOME, then PATH - whichever provides java.exe first.
$projectJdk = Join-Path $env:USERPROFILE '.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin\java.exe'
$javaExe = $null
if (Test-Path $projectJdk) {
    $javaExe = $projectJdk
} elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
} elseif (Get-Command java -ErrorAction SilentlyContinue) {
    $javaExe = 'java'
} else {
    throw "No Java runtime found. Expected the project JDK at $projectJdk or java on PATH."
}
Write-Host "Using Java: $javaExe"

$configUrl = 'http://127.0.0.1:8088/jav_local_239.ws'
Write-Host "Starting UnForge client directly against $configUrl"
Write-Host "The game connection target is 127.0.0.1:43594; RSProx is not used."

$javaArguments = @('-ea')
if ($HeapMb -gt 0) {
    $javaArguments += "-Xmx${HeapMb}m"
    Write-Host "Java heap arguments: -Xmx${HeapMb}m"
}
$javaArguments += @(
    '-jar', $ClientJar,
    '--disable-telemetry',
    "--jav_config=$configUrl",
    '--developer-mode',
    '--noupdate'
)

& $javaExe @javaArguments
exit $LASTEXITCODE
