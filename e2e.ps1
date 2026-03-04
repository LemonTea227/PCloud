param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs,
    [int]$DeviceWaitSeconds = 180
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$clientDir = Join-Path $repoRoot "PCloud Client"
$reportPath = "PCloud Client/app/build/reports/androidTests/connected/index.html"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$gradleExitCode = 1

Write-Host "Running Android E2E tests..."

if (Test-Path $adb) {
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
        & $adb shell settings put global window_animation_scale 0 | Out-Null
        & $adb shell settings put global transition_animation_scale 0 | Out-Null
        & $adb shell settings put global animator_duration_scale 0 | Out-Null
        Write-Host "Device animations disabled for stable Espresso runs."
    } catch {
        Write-Host "Warning: could not set animation scales via adb."
    }
} else {
    Write-Host "Warning: adb not found at $adb"
}

Push-Location $clientDir
try {
    & ".\gradlew.bat" connectedDebugAndroidTest --no-daemon @GradleArgs
    $gradleExitCode = $LASTEXITCODE
} catch {
    $gradleExitCode = 1
    Write-Host "E2E execution threw an error: $($_.Exception.Message)"
}
finally {
    Pop-Location
}

if ($gradleExitCode -eq 0) {
    Write-Host "E2E passed. Report: $reportPath"
} else {
    Write-Host "E2E failed. Report: $reportPath"
}

exit $gradleExitCode
