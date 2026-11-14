$ErrorActionPreference = 'Stop'

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
    } catch [System.Net.WebException] {
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
    throw 'Maven was not found. Install Maven 3.6.3 or newer.'
}

Set-Location -LiteralPath $PSScriptRoot
Write-ServerLog "Monitoring the restaurant server with $mavenCommand."

while ($true) {
    if (Test-RestaurantServer) {
        Start-Sleep -Seconds 10
        continue
    }

    Write-ServerLog 'Server is not responding. Starting it.'
    & $mavenCommand compile exec:java >> $logFile 2>&1
    $exitCode = $LASTEXITCODE
    Write-ServerLog "Maven exited with code $exitCode. Retrying in 15 seconds."
    Start-Sleep -Seconds 15
}
