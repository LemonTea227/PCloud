# PCloud

Monorepo with:
- `PCloud Server` (Python server)
- `PCloud Client` (Android app)

## One-command run

From repo root:

```powershell
./run.ps1
```

What it does:
1. Starts the Python server (`py -2 pcloud_server.py`) in background.
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
- Android formatter: `spotlessApply`
- Android linter: `lintDebug`
- Android unit tests: `testDebugUnitTest`
- Server tests: `python -m unittest`

## Server dependencies

Install server runtime deps:

```powershell
cd "PCloud Server"
py -2 -m pip install -r requirements.txt
```

Install optional server dev deps:

```powershell
py -2 -m pip install -r requirements-dev.txt
```
