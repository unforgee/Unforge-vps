$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$serverScript = Join-Path $PSScriptRoot 'Start-Unforge239-Server-Dev.ps1'
$clientScript = Join-Path $PSScriptRoot 'Start-Unforge239-Client-Dev.ps1'
$configScript = Join-Path $root 'tools\standalone\Serve-UnforgeClientConfig.ps1'
$logDirectory = Join-Path $root 'build\local-launcher-v2'

$form = New-Object System.Windows.Forms.Form
$form.Text = 'UnForge Launcher V2'
$form.StartPosition = 'CenterScreen'
$form.ClientSize = New-Object System.Drawing.Size(620, 410)
$form.MinimumSize = New-Object System.Drawing.Size(620, 410)
$form.MaximizeBox = $false

$title = New-Object System.Windows.Forms.Label
$title.Text = 'UnForge revision 239'
$title.AutoSize = $true
$title.Font = New-Object System.Drawing.Font('Segoe UI', 15, [System.Drawing.FontStyle]::Bold)
$title.Location = New-Object System.Drawing.Point(20, 18)
$form.Controls.Add($title)

$description = New-Object System.Windows.Forms.Label
$description.Text = 'Start the local server and client from the copied launcher.'
$description.AutoSize = $true
$description.Location = New-Object System.Drawing.Point(22, 52)
$form.Controls.Add($description)

$serverButton = New-Object System.Windows.Forms.Button
$serverButton.Text = 'Start Server'
$serverButton.Size = New-Object System.Drawing.Size(170, 46)
$serverButton.Location = New-Object System.Drawing.Point(20, 82)
$serverButton.Font = New-Object System.Drawing.Font('Segoe UI', 10, [System.Drawing.FontStyle]::Bold)
$serverButton.UseVisualStyleBackColor = $true
$form.Controls.Add($serverButton)

$clientButton = New-Object System.Windows.Forms.Button
$clientButton.Text = 'Start Client'
$clientButton.Size = New-Object System.Drawing.Size(170, 46)
$clientButton.Location = New-Object System.Drawing.Point(205, 82)
$clientButton.Font = New-Object System.Drawing.Font('Segoe UI', 10, [System.Drawing.FontStyle]::Bold)
$clientButton.UseVisualStyleBackColor = $true
$form.Controls.Add($clientButton)

$openFolderButton = New-Object System.Windows.Forms.Button
$openFolderButton.Text = 'Open Project Folder'
$openFolderButton.Size = New-Object System.Drawing.Size(170, 46)
$openFolderButton.Location = New-Object System.Drawing.Point(390, 82)
$openFolderButton.UseVisualStyleBackColor = $true
$form.Controls.Add($openFolderButton)

$refreshServerButton = New-Object System.Windows.Forms.Button
$refreshServerButton.Text = 'Rebuild + Restart Server'
$refreshServerButton.Size = New-Object System.Drawing.Size(220, 46)
$refreshServerButton.Location = New-Object System.Drawing.Point(20, 136)
$refreshServerButton.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
$refreshServerButton.UseVisualStyleBackColor = $true
$form.Controls.Add($refreshServerButton)

$statusLabel = New-Object System.Windows.Forms.Label
$statusLabel.Text = 'Ready.'
$statusLabel.AutoSize = $true
$statusLabel.Location = New-Object System.Drawing.Point(22, 192)
$form.Controls.Add($statusLabel)

$logBox = New-Object System.Windows.Forms.TextBox
$logBox.Multiline = $true
$logBox.ReadOnly = $true
$logBox.ScrollBars = 'Vertical'
$logBox.BackColor = [System.Drawing.SystemColors]::Window
$logBox.Location = New-Object System.Drawing.Point(20, 218)
$logBox.Size = New-Object System.Drawing.Size(580, 172)
$form.Controls.Add($logBox)

function Add-LauncherLog([string] $message) {
    $timestamp = Get-Date -Format 'HH:mm:ss'
    $logBox.AppendText("[$timestamp] $message`r`n")
}

