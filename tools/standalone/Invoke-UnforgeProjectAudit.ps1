<#
    Read-only project state audit for the UnForge r239 workspace.

    Produces a Desktop folder containing:
      report.md    - human-readable Markdown report
      summary.json - machine-readable summary
      logs\        - full output of every invoked check

    The base audit never writes into the project, never touches the cache,
    never runs packCache and never starts/stops processes. It is safe to run
    while the server or client is live.

    -Deep / -IncludeBuildChecks additionally runs Gradle checks
    (tasks / spotlessCheck / check). Those commands are Gradle and therefore
    DO write build outputs - they still never run packCache.

    Usage
    -----
      .\Invoke-UnforgeProjectAudit.ps1 `
          -ProjectRoot 'C:\Users\Administrator\Desktop\Unforge-vps' `
          -OutputDirectory ([Environment]::GetFolderPath('Desktop')) `
          -IncludeBuildChecks -IncludeClientChecks -IncludeServerChecks `
          -IncludeContentChecks -IncludeGitChecks -IncludeRuntimeChecks

    Shorter form:
      .\Invoke-UnforgeProjectAudit.ps1 -Deep -IncludeClient -IncludeContent -IncludeRuntime

    Overall status:
      HEALTHY            - builds and key checks pass
      ATTENTION_REQUIRED - warnings or individual failed checks
      BLOCKED            - the audit itself cannot run (environment problem)
      CRITICAL           - build broken, cache corrupt or config mismatch
#>

[CmdletBinding()]
param(
    [string] $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string] $OutputDirectory = ([Environment]::GetFolderPath('Desktop')),

    # Runs Gradle-based checks (tasks, spotlessCheck, check).
    [switch] $Deep,
    [switch] $IncludeBuildChecks,

    # Section switches. With none of these set, every read-only section runs.
    [switch] $IncludeGitChecks,
    [switch] $IncludeServerChecks,
    [switch] $IncludeContentChecks,
    [switch] $IncludeContent,
    [switch] $IncludeClientChecks,
    [switch] $IncludeClient,
    [switch] $IncludeRuntimeChecks,
    [switch] $IncludeRuntime,

    # Cap for log lines embedded into report.md per check (full output always
    # goes to logs\).
    [int] $MaxReportLogLines = 60
)

$ErrorActionPreference = 'Continue'
$auditStart = Get-Date

# ---------------------------------------------------------------------------
# Section gates. The base audit is everything read-only; gradle work is gated.
# ---------------------------------------------------------------------------
$anySectionFlag = $IncludeGitChecks -or $IncludeServerChecks -or $IncludeContentChecks -or `
    $IncludeContent -or $IncludeClientChecks -or $IncludeClient -or $IncludeRuntimeChecks -or `
    $IncludeRuntime -or $IncludeBuildChecks -or $Deep

$doGit = $IncludeGitChecks -or -not $anySectionFlag
$doServer = $IncludeServerChecks -or -not $anySectionFlag
$doContent = $IncludeContentChecks -or $IncludeContent -or -not $anySectionFlag
$doClient = $IncludeClientChecks -or $IncludeClient -or -not $anySectionFlag
$doRuntime = $IncludeRuntimeChecks -or $IncludeRuntime -or -not $anySectionFlag
$doBuild = $IncludeBuildChecks -or $Deep

# ---------------------------------------------------------------------------
# Output layout
# ---------------------------------------------------------------------------
$stamp = $auditStart.ToString('yyyy-MM-dd-HHmm')
$auditDir = Join-Path $OutputDirectory "Unforge-Audit-$stamp"
$logDir = Join-Path $auditDir 'logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

$reportPath = Join-Path $auditDir 'report.md'
$summaryPath = Join-Path $auditDir 'summary.json'

# ---------------------------------------------------------------------------
# Result model
# ---------------------------------------------------------------------------
$script:checks = New-Object System.Collections.Generic.List[object]   # invoked commands
$script:findings = New-Object System.Collections.Generic.List[object] # severity findings
$script:report = New-Object System.Text.StringBuilder

function Add-Finding([string] $Severity, [string] $Area, [string] $Message) {
    # Severity: CRITICAL | WARNING | INFO
    $script:findings.Add([ordered]@{ severity = $Severity; area = $Area; message = $Message })
}

function Add-Report([string] $Line = '') {
    [void] $script:report.AppendLine($Line)
}

function Add-Check {
    param(
        [string] $Name,
        [string] $Command,
        [string] $Status,        # PASS | FAIL | SKIP | BLOCKED | WARN | INFO
        [string] $LogFile = '',
        [double] $DurationSec = 0,
        [int] $ExitCode = -1
    )
    $script:checks.Add([ordered]@{
        name        = $Name
        command     = $Command
        status      = $Status
        exitCode    = $ExitCode
        durationSec = [math]::Round($DurationSec, 1)
        logFile     = $LogFile
    })
}

