$ErrorActionPreference = 'Stop'

$serverScript = Join-Path $PSScriptRoot 'start-server.ps1'
if (-not (Test-Path -LiteralPath $serverScript)) {
    throw "Server launcher not found: $serverScript"
}

$user = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$taskName = 'RestaurantSystem-LocalServer'
# Always use Windows PowerShell 5.1 (works even if this setup is run from PowerShell 7).
$powershell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$arguments = '-NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File "{0}"' -f $serverScript

$action = New-ScheduledTaskAction -Execute $powershell -Argument $arguments -WorkingDirectory $PSScriptRoot

# Start at sign-in AND re-launch every minute. If the monitor was ever stopped,
# it comes back on its own (a second copy exits immediately).
$logonTrigger = New-ScheduledTaskTrigger -AtLogOn -User $user
$repeatTrigger = New-ScheduledTaskTrigger -Once -At (Get-Date) `
    -RepetitionInterval (New-TimeSpan -Minutes 1) `
    -RepetitionDuration (New-TimeSpan -Days 3650)

$principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -MultipleInstances IgnoreNew `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -RestartCount 999 `
    -RestartInterval (New-TimeSpan -Minutes 1)

Register-ScheduledTask `
    -TaskName $taskName `
    -Action $action `
    -Trigger @($logonTrigger, $repeatTrigger) `
    -Principal $principal `
    -Settings $settings `
    -Description 'Keeps the local Restaurant System server running for the signed-in user.' `
    -Force | Out-Null

Start-ScheduledTask -TaskName $taskName
Write-Output "Registered '$taskName'. The server will start at sign-in and restart automatically if it stops."