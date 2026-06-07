# kkFileView 部署与文件预览配置说明

本文档说明 FlashBrain 中 Office / OFD 文件在线预览所需的 kkFileView 部署方式，以及前端环境变量中两个 URL 的含义和配置方法。

## 1. 功能背景

FlashBrain 上传文件后，文件列表中的“查看”按钮会在新浏览器标签页中打开预览页面。

当前预览规则：

- 图片、PDF、TXT：浏览器直接内嵌原文件地址预览。
- DOC、DOCX、OFD：通过 kkFileView 进行在线预览，以尽量保留原文件格式、版式和内容。

因此，如果需要预览 DOC / DOCX / OFD，必须部署 kkFileView 服务，并在前端配置 kkFileView 地址和后端文件公开访问地址。

## 2. kkFileView 部署

### 2.1 Docker 部署（推荐）

先确认 Docker 已安装：

```bash
docker --version
```

启动 kkFileView：

```bash
docker run -d \
  --name kkfileview \
  -p 8012:8012 \
  keking/kkfileview:latest
```

Windows PowerShell 可使用一行命令：

```powershell
docker run -d --name kkfileview -p 8012:8012 keking/kkfileview:latest
```

启动后访问：

```text
http://localhost:8012
```

如果能打开 kkFileView 页面，说明服务已启动成功。

常用 Docker 命令：

```bash
# 查看运行状态
docker ps

# 查看日志
docker logs kkfileview

# 停止服务
docker stop kkfileview

# 重新启动服务
docker start kkfileview

# 删除容器
docker rm -f kkfileview
```

### 2.2 手动部署（可选）

也可以手动部署 kkFileView，但步骤更多，通常需要准备：

1. Java 运行环境。
2. LibreOffice / OpenOffice 等转换组件。
3. kkFileView 发布包。
4. 字体、端口、转换工具路径等配置。

本项目推荐优先使用 Docker 部署，避免本机环境差异导致预览失败。

## 3. 前端配置文件位置

本地开发环境建议使用：

```text
frontend/.env.local
```

生产环境建议使用：

```text
frontend/.env.production
```

如果文件不存在，可以自行创建。

注意：Vite 的 `VITE_` 环境变量会在前端启动或构建时读取。

- 修改 `.env.local` 后，需要重启前端开发服务。
- 修改 `.env.production` 后，需要重新构建前端。

本地重启前端：

```bash
cd frontend
npm run dev
```

生产构建：

```bash
cd frontend
npm run build
```

## 4. 两个 URL 配置说明

文件预览相关环境变量有两个：

```env
VITE_KKFILEVIEW_BASE_URL=
VITE_BACKEND_PUBLIC_BASE_URL=
```

### 4.1 VITE_KKFILEVIEW_BASE_URL

`VITE_KKFILEVIEW_BASE_URL` 是 kkFileView 服务地址。

它是浏览器访问 kkFileView 的入口。

本地 Docker 部署时通常配置为：

```env
VITE_KKFILEVIEW_BASE_URL=http://localhost:8012
```

生产环境示例：

```env
VITE_KKFILEVIEW_BASE_URL=https://preview.example.com
```

### 4.2 VITE_BACKEND_PUBLIC_BASE_URL

`VITE_BACKEND_PUBLIC_BASE_URL` 是后端上传文件的公开访问地址。

FlashBrain 后端会把上传文件暴露为类似：

```text
/uploads/ocr-images/{snippetId}/{filename}
```

前端预览页会将它拼成完整 URL，例如：

```text
http://localhost:8080/uploads/ocr-images/1/example.docx
```

然后把该完整 URL 传给 kkFileView。

也就是说：

> `VITE_BACKEND_PUBLIC_BASE_URL` 必须是 kkFileView 服务能够访问到的后端地址。

本地后端直接运行在宿主机 8080 端口时，常见配置为：

```env
VITE_BACKEND_PUBLIC_BASE_URL=http://localhost:8080
```

如果 kkFileView 运行在 Docker 容器中，而后端运行在宿主机 Windows 上，则容器里的 `localhost` 指的是 kkFileView 容器自己，不是宿主机。此时可配置为：

```env
VITE_BACKEND_PUBLIC_BASE_URL=http://host.docker.internal:8080
```

也可以使用宿主机局域网 IP：

```env
VITE_BACKEND_PUBLIC_BASE_URL=http://192.168.1.10:8080
```

生产环境示例：

```env
VITE_BACKEND_PUBLIC_BASE_URL=https://api.example.com
```

## 5. 本地开发推荐配置

如果：

- 前端 Vite 运行在 `http://localhost:5173`
- 后端 Spring Boot 运行在 `http://localhost:8080`
- kkFileView Docker 映射到 `http://localhost:8012`

则 `frontend/.env.local` 可配置为：

```env
VITE_KKFILEVIEW_BASE_URL=http://localhost:8012
VITE_BACKEND_PUBLIC_BASE_URL=http://host.docker.internal:8080
```

如果 kkFileView 不是 Docker，而是直接运行在本机，则可以使用：

