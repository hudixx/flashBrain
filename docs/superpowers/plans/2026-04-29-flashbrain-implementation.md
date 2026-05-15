# FlashBrain 碎片知识内化系统实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 构建一个支持 OCR、双区编辑和物理归档的跨平台碎片知识管理系统。

**Architecture:** 采用“方案 A：双服务协同模式”。Vue 3 前端 + Spring Boot 业务后端 + FastAPI OCR 引擎。

**Tech Stack:** Vue 3, Vite, Element Plus, Spring Boot 3, Maven, PaddleOCR, FastAPI.

---

### Task 1: 初始化项目结构

**Files:**
- Create: pom.xml (Root)
- Create: ackend/pom.xml (Spring Boot)
- Create: rontend/package.json (Vue 3)
- Create: ocr-server/main.py (FastAPI)

- [ ] **Step 1: 创建多模块工程目录**
- [ ] **Step 2: 初始化 Spring Boot 项目依赖**
- [ ] **Step 3: 初始化 Vue 3 + Vite 项目**
- [ ] **Step 4: 提交**

### Task 2: 后端 - 科目与碎片实体设计及 CRUD

**Files:**
- Modify: ackend/src/main/java/com/flashbrain/entity/Subject.java
- Modify: ackend/src/main/java/com/flashbrain/entity/Snippet.java
- Test: ackend/src/test/java/com/flashbrain/repository/SnippetRepositoryTest.java

- [ ] **Step 1: 编写 Snippet 实体，包含 ocrText 和 noteContent 字段**
- [ ] **Step 2: 实现 Subject 树状结构支持**
- [ ] **Step 3: 编写测试验证 CRUD 逻辑**
- [ ] **Step 4: 提交**

### Task 3: OCR 服务 - 封装 PaddleOCR

**Files:**
- Create: ocr-server/requirements.txt
- Create: ocr-server/api.py

- [ ] **Step 1: 安装 paddlepaddle 和 paddleocr**
- [ ] **Step 2: 编写 FastAPI 接口接收图片并返回清洗后的文本**
- [ ] **Step 3: 手动验证 OCR 接口响应**
- [ ] **Step 4: 提交**

### Task 4: 前端 - 三栏布局与双区编辑器

**Files:**
- Create: rontend/src/layout/MainLayout.vue
- Create: rontend/src/components/DoubleEditor.vue

- [ ] **Step 1: 实现 280px | 1fr | 450px 的标准三栏布局**
- [ ] **Step 2: 实现 Markdown 实时预览与独立保存按钮**
- [ ] **Step 3: 提交**

### Task 5: 核心逻辑 - 排序与搬家

**Files:**
- Modify: ackend/src/main/java/com/flashbrain/service/SnippetService.java

- [ ] **Step 1: 实现 sort_order 浮点数计算逻辑**
- [ ] **Step 2: 实现物理搬家（修改 subject_id）逻辑**
- [ ] **Step 3: 提交**