function Test-LocalPort([int] $port) {
    return $null -ne (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}

function Start-LauncherScript(
    [string] $label,
    [string] $scriptPath,
    [switch] $hidden,
    [string] $redirectLog
) {
    if (-not (Test-Path -LiteralPath $scriptPath)) {
        throw "$label script is missing: $scriptPath"
    }

    $argumentList = @(
        '-NoProfile',
        '-ExecutionPolicy',
        'Bypass',
        '-File',
        ('"{0}"' -f $scriptPath)
    )
    $parameters = @{
        FilePath = 'powershell.exe'
        WorkingDirectory = $root
        ArgumentList = $argumentList
        PassThru = $true
    }
    if ($hidden) {
        $parameters.WindowStyle = 'Hidden'
    }
    if (-not [string]::IsNullOrWhiteSpace($redirectLog)) {
        $parameters.RedirectStandardOutput = $redirectLog
        $parameters.RedirectStandardError = $redirectLog.Replace('.out.', '.err.')
    }

    $process = Start-Process @parameters
    Add-LauncherLog "$label started (PID $($process.Id))."
    return $process
}

function Ensure-ClientConfig {
    if (Test-LocalPort 8088) {
        Add-LauncherLog 'Client config is already running on 127.0.0.1:8088.'
        return
    }

    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $outputLog = Join-Path $logDirectory 'client-config.out.log'
    Start-LauncherScript -label 'Client config' -scriptPath $configScript -hidden -redirectLog $outputLog | Out-Null

    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        if (Test-LocalPort 8088) {
            Add-LauncherLog 'Client config is ready on 127.0.0.1:8088.'
            return
        }
        Start-Sleep -Milliseconds 250
    }

    throw 'Client config did not become ready on port 8088. Check build/local-launcher-v2/client-config.err.log.'
}

function Start-LocalServer {
    try {
        Ensure-ClientConfig
        if (Test-LocalPort 43594) {
            $statusLabel.Text = 'Server is already running on port 43594.'
            Add-LauncherLog 'Server is already running on 127.0.0.1:43594.'
            return
        }

        Start-LauncherScript -label 'Server' -scriptPath $serverScript | Out-Null
        $statusLabel.Text = 'Server launch requested.'
    }
    catch {
        $statusLabel.Text = 'Server launch failed.'
        Add-LauncherLog $_.Exception.Message
    }
}

function Rebuild-AndRestart-LocalServer {
    try {
        Ensure-ClientConfig
        Start-LauncherScript -label 'Server rebuild/restart' -scriptPath (Join-Path $PSScriptRoot 'Rebuild-Restart-Unforge239-Server-Dev.ps1') | Out-Null
        $statusLabel.Text = 'Server rebuild and restart requested.'
        Add-LauncherLog 'The current source and latest local data will be used after the rebuild.'
    }
    catch {
        $statusLabel.Text = 'Server rebuild/restart failed.'
        Add-LauncherLog $_.Exception.Message
    }
}

function Start-LocalClient {
    try {
        Ensure-ClientConfig
        Start-LauncherScript -label 'Client' -scriptPath $clientScript | Out-Null
        $statusLabel.Text = 'Client launch requested.'
    }
    catch {
        $statusLabel.Text = 'Client launch failed.'
        Add-LauncherLog $_.Exception.Message
    }
}

$serverButton.Add_Click({ Start-LocalServer })
$refreshServerButton.Add_Click({ Rebuild-AndRestart-LocalServer })
$clientButton.Add_Click({ Start-LocalClient })
$openFolderButton.Add_Click({ Start-Process -FilePath 'explorer.exe' -ArgumentList ('"{0}"' -f $root) })

Add-LauncherLog "Using project root: $root"
Add-LauncherLog 'Old LocalLauncher is preserved. This is the V2 copy.'
$form.Add_Shown({ $form.Activate() })
[void] $form.ShowDialog()
