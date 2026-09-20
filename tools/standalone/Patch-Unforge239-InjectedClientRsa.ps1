param(
    [Parameter(Mandatory = $true)]
    [string] $InputJar,

    [Parameter(Mandatory = $true)]
    [string] $OutputJar,

    [string] $PublicKeyFile
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

if ([string]::IsNullOrWhiteSpace($PublicKeyFile)) {
    $PublicKeyFile = Join-Path $PSScriptRoot '..\..\.data\client.key'
}

function Read-PublicModulus([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Public RSA key file is missing: $Path"
    }

    $lines = Get-Content -LiteralPath $Path
    $exponentLine = $lines | Where-Object { $_ -match '^Exponent:\s*(\S+)' } | Select-Object -First 1
    $modulusLine = $lines | Where-Object { $_ -match '^Modulus:\s*(\S+)' } | Select-Object -First 1
    if ($null -eq $exponentLine -or $null -eq $modulusLine) {
        throw "Public RSA key file has no Exponent/Modulus records: $Path"
    }

    $exponent = ([regex]::Match($exponentLine, '^Exponent:\s*(\S+)')).Groups[1].Value
    $modulus = ([regex]::Match($modulusLine, '^Modulus:\s*(\S+)')).Groups[1].Value.ToLowerInvariant()
    if ($exponent -ne '10001') {
        throw "Unsupported RSA public exponent in $Path"
    }
    if ($modulus -notmatch '^[0-9a-f]{256}$') {
        throw "Expected a 1024-bit RSA modulus in $Path"
    }
    return $modulus
}

function Find-AsciiModulus([byte[]] $Bytes) {
    $ascii = [Text.Encoding]::ASCII.GetString($Bytes)
    $matches = [regex]::Matches($ascii, '(?<![0-9a-f])[0-9a-f]{200,}(?![0-9a-f])') |
        Where-Object { $_.Length -eq 256 }
    if ($matches.Count -ne 1) {
        throw "Expected exactly one 1024-bit RSA modulus in bg.class, found $($matches.Count)"
    }
    return $matches[0]
}

$modulus = Read-PublicModulus $PublicKeyFile
$inputPath = [IO.Path]::GetFullPath($InputJar)
$outputPath = [IO.Path]::GetFullPath($OutputJar)
if (-not (Test-Path -LiteralPath $inputPath -PathType Leaf)) {
    throw "Injected client jar is missing: $inputPath"
}

$outputDirectory = Split-Path -Parent $outputPath
[IO.Directory]::CreateDirectory($outputDirectory) | Out-Null

if (Test-Path -LiteralPath $outputPath) {
    Remove-Item -LiteralPath $outputPath -Force
}

$utf8 = [Text.Encoding]::ASCII
$patched = $false
$inputZip = [IO.Compression.ZipFile]::OpenRead($inputPath)
try {
    $outputZip = [IO.Compression.ZipFile]::Open($outputPath, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entry in $inputZip.Entries) {
            $target = $outputZip.CreateEntry($entry.FullName, [IO.Compression.CompressionLevel]::Optimal)
            $sourceStream = $entry.Open()
            $targetStream = $target.Open()
            try {
                if ($entry.FullName -eq 'bg.class') {
                    $buffer = New-Object IO.MemoryStream
                    try {
                        $sourceStream.CopyTo($buffer)
                        $bytes = $buffer.ToArray()
                    }
                    finally {
                        $buffer.Dispose()
                    }

                    $match = Find-AsciiModulus $bytes
                    $newBytes = $utf8.GetBytes($modulus)
                    [Buffer]::BlockCopy($newBytes, 0, $bytes, $match.Index, $newBytes.Length)
                    $targetStream.Write($bytes, 0, $bytes.Length)
                    $patched = $true
                }
                else {
                    $sourceStream.CopyTo($targetStream)
                }
            }
            finally {
                $targetStream.Dispose()
                $sourceStream.Dispose()
            }
        }
    }
    finally {
        $outputZip.Dispose()
    }
}
finally {
    $inputZip.Dispose()
}

if (-not $patched) {
    Remove-Item -LiteralPath $outputPath -Force
    throw 'Injected client jar did not contain bg.class.'
}

Write-Host "Patched r239 injected client RSA modulus: $outputPath"
