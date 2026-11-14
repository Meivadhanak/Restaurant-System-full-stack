$ErrorActionPreference = 'Stop'

$serverScript = Join-Path $PSScriptRoot 'start-server.ps1'
if (-not (Test-Path -LiteralPath $serverScript)) {
    throw "Server launcher not found: $serverScript"
}

$user = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$taskName = 'RestaurantSystem-LocalServer'
$powershell = Join-Path $PSHOME 'powershell.exe'
$arguments = '-NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File "{0}"' -f $serverScript

$action = New-ScheduledTaskAction -Execute $powershell -Argument $arguments -WorkingDirectory $PSScriptRoot
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $user
$principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -MultipleInstances IgnoreNew `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1)

Register-ScheduledTask `
    -TaskName $taskName `
    -Action $action `
    -Trigger $trigger `
    -Principal $principal `
    -Settings $settings `
    -Description 'Keeps the local Restaurant System server running for the signed-in user.' `
    -Force | Out-Null

Start-ScheduledTask -TaskName $taskName
Write-Output "Registered '$taskName' to monitor and start the restaurant server at sign-in."
