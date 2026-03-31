@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "JAR_DIR=%SCRIPT_DIR%modules\coding-agent-cli\build\libs"
set "BUILD_MARKER=%JAR_DIR%\.build-timestamp"

:: Auto-detect JDK 21
call :detect_jdk21
if errorlevel 1 (
    echo Error: JDK 21 not found. >&2
    echo Install options: >&2
    echo   winget install EclipseAdoptium.Temurin.21.JDK >&2
    echo   Or download from https://adoptium.net/ >&2
    exit /b 1
)

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

:: Find JAR (avoid hardcoding version)
call :find_jar
if not defined JAR set "NEED_BUILD=1"

:: Check if rebuild needed
if "%REBUILD%"=="1" (
    echo Rebuilding campusclaw-agent...
    set "NEED_BUILD=1"
) else if not defined JAR (
    echo Building campusclaw-agent...
    set "NEED_BUILD=1"
) else if not exist "%BUILD_MARKER%" (
    echo Building campusclaw-agent...
    set "NEED_BUILD=1"
)

if defined NEED_BUILD (
    call "%SCRIPT_DIR%gradlew.bat" -p "%SCRIPT_DIR%" :modules:campusclaw-coding-agent:bootJar -q
    if errorlevel 1 exit /b 1
    echo.> "%BUILD_MARKER%"
    call :find_jar
)

if not defined JAR (
    echo Error: Build failed, JAR not found. >&2
    exit /b 1
)

"%JAVA_HOME%\bin\java" -jar "%JAR%" %ARGS%
exit /b %errorlevel%

:find_jar
set "JAR="
for %%f in ("%JAR_DIR%\campusclaw-agent-*.jar") do (
    echo %%~nf | findstr /v "\-plain" >nul 2>&1
    if not errorlevel 1 set "JAR=%%f"
)
exit /b 0

:detect_jdk21
:: 1. Current JAVA_HOME already JDK 21?
if defined JAVA_HOME (
    for /f "tokens=3" %%v in ('"%JAVA_HOME%\bin\java" -version 2^>^&1 ^| findstr /r "\"21\."') do (
        exit /b 0
    )
)

:: 2. Check common install paths
for /d %%d in (
    "C:\Program Files\Eclipse Adoptium\jdk-21*"
    "C:\Program Files\Java\jdk-21*"
    "C:\Program Files\Microsoft\jdk-21*"
    "C:\Program Files\Zulu\zulu-21*"
) do (
    if exist "%%~d\bin\java.exe" (
        set "JAVA_HOME=%%~d"
        exit /b 0
    )
)

:: 3. Check SDKMAN (Git Bash on Windows)
if defined SDKMAN_DIR (
    for /d %%d in ("%SDKMAN_DIR%\candidates\java\21*") do (
        if exist "%%~d\bin\java.exe" (
            set "JAVA_HOME=%%~d"
            exit /b 0
        )
    )
)

:: 4. Default java is 21?
where java >nul 2>&1
if not errorlevel 1 (
    for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /r "\"21\."') do (
        for /f "tokens=*" %%p in ('where java') do (
            set "JAVA_HOME=%%~dpp.."
            exit /b 0
        )
    )
)

exit /b 1
