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

function Invoke-RealServerCleanup {
    if (-not (Test-Path $cleanupScript)) {
        Write-Host "Warning: cleanup script not found at $cleanupScript"
        return
    }

    $pythonExe = Get-Python3Executable -RepoRoot $repoRoot
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

# Sentinel returned by Get-AdbGlobalSetting when the adb read fails, distinct from $null (confirmed absent).
$script:AdbReadError = [PSCustomObject]@{ __sentinel__ = 'read_error' }

function Get-AdbGlobalSetting([string]$adbPath, [string]$setting) {
    try {
        $value = (& $adbPath shell settings get global $setting 2>$null | Select-Object -Last 1)
        if ($LASTEXITCODE -ne 0) { return $script:AdbReadError }
        if ($null -ne $value) {
            $value = $value.Trim()
        }
        if ($value -eq "null" -or [string]::IsNullOrEmpty($value)) { return $null }
        return $value
    } catch {
        return $script:AdbReadError
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
    $allSet = $true
    & $adbPath shell settings put global verifier_verify_adb_installs 0 | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host "Warning: could not set verifier_verify_adb_installs to 0."; $allSet = $false }
    & $adbPath shell settings put global package_verifier_enable 0 | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host "Warning: could not set package_verifier_enable to 0."; $allSet = $false }
    if ($allSet) {
        Write-Host "Package verifier disabled on device."
    }
    return $saved
}

function Restore-AdbPackageVerification([string]$adbPath, [hashtable]$saved) {
    if (-not $adbPath -or -not $saved) { return }
    $failures = 0
    foreach ($key in $saved.Keys) {
        $value = $saved[$key]
        if ($value -is [PSCustomObject] -and $value.__sentinel__ -eq 'read_error') {
            Write-Host "Warning: original value of '$key' could not be read; skipping restore."
            $failures++
        } elseif ($null -eq $value) {
            & $adbPath shell settings delete global $key | Out-Null
            if ($LASTEXITCODE -ne 0) { Write-Host "Warning: could not delete '$key' setting."; $failures++ }
        } else {
            & $adbPath shell settings put global $key $value | Out-Null
            if ($LASTEXITCODE -ne 0) { Write-Host "Warning: could not restore '$key' to '$value'."; $failures++ }
        }
    }
    if ($failures -eq 0) {
        Write-Host "Restored package verifier settings on device."
    } else {
        Write-Host "Warning: $failures package verifier setting(s) could not be fully restored on device."
    }
}

function Disable-AdbAnimationScales([string]$adbPath) {
    if (-not $adbPath) { return $null }
    $saved = @{
        window_animation_scale     = Get-AdbGlobalSetting -adbPath $adbPath -setting "window_animation_scale"
        transition_animation_scale = Get-AdbGlobalSetting -adbPath $adbPath -setting "transition_animation_scale"
        animator_duration_scale    = Get-AdbGlobalSetting -adbPath $adbPath -setting "animator_duration_scale"
    }
    $allSet = $true
    & $adbPath shell settings put global window_animation_scale 0 | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host "Warning: could not set window_animation_scale to 0."; $allSet = $false }
    & $adbPath shell settings put global transition_animation_scale 0 | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host "Warning: could not set transition_animation_scale to 0."; $allSet = $false }
    & $adbPath shell settings put global animator_duration_scale 0 | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host "Warning: could not set animator_duration_scale to 0."; $allSet = $false }
    if ($allSet) {
        Write-Host "Device animations disabled for stable Espresso runs."
    }
    return $saved
}

function Restore-AdbAnimationScales([string]$adbPath, [hashtable]$saved) {
    if (-not $adbPath -or -not $saved) { return }
    $failures = 0
    foreach ($key in $saved.Keys) {
        $value = $saved[$key]
        if ($value -is [PSCustomObject] -and $value.__sentinel__ -eq 'read_error') {
            Write-Host "Warning: original value of '$key' could not be read; skipping restore."
            $failures++
        } elseif ($null -eq $value) {
            & $adbPath shell settings delete global $key | Out-Null
            if ($LASTEXITCODE -ne 0) { Write-Host "Warning: could not delete '$key' setting."; $failures++ }
        } else {
            & $adbPath shell settings put global $key $value | Out-Null
            if ($LASTEXITCODE -ne 0) { Write-Host "Warning: could not restore '$key' to '$value'."; $failures++ }
        }
    }
    if ($failures -eq 0) {
        Write-Host "Restored animation scale settings on device."
    } else {
        Write-Host "Warning: $failures animation scale setting(s) could not be fully restored on device."
    }
}

Write-Host "Running Android E2E tests..."

if ($IncludeRealServer) {
    Write-Host "Pre-run cleanup of real-server e2e test data..."
    Invoke-RealServerCleanup
}

$savedVerifierSettings = $null
$savedAnimationScales = $null
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
        $isEmulator = $false
        try {
            $qemu = (& $adb shell getprop ro.kernel.qemu 2>$null | Out-String).Trim()
            $isEmulator = ($qemu -eq "1")
        } catch {}

        if ($isEmulator) {
            $savedVerifierSettings = Disable-AdbPackageVerification -adbPath $adb
        } else {
            Write-Host "Physical device detected. Skipping package verifier changes (emulators only)."
        }
        $savedAnimationScales = Disable-AdbAnimationScales -adbPath $adb
    } catch {
        Write-Host "Warning: could not complete device setup (emulator detection, package verifier, or animation scales)."
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
    if ($savedAnimationScales) {
        Restore-AdbAnimationScales -adbPath $adb -saved $savedAnimationScales
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
