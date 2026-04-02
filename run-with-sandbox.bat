@echo off
REM CampusClaw Docker 沙箱运行脚本 (Windows)
REM 使用方式: run-with-sandbox.bat [选项] [模型]

echo === CampusClaw Docker 沙箱运行脚本 ===

REM 检查 Docker 是否安装
docker version >nul 2>&1
if errorlevel 1 (
    echo 错误: Docker 未安装或未运行
    echo 请安装 Docker Desktop: https://www.docker.com/products/docker-desktop
    exit /b 1
)

echo [OK] Docker 已就绪

REM 设置沙箱环境变量
set "TOOL_EXECUTION_DEFAULT_MODE=SANDBOX"
set "TOOL_EXECUTION_SANDBOX_ENABLED=true"
set "TOOL_EXECUTION_LOCAL_ENABLED=true"

REM Windows 使用默认命名管道
set "DOCKER_HOST=npipe:////./pipe/docker_engine"

echo.
echo 执行模式: %TOOL_EXECUTION_DEFAULT_MODE%
echo Docker 主机: %DOCKER_HOST%
echo.

REM 检查 JAR 文件
set "JAR_PATH=modules\coding-agent-cli\target\campusclaw-agent.jar"
if not exist "%JAR_PATH%" (
    echo JAR 文件不存在，开始构建...
    call mvnw.cmd -f pom.xml package -pl modules/coding-agent-cli -am -DskipTests -q
)

if not exist "%JAR_PATH%" (
    echo 错误: 构建失败，找不到 JAR 文件。
    exit /b 1
)

REM 运行 CampusClaw
echo 启动 CampusClaw (沙箱模式)...
echo.

java -jar "%JAR_PATH%" --exec-mode sandbox %*
