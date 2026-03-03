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

Write-Host "[1/4] Validating prerequisites..."
Ensure-Command py
Ensure-Command java

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
        if (-not $SkipInstall) {
            if (Get-Command adb -ErrorAction SilentlyContinue) {
                $adbDevices = adb devices | Select-String -Pattern "\tdevice$"
                if ($adbDevices) {
                    .\gradlew.bat installDebug
                } else {
                    .\gradlew.bat assembleDebug
                    Write-Host "No connected device/emulator detected. Built debug APK instead of installing."
                }
            } else {
                .\gradlew.bat assembleDebug
                Write-Host "adb not found; built debug APK instead of installing."
            }
        }
        if (Get-Command adb -ErrorAction SilentlyContinue) {
            $adbDevices = adb devices | Select-String -Pattern "\tdevice$"
            if ($adbDevices) {
                adb shell am start -n com.example.pcloud/.SplashActivity | Out-Null
                Write-Host "Client launched via adb."
            } else {
                Write-Host "No connected device/emulator; launch APK manually after connecting a device."
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
