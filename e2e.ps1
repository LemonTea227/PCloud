param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$clientDir = Join-Path $repoRoot "PCloud Client"
$reportPath = "PCloud Client/app/build/reports/androidTests/connected/index.html"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

Write-Host "Running Android E2E tests..."

if (Test-Path $adb) {
    & $adb wait-for-device | Out-Null
    & $adb shell settings put global window_animation_scale 0 | Out-Null
    & $adb shell settings put global transition_animation_scale 0 | Out-Null
    & $adb shell settings put global animator_duration_scale 0 | Out-Null
    Write-Host "Device animations disabled for stable Espresso runs."
}

Push-Location $clientDir
try {
    & ".\gradlew.bat" connectedDebugAndroidTest --no-daemon @GradleArgs
    $gradleExitCode = $LASTEXITCODE
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
