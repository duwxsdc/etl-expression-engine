@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

set APP_NAME=etl-expression-engine
set APP_VERSION=1.0.0
set JAR_NAME=%APP_NAME%-%APP_VERSION%.jar
set APP_HOME=%~dp0
set JAR_PATH=%APP_HOME%target\%JAR_NAME%
set PID_FILE=%APP_HOME%application.pid
set LOG_FILE=%APP_HOME%app.log
set ERROR_LOG_FILE=%APP_HOME%app-error.log

set JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseZGC --enable-preview

if "%1"=="" goto usage
if "%1"=="start" goto start
if "%1"=="stop" goto stop
if "%1"=="restart" goto restart
if "%1"=="status" goto status
if "%1"=="build" goto build
goto usage

:start
    if not exist "%JAR_PATH%" (
        echo [错误] 未找到应用包: %JAR_PATH%
        echo [提示] 请先执行: %~nx0 build
        exit /b 1
    )

    if exist "%PID_FILE%" (
        set /p EXISTING_PID=<"%PID_FILE%"
        tasklist /FI "PID eq !EXISTING_PID!" 2>nul | find "!EXISTING_PID!" >nul
        if !errorlevel! equ 0 (
            echo [警告] 应用已在运行中, PID: !EXISTING_PID!
            exit /b 1
        ) else (
            echo [提示] 检测到残留PID文件, 清理中...
            del "%PID_FILE%" >nul 2>&1
        )
    )

    echo [启动] 正在启动 %APP_NAME% ...
    echo [信息] JAR: %JAR_PATH%
    echo [信息] 日志: %LOG_FILE%
    echo [信息] JVM参数: %JAVA_OPTS%

    start "%APP_NAME%" /min java %JAVA_OPTS% -jar "%JAR_PATH%" >> "%LOG_FILE%" 2>> "%ERROR_LOG_FILE%"

    timeout /t 3 /nobreak >nul

    for /f "tokens=2" %%a in ('tasklist /FI "IMAGENAME eq java.exe" /FI "WINDOWTITLE eq %APP_NAME%" /NH 2^>nul ^| find "java.exe"') do (
        set APP_PID=%%a
    )

    if defined APP_PID (
        echo !APP_PID!> "%PID_FILE%"
        echo [成功] %APP_NAME% 已启动, PID: !APP_PID!
        echo [信息] 访问地址: http://localhost:8080
    ) else (
        echo [警告] 无法获取PID, 应用可能仍在启动中
        echo [提示] 请检查日志: %LOG_FILE%
    )
    goto end

:stop
    if not exist "%PID_FILE%" (
        echo [警告] 未找到PID文件, 应用可能未在运行
        goto try_find_and_stop
    )

    set /p APP_PID=<"%PID_FILE%"
    tasklist /FI "PID eq !APP_PID!" 2>nul | find "!APP_PID!" >nul
    if !errorlevel! neq 0 (
        echo [警告] PID !APP_PID! 对应的进程不存在
        del "%PID_FILE%" >nul 2>&1
        goto try_find_and_stop
    )

    echo [停止] 正在停止 %APP_NAME%, PID: !APP_PID! ...
    taskkill /PID !APP_PID! /F >nul 2>&1

    timeout /t 2 /nobreak >nul

    tasklist /FI "PID eq !APP_PID!" 2>nul | find "!APP_PID!" >nul
    if !errorlevel! equ 0 (
        echo [错误] 进程未能正常停止, 请手动检查
        exit /b 1
    ) else (
        del "%PID_FILE%" >nul 2>&1
        echo [成功] %APP_NAME% 已停止
    )
    goto end

:try_find_and_stop
    echo [搜索] 正在查找 %APP_NAME% 相关进程...
    for /f "tokens=2" %%a in ('wmic process where "commandline like '%%%JAR_NAME%%%' and name='java.exe'" get processid /value 2^>nul ^| find "ProcessId"') do (
        set FOUND_PID=%%a
    )

    if defined FOUND_PID (
        echo [发现] 找到进程 PID: !FOUND_PID!
        echo [停止] 正在停止...
        taskkill /PID !FOUND_PID! /F >nul 2>&1
        timeout /t 2 /nobreak >nul
        echo [成功] 进程已停止
    ) else (
        echo [信息] 未找到运行中的 %APP_NAME% 进程
    )
    goto end

:restart
    echo [重启] 正在重启 %APP_NAME% ...
    call "%~f0" stop
    timeout /t 3 /nobreak >nul
    call "%~f0" start
    goto end

:status
    echo [状态] %APP_NAME% 状态检查:
    echo ========================================

    if exist "%PID_FILE%" (
        set /p APP_PID=<"%PID_FILE%"
        tasklist /FI "PID eq !APP_PID!" 2>nul | find "!APP_PID!" >nul
        if !errorlevel! equ 0 (
            echo [运行中] PID: !APP_PID!
            echo [信息]   JAR: %JAR_PATH%
            echo [信息]   日志: %LOG_FILE%

            netstat -ano 2>nul | find ":8080" | find "LISTENING" >nul
            if !errorlevel! equ 0 (
                echo [信息]   端口: 8080 已监听
                echo [信息]   地址: http://localhost:8080
            ) else (
                echo [警告]   端口: 8080 未监听, 应用可能仍在启动中
            )
        ) else (
            echo [已停止] PID文件存在但进程不存在 (残留PID文件)
            del "%PID_FILE%" >nul 2>&1
        )
    ) else (
        echo [已停止] 未检测到运行中的进程
    )

    echo ========================================
    goto end

:build
    echo [构建] 正在编译打包 %APP_NAME% ...
    call mvn clean package -DskipTests -f "%APP_HOME%pom.xml"
    if !errorlevel! equ 0 (
        echo [成功] 构建完成: %JAR_PATH%
    ) else (
        echo [错误] 构建失败
        exit /b 1
    )
    goto end

:usage
    echo.
    echo 用法: %~nx0 {start^|stop^|restart^|status^|build}
    echo.
    echo 命令说明:
    echo   start    启动应用
    echo   stop     停止应用
    echo   restart  重启应用
    echo   status   查看应用状态
    echo   build    编译打包应用
    echo.
    echo 示例:
    echo   %~nx0 start     启动ETL表达式引擎
    echo   %~nx0 status    检查运行状态
    echo   %~nx0 restart   重启应用
    echo.

:end
    endlocal