function Invoke-AuditCommand {
    <#
        Runs a command read-only against the project, captures stdout+stderr
        into logs\<name>.log and records the result. Never throws.
    #>
    param(
        [string] $Name,
        [string] $CommandLine,          # display string
        [scriptblock] $Body,
        [string] $WorkingDirectory = $ProjectRoot
    )
    $logFile = Join-Path $logDir "$Name.log"
    $start = Get-Date
    $exitCode = 0
    $status = 'PASS'
    try {
        Push-Location $WorkingDirectory
        try {
            $output = & $Body 2>&1 | Out-String -Width 300
            $exitCode = if ($null -ne $LASTEXITCODE) { $LASTEXITCODE } else { 0 }
        } finally {
            Pop-Location
        }
        $output | Out-File -FilePath $logFile -Encoding utf8
        if ($exitCode -ne 0) { $status = 'FAIL' }
    } catch {
        $_.Exception.Message | Out-File -FilePath $logFile -Encoding utf8
        $status = 'FAIL'
        $exitCode = -1
    }
    $duration = ((Get-Date) - $start).TotalSeconds
    Add-Check -Name $Name -Command $CommandLine -Status $status -ExitCode $exitCode `
        -DurationSec $duration -LogFile "logs\$Name.log"
    return [ordered]@{ status = $status; exitCode = $exitCode; log = $logFile }
}

function Get-FileCount([string] $Path, [string[]] $Include) {
    if (-not (Test-Path $Path)) { return 0 }
    return (Get-ChildItem -Path $Path -Recurse -File -Include $Include -ErrorAction SilentlyContinue).Count
}

function Get-NewestWriteTime([string] $Path, [string[]] $Include) {
    if (-not (Test-Path $Path)) { return $null }
    return (Get-ChildItem -Path $Path -Recurse -File -Include $Include -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
}

function Format-Date($Date) {
    if ($null -eq $Date) { return '-' }
    return ([datetime] $Date).ToString('yyyy-MM-dd HH:mm')
}

# ---------------------------------------------------------------------------
# Header
# ---------------------------------------------------------------------------
Add-Report "# UnForge Project Audit"
Add-Report ""
Add-Report "- Generated: $($auditStart.ToString('yyyy-MM-dd HH:mm:ss'))"
Add-Report "- ProjectRoot: ``$ProjectRoot``"
Add-Report "- Output: ``$auditDir``"
Add-Report "- Mode: $(if ($doBuild) { 'base + deep (gradle checks)' } else { 'base (read-only)' })"
Add-Report ""

# ===========================================================================
# 1. Environment
# ===========================================================================
Add-Report "## 1. Environment"
Add-Report ""
try {
    $os = (Get-CimInstance Win32_OperatingSystem).Caption
    $osVersion = [System.Environment]::OSVersion.Version.ToString()
} catch { $os = 'unknown'; $osVersion = 'unknown' }

$javaInfo = 'not found'
$javaVersionOut = $null
try {
    $javaVersionOut = (& java -version 2>&1 | Select-Object -First 1) -join ' '
    if ($javaVersionOut) { $javaInfo = $javaVersionOut }
} catch {
    $bundled = Join-Path $ProjectRoot 'runtime\jdk-21.0.12.1+1-jre\bin\java.exe'
    if (Test-Path $bundled) {
        try { $javaInfo = 'bundled: ' + ((& $bundled -version 2>&1 | Select-Object -First 1) -join ' ') } catch {}
    }
}
if ($javaInfo -eq 'not found') {
    Add-Finding 'WARNING' 'environment' 'java is not on PATH and no bundled runtime found'
}

$gradleWrapper = Join-Path $ProjectRoot 'gradle\wrapper\gradle-wrapper.properties'
$gradleVersion = 'unknown'
if (Test-Path $gradleWrapper) {
    $gradleVersion = (Select-String -Path $gradleWrapper -Pattern 'distributionUrl=.*gradle-([\d.]+)' |
        ForEach-Object { $_.Matches.Groups[1].Value } | Select-Object -First 1)
}

$drive = (Get-Item $ProjectRoot).PSDrive.Name
$disk = Get-PSDrive -Name $drive -ErrorAction SilentlyContinue
$freeGb = if ($disk) { [math]::Round($disk.Free / 1GB, 1) } else { 'unknown' }

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).
    IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

Add-Report "| Key | Value |"
Add-Report "|---|---|"
Add-Report "| Windows | $os ($osVersion) |"
Add-Report "| PowerShell | $($PSVersionTable.PSVersion) |"
Add-Report "| Java | $javaInfo |"
Add-Report "| JAVA_HOME | $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { '(not set)' }) |"
Add-Report "| Gradle wrapper | $gradleVersion |"
Add-Report "| Free disk ($drive`:) | $freeGb GB |"
Add-Report "| Project root | ``$ProjectRoot`` |"
Add-Report "| Elevated | $isAdmin |"
Add-Report "| Audit started | $(Format-Date $auditStart) |"
Add-Report ""

if (-not (Test-Path (Join-Path $ProjectRoot 'gradlew.bat'))) {
    Add-Finding 'CRITICAL' 'environment' "gradlew.bat missing - cannot run any build check"
}
if ($freeGb -ne 'unknown' -and $freeGb -lt 5) {
    Add-Finding 'WARNING' 'environment' "low disk space on ${drive}: ${freeGb} GB free"
}

