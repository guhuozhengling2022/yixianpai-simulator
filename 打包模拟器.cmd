@echo off
setlocal
chcp 65001 >nul
pushd "%~dp0" || goto :failed

set "YXP_BUILD_MAVEN=%CD%\tools\apache-maven-3.9.9\bin\mvn.cmd"
if not exist "%YXP_BUILD_MAVEN%" set "YXP_BUILD_MAVEN=mvn.cmd"
set "YXP_BUILD_PYTHON=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
if not exist "%YXP_BUILD_PYTHON%" (
    echo Python runtime not found: "%YXP_BUILD_PYTHON%"
    goto :failed
)

call "%YXP_BUILD_MAVEN%" "-Dmaven.repo.local=%CD%\tools\m2" -f backend/pom.xml -DskipTests package
if errorlevel 1 goto :failed

if not exist "frontend\node_modules\." (
    call npm.cmd --prefix frontend ci
    if errorlevel 1 goto :failed
)
call npm.cmd --prefix frontend run build
if errorlevel 1 goto :failed

"%YXP_BUILD_PYTHON%" scripts/build-portable.py
if errorlevel 1 goto :failed

echo.
echo Done. The ZIP is in "_local\release".
popd
pause
exit /b 0

:failed
echo.
echo Packaging failed. Check the output above.
pause
exit /b 1
