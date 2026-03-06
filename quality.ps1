$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $repoRoot "pcloud-helpers.ps1")
$clientDir = Join-Path $repoRoot "PCloud Client"
$serverDir = Join-Path $repoRoot "PCloud Server"
$gradlePropertiesFile = Join-Path $clientDir "gradle.properties"

$projectJavaHome = Get-ProjectJavaHome -GradlePropertiesFile $gradlePropertiesFile
if ($projectJavaHome) {
    $env:JAVA_HOME = $projectJavaHome
    $javaBinPath = "$projectJavaHome\bin"
    if (($env:Path -split ';') -notcontains $javaBinPath) {
        $env:Path = "$javaBinPath;" + $env:Path
    }
}

$androidSdkConfigured = Test-AndroidSdk -ClientPath $clientDir

if ($androidSdkConfigured -and (Test-Java11OrNewer)) {
    Write-Host "[1/3] Android formatting + lint + unit tests"
    Push-Location $clientDir
    try {
        .\gradlew.bat :app:spotlessApply :app:lintDebug :app:testDebugUnitTest
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

Write-Host "[2/3] Python formatting + tests"
$pythonExe = Get-Python3Executable -RepoRoot $repoRoot
if (-not $pythonExe) {
    throw "Python 3 executable was not found. Install Python 3.10+ or ensure 'py -3' works."
}

& $pythonExe -m black $serverDir
if ($LASTEXITCODE -ne 0) {
    throw "Python formatting (black) failed (exit code $LASTEXITCODE)."
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