# ===========================================================================
# 2. Git and worktree state
# ===========================================================================
Add-Report "## 2. Git / Worktree"
Add-Report ""
$gitState = 'UNKNOWN'
$gitSummary = [ordered]@{}
if ($doGit) {
    $hasDotGit = Test-Path (Join-Path $ProjectRoot '.git')
    $gitExe = Get-Command git -ErrorAction SilentlyContinue
    if (-not $hasDotGit -and -not $gitExe) {
        $gitState = 'UNKNOWN'
        Add-Report "**Status: UNKNOWN** - no ``.git`` directory and ``git`` is not on PATH."
        Add-Report ""
        Add-Report "Worktree state cannot be diffed. Snapshot-based change tracking only."
        Add-Finding 'INFO' 'git' 'no .git directory; git not available - worktree diff impossible'
    } elseif (-not $hasDotGit) {
        $gitState = 'UNKNOWN'
        Add-Report "**Status: UNKNOWN** - ``git`` exists but ``.git`` directory is missing."
        Add-Finding 'INFO' 'git' '.git directory missing - workspace is a snapshot, not a checkout'
    } else {
        $r = Invoke-AuditCommand -Name 'git-status' -CommandLine 'git status --porcelain=v1 -b' `
            -Body { & git status --porcelain=v1 -b }
        Invoke-AuditCommand -Name 'git-log' -CommandLine 'git log -10 --pretty=format:%h%x09%ad%x09%s --date=short' `
            -Body { & git log -10 '--pretty=format:%h%x09%ad%x09%s' --date=short } | Out-Null
        if ($r.status -eq 'PASS') {
            $lines = Get-Content (Join-Path $logDir 'git-status.log') | Where-Object { $_ -and $_ -notmatch '^##' }
            $branch = (Get-Content (Join-Path $logDir 'git-status.log') | Select-String '^##' | Select-Object -First 1) -replace '^## ',''
            $staged = @($lines | Where-Object { $_ -match '^[MADRC]' })
            $unstaged = @($lines | Where-Object { $_ -match '^.[MD]' })
            $untracked = @($lines | Where-Object { $_ -match '^\?\?' })
            $conflicts = @($lines | Where-Object { $_ -match '^(UU|AA|DD|AU|UA|DU|UD)' })
            $gitState = if ($conflicts.Count -gt 0) { 'CONFLICT' }
                elseif ($untracked.Count -gt 0 -and $staged.Count -eq 0 -and $unstaged.Count -eq 0) { 'UNTRACKED' }
                elseif ($staged.Count -gt 0 -or $unstaged.Count -gt 0) { 'MODIFIED' }
                else { 'CLEAN' }
            $gitSummary = [ordered]@{
                branch = $branch; staged = $staged.Count; unstaged = $unstaged.Count
                untracked = $untracked.Count; conflicts = $conflicts.Count
            }
            Add-Report "**Status: $gitState** (branch ``$branch``)"
            Add-Report ""
            Add-Report "| Kind | Count |"
            Add-Report "|---|---|"
            Add-Report "| staged | $($staged.Count) |"
            Add-Report "| unstaged | $($unstaged.Count) |"
            Add-Report "| untracked | $($untracked.Count) |"
            Add-Report "| conflicts | $($conflicts.Count) |"
            Add-Report ""
            if ($conflicts.Count -gt 0) {
                Add-Finding 'CRITICAL' 'git' "$($conflicts.Count) merge conflicts present"
            }
            $log = Get-Content (Join-Path $logDir 'git-log.log') -ErrorAction SilentlyContinue
            if ($log) {
                Add-Report "Recent commits:"
                Add-Report '```'
                $log | Select-Object -First 10 | ForEach-Object { Add-Report $_ }
                Add-Report '```'
                Add-Report ""
            }
        } else {
            Add-Report "**Status: UNKNOWN** - ``git status`` failed (see logs\git-status.log)."
            Add-Finding 'WARNING' 'git' 'git status failed'
        }
    }
} else {
    Add-Report "_Skipped (no -IncludeGitChecks)._"
}
Add-Report ""

