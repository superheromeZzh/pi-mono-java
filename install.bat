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

:: Write campusclaw.cmd — simple wrapper that delegates to campusclaw.bat
(
    echo @echo off
    echo call "%SCRIPT_DIR%\campusclaw.bat" %%*
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
