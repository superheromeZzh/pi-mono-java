@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "JAR_DIR=%SCRIPT_DIR%modules\coding-agent-cli\target"
set "BUILD_MARKER=%JAR_DIR%\.build-timestamp"

:: Parse --rebuild flag
set "REBUILD=0"
set "ARGS="
set "HAS_ARGS=0"
for %%a in (%*) do (
    if "%%~a"=="--rebuild" (
        set "REBUILD=1"
    ) else (
        if !HAS_ARGS!==1 (
            set "ARGS=!ARGS! %%a"
        ) else (
            set "ARGS=%%a"
            set "HAS_ARGS=1"
        )
    )
)

:: Find JAR
call :find_jar

:: Build if no JAR, --rebuild, or source changed
if "%REBUILD%"=="1" (
    echo 正在重新构建 campusclaw-agent...
    call :build
) else if not defined JAR (
    echo 正在构建 campusclaw-agent...
    call :build
) else (
    call :check_needs_build
    if "!NEEDS_BUILD!"=="1" (
        echo 源代码已更新，正在重新构建 campusclaw-agent...
        call :build
    )
)

:: Find JAR again after build
call :find_jar

if not defined JAR (
    echo 错误：构建失败，找不到 JAR 文件。
    pause
    exit /b 1
)

:: Launch
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Djavax.net.ssl.trustStoreType=WINDOWS-ROOT -jar "%JAR%" %ARGS%
pause
exit /b %errorlevel%

:build
call "%SCRIPT_DIR%mvnw.cmd" -f "%SCRIPT_DIR%pom.xml" package -pl modules/coding-agent-cli -am -q -DskipTests
if errorlevel 1 (
    echo 错误：构建失败。
    pause
    exit /b 1
)
:: Update build marker
echo.> "%BUILD_MARKER%"
exit /b 0

:find_jar
set "JAR="
if exist "%JAR_DIR%\campusclaw-agent.jar" set "JAR=%JAR_DIR%\campusclaw-agent.jar"
exit /b 0

:check_needs_build
set "NEEDS_BUILD=0"
:: No build marker means never tracked — rebuild
if not exist "%BUILD_MARKER%" (
    set "NEEDS_BUILD=1"
    exit /b 0
)
:: Use PowerShell to check if any source file is newer than the build marker
for /f %%r in ('powershell -NoProfile -Command "$m=(Get-Item '%BUILD_MARKER%').LastWriteTime; $n=Get-ChildItem -Path '%SCRIPT_DIR%modules' -Recurse -Include '*.java','*.xml','*.yml','*.properties' -ErrorAction SilentlyContinue | Where-Object {$_.LastWriteTime -gt $m} | Select-Object -First 1; if($n){'1'}else{'0'}" 2^>nul') do set "NEEDS_BUILD=%%r"
exit /b 0
