$ErrorActionPreference = 'Continue'

# Only one monitor may run at a time (the scheduled task launches this script repeatedly).
$createdNew = $false
$mutex = New-Object System.Threading.Mutex($true, 'Global\RestaurantSystemMonitor', [ref]$createdNew)
if (-not $createdNew) { exit 0 }

$logDirectory = Join-Path $env:LOCALAPPDATA 'RestaurantSystem'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$logFile = Join-Path $logDirectory 'server.log'

function Write-ServerLog {
    param([string]$Message)
    Add-Content -LiteralPath $logFile -Value ("[{0}] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message)
}

function Test-RestaurantServer {
    try {
        $response = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/health' -TimeoutSec 3
        return $response.status -eq 'ok'
    } catch {
        # Any failure (refused connection, timeout, bad response) means "not running".
        return $false
    }
}

$mavenCandidates = @()
if ($env:MAVEN_HOME) {
    $mavenCandidates += Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'
}
if ($env:LOCALAPPDATA) {
    $mavenCandidates += Join-Path $env:LOCALAPPDATA 'Programs\Apache\Maven\apache-maven-3.10.0\bin\mvn.cmd'
}

$mavenCommand = $mavenCandidates |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1

if (-not $mavenCommand) {
    $mavenOnPath = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($mavenOnPath) {
        $mavenCommand = $mavenOnPath.Source
    }
}

if (-not $mavenCommand) {
    Write-ServerLog 'ERROR: Maven was not found. Install Maven 3.6.3 or newer.'
    exit 1
}

Set-Location -LiteralPath $PSScriptRoot
Write-ServerLog "Monitoring the restaurant server with $mavenCommand."

while ($true) {
    if (Test-RestaurantServer) {
        Start-Sleep -Seconds 5
        continue
    }

    Write-ServerLog 'Server is not responding. Starting it.'
    # Run through cmd.exe so Maven's stderr output cannot be turned into a
    # PowerShell error that kills this monitor.
    cmd.exe /c "`"$mavenCommand`" -q compile exec:java >> `"$logFile`" 2>&1"
    Write-ServerLog "Maven exited with code $LASTEXITCODE. Retrying in 5 seconds."
    Start-Sleep -Seconds 5
}