# ===========================================================================
# 3. Server structure
# ===========================================================================
Add-Report "## 3. Server structure"
Add-Report ""
$moduleRows = New-Object System.Collections.Generic.List[object]
$modulesNoTests = New-Object System.Collections.Generic.List[string]
$sourceDirsNoBuild = New-Object System.Collections.Generic.List[string]
if ($doServer) {
    $areas = 'server', 'api', 'engine', 'content', 'config', 'protocol', 'bridge'
    foreach ($area in $areas) {
        $areaPath = Join-Path $ProjectRoot $area
        if (-not (Test-Path $areaPath)) {
            Add-Report "``$area/`` - **missing**"
            Add-Finding 'WARNING' 'structure' "expected directory missing: $area/"
            continue
        }
        $buildFiles = Get-ChildItem -Path $areaPath -Recurse -Filter 'build.gradle.kts' -File -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '\\build\\|\\bin\\' }
        foreach ($bf in $buildFiles) {
            $modDir = $bf.DirectoryName
            $rel = $modDir.Substring($ProjectRoot.Length).TrimStart('\')
            $mainKt = Get-FileCount (Join-Path $modDir 'src\main') @('*.kt', '*.java')
            $testKt = Get-FileCount (Join-Path $modDir 'src\test') @('*.kt', '*.java')
            $intKt = Get-FileCount (Join-Path $modDir 'src\integration') @('*.kt', '*.java')
            $hasBuild = Test-Path (Join-Path $modDir 'build')
            $newestSrc = Get-NewestWriteTime (Join-Path $modDir 'src') @('*.kt', '*.java')
            $newestBuild = $null
            $buildDir = Join-Path $modDir 'build'
            if (Test-Path $buildDir) {
                $newestBuild = (Get-ChildItem $buildDir -Recurse -File -ErrorAction SilentlyContinue |
                    Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
            }
            $stale = ($newestSrc -and $newestBuild -and $newestSrc -gt $newestBuild) -or
                     ($newestSrc -and -not $newestBuild)
            # failed test results
            $failedTests = 0
            $trDir = Join-Path $modDir 'build\test-results'
            if (Test-Path $trDir) {
                $failedTests = (Get-ChildItem $trDir -Recurse -Filter '*.xml' -ErrorAction SilentlyContinue |
                    Select-String -Pattern 'failures="[1-9]|errors="[1-9]' -List).Count
            }
            $moduleRows.Add([ordered]@{
                module = $rel; main = $mainKt; test = $testKt; integration = $intKt
                buildOutput = $hasBuild; staleSources = $stale; failedTestReports = $failedTests
            })
            if ($mainKt -gt 0 -and $testKt -eq 0 -and $intKt -eq 0) {
                $modulesNoTests.Add($rel)
            }
            if ($failedTests -gt 0) {
                Add-Finding 'WARNING' 'tests' "$rel`: $failedTests failed test report(s) on disk"
            }
        }
        # source trees without a build file (never compiled)
        $srcRoots = Get-ChildItem -Path $areaPath -Recurse -Directory -Filter 'kotlin' -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match 'src\\(main|test|integration)\\kotlin$' -and $_.FullName -notmatch '\\build\\' }
        foreach ($sr in $srcRoots) {
            $modDir = $sr.FullName -replace '\\src\\(main|test|integration)\\kotlin$', ''
            if (-not (Test-Path (Join-Path $modDir 'build.gradle.kts'))) {
                $sourceDirsNoBuild.Add($modDir.Substring($ProjectRoot.Length).TrimStart('\'))
            }
        }
    }

    Add-Report "Modules with ``build.gradle.kts``: **$($moduleRows.Count)**"
    Add-Report ""
    Add-Report "| Module | Main | Unit | Integration | Built | Stale |"
    Add-Report "|---|---|---|---|---|---|"
    foreach ($m in $moduleRows) {
        Add-Report "| $($m.module) | $($m.main) | $($m.test) | $($m.integration) | $(if ($m.buildOutput) {'yes'} else {'no'}) | $(if ($m.staleSources) {'YES'} else {'-'}) |"
    }
    Add-Report ""
    if ($sourceDirsNoBuild.Count -gt 0) {
        Add-Report "Source trees **without** a ``build.gradle.kts`` (never compiled):"
        foreach ($s in $sourceDirsNoBuild | Sort-Object -Unique) { Add-Report "- ``$s``" }
        Add-Report ""
        Add-Finding 'WARNING' 'structure' "$($sourceDirsNoBuild.Count) source tree(s) not wired into any build"
    }
    if ($modulesNoTests.Count -gt 0) {
        Add-Report "Modules with sources but **no tests**: $($modulesNoTests.Count)"
        Add-Report ""
        Add-Finding 'INFO' 'tests' "$($modulesNoTests.Count) module(s) have no tests"
    }
} else {
    Add-Report "_Skipped._"
}
Add-Report ""

# ===========================================================================
# 4. Custom content coverage
# ===========================================================================
Add-Report "## 4. Custom content"
Add-Report ""
$contentStats = [ordered]@{}
if ($doContent) {
    $contentRoot = Join-Path $ProjectRoot 'content'
    $contentModules = @($moduleRows | Where-Object { $_.module -like 'content*' })

    Add-Report "| Area | Value |"
    Add-Report "|---|---|"
    Add-Report "| content modules (build file) | $($contentModules.Count) |"
    Add-Report "| modules w/o tests | $(@($contentModules | Where-Object { $_.main -gt 0 -and $_.test -eq 0 -and $_.integration -eq 0 }).Count) |"
    Add-Report ""

    # content resource/data inventory
    $kronosData = Join-Path $contentRoot 'kronos-data'
    $kronosJson = if (Test-Path $kronosData) {
        (Get-ChildItem $kronosData -Recurse -Filter '*.json' -ErrorAction SilentlyContinue).Count
    } else { 'missing' }
    $tomlFiles = Get-ChildItem $contentRoot -Recurse -Filter '*.toml' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch '\\build\\' }
    $jsonRes = Get-ChildItem $contentRoot -Recurse -Filter '*.json' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match 'src\\main\\resources' }
    Add-Report "| Data | Count |"
    Add-Report "|---|---|"
    Add-Report "| kronos-data JSON files | $kronosJson |"
    Add-Report "| spawn/resource TOML | $($tomlFiles.Count) |"
    Add-Report "| resource JSON (src/main/resources) | $($jsonRes.Count) |"
    Add-Report ""

    # marker scan
    $markerPatterns = [ordered]@{
        'TODO'                = '(?://|\*)\s*TODO'
        'FIXME'               = '(?://|\*)\s*FIXME'
        'XXX'                 = '(?://|\*)\s*XXX'
        'NotImplementedError' = 'NotImplementedError'
        'error NotImpl'       = 'error\("Not implemented'
        'println'             = '\bprintln\('
        'printStackTrace'     = '\.printStackTrace\(\)'
        'localhost/127.0.0.1' = '(127\.0\.0\.1|localhost)'
    }
    Add-Report "| Marker | Files | Hits |"
    Add-Report "|---|---|---|"
    $markerSummary = [ordered]@{}
    foreach ($key in $markerPatterns.Keys) {
        $hits = Get-ChildItem $contentRoot -Recurse -Filter '*.kt' -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '\\build\\|\\bin\\' } |
            Select-String -Pattern $markerPatterns[$key] -ErrorAction SilentlyContinue
        $files = @($hits | Group-Object Path).Count
        $count = @($hits).Count
        $markerSummary[$key] = [ordered]@{ files = $files; hits = $count }
        Add-Report "| ``$key`` | $files | $count |"
    }
    Add-Report ""
    $todoHits = $markerSummary['TODO'].hits + $markerSummary['FIXME'].hits
    if ($todoHits -gt 0) { Add-Finding 'INFO' 'content' "$todoHits TODO/FIXME marker(s) in content sources" }
    if ($markerSummary['NotImplementedError'].hits -gt 0) {
        Add-Finding 'WARNING' 'content' "$($markerSummary['NotImplementedError'].hits) NotImplementedError site(s)"
    }
    $contentStats = [ordered]@{
        contentModules = $contentModules.Count
        kronosJsonFiles = $kronosJson
        tomlFiles = $tomlFiles.Count
        markers = $markerSummary
    }
} else {
    Add-Report "_Skipped._"
}
Add-Report ""

