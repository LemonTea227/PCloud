param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$clientDir = Join-Path $repoRoot "PCloud Client"
$reportPath = "PCloud Client/app/build/reports/androidTests/connected/index.html"

Write-Host "Running Android E2E tests..."

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
