param(
    [switch]$SkipClient,
    [switch]$SkipInstall,
    [string]$ServerHost = "10.0.2.2",
    [int]$ServerPort = 22703
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverDir = Join-Path $repoRoot "PCloud Server"
$clientDir = Join-Path $repoRoot "PCloud Client"
$socketFile = Join-Path $clientDir "app/src/main/java/com/example/pcloud/MySocket.java"
$gradlePropertiesFile = Join-Path $clientDir "gradle.properties"

function Ensure-Command([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Required command '$name' was not found in PATH."
    }
}

function Get-ProjectJavaHome {
    if ($env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) {
        return $env:JAVA_HOME
    }

    if (Test-Path $gradlePropertiesFile) {
        $javaHomeLine = (Get-Content $gradlePropertiesFile | Where-Object { $_ -match '^org\.gradle\.java\.home=' } | Select-Object -First 1)
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

$projectJavaHome = Get-ProjectJavaHome
if ($projectJavaHome) {
    $env:JAVA_HOME = $projectJavaHome
    if ($env:Path -notlike "$projectJavaHome\\bin*") {
        $env:Path = "$projectJavaHome\\bin;" + $env:Path
    }
}

function Set-ClientSocketConfig([string]$file, [string]$socketHost, [int]$socketPort) {
    $raw = Get-Content -Raw -Path $file
    $updated = $raw -replace 'private static String INERIP = ".*?";', "private static String INERIP = `"$socketHost`";"
    $updated = $updated -replace 'private static String IP = ".*?";\s*//.*', "private static String IP = `"$socketHost`"; // configured by run.ps1"
    $updated = $updated -replace 'private static int Port = \d+;', "private static int Port = $socketPort;"
    if ($updated -ne $raw) {
        Set-Content -Path $file -Value $updated -NoNewline
    }
}

function Test-AndroidSdk([string]$clientPath) {
    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
        return $true
    }

    $localPropertiesFile = Join-Path $clientPath "local.properties"
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

function Wait-ForAndroidBoot([string]$adbPath, [int]$timeoutSeconds = 120) {
    if (-not $adbPath) {
        return $false
    }

    try {
        & $adbPath wait-for-device | Out-Null
    } catch {
        Start-Sleep -Seconds 2
    }
    $attempts = [Math]::Ceiling($timeoutSeconds / 2)
    for ($i = 0; $i -lt $attempts; $i++) {
        try {
            $boot = (& $adbPath shell getprop sys.boot_completed 2>$null).Trim()
        } catch {
            Start-Sleep -Seconds 2
            continue
        }
        if ($boot -eq "1") {
            return $true
        }
        Start-Sleep -Seconds 2
    }

    return $false
}

function Get-EmulatorPath {
    $emuCmd = Get-Command emulator -ErrorAction SilentlyContinue
    if ($emuCmd) {
        return $emuCmd.Source
    }

    $sdkCandidates = @()
    if ($env:ANDROID_SDK_ROOT) {
        $sdkCandidates += $env:ANDROID_SDK_ROOT
    }
    $sdkCandidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk")

    foreach ($sdk in $sdkCandidates) {
        if (-not $sdk) { continue }
        $emuPath = Join-Path $sdk "emulator\emulator.exe"
        if (Test-Path $emuPath) {
            return $emuPath
        }
    }

    return $null
}

function Get-ConnectedDeviceCount([string]$adbPath) {
    if (-not $adbPath) { return 0 }
    $lines = & $adbPath devices
    return @($lines | Select-String -Pattern "\tdevice$").Count
}

function Ensure-ClientDevice([string]$adbPath) {
    if (-not $adbPath) {
        return $false
    }

    if ((Get-ConnectedDeviceCount -adbPath $adbPath) -gt 0) {
        return $true
    }

    $emulatorPath = Get-EmulatorPath
    if (-not $emulatorPath) {
        return $false
    }

    $avds = & $emulatorPath -list-avds 2>$null
    if (-not $avds -or $avds.Count -eq 0) {
        return $false
    }

    $preferredPatterns = @(
        "Pixel_8_Pro",
        "Pixel_8",
        "Pixel_7_Pro",
        "Pixel_7",
        "Pixel_6_Pro",
        "Pixel_6",
        "Pixel_5",
        "Pixel_4_XL"
    )
    $selectedAvd = $null
    foreach ($pattern in $preferredPatterns) {
        $selectedAvd = $avds | Where-Object { $_ -like "*$pattern*" } | Select-Object -First 1
        if ($selectedAvd) {
            break
        }
    }
    if (-not $selectedAvd) {
        $selectedAvd = $avds | Select-Object -First 1
    }
    Write-Host "No connected phone detected. Starting emulator '$selectedAvd'..."

    $emuDir = Split-Path -Parent $emulatorPath
    Push-Location $emuDir
    try {
        Start-Process -FilePath $emulatorPath -ArgumentList "-avd", $selectedAvd | Out-Null
    } finally {
        Pop-Location
    }

    & $adbPath start-server | Out-Null
    return (Wait-ForAndroidBoot -adbPath $adbPath -timeoutSeconds 240)
}

Write-Host "[1/4] Validating prerequisites..."
Ensure-Command py
Ensure-Command java
$adbPath = Get-AdbPath

Write-Host "[2/4] Starting server..."
$serverProcess = Start-Process -FilePath "py" -ArgumentList "-2", "pcloud_server.py" -WorkingDirectory $serverDir -PassThru
$keepServerRunning = $false
Start-Sleep -Seconds 2
if ($serverProcess.HasExited) {
    throw "Server process exited immediately. Check 'PCloud Server' dependencies and Python 2 availability."
}
Write-Host "Server PID: $($serverProcess.Id)"

try {
    if ($SkipClient) {
        Write-Host "Client step skipped. Server is running."
        $keepServerRunning = $true
        return
    }

    Write-Host "[3/4] Updating client socket target (host=$ServerHost, port=$ServerPort)..."
    Set-ClientSocketConfig -file $socketFile -socketHost $ServerHost -socketPort $ServerPort

    Write-Host "[4/4] Building/installing Android client..."
    if (-not (Test-Java11OrNewer)) {
        throw "Android build requires Java 11+. Please set JAVA_HOME to a JDK 11+ installation."
    }

    if (-not (Test-AndroidSdk -clientPath $clientDir)) {
        throw "Android SDK path is not configured. Set ANDROID_SDK_ROOT or fix sdk.dir in 'PCloud Client/local.properties'."
    }

    Push-Location $clientDir
    try {
        $deviceReady = Ensure-ClientDevice -adbPath $adbPath

        if (-not $SkipInstall) {
            if ($adbPath) {
                if ($deviceReady) {
                    if (Wait-ForAndroidBoot -adbPath $adbPath) {
                        .\gradlew.bat installDebug
                    } else {
                        .\gradlew.bat assembleDebug
                        Write-Host "Device detected but not fully booted. Built debug APK instead of installing."
                    }
                } else {
                    .\gradlew.bat assembleDebug
                    Write-Host "No connected phone/emulator available. Built debug APK instead of installing."
                }
            } else {
                .\gradlew.bat assembleDebug
                Write-Host "adb not found; built debug APK instead of installing."
            }
        }
        if ($adbPath) {
            if ($deviceReady -or (Get-ConnectedDeviceCount -adbPath $adbPath) -gt 0) {
                if (Wait-ForAndroidBoot -adbPath $adbPath) {
                    & $adbPath shell am start -n com.example.pcloud/.SplashActivity | Out-Null
                    if ($LASTEXITCODE -eq 0) {
                        Write-Host "Client launched via adb."
                    } else {
                        Write-Host "Launch command failed. Open the app manually on the emulator."
                    }
                } else {
                    Write-Host "Device not fully booted; launch the app manually when boot completes."
                }
            } else {
                Write-Host "No connected phone/emulator; launch APK manually after connecting a device."
            }
        } else {
            Write-Host "adb not found; launch the app manually after installing APK on a device/emulator."
        }
    } finally {
        Pop-Location
    }

    $keepServerRunning = $true
    Write-Host "Done. Server running in background (PID $($serverProcess.Id)). Use Stop-Process -Id $($serverProcess.Id) to stop it."
} finally {
    if (-not $keepServerRunning -and $serverProcess -and -not $serverProcess.HasExited) {
        Stop-Process -Id $serverProcess.Id
        Write-Host "Stopped server PID $($serverProcess.Id) after setup failure."
    }
}
