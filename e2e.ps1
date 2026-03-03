param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$clientDir = Join-Path $repoRoot "PCloud Client"

Push-Location $clientDir
try {
    & ".\gradlew.bat" connectedDebugAndroidTest --no-daemon @GradleArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
