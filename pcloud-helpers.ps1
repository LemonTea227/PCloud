# Shared helper functions for PCloud PowerShell scripts.
# Dot-source this file from run.ps1, set-phone-host.ps1, quality.ps1, and e2e.ps1.

function Test-Python310OrNewer([string]$pythonExe) {
    try {
        $versionStr = (& $pythonExe -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')" 2>$null | Select-Object -Last 1).Trim()
        if ($versionStr -match '^(\d+)\.(\d+)') {
            $major = [int]$matches[1]
            $minor = [int]$matches[2]
            return ($major -gt 3) -or ($major -eq 3 -and $minor -ge 10)
        }
    } catch {}
    return $false
}

function Get-Python3Executable {
    param([string]$RepoRoot = $PSScriptRoot)

    $venvPython = Join-Path $RepoRoot ".venv/Scripts/python.exe"
    if ((Test-Path $venvPython) -and (Test-Python310OrNewer $venvPython)) {
        return $venvPython
    }

    $pyCommand = Get-Command py -ErrorAction SilentlyContinue
    if ($pyCommand) {
        try {
            $candidate = (& $pyCommand.Source -3 -c "import sys;print(sys.executable)" 2>$null | Select-Object -Last 1)
            if ($candidate) {
                $candidate = $candidate.Trim()
                if ($candidate -and (Test-Path $candidate) -and (Test-Python310OrNewer $candidate)) {
                    return $candidate
                }
            }
        } catch {
        }
    }

    $fallbacks = @(
        "C:\Python311\python.exe",
        "C:\Python310\python.exe"
    )
    foreach ($path in $fallbacks) {
        if ((Test-Path $path) -and (Test-Python310OrNewer $path)) {
            return $path
        }
    }

    return $null
}

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
        [string]$InnerIp,
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
    # Separate INERIP and IP if they appear concatenated on the same line, capturing and reusing the leading indentation.
    # The INERIP capture group includes any optional trailing comment so it is preserved when the declarations are split.
    # Use [^\S\r\n]* for the indent capture so \s does not accidentally span newlines in .NET regex.
    $updated = [regex]::Replace($updated, '(?m)^([^\S\r\n]*)(private\s+static\s+String\s+INERIP\s*=\s*"[^"]*"\s*;[^\S\r\n]*(//[^\r\n]*)?)[^\S\r\n]*(private\s+static\s+String\s+IP)', "`$1`$2`n`$1`$4")
    # Replace INERIP only when an explicit InnerIp value is provided; otherwise the existing fallback is preserved as-is.
    if ($InnerIp) {
        $ip = $null
        $innerIpParsed = [System.Net.IPAddress]::TryParse($InnerIp, [ref]$ip)
        $isValidInner = ($innerIpParsed -and $ip.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork) -or
            ($InnerIp -match '^[A-Za-z0-9]([A-Za-z0-9\-]{0,61}[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9\-]{0,61}[A-Za-z0-9])?)*$')
        if (-not $isValidInner) {
            throw "Invalid InnerIp value '$InnerIp'. Must be a valid IPv4 address or hostname."
        }
        $updated = [regex]::Replace($updated, '(?m)^([^\S\r\n]*)private\s+static\s+String\s+INERIP\s*=\s*"[^"]*"\s*;([^\S\r\n]*//[^\r\n]*)?', "`$1private static String INERIP = `"$InnerIp`";`$2")
    }
    # Replace IP, anchoring to line start to preserve leading indentation and any existing trailing comment
    $updated = [regex]::Replace($updated, '(?m)^([^\S\r\n]*)private\s+static\s+String\s+IP\s*=\s*"[^"]*"\s*;([^\S\r\n]*//[^\r\n]*)?', "`$1private static String IP = `"$SocketHost`";`$2")
    # Replace Port, anchoring to line start to preserve leading indentation
    $updated = [regex]::Replace($updated, '(?m)^([^\S\r\n]*)private\s+static\s+int\s+Port\s*=\s*\d+\s*;', "`$1private static int Port = $Port;")

    if ($updated -eq $raw) {
        Write-Host "No socket config changes were needed."
        return
    }

    if ($DryRun) {
        $innerMsg = if ($InnerIp) { " innerIp=$InnerIp" } else { "" }
        Write-Host "Dry-run: socket config would be updated to host=$SocketHost$innerMsg port=$Port"
        return
    }

    [System.IO.File]::WriteAllText($File, $updated, (New-Object System.Text.UTF8Encoding $false))
    $innerMsg = if ($InnerIp) { " innerIp=$InnerIp" } else { "" }
    Write-Host "Updated MySocket.java with host=$SocketHost$innerMsg port=$Port"

    $verify = Get-Content -Raw -Path $File
    $ineripMatch = [regex]::Match($verify, 'private\s+static\s+String\s+INERIP\s*=\s*"([^"]+)"')
    $ipMatch = [regex]::Match($verify, 'private\s+static\s+String\s+IP\s*=\s*"([^"]+)"')
    $portMatch = [regex]::Match($verify, 'private\s+static\s+int\s+Port\s*=\s*(\d+)')
    if ($ineripMatch.Success -and $ipMatch.Success -and $portMatch.Success) {
        Write-Host "Applied values => INERIP=$($ineripMatch.Groups[1].Value), IP=$($ipMatch.Groups[1].Value), Port=$($portMatch.Groups[1].Value)"
    }
}

function Initialize-JavaHome {
    param([string]$GradlePropertiesFile = "")
    $projectJavaHome = Get-ProjectJavaHome -GradlePropertiesFile $GradlePropertiesFile
    if ($projectJavaHome) {
        $env:JAVA_HOME = $projectJavaHome
        $javaBinPath = "$projectJavaHome\bin"
        if (($env:Path -split ';') -notcontains $javaBinPath) {
            $env:Path = "$javaBinPath;" + $env:Path
        }
    }
}

function Get-ProjectJavaHome {
    param([string]$GradlePropertiesFile = "")

    if ($env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) {
        return $env:JAVA_HOME
    }

    if ($GradlePropertiesFile -and (Test-Path $GradlePropertiesFile)) {
        $javaHomeLine = (Get-Content $GradlePropertiesFile | Where-Object { $_ -match '^org\.gradle\.java\.home=' } | Select-Object -First 1)
        if ($javaHomeLine) {
            $javaHome = $javaHomeLine.Substring('org.gradle.java.home='.Length)
            if (Test-Path $javaHome) {
                return $javaHome
            }
        }
    }

    return $null
}

function Test-Java11OrNewer {
    $javaOutput = cmd /c "java -version 2>&1"
    $versionLine = $javaOutput | Select-Object -First 1

    if ($versionLine -match 'version "(\d+)') {
        $major = [int]$matches[1]
        if ($major -eq 1 -and $versionLine -match 'version "1\.(\d+)') {
            return ([int]$matches[1] -ge 11)
        }
        return ($major -ge 11)
    }

    return $false
}

function Test-AndroidSdk {
    param([string]$ClientPath)

    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
        return $true
    }

    $localPropertiesFile = Join-Path $ClientPath "local.properties"
    if (-not (Test-Path $localPropertiesFile)) {
        return $false
    }

    $sdkLine = (Get-Content $localPropertiesFile | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1)
    if (-not $sdkLine) {
        return $false
    }

    $sdkPath = $sdkLine.Substring(8)
    $sdkPath = $sdkPath -replace '\\\\', '\\'
    $sdkPath = $sdkPath -replace '\\:', ':'
    return (Test-Path $sdkPath)
}

function Get-AdbPath {
    $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCmd) {
        return $adbCmd.Source
    }

    $sdkCandidates = @()
    if ($env:ANDROID_SDK_ROOT) {
        $sdkCandidates += $env:ANDROID_SDK_ROOT
    }
    $sdkCandidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk")

    foreach ($sdk in $sdkCandidates) {
        if (-not $sdk) { continue }
        $adbPath = Join-Path $sdk "platform-tools\adb.exe"
        if (Test-Path $adbPath) {
            return $adbPath
        }
    }

    return $null
}
