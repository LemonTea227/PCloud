# Shared helper functions for PCloud PowerShell scripts.
# Dot-source this file from run.ps1 and set-phone-host.ps1.

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
                    IP        = $ip
                    Score     = $score
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

function Set-ClientSocketConfig {
    param(
        [Parameter(Mandatory)][string]$File,
        [Parameter(Mandatory)][string]$SocketHost,
        [Parameter(Mandatory)][int]$Port,
        [switch]$DryRun
    )

    if (-not (Test-Path $File)) {
        throw "MySocket.java not found at $File"
    }

    $ip = $null
    $ipv4Parsed = [System.Net.IPAddress]::TryParse($SocketHost, [ref]$ip)
    $isValidIpv4 = $ipv4Parsed -and $ip.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork

    $isValidHostname = $false
    if (-not $isValidIpv4) {
        $isValidHostname = $SocketHost -match '^[A-Za-z0-9]([A-Za-z0-9\-]{0,61}[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9\-]{0,61}[A-Za-z0-9])?)*$'
    }
    if (-not ($isValidIpv4 -or $isValidHostname)) {
        throw "Invalid host value '$SocketHost'. Must be a valid IPv4 address or hostname."
    }

    $raw = Get-Content -Raw -Path $File
    $updated = $raw
    $updated = [regex]::Replace($updated, 'private\s+static\s+String\s+INERIP\s*=\s*"[^"]*"\s*;\s*(//[^\r\n]*)?', "private static String INERIP = `"$SocketHost`";")
    $updated = [regex]::Replace($updated, 'private\s+static\s+String\s+IP\s*=\s*"[^"]*"\s*;\s*(//[^\r\n]*)?', "private static String IP = `"$SocketHost`"; // configured by pcloud scripts")
    $updated = [regex]::Replace($updated, 'private\s+static\s+int\s+Port\s*=\s*\d+\s*;', "private static int Port = $Port;")

    if ($updated -eq $raw) {
        Write-Host "No socket config changes were needed."
        return
    }

    if ($DryRun) {
        Write-Host "Dry-run: socket config would be updated to host=$SocketHost port=$Port"
        return
    }

    Set-Content -Path $File -Value $updated -Encoding utf8
    Write-Host "Updated MySocket.java with host=$SocketHost port=$Port"

    $verify = Get-Content -Raw -Path $File
    $ineripMatch = [regex]::Match($verify, 'private\s+static\s+String\s+INERIP\s*=\s*"([^"]+)"')
    $ipMatch = [regex]::Match($verify, 'private\s+static\s+String\s+IP\s*=\s*"([^"]+)"')
    $portMatch = [regex]::Match($verify, 'private\s+static\s+int\s+Port\s*=\s*(\d+)')
    if ($ineripMatch.Success -and $ipMatch.Success -and $portMatch.Success) {
        Write-Host "Applied values => INERIP=$($ineripMatch.Groups[1].Value), IP=$($ipMatch.Groups[1].Value), Port=$($portMatch.Groups[1].Value)"
    }
}
