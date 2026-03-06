param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs,
    [int]$DeviceWaitSeconds = 180,
    [switch]$IncludeRealServer
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $repoRoot "pcloud-helpers.ps1")
$clientDir = Join-Path $repoRoot "PCloud Client"
$serverDir = Join-Path $repoRoot "PCloud Server"
$cleanupScript = Join-Path $serverDir "cleanup_real_server_test_data.py"
$reportPath = "PCloud Client/app/build/reports/androidTests/connected/index.html"
$adb = Get-AdbPath
$gradleExitCode = 1

function Get-Python3Executable {
    $venvPython = Join-Path $repoRoot ".venv/Scripts/python.exe"
    if (Test-Path $venvPython) {
        return $venvPython
    }

    $pyCommand = Get-Command py -ErrorAction SilentlyContinue
    if ($pyCommand) {
        try {
            $candidate = (& $pyCommand.Source -3 -c "import sys;print(sys.executable)" 2>$null | Select-Object -Last 1)
            if ($candidate) {
                $candidate = $candidate.Trim()
                if ($candidate -and (Test-Path $candidate)) {
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
        if (Test-Path $path) {
            return $path
        }
    }

    return $null
}

function Invoke-RealServerCleanup {
    if (-not (Test-Path $cleanupScript)) {
        Write-Host "Warning: cleanup script not found at $cleanupScript"
        return
    }

    $pythonExe = Get-Python3Executable
    if (-not $pythonExe) {
        Write-Host "Warning: Python 3 not found; skipping real-server test data cleanup."
        return
    }

    try {
        Push-Location $serverDir
        & $pythonExe $cleanupScript | Out-Host
    } catch {
        Write-Host "Warning: real-server cleanup failed: $($_.Exception.Message)"
    } finally {
        Pop-Location
    }
}

function Get-AdbGlobalSetting([string]$adbPath, [string]$setting) {
    try {
        $value = (& $adbPath shell settings get global $setting 2>$null | Select-Object -Last 1)
        if ($null -ne $value) {
            $value = $value.Trim()
        }
        if ($value -eq "null" -or [string]::IsNullOrEmpty($value)) { return $null }
        return $value
    } catch {
        return $null
    }
}

function Disable-AdbPackageVerification([string]$adbPath) {
    if (-not $adbPath) {
        return $null
    }
    $saved = @{
        verifier_verify_adb_installs = Get-AdbGlobalSetting -adbPath $adbPath -setting "verifier_verify_adb_installs"
        package_verifier_enable      = Get-AdbGlobalSetting -adbPath $adbPath -setting "package_verifier_enable"
    }
    try {
        & $adbPath shell settings put global verifier_verify_adb_installs 0 | Out-Null
        & $adbPath shell settings put global package_verifier_enable 0 | Out-Null
    } catch {
        Write-Host "Warning: could not disable package verifier on device."
    }
    return $saved
}

function Restore-AdbPackageVerification([string]$adbPath, [hashtable]$saved) {
    if (-not $adbPath -or -not $saved) { return }
    try {
        foreach ($key in $saved.Keys) {
            $value = $saved[$key]
            if ($null -eq $value) {
                & $adbPath shell settings delete global $key | Out-Null
            } else {
                & $adbPath shell settings put global $key $value | Out-Null
            }
        }
        Write-Host "Restored package verifier settings on device."
    } catch {
        Write-Host "Warning: could not restore package verifier settings on device."
    }
}

Write-Host "Running Android E2E tests..."

if ($IncludeRealServer) {
    Write-Host "Pre-run cleanup of real-server e2e test data..."
    Invoke-RealServerCleanup
}

$savedVerifierSettings = $null
if ($adb -and (Test-Path $adb)) {
    try {
        & $adb start-server | Out-Null
    } catch {
    }

    $hasDevice = $false
    $attempts = [Math]::Ceiling($DeviceWaitSeconds / 2)
    for ($i = 0; $i -lt $attempts; $i++) {
        try {
            $deviceLines = & $adb devices
            if (@($deviceLines | Select-String -Pattern "\tdevice$").Count -gt 0) {
                $hasDevice = $true
                break
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    }

    if (-not $hasDevice) {
        Write-Host "E2E failed: no connected emulator/device detected within $DeviceWaitSeconds seconds."
        exit 1
    }

    try {
        $savedVerifierSettings = Disable-AdbPackageVerification -adbPath $adb
        & $adb shell settings put global window_animation_scale 0 | Out-Null
        & $adb shell settings put global transition_animation_scale 0 | Out-Null
        & $adb shell settings put global animator_duration_scale 0 | Out-Null
        Write-Host "Device animations disabled for stable Espresso runs."
    } catch {
        Write-Host "Warning: could not set animation scales via adb."
    }
} else {
    throw "adb not found. Install Android SDK Platform-Tools and ensure it is on PATH or ANDROID_SDK_ROOT is set."
}

Push-Location $clientDir
try {
    & ".\gradlew.bat" :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle assemble tasks (:app:assembleDebug, :app:assembleDebugAndroidTest) failed (exit code $LASTEXITCODE)."
    }
    if ($adb -and (Test-Path $adb)) {
        & $adb install -r "app/build/outputs/apk/debug/app-debug.apk" | Out-Host
        & $adb install -r "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" | Out-Host
    }

    $effectiveGradleArgs = @()
    if ($GradleArgs) {
        $effectiveGradleArgs += $GradleArgs
    }
    if (-not $IncludeRealServer) {
        # Run only ClientE2ETest (excludes RealServerE2ETest) to avoid real-server dependency
        $effectiveGradleArgs += "-Pandroid.testInstrumentationRunnerArguments.class=com.example.pcloud.ClientE2ETest"
    }

    & ".\gradlew.bat" connectedDebugAndroidTest --no-daemon @effectiveGradleArgs
    $gradleExitCode = $LASTEXITCODE
} catch {
    $gradleExitCode = 1
    Write-Host "E2E execution threw an error: $($_.Exception.Message)"
}
finally {
    Pop-Location
    if ($savedVerifierSettings) {
        Restore-AdbPackageVerification -adbPath $adb -saved $savedVerifierSettings
    }
}

if ($gradleExitCode -eq 0) {
    Write-Host "E2E passed. Report: $reportPath"
} else {
    Write-Host "E2E failed. Report: $reportPath"
}

if ($IncludeRealServer) {
    Write-Host "Post-run cleanup of real-server e2e test data..."
    Invoke-RealServerCleanup
}

exit $gradleExitCode