# ===========================================================================
# 5. Build and test checks (deep only)
# ===========================================================================
Add-Report "## 5. Build / test checks"
Add-Report ""
if ($doBuild) {
    Add-Report "Gradle checks (each output in ``logs\``):"
    Add-Report ""
    $gradlew = Join-Path $ProjectRoot 'gradlew.bat'
    if (-not (Test-Path $gradlew)) {
        Add-Check -Name 'gradle-tasks' -Command 'gradlew tasks --all' -Status 'BLOCKED'
        Add-Report "- ``[BLOCKED]`` gradlew.bat missing"
        Add-Finding 'CRITICAL' 'build' 'gradle wrapper missing'
    } else {
        $env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else {
            (Get-ChildItem (Join-Path $env:USERPROFILE '.gradle\jdks') -Directory -ErrorAction SilentlyContinue |
                Where-Object Name -match 'jdk|adoptium|temurin' | Select-Object -First 1 -ExpandProperty FullName)
        }
        $r1 = Invoke-AuditCommand -Name 'gradle-tasks' -CommandLine 'gradlew.bat tasks --all --no-daemon --console=plain' `
            -Body { & .\gradlew.bat tasks --all --no-daemon --console=plain }
        Add-Report "- ``[$($r1.status)]`` ``gradlew tasks --all`` ($($r1.exitCode))"
        if ($r1.status -eq 'PASS') {
            $r2 = Invoke-AuditCommand -Name 'gradle-spotless' -CommandLine 'gradlew.bat spotlessCheck --no-daemon --console=plain' `
                -Body { & .\gradlew.bat spotlessCheck --no-daemon --console=plain }
            Add-Report "- ``[$($r2.status)]`` ``spotlessCheck`` ($($r2.exitCode))"
            if ($r2.status -ne 'PASS') {
                Add-Finding 'WARNING' 'build' 'spotlessCheck failed - run spotlessApply'
            }
            $r3 = Invoke-AuditCommand -Name 'gradle-check' -CommandLine 'gradlew.bat check --no-daemon --max-workers=2 --console=plain' `
                -Body { & .\gradlew.bat check --no-daemon --max-workers=2 --console=plain }
            Add-Report "- ``[$($r3.status)]`` ``check`` ($($r3.exitCode))"
            if ($r3.status -ne 'PASS') {
                Add-Finding 'CRITICAL' 'build' 'gradle check failed - see logs\gradle-check.log'
            }
        } else {
            Add-Finding 'CRITICAL' 'build' 'gradlew tasks --all failed - gradle unusable'
            Add-Report "- ``[SKIP]`` spotlessCheck / check (tasks failed)"
            Add-Check -Name 'gradle-spotless' -Command 'spotlessCheck' -Status 'SKIP'
            Add-Check -Name 'gradle-check' -Command 'check' -Status 'SKIP'
        }
    }
    Add-Report ""
    Add-Report "``packCache``: **never run by this audit** (mutates ``.data/cache``) - analysed in section 6."
} else {
    Add-Report "_Skipped - run with ``-Deep`` or ``-IncludeBuildChecks`` to enable gradle checks._"
}
Add-Report ""

# ===========================================================================
# 6. Cache and symbols
# ===========================================================================
Add-Report "## 6. Cache / symbols"
Add-Report ""
$cacheStatus = [ordered]@{}
$cacheDir = Join-Path $ProjectRoot '.data\cache'
$symDir = Join-Path $ProjectRoot '.data\symbols'

foreach ($store in 'game', 'js5', 'enriched', 'vanilla') {
    $dir = Join-Path $cacheDir $store
    if (-not (Test-Path $dir)) {
        $cacheStatus[$store] = 'CACHE_MISSING'
        continue
    }
    $dat = Get-ChildItem $dir -Filter 'main_file_cache.dat2' -ErrorAction SilentlyContinue
    $idx = Get-ChildItem $dir -Filter 'main_file_cache.idx255' -ErrorAction SilentlyContinue
    if (-not $dat -or -not $idx) {
        $cacheStatus[$store] = 'CACHE_SUSPECTED_CORRUPT'
        Add-Finding 'CRITICAL' 'cache' "$store store incomplete (dat2/idx255 missing)"
        continue
    }
    # group data newer than the master index = crash-signature observed in this workspace
    $state = if ($dat.LastWriteTime -gt $idx.LastWriteTime.AddMinutes(2)) {
        'CACHE_SUSPECTED_CORRUPT'
    } else { 'CACHE_PRESENT' }
    if ($state -eq 'CACHE_SUSPECTED_CORRUPT') {
        Add-Finding 'WARNING' 'cache' "$store`: dat2 newer than idx255 (possible stale master index)"
    }
    $cacheStatus[$store] = $state
}

$symbolStats = [ordered]@{ missingReferences = 0; duplicateIds = 0; duplicateNames = 0; malformed = 0; emptyFiles = @() }
if (Test-Path $symDir) {
    foreach ($sym in (Get-ChildItem $symDir -Filter '*.sym' -ErrorAction SilentlyContinue)) {
        if ($sym.Length -eq 0) { $symbolStats.emptyFiles += $sym.Name; continue }
        $ids = @{}; $names = @{}
        foreach ($line in (Get-Content $sym.FullName -ErrorAction SilentlyContinue)) {
            # id field is either `<num>` or `<name>:<num>` (component.sym)
            if ($line -notmatch '^(\d+|[^\t]+:\d+)\t[^\t]+$') {
                if ($line.Trim().Length -gt 0) { $symbolStats.malformed++ }
                continue
            }
            $parts = $line -split "`t", 2
            if ($ids.ContainsKey($parts[0])) { $symbolStats.duplicateIds++ } else { $ids[$parts[0]] = $true }
            if ($names.ContainsKey($parts[1])) { $symbolStats.duplicateNames++ } else { $names[$parts[1]] = $true }
        }
    }
}
if ($symbolStats.duplicateIds -gt 0) {
    Add-Finding 'CRITICAL' 'cache' "$($symbolStats.duplicateIds) duplicate symbol id(s) in .data\symbols"
}
if ($symbolStats.duplicateNames -gt 0) {
    Add-Finding 'WARNING' 'cache' "$($symbolStats.duplicateNames) duplicate symbol name(s)"
}
if ($symbolStats.malformed -gt 0) {
    Add-Finding 'WARNING' 'cache' "$($symbolStats.malformed) malformed symbol line(s)"
}

