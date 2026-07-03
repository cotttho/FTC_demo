@echo off
setlocal EnableExtensions

cd /d "%~dp0" || exit /b 1

if /I "%~1"=="-h" goto usage
if /I "%~1"=="--help" goto usage

call :find_python || exit /b 1

set "target=%~1"
if not defined target set "target=auto"
if not defined DANCEBOT_DURATION set "DANCEBOT_DURATION=22"

if /I "%target%"=="auto" (
  set "opmode=DanceBot Auto"
  goto run
)
if /I "%target%"=="DanceBot Auto" (
  set "opmode=DanceBot Auto"
  goto run
)
if /I "%target%"=="motor-test" (
  set "opmode=DanceBot Motor Test"
  goto run
)
if /I "%target%"=="DanceBot Motor Test" (
  set "opmode=DanceBot Motor Test"
  goto run
)
if /I "%target%"=="list" goto list

set "opmode=%target%"

:run
"%PYTHON_EXE%" %PYTHON_ARGS% "%~dp0run-ftc-opmode-robocol" run-bounded "%opmode%" --duration "%DANCEBOT_DURATION%" --allow-motion
exit /b %ERRORLEVEL%

:list
"%PYTHON_EXE%" %PYTHON_ARGS% "%~dp0run-ftc-opmode-robocol" list
exit /b %ERRORLEVEL%

:find_python
where py >nul 2>nul
if not errorlevel 1 (
  set "PYTHON_EXE=py"
  set "PYTHON_ARGS=-3"
  exit /b 0
)

where python >nul 2>nul
if not errorlevel 1 (
  set "PYTHON_EXE=python"
  set "PYTHON_ARGS="
  exit /b 0
)

echo error: Python 3 was not found. Install Python, then make sure py.exe or python.exe is on PATH.
exit /b 1

:usage
echo Usage:
echo   launch-dancebot.bat [auto^|motor-test^|list^|^<opmode name^>]
echo.
echo Launches a DanceBot OpMode over Robocol UDP without a Driver Station.
echo Connect this computer to the Control Hub Wi-Fi before running it.
echo.
echo Defaults:
echo   auto                 Runs "DanceBot Auto"
echo   DANCEBOT_DURATION   22 seconds
echo.
echo Examples:
echo   launch-dancebot.bat
echo   launch-dancebot.bat motor-test
echo   set DANCEBOT_DURATION=10
echo   launch-dancebot.bat auto
exit /b 0