```env
VITE_KKFILEVIEW_BASE_URL=http://localhost:8012
VITE_BACKEND_PUBLIC_BASE_URL=http://localhost:8080
```

如果使用局域网 IP，则可以使用：

```env
VITE_KKFILEVIEW_BASE_URL=http://localhost:8012
VITE_BACKEND_PUBLIC_BASE_URL=http://你的电脑IP:8080
```

例如：

```env
VITE_KKFILEVIEW_BASE_URL=http://localhost:8012
VITE_BACKEND_PUBLIC_BASE_URL=http://172.20.130.92:8080
```

注意不要写成：

```env
VITE_BACKEND_PUBLIC_BASE_URL=http://http://172.20.130.92:8080
```

多写一个 `http://` 会导致 kkFileView 获取到空文件，OFD 预览可能出现空白或 `Corrupted zip` 错误。

## 6. 生产环境推荐配置

生产环境建议创建：

```text
frontend/.env.production
```

示例：

```env
VITE_KKFILEVIEW_BASE_URL=https://preview.example.com
VITE_BACKEND_PUBLIC_BASE_URL=https://api.example.com
```

然后重新构建前端：

```bash
cd frontend
npm run build
```

也可以在 CI/CD 或部署脚本中注入环境变量，而不是写入文件：

```bash
VITE_KKFILEVIEW_BASE_URL=https://preview.example.com \
VITE_BACKEND_PUBLIC_BASE_URL=https://api.example.com \
npm run build
```

Windows PowerShell 示例：

```powershell
$env:VITE_KKFILEVIEW_BASE_URL="https://preview.example.com"
$env:VITE_BACKEND_PUBLIC_BASE_URL="https://api.example.com"
npm run build
```

## 7. 工作原理

以 DOCX 预览为例：

1. 用户点击文件列表中的“查看”。
2. 前端打开 FlashBrain 的预览页面，例如：

```text
http://localhost:5173/preview/snippets/1/files/6
```

3. 预览页调用后端接口获取文件信息：

```text
GET /api/snippets/1/files/6/preview
```

4. 后端返回文件 URL，例如：

```json
{
  "originalFilename": "test.docx",
  "fileType": "DOCX",
  "url": "/uploads/ocr-images/1/test.docx"
}
```

5. 前端拼接完整文件 URL：

```text
{VITE_BACKEND_PUBLIC_BASE_URL}/uploads/ocr-images/1/test.docx
```

6. 前端将该 URL Base64 编码后传给 kkFileView：

```text
{VITE_KKFILEVIEW_BASE_URL}/onlinePreview?url={base64后的文件URL}
```

7. kkFileView 访问该文件 URL，并在 iframe 中渲染预览页面。

## 8. 排查方法

### 8.1 检查 kkFileView 是否可访问

浏览器访问：

```text
http://localhost:8012
```

如果打不开，说明 kkFileView 没有启动或端口映射不正确。

### 8.2 检查后端文件 URL 是否可访问

找到上传文件 URL，例如：

```text
/uploads/ocr-images/1/example.ofd
```

拼成完整地址：

```text
http://localhost:8080/uploads/ocr-images/1/example.ofd
```

或：

```text
http://host.docker.internal:8080/uploads/ocr-images/1/example.ofd
```

确保 kkFileView 所在环境能访问该地址。

### 8.3 OFD 空白常见原因

OFD 空白通常不是前端 iframe 问题，而是 kkFileView 没有成功拿到 OFD 文件。

常见原因：

1. `VITE_BACKEND_PUBLIC_BASE_URL` 配错。
2. 多写了 `http://`，例如 `http://http://...`。
3. kkFileView 容器无法访问后端地址。
4. 后端 `/uploads/**` 文件不可访问。
5. OFD 文件本身损坏或 kkFileView 不支持该 OFD 文件。

可在浏览器开发者工具 Network 中检查 kkFileView 的 `getCorsFile` 请求：

- 如果响应 `content-length: 0`，说明 kkFileView 没拿到真实文件。
- 如果响应有文件内容但仍空白，可能是 OFD 文件兼容性问题。

## 9. 安全提示

当前方案沿用 `/uploads/**` 公开静态资源，意味着只要知道文件 URL，就可以直接访问上传文件。

这符合当前预览需求，kkFileView 也能直接读取文件。但如果未来文件需要严格权限控制，应改为：

- 使用签名 URL；或
- 后端提供有权限校验的文件代理接口；或
- 让 kkFileView 与后端处于受控内网环境。

## 10. 总结

本地开发常用配置：

```env
VITE_KKFILEVIEW_BASE_URL=http://localhost:8012
VITE_BACKEND_PUBLIC_BASE_URL=http://host.docker.internal:8080
```

生产环境常用配置：

```env
VITE_KKFILEVIEW_BASE_URL=https://preview.example.com
VITE_BACKEND_PUBLIC_BASE_URL=https://api.example.com
```

关键判断标准：

> kkFileView 服务必须能访问 `VITE_BACKEND_PUBLIC_BASE_URL + /uploads/...` 拼出来的文件地址。
