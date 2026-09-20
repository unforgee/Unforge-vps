[CmdletBinding()]
param(
    [string] $JarPath = '',
    [string] $OutputExe = ''
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $JarPath = Join-Path $root 'dist\unforge.jar'
}
if ([string]::IsNullOrWhiteSpace($OutputExe)) {
    $OutputExe = Join-Path $root 'dist\Unforge-Client-Installer.exe'
}

$runtime = Join-Path $root 'runtime\jdk-21.0.12.1+1-jre'
$iexpress = Join-Path $env:WINDIR 'System32\iexpress.exe'
$sourceRoot = Join-Path $PSScriptRoot 'unforge-installer'
$staging = Join-Path $root ('work\unforge-installer-' + [guid]::NewGuid().ToString('N'))
$payloadRoot = Join-Path $staging 'payload-root'
$payloadZip = Join-Path $staging 'payload.zip'
$sedPath = Join-Path $staging 'unforge-installer.sed'
$outputParent = Split-Path -Parent $OutputExe

foreach ($required in @($JarPath, (Join-Path $runtime 'bin\javaw.exe'), (Join-Path $sourceRoot 'setup.cmd'), (Join-Path $sourceRoot 'Setup-UnforgeInstaller.ps1'), (Join-Path $sourceRoot 'Install-UnforgeClient.ps1'))) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Installer-tiedosto puuttuu: $required"
    }
}
if (-not (Test-Path -LiteralPath $iexpress -PathType Leaf)) {
    throw 'Windows IExpress puuttuu: %WINDIR%\System32\iexpress.exe'
}

New-Item -ItemType Directory -Path $payloadRoot, $outputParent -Force | Out-Null
Copy-Item -LiteralPath $JarPath -Destination (Join-Path $payloadRoot 'unforge.jar') -Force
Copy-Item -LiteralPath $runtime -Destination (Join-Path $payloadRoot 'runtime') -Recurse -Force
Copy-Item -LiteralPath (Join-Path $sourceRoot 'Install-UnforgeClient.ps1') -Destination (Join-Path $payloadRoot 'Install-UnforgeClient.ps1') -Force

Write-Host 'Compressing bundled Java runtime and client...'
Compress-Archive -Path (Join-Path $payloadRoot '*') -DestinationPath $payloadZip -CompressionLevel Optimal

Copy-Item -LiteralPath (Join-Path $sourceRoot 'setup.cmd') -Destination (Join-Path $staging 'setup.cmd') -Force
Copy-Item -LiteralPath (Join-Path $sourceRoot 'Setup-UnforgeInstaller.ps1') -Destination (Join-Path $staging 'Setup-UnforgeInstaller.ps1') -Force

$sed = @"
[Version]
Class=IEXPRESS
SEDVersion=3

[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=1
HideExtractAnimation=1
UseLongFileName=1
InsideCompressed=1
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=UnForge Client installer
DisplayLicense=
FinishMessage=UnForge Client on asennettu työpöydälle.
TargetName=$OutputExe
FriendlyName=UnForge Client
AppLaunched=cmd.exe /d /s /c setup.cmd
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
SourceFiles=SourceFiles

[SourceFiles]
SourceFiles0=$staging\

[SourceFiles0]
%FILE0%=
%FILE1%=
%FILE2%=

[Strings]
FILE0="setup.cmd"
FILE1="Setup-UnforgeInstaller.ps1"
FILE2="payload.zip"
"@
Set-Content -LiteralPath $sedPath -Value $sed -Encoding ASCII

if (Test-Path -LiteralPath $OutputExe) {
    Remove-Item -LiteralPath $OutputExe -Force
}

Write-Host "Building $OutputExe ..."
$iexpressProcess = Start-Process -FilePath $iexpress -ArgumentList @('/N', '/Q', $sedPath) -Wait -PassThru
$iexpressExitCode = $iexpressProcess.ExitCode
if ($iexpressExitCode -ne 0 -or -not (Test-Path -LiteralPath $OutputExe -PathType Leaf)) {
    throw "IExpress-pakkaus epäonnistui: exit code $iexpressExitCode"
}

$result = Get-Item -LiteralPath $OutputExe
Write-Host ("Ready: {0} ({1:N1} MB)" -f $result.FullName, ($result.Length / 1MB))
