# 后端中转 OCR 识别设计文档

> **状态**：待评审
> **日期**：2026-05-15
> **背景**：当前系统由前端直接调用 Python OCR 服务。为了提高系统架构的安全性、可维护性以及未来扩展性（如统一鉴权、日志记录），现将 OCR 识别流程改为由后端中转。

## 1. 架构变更

### 变更前
`前端 (Vue 3)` --(HTTP POST:8001)--> `OCR 服务 (FastAPI)`

### 变更后
`前端 (Vue 3)` --(HTTP POST:8080)--> `后端 (Spring Boot)` --(HTTP POST:8001)--> `OCR 服务 (FastAPI)`

---

## 2. 后端实现 (Spring Boot)

### 2.1 配置管理
在 `backend/src/main/resources/application.properties` 中添加 OCR 服务配置：
```properties
# OCR 服务地址
ocr.server.url=http://localhost:8001/ocr
# OCR 请求超时设置 (毫秒)
ocr.timeout.connect=5000
ocr.timeout.read=15000
```

### 2.2 配置类 (RestTemplate)
创建 `com.flashbrain.config.RestTemplateConfig`，配置具备超时控制的 `RestTemplate`。

### 2.3 控制层 (OcrController)
- **路径**：`POST /api/ocr/upload`
- **参数**：`MultipartFile file`
- **返回**：`String` (识别出的纯文本)
- **说明**：
    - 仅负责接收文件并透传。
    - 捕获异常并返回统一的错误状态。

### 2.4 服务层 (OcrService)
- **功能**：
    - 使用 `RestTemplate` 构造 `Multipart` 请求。
    - 将图片字节流转发给 FastAPI 服务的 `/ocr` 接口。
    - 接收 FastAPI 返回的 JSON（形如 `{"text": "...", "status": "success"}`）。
    - 提取并返回 `text` 字段。

---

## 3. 前端实现 (Vue 3)

### 3.1 修改 `DoubleEditor.vue`
- 将原有的 `axios.post('http://localhost:8001/ocr', ...)` 修改为：
  `axios.post('/api/ocr/upload', ...)`。
- 注意：由于前端已配置 Vite 代理，后端接口应走代理路径 `/api`。

---

## 4. 异常处理

| 场景 | 后端表现 | 建议前端反馈 |
| :--- | :--- | :--- |
| OCR 服务未启动 | 返回 `503 Service Unavailable` | 提示“OCR 服务暂时不可用” |
| OCR 服务识别失败 | 返回 `500 Internal Server Error` | 提示“图片识别失败，请重试” |
| 上传非图片文件 | 返回 `400 Bad Request` | 提示“不支持的文件格式” |
| 网络超时 | 返回 `504 Gateway Timeout` | 提示“识别超时，请检查服务状态” |

---

## 5. 自我评审 (Self-Review)

- **占位符检查**：无 TBD 或 TODO。
- **内部一致性**：架构图、配置和代码逻辑匹配。
- **范围检查**：专注于中转逻辑，不涉及不必要的重构。
- **模糊性检查**：明确了请求路径、参数类型和返回格式。
