# 后端中转 OCR 识别实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将前端直接调用 OCR 服务的方式改为由 Spring Boot 后端中转，以增强系统架构。

**Architecture:** 方案 A（纯中转代理模式）。后端 Controller 接收文件，Service 使用 RestTemplate 转发给 FastAPI OCR 服务，最后将识别文字返回前端。

**Tech Stack:** Spring Boot 2.7, RestTemplate, MultipartFile, Axios.

---

### Task 1: 后端 - 环境配置

**Files:**
- Modify: `backend/src/main/resources/application.properties`

- [ ] **Step 1: 添加 OCR 服务相关配置**

```properties
# OCR 服务配置
ocr.server.url=http://localhost:8001/ocr
ocr.timeout.connect=5000
ocr.timeout.read=15000
```

- [ ] **Step 2: 验证配置项已保存**

---

### Task 2: 后端 - RestTemplate 基础设施

**Files:**
- Create: `backend/src/main/java/com/flashbrain/config/RestTemplateConfig.java`

- [ ] **Step 1: 创建配置类并配置具备超时控制的 RestTemplate**

```java
package com.flashbrain.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Value("${ocr.timeout.connect:5000}")
    private int connectTimeout;

    @Value("${ocr.timeout.read:15000}")
    private int readTimeout;

    @Bean
    public RestTemplate ocrRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout))
                .build();
    }
}
```

- [ ] **Step 2: 确保代码无编译错误**

---

### Task 3: 后端 - 实现 OcrService

**Files:**
- Create: `backend/src/main/java/com/flashbrain/service/OcrService.java`

- [ ] **Step 1: 编写业务逻辑，实现文件透传与结果解析**

```java
package com.flashbrain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@Slf4j
public class OcrService {

    @Autowired
    private RestTemplate ocrRestTemplate;

    @Value("${ocr.server.url}")
    private String ocrServerUrl;

    public String recognizeText(MultipartFile file) {
        log.info("Sending OCR request for file: {}", file.getOriginalFilename());

        // 构造 Multipart 请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = ocrRestTemplate.postForEntity(ocrServerUrl, requestEntity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("text");
            } else {
                throw new RuntimeException("OCR service returned error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to call OCR service", e);
            throw new RuntimeException("OCR process failed: " + e.getMessage());
        }
    }
}
```

---

### Task 4: 后端 - 实现 OcrController

**Files:**
- Create: `backend/src/main/java/com/flashbrain/controller/OcrController.java`

- [ ] **Step 1: 创建控制器并暴露上传接口**

```java
package com.flashbrain.controller;

import com.flashbrain.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ocr")
@Slf4j
public class OcrController {

    @Autowired
    private OcrService ocrService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadAndRecognize(@RequestParam("file") MultipartFile file) {
        log.info("Received OCR upload request for: {}", file.getOriginalFilename());
        try {
            String text = ocrService.recognizeText(file);
            return ResponseEntity.ok(text);
        } catch (Exception e) {
            log.error("OCR upload failed", e);
            return ResponseEntity.internalServerError().body("OCR 识别失败: " + e.getMessage());
        }
    }
}
```

---

### Task 5: 前端 - 修改调用逻辑

**Files:**
- Modify: `frontend/src/components/DoubleEditor.vue`

- [ ] **Step 1: 将直接调用 Python 改为调用后端中转接口**

```javascript
// 修改前
// const res = await axios.post('http://localhost:8001/ocr', formData)

// 修改后
const res = await axios.post('/api/ocr/upload', formData)
// 适配返回格式：后端直接返回 String，而 FastAPI 原本返回的是 {text: "..."}
const ocrResultText = res.data; 
```

---

### Task 6: 最终验证

- [ ] **Step 1: 编译后端并启动**
- [ ] **Step 2: 确保 Python OCR 服务已在 8001 启动**
- [ ] **Step 3: 在前端上传图片，确认 OCR 结果能够正常回显在编辑器左侧**
- [ ] **Step 4: 检查后端日志，确认请求是否经过了 OcrController**