# rebuild recommendation: cache older than newest content/source file
$newestSource = Get-NewestWriteTime (Join-Path $ProjectRoot 'content') @('*.kt', '*.toml', '*.json')
$gameDat = Join-Path $cacheDir 'game\main_file_cache.dat2'
$rebuild = 'NO'
if ((Test-Path $gameDat) -and $newestSource -and
    (Get-Item $gameDat).LastWriteTime -lt $newestSource) {
    $rebuild = 'CACHE_REBUILD_REQUIRED'
}
if ($cacheStatus['game'] -eq 'CACHE_MISSING' -or $cacheStatus['game'] -eq 'CACHE_SUSPECTED_CORRUPT') {
    $rebuild = 'CACHE_REBUILD_REQUIRED'
}

# historical corruption markers in logs
$corruptHits = @()
foreach ($scanDir in @((Join-Path $ProjectRoot 'build\local-shadow-logs'), (Join-Path $ProjectRoot 'work'))) {
    if (Test-Path $scanDir) {
        $corruptHits += Get-ChildItem $scanDir -Recurse -Include '*.log','*.txt' -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Length -lt 20MB } |
            Select-String -Pattern 'StoreCorruptException|TypeVerifier' -List -ErrorAction SilentlyContinue
    }
}

Add-Report "| Store | State | dat2 size | dat2 mtime |"
Add-Report "|---|---|---|---|"
foreach ($store in 'game', 'js5', 'enriched', 'vanilla') {
    $dat = Join-Path $cacheDir "$store\main_file_cache.dat2"
    $sz = if (Test-Path $dat) { '{0:N0} MB' -f ((Get-Item $dat).Length / 1MB) } else { '-' }
    $mt = if (Test-Path $dat) { Format-Date (Get-Item $dat).LastWriteTime } else { '-' }
    Add-Report "| $store | ``$($cacheStatus[$store])`` | $sz | $mt |"
}
Add-Report ""
Add-Report "Symbols:"
Add-Report '```'
Add-Report "  duplicate ids:    $($symbolStats.duplicateIds)"
Add-Report "  duplicate names:  $($symbolStats.duplicateNames)"
Add-Report "  malformed lines:  $($symbolStats.malformed)"
Add-Report "  empty .sym files: $($symbolStats.emptyFiles -join ', ')"
Add-Report '```'
Add-Report ""
Add-Report "Cache:"
Add-Report '```'
Add-Report "  rebuild recommendation: $rebuild"
Add-Report "  StoreCorrupt/TypeVerifier log hits: $($corruptHits.Count)"
Add-Report '```'
Add-Report ""
if ($rebuild -eq 'CACHE_REBUILD_REQUIRED') {
    Add-Finding 'WARNING' 'cache' 'cache is older than content sources or corrupt - packCache recommended'
}

# ===========================================================================
# 7. Client
# ===========================================================================
Add-Report "## 7. Client"
Add-Report ""
$clientStats = [ordered]@{}
if ($doClient) {
    $clientRoot = Join-Path $ProjectRoot 'client-src'
    if (-not (Test-Path $clientRoot)) {
        Add-Report "``client-src/`` **missing**"
        Add-Finding 'WARNING' 'client' 'client-src missing'
    } else {
        $rlClient = Join-Path $clientRoot 'runelite-client'
        $pluginRoot = Join-Path $rlClient 'src\main\java\net\runelite\client\plugins'
        $unforgePlugins = @()
        if (Test-Path $pluginRoot) {
            $unforgePlugins = Get-ChildItem $pluginRoot -Directory -ErrorAction SilentlyContinue |
                Where-Object Name -like 'unforge*'
        }
        $clientJava = Get-FileCount (Join-Path $rlClient 'src\main') @('*.java')
        $clientTests = Get-FileCount (Join-Path $rlClient 'src\test') @('*.java', '*.kt')
        $clientGradle = Test-Path (Join-Path $clientRoot 'build.gradle.kts')
        $clientGradlew = Test-Path (Join-Path $clientRoot 'gradlew.bat')

        $libsDir = Join-Path $rlClient 'build\libs'
        $shaded = Get-ChildItem $libsDir -Filter '*-shaded.jar' -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        $newestClientSrc = Get-NewestWriteTime (Join-Path $rlClient 'src\main') @('*.java')
        $jarStale = $shaded -and $newestClientSrc -and ($newestClientSrc -gt $shaded.LastWriteTime)

        # jav config
        $javConfig = Join-Path $ProjectRoot 'tools\standalone\client\jav_local_239.ws'
        $javProps = @{}
        if (Test-Path $javConfig) {
            foreach ($l in (Get-Content $javConfig)) {
                # jav_config lines are `key=value` or `param=<n>=<value>` - keep `param=<n>` as key
                $parts = $l -split '=', 3
                if ($parts.Count -ge 3) {
                    $javProps["$($parts[0])=$($parts[1])"] = $parts[2]
                } elseif ($parts.Count -eq 2) {
                    $javProps[$parts[0]] = $parts[1]
                }
            }
        }
        $gamepack = Join-Path $ProjectRoot "tools\standalone\client\$($javProps['initial_jar'])"
        $gamepackOk = ($javProps['initial_jar'] -and (Test-Path $gamepack))

        Add-Report "| Area | Value |"
        Add-Report "|---|---|"
        Add-Report "| runelite-client | $(if (Test-Path $rlClient) {'present'} else {'MISSING'}) |"
        Add-Report "| Java sources | $clientJava |"
        Add-Report "| tests | $clientTests |"
        Add-Report "| build.gradle.kts / gradlew | $clientGradle / $clientGradlew |"
        Add-Report "| unforge plugin dirs | $($unforgePlugins.Count) ($($unforgePlugins.Name -join ', ')) |"
        Add-Report "| shaded jar | $(if ($shaded) { "$($shaded.Name) ($(Format-Date $shaded.LastWriteTime))" } else { 'MISSING' }) |"
        Add-Report "| jar older than sources | $(if ($jarStale) {'YES'} else {'no'}) |"
        Add-Report "| jav config revision | param=25 -> $($javProps['param=25']) |"
        Add-Report "| initial_jar | $($javProps['initial_jar']) $(if ($gamepackOk) {'(present)'} else {'(MISSING)'}) |"
        Add-Report ""

        if (-not $shaded) { Add-Finding 'WARNING' 'client' 'shaded client jar missing - build :client:shadowJar' }
        if ($jarStale) { Add-Finding 'WARNING' 'client' 'client jar older than client sources - rebuild recommended' }
        if (-not $gamepackOk) { Add-Finding 'WARNING' 'client' "gamepack jar missing: $($javProps['initial_jar'])" }
        if ($javProps['param=25'] -ne '239') {
            Add-Finding 'WARNING' 'client' "jav config revision param=25 is '$($javProps['param=25'])', expected 239"
        }

        # RSA / config references (count only - read-only)
        $rsaHits = @(Get-ChildItem $clientRoot -Recurse -Include '*.java' -File -ErrorAction SilentlyContinue |
            Select-String -Pattern 'modulus|RSA|rsa' -List -ErrorAction SilentlyContinue).Count
        Add-Report "RSA/modulus source references: $rsaHits file(s); patch script: ``tools\standalone\Patch-Unforge239-InjectedClientRsa.ps1``"
        Add-Report ""
        $clientStats = [ordered]@{
            javaSources = $clientJava; tests = $clientTests; unforgePlugins = $unforgePlugins.Count
            shadedJar = if ($shaded) { $shaded.Name } else { $null }; jarStale = $jarStale
            revision = $javProps['param=25']
        }
    }
} else {
    Add-Report "_Skipped._"
}
Add-Report ""

