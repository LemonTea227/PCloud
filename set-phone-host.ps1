param(
    [ValidateSet("auto", "emulator", "custom")]
    [string]$Mode = "auto",
    [string]$CustomHost,
    [int]$Port = 22703,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$socketFile = Join-Path $repoRoot "PCloud Client\app\src\main\java\com\example\pcloud\MySocket.java"

function Get-PreferredLanIPv4 {
    $candidates = @()

    try {
        $configs = Get-NetIPConfiguration -ErrorAction Stop | Where-Object {
            $_.NetAdapter -and $_.NetAdapter.Status -eq "Up" -and $_.IPv4Address
        }

        foreach ($cfg in $configs) {
            foreach ($ipObj in $cfg.IPv4Address) {
                $ip = $ipObj.IPAddress
                if (-not $ip) { continue }
                if ($ip -like "127.*" -or $ip -like "169.254.*" -or $ip -eq "0.0.0.0") { continue }

                $score = 0
                if ($cfg.IPv4DefaultGateway) { $score += 10 }
                if ($cfg.NetAdapter.InterfaceDescription -match "Wi-?Fi|Wireless") { $score += 4 }
                if ($cfg.NetAdapter.InterfaceDescription -match "Ethernet") { $score += 3 }
                if ($cfg.NetAdapter.InterfaceDescription -match "Hyper-V|Virtual|VMware|TAP|Loopback") { $score -= 20 }

                $isPrivate = $ip -like "10.*" -or $ip -like "192.168.*" -or ($ip -match "^172\.(1[6-9]|2[0-9]|3[0-1])\.")
                if ($isPrivate) { $score += 5 }

                $candidates += [PSCustomObject]@{
                    IP = $ip
                    Score = $score
                    Interface = $cfg.NetAdapter.InterfaceDescription
                }
            }
        }
    } catch {
        $fallback = [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName()) |
            Where-Object {
                $_.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork -and
                $_.IPAddressToString -notlike "127.*" -and
                $_.IPAddressToString -notlike "169.254.*"
            } |
            Select-Object -ExpandProperty IPAddressToString
        if ($fallback) {
            return $fallback[0]
        }
        return $null
    }

    if (-not $candidates -or $candidates.Count -eq 0) {
        return $null
    }

    $best = $candidates | Sort-Object -Property Score -Descending | Select-Object -First 1
    return $best.IP
}

function Set-ClientSocketConfig([string]$file, [string]$socketHost, [int]$port) {
    if (-not (Test-Path $file)) {
        throw "MySocket.java not found at $file"
    }

    $raw = Get-Content -Raw -Path $file
    $updated = $raw
    $updated = [regex]::Replace($updated, 'private\s+static\s+String\s+INERIP\s*=\s*"[^"]*"\s*;\s*(//[^\r\n]*)?', "private static String INERIP = `"$socketHost`";")
    $updated = [regex]::Replace($updated, 'private\s+static\s+String\s+IP\s*=\s*"[^"]*"\s*;\s*(//[^\r\n]*)?', "private static String IP = `"$socketHost`"; // configured by set-phone-host.ps1")
    $updated = [regex]::Replace($updated, 'private\s+static\s+int\s+Port\s*=\s*\d+\s*;', "private static int Port = $port;")

    if ($updated -eq $raw) {
        Write-Host "No socket config changes were needed."
        return
    }

    if ($DryRun) {
        Write-Host "Dry-run: socket config would be updated to host=$socketHost port=$port"
        return
    }

    Set-Content -Path $file -Value $updated -NoNewline
    Write-Host "Updated MySocket.java with host=$socketHost port=$port"

    $verify = Get-Content -Raw -Path $file
    $ineripMatch = [regex]::Match($verify, 'private\s+static\s+String\s+INERIP\s*=\s*"([^"]+)"')
    $ipMatch = [regex]::Match($verify, 'private\s+static\s+String\s+IP\s*=\s*"([^"]+)"')
    $portMatch = [regex]::Match($verify, 'private\s+static\s+int\s+Port\s*=\s*(\d+)')
    if ($ineripMatch.Success -and $ipMatch.Success -and $portMatch.Success) {
        Write-Host "Applied values => INERIP=$($ineripMatch.Groups[1].Value), IP=$($ipMatch.Groups[1].Value), Port=$($portMatch.Groups[1].Value)"
    }
}

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
Set-ClientSocketConfig -file $socketFile -socketHost $targetHost -port $Port
