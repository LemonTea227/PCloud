$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$clientDir = Join-Path $repoRoot "PCloud Client"
$serverDir = Join-Path $repoRoot "PCloud Server"
$localPropertiesFile = Join-Path $clientDir "local.properties"

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

if ($androidSdkConfigured) {
    Write-Host "[1/3] Android formatting + lint + unit tests"
    Push-Location $clientDir
    try {
        .\gradlew.bat :app:spotlessApply :app:lintDebug :app:testDebugUnitTest
    } finally {
        Pop-Location
    }
} else {
    Write-Warning "Android SDK is not configured to a valid path. Skipping Android quality checks."
}

Write-Host "[2/3] Python unit tests"
Push-Location $serverDir
try {
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