# ===========================================================================
# 8. Runtime / launcher state
# ===========================================================================
Add-Report "## 8. Runtime"
Add-Report ""
$runtimeStats = [ordered]@{}
if ($doRuntime) {
    $launchers = Get-ChildItem (Join-Path $ProjectRoot 'tools\standalone') -Filter '*.ps1' -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty Name
    $launchers2 = Get-ChildItem (Join-Path $ProjectRoot 'LocalLauncher-V2') -Filter '*.ps1' -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty Name
    $serverDist = Test-Path (Join-Path $ProjectRoot 'server\app\build\install\app\lib')
    $clientJar = Test-Path (Join-Path $ProjectRoot 'client-src\runelite-client\build\libs\client-1.12.34-SNAPSHOT-shaded.jar')

    Add-Report "| Asset | State |"
    Add-Report "|---|---|"
    Add-Report "| launcher scripts | $($launchers.Count + $launchers2.Count) found |"
    Add-Report "| server installDist | $(if ($serverDist) {'PROCESS_FOUND-compatible (present)'} else {'missing - run :server:app:installDist'}) |"
    Add-Report "| client shaded jar | $(if ($clientJar) {'present'} else {'missing'}) |"
    Add-Report ""

    if (-not $serverDist) { Add-Finding 'WARNING' 'runtime' 'server distribution missing (server\app\build\install\app)' }

    # processes (read-only)
    $javaProcs = Get-CimInstance Win32_Process -Filter "Name like 'java%'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'Unforge|GameServer|client-1\.12|content-editor|bridge|runelite' }
    Add-Report "| Process | PID | Detail |"
    Add-Report "|---|---|---|"
    if ($javaProcs) {
        foreach ($p in $javaProcs) {
            $detail = if ($p.CommandLine -match 'GameServer') { 'game server' }
                elseif ($p.CommandLine -match 'client-1\.12|runelite') { 'client' }
                elseif ($p.CommandLine -match 'content-editor') { 'content editor' }
                elseif ($p.CommandLine -match 'bridge') { 'ai bridge' }
                elseif ($p.CommandLine -match 'packCache|CachePacker') { 'packCache (running!)' }
                else { 'java (unforge-related)' }
            Add-Report "| $detail | $($p.ProcessId) | ``$($p.CommandLine.Substring(0, [Math]::Min(90, $p.CommandLine.Length)))`` |"
            if ($p.CommandLine -match 'packCache|CachePacker') {
                Add-Finding 'WARNING' 'runtime' 'packCache is currently running - cache state may be in flux'
            }
        }
    } else {
        Add-Report "| - | - | PROCESS_NOT_FOUND |"
    }
    Add-Report ""

    # ports (read-only)
    $expectedPorts = [ordered]@{
        43594 = 'game server'; 8088 = 'client config'; 18900 = 'bridge http'
        18901 = 'bridge ws'; 18902 = 'studio agent'; 18930 = 'content editor'; 8787 = 'AI4FUN studio'
    }
    $listeners = @{}
    foreach ($c in (Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue)) {
        $listeners[$c.LocalPort] = $true
    }
    Add-Report "| Port | Expected | State |"
    Add-Report "|---|---|---|"
    foreach ($entry in $expectedPorts.GetEnumerator()) {
        $port = [int] $entry.Key
        $state = if ($listeners[$port]) { 'LISTENING' } else { 'NOT_LISTENING' }
        $runtimeStats["port_$port"] = $state
        Add-Report "| $port | $($entry.Value) | ``$state`` |"
    }
    Add-Report ""

    # shallow health probes only when already listening (never starts anything)
    if ($listeners[18900]) {
        try {
            $resp = Invoke-WebRequest -Uri 'http://127.0.0.1:18900/health' -TimeoutSec 3 -UseBasicParsing
            Add-Report "Bridge health: HTTP $($resp.StatusCode)"
            $runtimeStats['bridge_health'] = $resp.StatusCode
        } catch {
            Add-Report "Bridge health: port listening but /health failed ($($_.Exception.Message))"
            $runtimeStats['bridge_health'] = 'unhealthy'
        }
    }
    $editorUrl = Join-Path $ProjectRoot 'work\content-editor\server.url'
    if (Test-Path $editorUrl) {
        Add-Report "Content editor url file: ``$(Get-Content $editorUrl -Raw)``"
    }
    Add-Report ""
} else {
    Add-Report "_Skipped._"
}
Add-Report ""

