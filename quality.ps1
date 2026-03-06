$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $repoRoot "pcloud-helpers.ps1")
$clientDir = Join-Path $repoRoot "PCloud Client"
$serverDir = Join-Path $repoRoot "PCloud Server"
$localPropertiesFile = Join-Path $clientDir "local.properties"
$gradlePropertiesFile = Join-Path $clientDir "gradle.properties"

$projectJavaHome = Get-ProjectJavaHome -GradlePropertiesFile $gradlePropertiesFile
if ($projectJavaHome) {
    $env:JAVA_HOME = $projectJavaHome
    $javaBinPath = "$projectJavaHome\bin"
    if (($env:Path -split ';') -notcontains $javaBinPath) {
        $env:Path = "$javaBinPath;" + $env:Path
    }
}

$androidSdkConfigured = $false
if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
    $androidSdkConfigured = $true
} elseif (Test-Path $localPropertiesFile) {
    $sdkLine = (Get-Content $localPropertiesFile | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1)
    if ($sdkLine) {
        $sdkPath = $sdkLine.Substring(8)
        $sdkPath = $sdkPath -replace '\\\\', '\\'
        $sdkPath = $sdkPath -replace '\\:', ':'
        if (Test-Path $sdkPath) {
            $androidSdkConfigured = $true
        }
    }
}

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

Write-Host "[2/3] Python unit tests"
Push-Location $serverDir
try {
    if (Test-Path (Join-Path $repoRoot ".venv/Scripts/python.exe")) {
        $pythonExe = (Join-Path $repoRoot ".venv/Scripts/python.exe")
    } else {
        $pythonExe = "py"
    }

    if ($pythonExe -eq "py") {
        & py -3 -m black .
        if ($LASTEXITCODE -ne 0) {
            throw "Python formatting (black) failed (exit code $LASTEXITCODE)."
        }
        & py -3 -m unittest discover -s tests -p "test_*.py"
        if ($LASTEXITCODE -ne 0) {
            throw "Python 3 test execution failed (exit code $LASTEXITCODE)."
        }
    } else {
        & $pythonExe -m black .
        if ($LASTEXITCODE -ne 0) {
            throw "Python formatting (black) failed (exit code $LASTEXITCODE)."
        }
        & $pythonExe -m unittest discover -s tests -p "test_*.py"
        if ($LASTEXITCODE -ne 0) {
            throw "Python 3 test execution failed (exit code $LASTEXITCODE)."
        }
    }
} finally {
    Pop-Location
}

Write-Host "[3/3] Done"
