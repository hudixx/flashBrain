# 阿里云服务器后端部署及运行指南

本文档介绍如何将打包后的后端程序部署到阿里云服务器（Linux 环境）上运行。

## 1. 部署前准备

在开始部署之前，请确保您的阿里云服务器已满足以下条件：

1. **安装 Java 11 运行环境**：
   - 本项目使用 Java 11 编译。您可以在服务器上运行 `java -version` 查看当前 Java 版本。
   - 若未安装，在 Ubuntu/Debian 上运行：
     ```bash
     sudo apt update
     sudo apt install openjdk-11-jre-headless
     ```
   - 在 CentOS/RHEL 上运行：
     ```bash
     sudo yum install java-11-openjdk-headless
     ```

2. **阿里云安全组规则配置**：
   - 登录阿里云控制台，找到您的 ECS 实例。
   - 进入“安全组配置”，在“入方向”添加规则：
     - **协议类型**：自定义 TCP
     - **端口范围**：`8011`（后端默认端口，若在配置文件中修改了端口，请使用对应端口）
     - **授权对象**：`0.0.0.0/0`（或者您指定的允许访问的 IP 范围）

---

## 2. 部署步骤

1. **上传文件**：
   - 将打包好的 `backend-1.0-SNAPSHOT.jar` 和 `run.sh` 脚本上传到阿里云服务器上的同一个目录下（例如：`/opt/flashbrain/backend/`）。
   - 可以使用 `scp` 命令、SFTP 客户端（如 FileZilla、Termius）或者阿里云自带的 WebShell 上传文件。

2. **为运行脚本赋予可执行权限**：
   - 在服务器上进入该文件所在目录，执行以下命令：
     ```bash
     chmod +x run.sh
     ```

---

## 3. 服务管理指令

使用 `run.sh` 脚本对后端进行日常维护管理：

* **启动服务**：
  ```bash
  ./run.sh start
  ```
  或者：
  ```bash
  sh run.sh start
  ```

* **停止服务**：
  ```bash
  ./run.sh stop
  ```

* **重启服务**：
  ```bash
  ./run.sh restart
  ```

* **查看运行状态**：
  ```bash
  ./run.sh status
  ```

---

## 4. 日志与调试

- **实时查看运行日志**：
  ```bash
  tail -f backend.log
  ```
- **查看日志最后 100 行**：
  ```bash
  tail -n 100 backend.log
  ```
