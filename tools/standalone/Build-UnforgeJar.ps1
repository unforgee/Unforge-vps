[CmdletBinding()]
param(
    [string] $ClientJar = '',
    [string] $OutputJar = ''
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

if ([string]::IsNullOrWhiteSpace($ClientJar)) {
    $ClientJar = Join-Path $root 'client-src\runelite-client\build\libs\client-1.12.34-SNAPSHOT-shaded-public.jar'
}
if ([string]::IsNullOrWhiteSpace($OutputJar)) {
    $OutputJar = Join-Path $root 'dist\unforge.jar'
}
$OutputJar = [System.IO.Path]::GetFullPath($OutputJar)

if (-not (Test-Path -LiteralPath $ClientJar -PathType Leaf)) {
    throw "Shaded client JAR puuttuu: $ClientJar. Buildaa ensin client-src\gradlew.bat :client:shadowJar."
}

$candidateJdks = @(
    (Join-Path $env:USERPROFILE '.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'),
    $env:JAVA_HOME
)
$javaHome = $candidateJdks | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe')) } | Select-Object -First 1
if (-not $javaHome) {
    throw 'Java 21 javac.exe puuttuu. Aseta JAVA_HOME tai asenna Java 21.'
}

$javac = Join-Path $javaHome 'bin\javac.exe'
$jarTool = Join-Path $javaHome 'bin\jar.exe'
$source = Join-Path $PSScriptRoot 'unforge-launcher\UnforgeLauncher.java'
$worldList = Join-Path $root 'tools\standalone\client\world_list_public.ws'
$launcherBackground = Join-Path $PSScriptRoot 'unforge-launcher\launcher-bg.png'
$staging = Join-Path $root ('work\unforge-jar-build-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $staging 'classes'
$bundled = Join-Path $staging 'bundled'
$outputParent = Split-Path -Parent $OutputJar

if (-not (Test-Path -LiteralPath $worldList -PathType Leaf)) {
    throw "world_list_public.ws puuttuu: $worldList"
}
if (-not (Test-Path -LiteralPath $launcherBackground -PathType Leaf)) {
    throw "Launcherin taustakuva puuttuu: $launcherBackground"
}

New-Item -ItemType Directory -Path $classes, $bundled, $outputParent -Force | Out-Null

Write-Host 'Compiling UnForge launcher...'
& $javac '-source' '17' '-target' '17' '-encoding' 'UTF-8' '-proc:none' '-d' $classes $source
if ($LASTEXITCODE -ne 0) {
    throw "Launcher-käännös epäonnistui: exit code $LASTEXITCODE"
}

Copy-Item -LiteralPath $ClientJar -Destination (Join-Path $bundled 'client.jar') -Force
Copy-Item -LiteralPath $worldList -Destination (Join-Path $bundled 'world_list.ws') -Force
Copy-Item -LiteralPath $launcherBackground -Destination (Join-Path $bundled 'launcher-bg.png') -Force

if (Test-Path -LiteralPath $OutputJar) {
    Remove-Item -LiteralPath $OutputJar -Force
}

Write-Host "Packing $OutputJar ..."
Push-Location $staging
try {
    & $jarTool '--create' '--file' $OutputJar '--main-class' 'org.unforge.launcher.UnforgeLauncher' '-C' $classes '.' '-C' $staging 'bundled'
    if ($LASTEXITCODE -ne 0) {
        throw "JAR-pakkaus epäonnistui: exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

$result = Get-Item -LiteralPath $OutputJar
Write-Host ("Ready: {0} ({1:N0} MB)" -f $result.FullName, ($result.Length / 1MB))
Write-Host ("Käynnistys: java -jar `"{0}`" --server-host <serverin-IP>" -f $OutputJar)
