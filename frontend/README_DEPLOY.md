# 阿里云服务器前端部署及 Nginx 配置指南

本文档介绍如何将打包后的前端项目部署到阿里云服务器上，并使用 Nginx 将前端运行在 `8010` 端口，同时通过反向代理正确访问到运行在 `8011` 端口的后端服务。

---

## 1. 阿里云安全组规则配置

在开始部署之前，请确保您的阿里云服务器已满足以下条件：
* 进入阿里云 ECS 的“安全组配置”，在“入方向”添加规则：
  - **协议类型**：自定义 TCP
  - **端口范围**：`8010`（前端访问端口）
  - **授权对象**：`0.0.0.0/0`（允许所有人访问）
* *(注：后端的 `8011` 端口由 Nginx 在服务器内网进行转发，**不需要**在阿里云安全组中对公网开放 `8011`，这更有利于保护后端服务的安全性。)*

---

## 2. 前端打包与上传

1. **打包前端项目**：
   在您的本地开发环境下，进入 `frontend` 目录，执行打包命令：
   ```bash
   npm run build
   ```
   打包完成后，会在 `frontend` 目录下生成一个 `dist` 文件夹，里面包含了所有的静态 HTML、CSS 和 JS 文件。

2. **上传到阿里云服务器**：
   将 `dist` 文件夹上传到阿里云服务器上的指定目录下（例如：`/opt/flashbrain/frontend/`）。
   确保上传后的完整路径为：`/opt/flashbrain/frontend/dist`。

---

## 3. Nginx 配置文件编写

在您的阿里云服务器上，编辑 Nginx 的配置文件（通常位于 `/etc/nginx/nginx.conf` 或 `/etc/nginx/conf.d/` 目录下）。

在 `http` 块中添加（或修改）以下 `server` 模块来监听 `8010` 端口：

```nginx
server {
    listen       8010;
    server_name  localhost; # 或者您的阿里云公网 IP/域名

    # 限制上传文件大小（如上传大图做 OCR），防止 Nginx 报 413 Payload Too Large 错误
    client_max_body_size 50m;

    # 1. 前端静态资源托管
    location / {
        root   /opt/flashbrain/frontend/dist; # 刚刚上传的前端打包产物 dist 目录路径
        index  index.html index.htm;
        
        # 兼容单页应用 (SPA) 的 History 路由模式，防止刷新页面时出现 404
        try_files $uri $uri/ /index.html;
    }

    # 2. 后端 API 反向代理
    # 浏览器请求 http://IP:8010/api/... 将被转发到 http://127.0.0.1:8011/api/...
    location /api {
        proxy_pass http://127.0.0.1:8011; # 后端的启动端口 8011
        
        # 传递客户端真实 IP 及 Header 信息
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 3. 后端静态图片资源反向代理
    # 浏览器请求 http://IP:8010/uploads/... 将被从 Nginx 转发到 http://127.0.0.1:8011/uploads/...
    location /uploads {
        proxy_pass http://127.0.0.1:8011; # 后端的启动端口 8011
        
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 错误页面配置（可选）
    error_page   500 502 503 504  /50x.html;
    location = /50x.html {
        root   /usr/share/nginx/html;
    }
}
```

---

## 4. 启动与重载 Nginx

在阿里云服务器上执行以下命令：

1. **检查 Nginx 配置语法是否正确**：
   ```bash
   nginx -t
   ```
   *(如果输出中有 `syntax is ok` 和 `test is successful` 字样，说明配置无误。)*

2. **重新加载 Nginx 配置**：
   ```bash
   nginx -s reload
   ```
   或者重启 Nginx 服务：
   ```bash
   systemctl restart nginx
   ```

---

## 5. 部署后的网络请求流程

在完成上述部署后，您的应用运行流程如下：
1. 用户在浏览器输入 `http://您的阿里云IP:8010` 访问前端网页。
2. 前端页面加载完成后，发起网络请求，其 `baseURL` 自动拼接为 `http://您的阿里云IP:8010/api/...`。
3. 该请求到达阿里云服务器的 `8010` 端口，被 Nginx 拦截。
4. Nginx 匹配到 `/api` 路径，并在内网转发到运行在本地的后端服务 `http://127.0.0.1:8011/api/...`。
5. 后端处理完成后返回数据，由 Nginx 再原路返回给浏览器。

这个方案不仅解决了跨域问题，还避免了对外直接暴露后端的 `8011` 端口，是一种非常安全和规范的生产环境部署方案。
