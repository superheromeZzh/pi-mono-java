@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "JAR_DIR=%SCRIPT_DIR%modules\coding-agent-cli\build\libs"

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

:: Build if no JAR or --rebuild
if "%REBUILD%"=="1" (
    echo 正在重新构建 campusclaw-agent...
    call :build
) else if not defined JAR (
    echo 正在构建 campusclaw-agent...
    call :build
)

:: Find JAR again after build
call :find_jar

if not defined JAR (
    echo 错误：构建失败，找不到 JAR 文件。
    pause
    exit /b 1
)

:: Launch
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%JAR%" %ARGS%
pause
exit /b %errorlevel%

:build
call "%SCRIPT_DIR%gradlew.bat" -p "%SCRIPT_DIR%" :modules:campusclaw-coding-agent:bootJar
if errorlevel 1 (
    echo 错误：构建失败。
    pause
    exit /b 1
)
exit /b 0

:find_jar
set "JAR="
for %%f in ("%JAR_DIR%\campusclaw-agent-*.jar") do (
    echo %%~nf | findstr /v "\-plain" >nul 2>&1
    if not errorlevel 1 set "JAR=%%f"
)
exit /b 0
