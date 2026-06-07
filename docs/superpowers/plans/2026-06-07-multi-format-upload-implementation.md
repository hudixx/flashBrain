# 多格式文件上传与文本读取实施计划

## Context

当前系统只支持在 `DoubleEditor.vue` 中上传图片到 `/api/ocr/upload`，后端保存到 `snippet_image` 并异步调用 OCR 服务，最终把识别文本写入 `snippet.ocr_text`。用户希望优化为支持图片、txt、docx、doc、pdf、ofd 等常用文件：图片继续沿用现有 OCR；其他格式直接读取内容并写入 OCR 原文区。

已确认的设计选择：

- OFD：第一版必须真实提取文本，不走 OCR 兜底。
- 扫描版 PDF：文本层为空时自动转图片并走 OCR。
- 文档解析结果：写入当前片段 OCR 原文区；如果已有内容或未保存编辑，需要前端确认后替换。
- 上传数量与 `.doc`：第一版一次只上传一个文件；`.doc` 用 Apache POI 直接解析，失败则明确提示。

## 推荐方案

实现统一的“上传文件并提取文本”能力，在保持现有 `/api/ocr/upload?snippetId=...` 入口可用的基础上，把后端逻辑从“图片 OCR 专用”升级为“按文件类型分发处理”：

- 图片：保存图片记录，继续异步调用现有 FastAPI OCR 服务。
- txt：后端直接解码读取文本，优先 UTF-8，失败尝试 GB18030。
- docx/doc：使用 Apache POI 提取文本。
- pdf：优先 PDFBox 读取文本层；若无文本层，使用 PDFBox 渲染页面为图片后逐页调用现有 OCR 服务。
- ofd：引入 OFD 文本提取依赖，提取有文本层的 OFD 内容；无法提取时返回明确错误。

解析成功后，将文本替换写入 `snippet.ocr_text`，并返回 JSON 给前端，使编辑器可立即显示最新文本。图片 OCR 因为是异步完成，需要增加防覆盖保护，避免后台 OCR 覆盖用户手动编辑。

## 后端实施步骤

### 1. 新增依赖

修改 `backend/pom.xml`：

- Apache POI：`poi`、`poi-ooxml`、必要时 `poi-scratchpad`，用于 docx/doc。
- PDFBox：`pdfbox`，用于 PDF 文本层提取和页面渲染。
- OFD：选用 Java 11 可用、Maven 可获取且许可证合适的 OFD 文本提取库（实施前先确认可用 API；若库无法可靠提取文本，返回明确错误而不是静默成功）。

不优先引入完整 Tika parser 包，避免依赖面和解析边界过大。

### 2. DTO 与类型定义

新增代表性文件：

- `backend/src/main/java/com/flashbrain/dto/UploadResult.java`
- `backend/src/main/java/com/flashbrain/service/FileKind.java`
- `backend/src/main/java/com/flashbrain/service/ExtractStatus.java`

`UploadResult` 返回字段建议包括：

- `status`：`OCR_PROCESSING` / `TEXT_EXTRACTED` / `FAILED`
- `snippetId`
- `fileType`
- `text`
- `message`

### 3. 文件类型分发与提取服务

新增服务：

- `backend/src/main/java/com/flashbrain/service/FileTextExtractor.java`

职责：

- 根据扩展名、content type、文件头识别文件类型。
- 图片返回“应走 OCR”的类型结果，不直接解析文本。
- txt/docx/doc/pdf/ofd 调用对应解析方法。
- 对空文件、超大文件、未知格式、加密/损坏文档抛出语义清晰的异常。

PDF 处理规则：

- 先用 PDFBox 提取文本层。
- 若文本为空，限制页数与文件大小后，把页面渲染为图片 byte[]，逐页调用现有 `OcrService` 的 OCR 请求能力并合并文本。
- 多页 OCR 输出按页加分隔，避免文本混在一起。

OFD 处理规则：

- 使用选定 OFD 库提取文本层。
- 加密、图片化、复杂签章或无文本层时返回“无法提取 OFD 文本”。
- 不对 OFD 走图片 OCR，符合“其他格式直接读取内容”的约束。

