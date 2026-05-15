# FlashBrain 碎片知识内化系统 - 详细设计文档

**日期：** 2026-04-29
**技术栈：** Vue 3 (Vite) + Spring Boot 3 + Python (FastAPI/PaddleOCR)

## 1. 系统架构 (Architecture)
采用“方案 A：双服务协同模式”。
- **Frontend (Vue 3)**: 使用 TypeScript + Element Plus。负责 PC/移动端自适应布局、Markdown 渲染、手势交互。
- **Primary Backend (Spring Boot)**: 核心业务逻辑、数据库持久化（推荐使用 H2 或 MySQL）、图片本地文件系统存储映射。
- **OCR Engine (FastAPI)**: 封装 PaddleOCR，提供 POST /ocr 接口，模型驻留内存以实现快速响应。

## 2. 数据模型 (Data Model)
- **Subject (科目)**: id, name, parent_id (支持归档子科目), icon, is_deleted.
- **Snippet (碎片)**: 
    - id, subject_id, title, image_path.
    - ocr_text (原文区) 与 note_content (笔记区) 物理分离，支持独立保存。
    - sort_order (Double): 支持 Lexical Ordering 拖拽排序。
    - is_mastered (Boolean): 掌握状态。

## 3. 核心功能逻辑
- **局部置顶 (Lexical Ordering)**: 插入排序逻辑 new_order = (prev_order + next_order) / 2。
- **搜索穿透**: 搜索请求将穿透 UI 过滤器，检索包含已归档和已掌握在内的全量数据。
- **物理搬家**: 通过更新 snippet.subject_id 实现从主科目流向归档目录的物理迁移。
- **双区独立保存**: 前端提供两个独立的保存按钮/触发器，后端提供对应的局部更新 API。

## 4. 视觉规范
- **布局**: 标准三栏布局（左侧导航 280px，中间流 1fr，右侧编辑 450px）。
- **交互反馈**: 置顶卡片背景色改为 #E6F7FF (淡蓝)，已掌握卡片置为灰度并应用 0.5 不透明度。

---
**审批状态：等待用户评审**
