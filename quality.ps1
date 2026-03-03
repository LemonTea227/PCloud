$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$clientDir = Join-Path $repoRoot "PCloud Client"
$serverDir = Join-Path $repoRoot "PCloud Server"
$localPropertiesFile = Join-Path $clientDir "local.properties"

function Test-Java11OrNewer {
    $javaOutput = & java -version 2>&1
    $versionLine = $javaOutput | Select-Object -First 1

    if ($versionLine -match 'version "(\d+)') {
        $major = [int]$matches[1]
        if ($major -eq 1 -and $versionLine -match 'version "1\.(\d+)') {
            return ([int]$matches[1] -ge 11)
        }
        return ($major -ge 11)
    }

    return $false
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
        & (Join-Path $repoRoot ".venv/Scripts/python.exe") -m black .
    } else {
        & py -3 -m black .
    }

    & py -2 -m unittest discover -s tests -p "test_*.py"
    if ($LASTEXITCODE -ne 0) {
        if (Test-Path (Join-Path $repoRoot ".venv/Scripts/python.exe")) {
            & (Join-Path $repoRoot ".venv/Scripts/python.exe") -m unittest discover -s tests -p "test_*.py"
        } else {
            throw "Python test execution failed and no fallback interpreter was found."
        }
    }
} finally {
    Pop-Location
}

Write-Host "[3/3] Done"
