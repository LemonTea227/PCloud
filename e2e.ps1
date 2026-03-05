param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs,
    [int]$DeviceWaitSeconds = 180,
    [switch]$IncludeRealServer
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$clientDir = Join-Path $repoRoot "PCloud Client"
$serverDir = Join-Path $repoRoot "PCloud Server"
$cleanupScript = Join-Path $serverDir "cleanup_real_server_test_data.py"
$reportPath = "PCloud Client/app/build/reports/androidTests/connected/index.html"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$gradleExitCode = 1

function Get-Python2Executable {
    $pyCommand = Get-Command py -ErrorAction SilentlyContinue
    if ($pyCommand) {
        try {
            $candidate = (& $pyCommand.Source -2 -c "import sys;print(sys.executable)" 2>$null | Select-Object -Last 1)
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
        "C:\Python27\python.exe",
        "C:\Python2\python.exe"
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

    $python2Exe = Get-Python2Executable
    if (-not $python2Exe) {
        Write-Host "Warning: Python 2.7 not found; skipping real-server test data cleanup."
        return
    }

    try {
        Push-Location $serverDir
        & $python2Exe $cleanupScript | Out-Host
    } catch {
        Write-Host "Warning: real-server cleanup failed: $($_.Exception.Message)"
    } finally {
        Pop-Location
    }
}

Write-Host "Running Android E2E tests..."

if ($IncludeRealServer) {
    Write-Host "Pre-run cleanup of real-server e2e test data..."
    Invoke-RealServerCleanup
}

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