### 4. 改造 `OcrService`

修改 `backend/src/main/java/com/flashbrain/service/OcrService.java`：

- 保留现有图片 OCR multipart 转发逻辑，抽出可复用方法：`recognizeText(byte[] fileBytes, String filename)`，供 PDF 扫描页 OCR 复用。
- 新增统一入口，例如 `uploadAndExtract(MultipartFile file, Long snippetId, Long userId, Long expectedOcrTextVersion)`。
- 图片分支继续保存图片并异步 OCR。
- 非图片分支同步解析文本，成功后写入 `snippet.ocr_text` 并返回文本。
- 异步 OCR 完成时不再无条件调用 `snippetService.updateOcr(...)`，而是调用带版本校验的更新方法。

现有可复用逻辑：

- `SnippetImageService.saveImage(...)` 的文件名清洗、UUID 存储名、按 snippet 分目录保存。
- `OcrService` 中 `ByteArrayResource` 覆盖 `getFilename()` 的 multipart 转发方式。

### 5. 防止 OCR/解析覆盖用户编辑

修改：

- `backend/src/main/java/com/flashbrain/entity/Snippet.java`
- `backend/src/main/java/com/flashbrain/service/SnippetService.java`
- `backend/src/main/java/com/flashbrain/config/DatabaseSchemaInitializer.java`

增加 `snippet.ocr_text_version BIGINT DEFAULT 0`：

- 手动保存 `ocrText` 时版本加 1。
- 上传文档替换 `ocrText` 时要求前端传当前版本，版本匹配才更新；不匹配返回 409。
- 图片异步 OCR 启动时记录当前版本，OCR 完成后版本仍匹配才写入；若不匹配，记录日志并跳过覆盖。

### 6. Controller 返回 JSON 与错误码

修改 `backend/src/main/java/com/flashbrain/controller/OcrController.java`：

- 返回 `ResponseEntity<UploadResult>`，不再返回纯字符串。
- 对空文件、不支持格式返回 400。
- 对解析失败（例如损坏 doc、无法提取 OFD）返回 422。
- 对版本冲突返回 409。
- 对系统 IO/OCR 服务调用失败返回 500。

### 7. 图片记录与附件记录

第一版优先保持现有 `snippet_image` 用于图片展示，避免破坏现有“查看图片”功能：

- 图片上传继续调用 `SnippetImageService.saveImage(...)`，仍保存到 `uploads/ocr-images/{snippetId}`。
- 非图片第一版不展示附件列表，只读取文本并写入 OCR 原文区。

后续如果需要查看/下载所有上传文档，再新增 `snippet_attachment` 表和附件列表 UI；本次先不扩大表结构，降低范围。

### 8. 删除逻辑

现有 `SnippetService.deleteSnippet(...)` 会调用 `SnippetImageService.deleteImagesBySnippetId(...)`，继续保留。若 PDF 扫描页 OCR 产生临时图片，必须使用临时目录并在解析结束后清理，不写入 uploads。

## 前端实施步骤

### 1. 改造上传控件

修改 `frontend/src/components/DoubleEditor.vue`：

- `handleImageChange` 重命名为 `handleFileChange`。
- `el-upload` 增加 `accept="image/*,.txt,.docx,.doc,.pdf,.ofd"`。
- 增加 `uploading` 状态，上传中禁用按钮。
- 按后端返回的 `status` 展示不同提示：
  - `OCR_PROCESSING`：提示图片 OCR 后台识别。
  - `TEXT_EXTRACTED`：把 `res.data.text` 写入本地 `ocrText`，展开 OCR 原文区。
  - `FAILED` 或错误响应：显示后端 message。

### 2. 替换前确认

在 `DoubleEditor.vue` 中新增 dirty 判断：

- 若上传的是非图片，且当前 `ocrText` 不为空或与 `props.snippet.ocrText` 不一致，则弹窗确认“上传解析结果将替换当前 OCR 原文”。
- 用户取消则不上传。
- 用户确认后上传，并把当前 `props.snippet.ocrTextVersion` 一并传给后端。

