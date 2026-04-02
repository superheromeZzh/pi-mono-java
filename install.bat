@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: ──────────────────────────────────────────────────────────────
:: CampusClaw Installer (Windows)
::
:: Creates a global `campusclaw` command that points back to this
:: source tree. Every run auto-rebuilds when source changes.
::
:: Layout after install:
::   %USERPROFILE%\.campusclaw\bin\campusclaw.cmd  (wrapper → this repo)
:: ──────────────────────────────────────────────────────────────

set "SCRIPT_DIR=%~dp0"
:: Remove trailing backslash
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
set "BIN_DIR=%USERPROFILE%\.campusclaw\bin"

:: ── Check JDK 21 ─────────────────────────────────────────────
:: Prefer JAVA_HOME if set, otherwise fall back to PATH
set "JAVA_CMD=java"
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java"
)

:: Capture version string and check for "21"
set "JDK_OK=0"
for /f "tokens=*" %%v in ('"%JAVA_CMD%" -version 2^>^&1') do (
    if "!JDK_OK!"=="0" (
        echo %%v | findstr /R "\"21\." >nul && set "JDK_OK=1"
        echo %%v | findstr /R " 21\." >nul && set "JDK_OK=1"
        echo %%v | findstr /R " 21 " >nul && set "JDK_OK=1"
    )
)
if "!JDK_OK!"=="0" (
    echo 错误：未找到 JDK 21。
    echo 当前 java 路径: %JAVA_CMD%
    echo.
    echo 请确认：
    echo   1. 已安装 JDK 21（下载: https://adoptium.net/temurin/releases/?version=21）
    echo   2. JAVA_HOME 指向 JDK 21 安装目录，或 PATH 中的 java 为 21 版本
    echo.
    echo 可通过以下命令验证：
    echo   java -version
    echo   echo %%JAVA_HOME%%
    pause
    exit /b 1
)
echo 已检测到 JDK 21
echo 源码目录: %SCRIPT_DIR%

:: ── Create wrapper script ─────────────────────────────────────
if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"

:: Write campusclaw.cmd — embed SCRIPT_DIR as hardcoded REPO_DIR
(
    echo @echo off
    echo chcp 65001 ^>nul
    echo setlocal enabledelayedexpansion
    echo.
    echo set "REPO_DIR=%SCRIPT_DIR%"
    echo set "JAR_DIR=%%REPO_DIR%%\modules\coding-agent-cli\target"
    echo set "JAR=%%JAR_DIR%%\campusclaw-agent.jar"
    echo.
    echo :: Parse --rebuild flag
    echo set "REBUILD=0"
    echo set "ARGS="
    echo set "HAS_ARGS=0"
    echo for %%%%a in ^(%%*^) do ^(
    echo     if "%%%%~a"=="--rebuild" ^(
    echo         set "REBUILD=1"
    echo     ^) else ^(
    echo         if ^^!HAS_ARGS^^!==1 ^(
    echo             set "ARGS=^^!ARGS^^! %%%%a"
    echo         ^) else ^(
    echo             set "ARGS=%%%%a"
    echo             set "HAS_ARGS=1"
    echo         ^)
    echo     ^)
    echo ^)
    echo.
    echo :: Build if no JAR or --rebuild
    echo if "%%REBUILD%%"=="1" ^(
    echo     echo 正在重新构建 campusclaw-agent...
    echo     call :do_build
    echo ^) else if not exist "%%JAR%%" ^(
    echo     echo 正在构建 campusclaw-agent...
    echo     call :do_build
    echo ^) else ^(
    echo     :: Check if any .java file is newer than JAR
    echo     for /f "delims=" %%%%f in ^('dir /s /b /o-d "%%REPO_DIR%%\modules\*.java" 2^^^>nul ^| findstr /n "^" ^| findstr "^1:"'^) do ^(
    echo         for %%%%j in ^("%%JAR%%"^) do ^(
    echo             for %%%%s in ^("%%%%~f"^) do ^(
    echo                 if "%%%%~ts" GTR "%%%%~tj" ^(
    echo                     echo 检测到源码变更，正在重新构建...
    echo                     call :do_build
    echo                 ^)
    echo             ^)
    echo         ^)
    echo     ^)
    echo ^)
    echo.
    echo if not exist "%%JAR%%" ^(
    echo     echo 错误：构建失败，找不到 JAR 文件。
    echo     exit /b 1
    echo ^)
    echo.
    echo java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%%JAR%%" %%ARGS%%
    echo exit /b %%errorlevel%%
    echo.
    echo :do_build
    echo call "%%REPO_DIR%%\mvnw.cmd" -f "%%REPO_DIR%%\pom.xml" package -pl modules/coding-agent-cli -am -q -DskipTests
    echo if errorlevel 1 ^(
    echo     echo 错误：构建失败。
    echo     exit /b 1
    echo ^)
    echo exit /b 0
) > "%BIN_DIR%\campusclaw.cmd"

echo 已创建命令 %BIN_DIR%\campusclaw.cmd

:: ── Add to user PATH ──────────────────────────────────────────
echo %PATH% | findstr /I /C:".campusclaw\bin" >nul
if errorlevel 1 (
    for /f "tokens=2,*" %%a in ('reg query "HKCU\Environment" /v Path 2^>nul') do set "USER_PATH=%%b"
    if not defined USER_PATH (
        setx PATH "%BIN_DIR%"
    ) else (
        echo !USER_PATH! | findstr /I /C:".campusclaw\bin" >nul
        if errorlevel 1 (
            setx PATH "%BIN_DIR%;!USER_PATH!"
        )
    )
    echo 已将 %BIN_DIR% 添加至用户 PATH
) else (
    echo PATH 已配置。
)

echo.
echo 安装完成！
echo 请重新打开命令行窗口，然后运行：
echo   campusclaw --help
echo   campusclaw skill list
echo.
pause
