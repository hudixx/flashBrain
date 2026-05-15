# OCR 服务端口管理与自动重启实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 优化 `ocr-server/main.py` 启动逻辑，固定使用 8093 端口，支持自动识别并重启自身的旧进程，冲突时提示错误。

**Architecture:** 在服务启动前，利用 `psutil` 扫描网络连接。若 8093 端口被占用，通过进程的 `cmdline` 判断是否为本项目脚本，是则杀掉并重启，否则报错退出。

**Tech Stack:** Python, FastAPI, uvicorn, psutil

---

### Task 1: 更新依赖项

**Files:**
- Modify: `ocr-server/requirements.txt`

- [ ] **Step 1: 添加 psutil 到 requirements.txt**

在文件末尾添加 `psutil`。

```text
psutil
```

- [ ] **Step 2: 安装新依赖**

Run: `pip install psutil`
Expected: 成功安装 psutil 及其依赖。

- [ ] **Step 3: 提交更改**

```bash
git add ocr-server/requirements.txt
```

---

### Task 2: 实现端口检测与进程管理逻辑

**Files:**
- Modify: `ocr-server/main.py`

- [ ] **Step 1: 导入 psutil 和 time**

在文件顶部导入所需模块。

```python
import psutil
import time
import sys
```

- [ ] **Step 2: 实现 `manage_port_conflict` 函数**

在 `if __name__ == "__main__":` 之前定义该函数。

```python
def manage_port_conflict(port):
    for conn in psutil.net_connections(kind='inet'):
        if conn.laddr.port == port and conn.status == 'LISTEN':
            pid = conn.pid
            if pid is None:
                continue
            
            try:
                proc = psutil.Process(pid)
                cmdline = proc.cmdline()
                # 识别逻辑：包含 main.py 且是 python 进程
                if any("main.py" in arg for arg in cmdline):
                    print(f"检测到旧版 OCR 服务 (PID: {pid}) 正在占用端口 {port}。正在尝试重启...")
                    proc.terminate()
                    # 等待端口释放，最多 5 秒
                    for _ in range(50):
                        if not any(c.laddr.port == port for c in psutil.net_connections(kind='inet')):
                            print("端口已释放，准备启动新实例。")
                            return
                        time.sleep(0.1)
                    # 如果 terminate 没用，强制 kill
                    print("进程未能在规定时间内退出，强制结束中...")
                    proc.kill()
                    time.sleep(1)
                else:
                    print(f"启动失败：端口 {port} 已被其他应用 (PID: {pid}, 命令行: {' '.join(cmdline)}) 占用。")
                    sys.exit(1)
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                print(f"启动失败：端口 {port} 已被系统进程或无权限访问的进程占用。")
                sys.exit(1)
```

- [ ] **Step 3: 更新启动入口**

修改 `if __name__ == "__main__":` 块。

```python
if __name__ == "__main__":
    import uvicorn
    port = 8093
    manage_port_conflict(port)
    uvicorn.run(app, host="0.0.0.0", port=port)
```

- [ ] **Step 4: 提交更改**

```bash
git add ocr-server/main.py
```

---

### Task 3: 验证功能

**Files:**
- None (Test execution)

- [ ] **Step 1: 验证首次正常启动**

1. 确保 8093 端口未被占用。
2. 运行 `python ocr-server/main.py`。
3. 预期：服务成功启动并在控制台显示 "Uvicorn running on http://0.0.0.0:8093"。

- [ ] **Step 2: 验证自动重启**

1. 在服务运行的同时，再次在另一个终端运行 `python ocr-server/main.py`。
2. 预期：第二个进程打印“检测到旧版 OCR 服务...正在尝试重启...”，第一个进程被关闭，第二个进程成功接管端口。

- [ ] **Step 3: 验证冲突拦截**

1. 先启动一个不相关的 Python HTTP 服务器：`python -m http.server 8093`。
2. 运行 `python ocr-server/main.py`。
3. 预期：打印“启动失败：端口 8093 已被其他应用...占用”，且程序退出。
