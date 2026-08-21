@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Build the Board Banker Android APK from Windows CMD.
rem Usage:
rem   build-android-apk.bat
rem   build-android-apk.bat debug
rem   build-android-apk.bat release
rem
rem Optional flags (any order after the variant):
rem   --skip-sync    Do not copy data/*.json into Android assets
rem   --with-tests   Run unit tests before assembling the APK
rem
rem The script can be run from any directory:
rem   C:\Personal\Monopoly\monopoly-ultimate-banking-qr\scripts\build-android-apk.bat

set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."
pushd "%PROJECT_ROOT%" >nul
set "PROJECT_ROOT=%CD%"
popd >nul

set "ANDROID_DIR=%PROJECT_ROOT%\android-app"
set "GRADLEW=%ANDROID_DIR%\gradlew.bat"
set "OUTPUT_DIR=%SCRIPT_DIR%output"
set "VARIANT=debug"
set "SKIP_SYNC=0"
set "WITH_TESTS=0"

:parse_args
if "%~1"=="" goto args_done
if /I "%~1"=="debug" set "VARIANT=debug" & shift & goto parse_args
if /I "%~1"=="release" set "VARIANT=release" & shift & goto parse_args
if /I "%~1"=="--skip-sync" set "SKIP_SYNC=1" & shift & goto parse_args
if /I "%~1"=="--with-tests" set "WITH_TESTS=1" & shift & goto parse_args
echo Unknown argument: %~1
echo Usage: build-android-apk.bat [debug^|release] [--skip-sync] [--with-tests]
exit /b 1

:args_done
echo.
echo === Board Banker Android APK build ===
echo Project: %PROJECT_ROOT%
echo Variant: %VARIANT%
echo.

if not exist "%GRADLEW%" (
  echo ERROR: Gradle wrapper not found:
  echo   %GRADLEW%
  exit /b 1
)

if "%SKIP_SYNC%"=="1" (
  echo Skipping asset sync.
) else (
  echo Syncing game JSON into Android assets...
  set "PYTHON_CMD="
  where py >nul 2>&1 && set "PYTHON_CMD=py -3"
  if not defined PYTHON_CMD (
    where python >nul 2>&1 && set "PYTHON_CMD=python"
  )
  if not defined PYTHON_CMD (
    echo ERROR: Python not found on PATH. Install Python 3 or pass --skip-sync.
    exit /b 1
  )
  !PYTHON_CMD! "%PROJECT_ROOT%\tools\sync_android_assets.py"
  if errorlevel 1 (
    echo ERROR: Asset sync failed.
    exit /b 1
  )
  echo.
)

if /I "%VARIANT%"=="release" (
  set "GRADLE_TASK=:app:assembleRelease"
  set "APK_SOURCE=%ANDROID_DIR%\app\build\outputs\apk\release\app-release.apk"
  set "APK_NAME=boardbanker-release.apk"
) else (
  set "GRADLE_TASK=:app:assembleDebug"
  set "APK_SOURCE=%ANDROID_DIR%\app\build\outputs\apk\debug\app-debug.apk"
  set "APK_NAME=boardbanker-debug.apk"
)

set "GRADLE_EXTRA="
if "%WITH_TESTS%"=="1" (
  set "GRADLE_EXTRA=:game-core:test :app:testDebugUnitTest"
  echo Running unit tests, then %GRADLE_TASK%...
) else (
  echo Building %GRADLE_TASK%...
)
echo.

pushd "%ANDROID_DIR%"
call "%GRADLEW%" --console=plain %GRADLE_EXTRA% %GRADLE_TASK%
set "BUILD_EXIT=%ERRORLEVEL%"
popd
if not "%BUILD_EXIT%"=="0" (
  echo.
  echo ERROR: Gradle build failed with exit code %BUILD_EXIT%.
  echo Make sure Android SDK and JDK 17 are installed, and that
  echo android-app\local.properties contains sdk.dir=...
  exit /b %BUILD_EXIT%
)

if not exist "%APK_SOURCE%" (
  echo ERROR: Build succeeded but APK was not found:
  echo   %APK_SOURCE%
  exit /b 1
)

if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
copy /Y "%APK_SOURCE%" "%OUTPUT_DIR%\%APK_NAME%" >nul
if errorlevel 1 (
  echo ERROR: Could not copy APK to %OUTPUT_DIR%
  exit /b 1
)

echo.
echo BUILD OK
echo APK: %OUTPUT_DIR%\%APK_NAME%
echo Also: %APK_SOURCE%
echo.
echo Install example:
echo   adb install -r "%OUTPUT_DIR%\%APK_NAME%"
exit /b 0
