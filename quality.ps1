param(
    [switch]$Check
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $repoRoot "pcloud-helpers.ps1")
$clientDir = Join-Path $repoRoot "PCloud Client"
$serverDir = Join-Path $repoRoot "PCloud Server"
$gradlePropertiesFile = Join-Path $clientDir "gradle.properties"

Initialize-JavaHome -GradlePropertiesFile $gradlePropertiesFile

$androidSdkConfigured = Test-AndroidSdk -ClientPath $clientDir

if ($androidSdkConfigured -and (Test-Java11OrNewer)) {
    $gradleTasks = if ($Check) {
        @(":app:spotlessCheck", ":app:lintDebug", ":app:testDebugUnitTest")
    } else {
        @(":app:spotlessApply", ":app:lintDebug", ":app:testDebugUnitTest")
    }
    Write-Host "[1/3] Android $(if ($Check) { 'format check' } else { 'formatting' }) + lint + unit tests"
    Push-Location $clientDir
    try {
        .\gradlew.bat @gradleTasks
        if ($LASTEXITCODE -ne 0) {
            throw "Android quality checks failed (exit code $LASTEXITCODE)."
        }
    } finally {
        Pop-Location
    }
} elseif ($androidSdkConfigured) {
    Write-Warning "Java 11+ is required for Android quality checks. Set JAVA_HOME to a JDK 11+ installation."
} else {
    Write-Warning "Android SDK is not configured to a valid path. Skipping Android quality checks."
}

Write-Host "[2/3] Python $(if ($Check) { 'format check' } else { 'formatting' }) + tests"
$pythonExe = Get-Python3Executable -RepoRoot $repoRoot
if (-not $pythonExe) {
    throw "Python 3 executable was not found. Install Python 3.10+ or ensure 'py -3' works."
}

$blackArgs = if ($Check) { @("-m", "black", "--check", $serverDir) } else { @("-m", "black", $serverDir) }
& $pythonExe @blackArgs
if ($LASTEXITCODE -ne 0) {
    throw "Python formatting (black) $(if ($Check) { 'check' } else { 'formatting' }) failed (exit code $LASTEXITCODE)."
}

Push-Location $serverDir
try {
    & $pythonExe -m unittest discover -s tests -p "test_*.py"
    if ($LASTEXITCODE -ne 0) {
        throw "Python 3 test execution failed (exit code $LASTEXITCODE)."
    }
} finally {
    Pop-Location
}

Write-Host "[3/3] Done"
