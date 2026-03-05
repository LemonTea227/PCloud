# PCloud

Monorepo with:
- `PCloud Server` (Python server)
- `PCloud Client` (Android app)

## Requirements

- Java JDK 11+ (required for Android Gradle Plugin 7.x)
- Android SDK configured via `ANDROID_SDK_ROOT` or `PCloud Client/local.properties`
- Python 3.10+ for server runtime

## One-command run

From repo root:

```powershell
./run.ps1
```

What it does:
1. Starts the Python server (`py -3 pcloud_server.py`) in background.
2. Updates Android socket host/port in `MySocket.java`.
3. Builds and installs debug APK.
4. Launches the app with `adb` (if available).

### Common options

```powershell
./run.ps1 -SkipClient
./run.ps1 -ServerHost 192.168.1.50 -ServerPort 22703
./run.ps1 -SkipInstall
```

## Quality checks (format + lint + tests)

From repo root:

```powershell
./quality.ps1
```

This runs:
- Android formatter: `spotlessApply` (Spotless + `google-java-format`)
- Android linter: `lintDebug`
- Android unit tests: `testDebugUnitTest`
- Server formatter: `black`
- Server tests: `python -m unittest`

When running `./e2e.ps1 -IncludeRealServer`, the script also performs pre/post cleanup of real-server test data (`realsrv*` users and `real_e2e_*` albums/photos/filesystem folders) via `PCloud Server/cleanup_real_server_test_data.py`.

## Server dependencies

Install server runtime deps:

```powershell
cd "PCloud Server"
py -3 -m pip install -r requirements.txt
```

Install optional server dev deps:

```powershell
py -3 -m pip install -r requirements-dev.txt
```