# ===========================================================================
# 9. Check results table
# ===========================================================================
Add-Report "## 9. Invoked checks"
Add-Report ""
if ($script:checks.Count -eq 0) {
    Add-Report "_No external commands were invoked (base read-only audit)._"
} else {
    Add-Report "| Check | Status | Exit | Duration | Log |"
    Add-Report "|---|---|---|---|---|"
    foreach ($c in $script:checks) {
        $exit = if ($c.exitCode -ge 0) { $c.exitCode } else { '-' }
        Add-Report "| ``$($c.name)`` | [$($c.status)] | $exit | $($c.durationSec)s | $($c.logFile) |"
    }
}
Add-Report ""

# ===========================================================================
# Summary + status classification
# ===========================================================================
$critical = @($script:findings | Where-Object severity -eq 'CRITICAL')
$warnings = @($script:findings | Where-Object severity -eq 'WARNING')
$infos = @($script:findings | Where-Object severity -eq 'INFO')
$failedChecks = @($script:checks | Where-Object status -eq 'FAIL')
$blockedChecks = @($script:checks | Where-Object status -eq 'BLOCKED')

$overall = 'HEALTHY'
if (-not (Test-Path (Join-Path $ProjectRoot 'gradlew.bat'))) { $overall = 'BLOCKED' }
if ($critical.Count -gt 0 -or $failedChecks.Count -gt 0) { $overall = 'CRITICAL' }
elseif ($warnings.Count -gt 0 -or $blockedChecks.Count -gt 0) { $overall = 'ATTENTION_REQUIRED' }

# suggested next steps from findings
$nextSteps = New-Object System.Collections.Generic.List[string]
if ($rebuild -eq 'CACHE_REBUILD_REQUIRED') { $nextSteps.Add('Run `.\gradlew.bat packCache --console=plain`') }
if ($symbolStats.duplicateIds -gt 0) { $nextSteps.Add('Fix duplicate symbol ids in .data\symbols\*.sym') }
if ($failedChecks.Count -gt 0) { $nextSteps.Add('Investigate failed gradle checks in logs\') }
if ($jarStale) { $nextSteps.Add('Rebuild client: `.\client-src\gradlew.bat :client:shadowJar`') }
if (-not $serverDist -and $doRuntime) { $nextSteps.Add('Build server distribution: `.\gradlew.bat :server:app:installDist`') }
if ($modulesNoTests.Count -gt 0) { $nextSteps.Add("Add tests to $($modulesNoTests.Count) untested module(s)") }
if ($nextSteps.Count -eq 0) { $nextSteps.Add('No actions required.') }

Add-Report "---"
Add-Report "## Summary"
Add-Report ""
Add-Report "**OVERALL STATUS: $overall**"
Add-Report ""
if ($critical.Count -gt 0) {
    Add-Report "Critical:"
    foreach ($f in $critical) { Add-Report "  - [$($f.area)] $($f.message)" }
    Add-Report ""
}
if ($warnings.Count -gt 0) {
    Add-Report "Warnings:"
    foreach ($f in $warnings) { Add-Report "  - [$($f.area)] $($f.message)" }
    Add-Report ""
}
Add-Report "Ready to continue:"
for ($i = 0; $i -lt $nextSteps.Count; $i++) { Add-Report "  $($i + 1). $($nextSteps[$i])" }
Add-Report ""
Add-Report "_Audit duration: $([math]::Round(((Get-Date) - $auditStart).TotalSeconds, 1))s_"

# insert status block near the top as well
$statusBlock = "**OVERALL STATUS: $overall**`n`nCritical findings: $($critical.Count) | Warnings: $($warnings.Count) | Checks failed: $($failedChecks.Count)"
$reportText = $script:report.ToString()
$reportText = $reportText -replace '(- Mode: .*\n)', "`$1`n$statusBlock`n"
$reportText | Out-File -FilePath $reportPath -Encoding utf8

# ---------------------------------------------------------------------------
# summary.json
# ---------------------------------------------------------------------------
$summary = [ordered]@{
    generatedAt  = $auditStart.ToString('o')
    durationSec  = [math]::Round(((Get-Date) - $auditStart).TotalSeconds, 1)
    overallStatus = $overall
    projectRoot  = $ProjectRoot
    environment  = [ordered]@{
        os = $os; powershell = $PSVersionTable.PSVersion.ToString(); java = $javaInfo
        javaHome = $env:JAVA_HOME; gradleWrapper = $gradleVersion; freeDiskGb = $freeGb
    }
    git          = [ordered]@{ state = $gitState; detail = $gitSummary }
    modules      = [ordered]@{
        total = $moduleRows.Count; withoutTests = $modulesNoTests
        sourcesWithoutBuild = $sourceDirsNoBuild
    }
    content      = $contentStats
    cache        = [ordered]@{
        stores = $cacheStatus; symbols = $symbolStats; rebuildRecommendation = $rebuild
    }
    client       = $clientStats
    runtime      = $runtimeStats
    checks       = $script:checks
    findings     = $script:findings
    nextSteps    = $nextSteps
}
$summary | ConvertTo-Json -Depth 8 | Out-File -FilePath $summaryPath -Encoding utf8

Write-Host ""
Write-Host "Audit complete: $overall"
Write-Host "  report:  $reportPath"
Write-Host "  summary: $summaryPath"
Write-Host "  logs:    $logDir"