### 3. 保存与版本同步

- 保存详情成功后，使用后端返回的最新 snippet 或触发 `emit('updated')` 刷新列表。
- 文档解析成功后，立即更新本地 `ocrText`，并触发父级刷新。
- 图片上传成功不修改本地 `ocrText`，避免误导用户。

### 4. 刷新当前片段引用

修改 `frontend/src/layout/MainLayout.vue` 的 `fetchSnippets`：

- 拉取新列表后，如果当前片段仍存在，用新列表中的同 id 对象替换 `currentSnippet`。
- 如果当前片段已不存在，则保持现有逻辑清空并退出全屏。

这样后端异步 OCR 或文档解析后，刷新列表能让编辑器拿到最新数据。

## 测试计划

### 后端测试

新增或扩展：

- `backend/src/test/java/com/flashbrain/service/FileTextExtractorTest.java`
  - txt UTF-8 / GB18030。
  - docx 文本提取。
  - doc POI 分支。
  - PDF 文本层提取。
  - PDF 文本层为空时触发 OCR 分支（mock `OcrService`）。
  - OFD 成功提取与失败提示。

- `backend/src/test/java/com/flashbrain/service/OcrServiceTest.java`
  - 图片上传返回 OCR_PROCESSING。
  - 非图片解析成功写入 OCR 文本。
  - OCR 异步版本匹配才更新。
  - 版本冲突不覆盖用户编辑。

- `backend/src/test/java/com/flashbrain/service/SnippetServiceTest.java`
  - 保存 OCR 文本时版本递增。
  - 文档解析替换时版本匹配成功。
  - 版本不匹配返回冲突/失败。

- `backend/src/test/java/com/flashbrain/config/DatabaseSchemaInitializerTest.java`
  - `snippet` 表新增 `ocr_text_version` 字段。

### 前端验证

当前前端没有测试脚本，先使用构建验证：

- `cd frontend && npm run build`

手工验证：

1. 创建片段，上传图片，确认仍提示 OCR 后台识别，图片可在“查看图片”看到。
2. 上传 txt，确认弹窗后内容进入 OCR 原文区。
3. 上传 docx/doc，确认文本进入 OCR 原文区。
4. 上传带文本层 PDF，确认直接读取文本。
5. 上传扫描版 PDF，确认逐页 OCR 后合并结果。
6. 上传 OFD，确认能提取文本；无法提取时有明确错误。
7. OCR 未完成期间手动编辑并保存，确认后台 OCR 不覆盖用户编辑。

### 命令验证

实施完成后运行：

```bash
mvn -pl backend test
cd frontend && npm run build
```

如依赖解析异常，再运行：

```bash
mvn -pl backend dependency:tree
```

## 关键文件

预计主要修改：

- `backend/pom.xml`
- `backend/src/main/java/com/flashbrain/controller/OcrController.java`
- `backend/src/main/java/com/flashbrain/service/OcrService.java`
- `backend/src/main/java/com/flashbrain/service/SnippetService.java`
- `backend/src/main/java/com/flashbrain/service/SnippetImageService.java`（复用图片保存逻辑，必要时小幅调整）
- `backend/src/main/java/com/flashbrain/service/FileTextExtractor.java`（新增）
- `backend/src/main/java/com/flashbrain/dto/UploadResult.java`（新增）
- `backend/src/main/java/com/flashbrain/entity/Snippet.java`
- `backend/src/main/java/com/flashbrain/config/DatabaseSchemaInitializer.java`
- `frontend/src/components/DoubleEditor.vue`
- `frontend/src/layout/MainLayout.vue`

## 注意事项

- 扫描版 PDF 自动 OCR 会消耗较多 CPU/内存，必须限制页数、文件大小和输出文本长度。
- OFD 文本提取依赖存在兼容性风险，实施时要先用真实库 API 做小范围验证。
- `.doc` 是旧格式，POI 不能保证所有文件成功解析；失败时应明确提示。
- 不要只依赖前端 `accept`，后端必须校验文件类型。
- 异步 OCR 必须通过 `ocr_text_version` 防止覆盖用户手动保存的内容。
