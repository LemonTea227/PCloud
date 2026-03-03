@echo off
setlocal

where py >nul 2>&1
if %errorlevel%==0 (
	py -2 pcloud_server.py
) else (
	python pcloud_server.py
)

endlocal