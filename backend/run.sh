#!/bin/bash

# ==============================================================================
# 后端 jar 包进程管理脚本
# ==============================================================================

# 配置变量
JAR_NAME="backend-1.0-SNAPSHOT.jar"
PID_FILE="backend.pid"
LOG_FILE="backend.log"

# JVM 启动参数（针对阿里云中小内存服务器，限制堆内存为 512M）
JAVA_OPTS="-Xms512m -Xmx512m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m -XX:+UseG1GC"

# 提示信息用法
usage() {
    echo "Usage: sh run.sh {start|stop|restart|status}"
    exit 1
}

# 启动服务
start() {
    # 检查进程文件是否存在
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        # 检查该 PID 对应的进程是否真的在运行
        if kill -0 "$PID" >/dev/null 2>&1; then
            echo "服务已经在运行中 (PID: $PID)，无需重复启动。"
            return 0
        else
            # 进程不存在但 PID 文件残留，清理掉
            rm -f "$PID_FILE"
        fi
    fi

    echo "正在启动后端服务..."
    # 后台运行，重定向标准输出及错误输出到日志文件
    nohup java $JAVA_OPTS -jar "$JAR_NAME" > "$LOG_FILE" 2>&1 &
    
    # 获取刚启动进程的 PID
    PID=$!
    echo "$PID" > "$PID_FILE"
    
    # 稍微等待 1 秒，检查进程是否成功启动
    sleep 1
    if kill -0 "$PID" >/dev/null 2>&1; then
        echo "服务启动成功！(PID: $PID)"
        echo "日志实时输出于: $LOG_FILE"
    else
        echo "服务启动失败，请检查日志文件 $LOG_FILE"
        exit 1
    fi
}

# 停止服务
stop() {
    if [ ! -f "$PID_FILE" ]; then
        echo "未找到进程文件 $PID_FILE，服务可能未在运行。"
        return 0
    fi

    PID=$(cat "$PID_FILE")
    if ! kill -0 "$PID" >/dev/null 2>&1; then
        echo "服务未运行 (进程 ID: $PID 不存在)。"
        rm -f "$PID_FILE"
        return 0
    fi

    echo "正在停止服务 (PID: $PID)..."
    kill "$PID"

    # 循环等待进程退出（最多等待 5 秒）
    TIMEOUT=5
    while [ $TIMEOUT -gt 0 ]; do
        if ! kill -0 "$PID" >/dev/null 2>&1; then
            echo "服务已成功优雅停止。"
            rm -f "$PID_FILE"
            return 0
        fi
        sleep 1
        TIMEOUT=$((TIMEOUT - 1))
    done

    # 若超时未退出，则强制终止
    echo "优雅停止超时，正在强制停止进程 (kill -9)..."
    kill -9 "$PID"
    rm -f "$PID_FILE"
    echo "服务已被强制停止。"
}

# 查看状态
status() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" >/dev/null 2>&1; then
            echo "服务状态: 正在运行 (PID: $PID)"
            return 0
        else
            echo "服务状态: 未运行 (残留无效进程文件 PID: $PID)"
            return 1
        fi
    else
        echo "服务状态: 已停止"
        return 0
    fi
}

# 根据参数执行相应命令
case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    status)
        status
        ;;
    restart)
        stop
        start
        ;;
    *)
        usage
        ;;
esac
