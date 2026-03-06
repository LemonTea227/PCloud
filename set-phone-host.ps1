param(
    [ValidateSet("auto", "emulator", "custom")]
    [string]$Mode = "auto",
    [string]$CustomHost,
    [int]$Port = 22703,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $repoRoot "pcloud-helpers.ps1")
$socketFile = Join-Path $repoRoot "PCloud Client\app\src\main\java\com\example\pcloud\MySocket.java"

$targetHost = $null
switch ($Mode) {
    "emulator" { $targetHost = "10.0.2.2" }
    "custom" {
        if (-not $CustomHost) {
            throw "-CustomHost is required when -Mode custom"
        }
        $targetHost = $CustomHost
    }
    default {
        $targetHost = Get-PreferredLanIPv4
        if (-not $targetHost) {
            throw "Could not auto-detect LAN IPv4. Use -Mode custom -CustomHost <ip>."
        }
    }
}

Write-Host "Selected host: $targetHost"
Set-ClientSocketConfig -File $socketFile -SocketHost $targetHost -Port $Port -DryRun:$DryRun
