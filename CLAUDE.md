# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

FlashBrain 是一个“碎片知识内化系统”，以科目（Subject）组织知识片段（Snippet），支持 OCR 原文区与个人 Markdown 笔记区的双区编辑。产品与交互要求主要记录在：

- `FlashBrain碎片知识内化系统 - 需求规格说明书 (SRS).md`
- `交互说明文档.md`
- `docs/superpowers/specs/` 与 `docs/superpowers/plans/` 中的历史规格和实施计划

## 常用命令

### 后端（Spring Boot / Maven）

```bash
# 在仓库根目录运行全部 Maven 测试
mvn test

# 只运行 backend 模块测试
mvn -pl backend test

# 运行单个测试类
mvn -pl backend -Dtest=SnippetRepositoryTest test

# 运行单个测试方法
mvn -pl backend -Dtest=SnippetRepositoryTest#shouldSaveAndFindBySubjectId test

# 启动后端服务（默认 Spring Boot 端口 8080）
mvn -pl backend spring-boot:run
```

### 前端（Vue 3 / Vite）

```bash
cd frontend

# 安装依赖（仓库使用 package-lock.json）
npm install

# 启动开发服务器
npm run dev

# 类型检查并构建生产包
npm run build

# 预览构建产物
npm run preview
```

当前 `frontend/package.json` 未定义 lint 或测试脚本；不要假设存在 `npm test` / `npm run lint`。

### OCR 服务（FastAPI / PaddleOCR）

```bash
# 建议在 ocr-server 目录准备 Python 环境
cd ocr-server
pip install -r requirements.txt

# 启动 OCR 服务，固定监听 8093 端口
python main.py
```

`ocr-server/main.py` 启动时会检查 8093 端口：若占用者是旧的 `main.py` OCR 进程则尝试终止并接管；若是其他进程则退出报错。

## 架构与数据流

### 三段式本地开发架构

- `frontend/`：Vue 3 + Vite + Element Plus 单页应用。
- `backend/`：Spring Boot 2.7（Java 11）REST API，使用 Spring Data JPA。
- `ocr-server/`：独立 FastAPI 服务，封装 PaddleOCR；后端通过 HTTP multipart 请求代理 OCR。

前端开发服务器通过 `frontend/vite.config.ts` 将 `/api` 代理到 `http://localhost:8080`。注意：`MainLayout.vue` 中部分请求仍直接写为 `http://localhost:8080/api/...`，而 `DoubleEditor.vue` 使用相对 `/api/...`。

### 后端结构

后端入口是 `backend/src/main/java/com/flashbrain/FlashBrainApplication.java`。

主要领域模型：

- `Subject`：科目/目录节点，字段包括 `name`、`parentId`、`icon`、`isDeleted`。
- `Snippet`：知识片段，字段包括 `subjectId`、`title`、`imagePath`、`ocrText`、`noteContent`、`sortOrder`、`isPinned`、`isMastered`。

主要 API：

- `SubjectController` 暴露 `/api/subjects`：目前支持查询全部与创建。
- `SnippetController` 暴露 `/api/snippets`：按科目查询、创建、删除、更新 OCR/笔记、切换置顶/掌握、移动排序、归档到新科目。
- `OcrController` 暴露 `/api/ocr/upload`：接收图片并调用 `OcrService` 转发到 OCR 服务。

关键业务逻辑集中在 `SnippetService`：

- 手动排序使用浮点 `sortOrder`：插入两个片段之间时计算 `(prevOrder + nextOrder) / 2`。
- 标记掌握时会自动取消置顶，符合 SRS 的“掌握后取消置顶”要求。
- 归档/搬家当前实现为修改 `subjectId`，并将 `sortOrder` 设为 `0.0`。

`backend/src/main/resources/application.yml` 使用内存 H2 数据库 `jdbc:h2:mem:flashbrain`，H2 console 已启用。OCR 地址默认是 `http://localhost:8093/ocr`，超时时间在 `ocr.timeout.*` 下配置。

### 前端结构

`frontend/src/main.ts` 注册 Vue、Pinia、Vue Router 和 Element Plus。当前路由只配置 `/` 到 `HomeView.vue`，但 `App.vue` 实际直接渲染 `MainLayout` 并通过插槽挂载 `DoubleEditor`。

核心 UI：

- `layout/MainLayout.vue`：三栏布局。左侧科目列表，中间片段流，右侧编辑器插槽；负责加载科目、按科目加载片段、创建科目、创建片段，以及“显示已掌握内容”的本地过滤。
- `components/DoubleEditor.vue`：右侧双区编辑器。处理图片上传 OCR、保存 OCR 原文、保存个人笔记、切换置顶/掌握、删除片段，并用 `markdown-it` 渲染笔记预览。

前端当前没有集中封装 API client；axios 调用直接写在组件中。

### OCR 服务结构

`ocr-server/main.py` 在模块加载时初始化 `PaddleOCR(lang="ch", device="cpu", enable_mkldnn=True, cpu_threads=20, use_angle_cls=False)`。`POST /ocr` 接收上传图片，使用 OpenCV 解码、限制最大边为 1280、加白色边距，再调用 PaddleOCR 并兼容解析 PaddleOCR 2.x/3.x 返回格式。

## 当前测试覆盖

后端已有两个 `@DataJpaTest` 仓储测试：

- `SnippetRepositoryTest`
- `SubjectRepositoryTest`

前端和 OCR 服务目前未配置自动化测试命令。新增前端/OCR 测试前，先补充相应脚本或测试运行方式。
