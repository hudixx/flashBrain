# MySQL 与 MyBatis-Plus 持久化改造设计

日期：2026-06-06

## 背景

FlashBrain 后端当前使用 Spring Boot 2.7、Spring Data JPA 和内存 H2 数据库。主要业务实体包括 `Subject`、`Snippet`、`SnippetImage`，Repository 通过 `JpaRepository` 提供 CRUD 能力。用户希望将数据存储切换为 MySQL，并使用 MyBatis-Plus 作为持久层框架。

同时，用户希望在新增实体类或字段后，应用能够自动检查数据库中是否存在对应表或字段；如果不存在，则自动创建。经确认，本次采用开发期友好的保守自动同步策略：启动时只创建缺失表和缺失字段，不删除已有字段，不自动修改已有字段类型、长度或默认值。数据库按空库或可重建处理。

## 目标

- 将后端持久化框架从 Spring Data JPA 迁移到 MyBatis-Plus。
- 将默认数据库从 H2 内存库切换为 MySQL。
- 使用本地配置文件保存真实数据库连接密码，避免把密码提交到仓库。
- 保持现有 REST API 路径、请求体和核心业务行为不变，尽量避免前端改动。
- 应用启动时自动检查并创建缺失表。
- 应用启动时自动检查并补充缺失字段。
- 自动同步过程不删除已有字段、不修改已有字段类型、不清空数据。
- 调整测试以覆盖 MyBatis-Plus 迁移后的核心业务逻辑。

## 非目标

- 不实现生产级数据库迁移版本管理系统，例如 Flyway 或 Liquibase。
- 不实现字段删除、字段重命名、字段类型变更、索引变更的全自动迁移。
- 不迁移旧 H2 数据到 MySQL。
- 不改变前端页面交互、OCR 服务接口或 REST API 语义。
- 不在仓库提交真实数据库密码。

## 推荐方案

采用 **MyBatis-Plus + 自定义启动同步器**。

后端依赖改为 MyBatis-Plus 和 MySQL Driver；实体类使用 MyBatis-Plus 注解描述表名、主键和字段名；Repository 包迁移为 Mapper 包；Service 层保留现有业务方法语义，只替换底层数据访问调用。

新增 `DatabaseSchemaInitializer` 在应用启动时执行，按照显式维护的表结构定义检查 MySQL：缺表则创建完整表，缺字段则执行 `ALTER TABLE ADD COLUMN`。如果发现已有字段类型与定义不一致，仅输出警告日志，不自动修改。

选择该方案的原因：它满足用户要求的“新增实体/字段后自动补表补字段”，同时比混用 Hibernate 自动 DDL 更可控，也比 Flyway 更贴近用户期望的自动化体验。

## 配置设计

### Maven 依赖

`backend/pom.xml` 调整方向：

- 移除 `spring-boot-starter-data-jpa`。
- 移除运行时 H2 依赖。
- 新增 `mybatis-plus-boot-starter`。
- 新增 `mysql-connector-j`。
- 保留 `spring-boot-starter-web`、`lombok`、`spring-boot-starter-test`。

### 应用配置

`backend/src/main/resources/application.yml` 只保存非敏感基础配置，例如：

- 应用名。
- MyBatis-Plus 驼峰映射配置。
- OCR 服务配置。
- 可选的本地配置导入声明。

真实 MySQL 地址、端口、库名、用户名、密码放入 `backend/src/main/resources/application-local.yml` 或同等本地配置文件，并加入 `.gitignore`，避免提交。该本地文件可使用用户提供的数据库连接信息，但不会进入版本库。

## 实体与表结构设计

Java 实体保持驼峰命名，数据库字段统一使用下划线命名，并启用 MyBatis-Plus 的下划线到驼峰映射。

### `Subject`

- Java 类：`com.flashbrain.entity.Subject`
- 表名：`subject`
- 字段：
  - `id`：`BIGINT AUTO_INCREMENT PRIMARY KEY`
  - `name`：`VARCHAR(255)`
  - `parent_id`：`BIGINT`
  - `icon`：`VARCHAR(255)`
  - `is_deleted`：`TINYINT(1) DEFAULT 0`

### `Snippet`

- Java 类：`com.flashbrain.entity.Snippet`
- 表名：`snippet`
- 字段：
  - `id`：`BIGINT AUTO_INCREMENT PRIMARY KEY`
  - `subject_id`：`BIGINT`
  - `title`：`VARCHAR(255)`
  - `image_path`：`VARCHAR(255)`
  - `ocr_text`：`LONGTEXT`
  - `note_content`：`LONGTEXT`
  - `sort_order`：`DOUBLE`
  - `is_pinned`：`TINYINT(1) DEFAULT 0`
  - `is_mastered`：`TINYINT(1) DEFAULT 0`

### `SnippetImage`

- Java 类：`com.flashbrain.entity.SnippetImage`
- 表名：`snippet_image`
- 字段：
  - `id`：`BIGINT AUTO_INCREMENT PRIMARY KEY`
  - `snippet_id`：`BIGINT`
  - `original_filename`：`VARCHAR(255)`
  - `stored_filename`：`VARCHAR(255)`
  - `url`：`VARCHAR(255)`
  - `created_at`：`DATETIME`

`SnippetImage` 原有的 `@PrePersist` 逻辑需要改为业务层或实体方法中的显式默认值处理，因为迁移到 MyBatis-Plus 后不再依赖 JPA 生命周期回调。

## Mapper 设计

新增 `com.flashbrain.mapper` 包：

- `SubjectMapper extends BaseMapper<Subject>`
- `SnippetMapper extends BaseMapper<Snippet>`
- `SnippetImageMapper extends BaseMapper<SnippetImage>`

应用入口 `FlashBrainApplication` 添加 `@MapperScan("com.flashbrain.mapper")`。

自定义查询通过 MyBatis-Plus 条件构造器优先实现：

- 按科目查询片段：`subject_id = ?`，并按 `is_pinned DESC, sort_order ASC` 排序。
- 按片段查询图片：`snippet_id = ?`，并按 `created_at ASC` 排序。

如后续查询复杂度提升，再补充 XML Mapper 或注解 SQL。

## 自动表结构同步设计

### 组件职责

新增 `DatabaseSchemaInitializer`，作为 Spring 启动组件在应用启动后执行。

它负责：

1. 维护当前应用支持的表结构定义。
2. 连接当前 MySQL 数据库。
3. 检查目标表是否存在。
4. 对不存在的表执行 `CREATE TABLE`。
5. 对已存在的表检查字段清单。
6. 对缺失字段执行 `ALTER TABLE ADD COLUMN`。
7. 对已有但类型不一致的字段输出警告日志。

### 表结构定义方式

初版采用显式定义方式，而不是完全反射扫描所有实体类并自动推断 SQL 类型。

原因：

- 字段 SQL 类型更可控，尤其是 `LONGTEXT`、`TINYINT(1)`、`DATETIME` 等需要明确表达的类型。
- 避免误把不应持久化的字段扫描成数据库列。
- 避免自动推断字段长度或默认值导致不可预期结构。

新增实体或字段时，需要同时更新实体类和同步器中的表结构定义。应用启动后，同步器会自动补齐数据库中缺失的表或字段。

### 安全边界

自动同步器明确不做以下操作：

- 不删除数据库已有字段。
- 不删除数据库已有表。
- 不修改已有字段类型、长度、默认值或是否可空。
- 不重命名字段。
- 不清空数据。
- 不自动创建或删除索引，除主键外索引后续按需设计。

如果已有字段与定义不一致，启动日志记录警告，提示开发者手动处理。

## Service 与 Controller 设计

### Controller

对外接口尽量不变：

- `SubjectController` 保持 `/api/subjects`。
- `SnippetController` 保持 `/api/snippets`。
- `OcrController` 保持 `/api/ocr/upload`。

为保持结构一致，建议新增一个薄的 `SubjectService`，让 `SubjectController` 不直接依赖 Mapper。

### `SnippetService`

保留现有业务方法语义：

- `getSnippetsBySubject(subjectId)`：使用 `SnippetMapper.selectList`，按 `is_pinned DESC, sort_order ASC` 排序。
- `createSnippet(snippet)`：如果 `sortOrder == null`，继续使用当前时间秒数作为默认排序值，然后插入。
- `updateSnippet(id, detail)`：先查询，未找到则抛出 `RuntimeException("Snippet not found")`，找到后更新标题、OCR 原文和笔记。
- `updateOcr(id, ocrText)`：只更新 OCR 原文。
- `updateNote(id, noteContent)`：只更新个人笔记。
- `togglePin(id)`：切换置顶状态。
- `toggleMastered(id)`：切换掌握状态；如果标记为掌握，则自动取消置顶。
- `moveSnippet(id, prevOrder, nextOrder)`：保持现有浮点排序算法。
- `archiveSnippet(id, newSubjectId)`：修改 `subjectId`，并将 `sortOrder` 设为 `0.0`。
- `deleteSnippet(id)`：按 id 删除。

### `SnippetImageService`

文件保存逻辑保持不变。数据库保存从 `SnippetImageRepository.save` 改为 `SnippetImageMapper.insert`。

保存前需要显式设置：

- `snippetId`
- `originalFilename`
- `storedFilename`
- `url`
- `createdAt`，如果为空则设为 `LocalDateTime.now()`

## 错误处理设计

- 数据库连接失败：应用启动失败，保留 Spring Boot 原始错误日志。
- 自动建表失败：应用启动失败，避免服务在表结构不完整时运行。
- 自动补字段失败：应用启动失败，提示具体表名、字段名和 SQL。
- 字段类型不一致：记录警告日志，不阻止启动。
- 本地配置缺失：启动时给出明确配置错误，或允许通过环境变量覆盖。
- 按 id 查询片段不存在：保持现有 `RuntimeException("Snippet not found")` 行为。

## 测试与验证设计

### 自动化测试

当前 `@DataJpaTest` 仓储测试会在迁移后失效，需要改造或替换。

建议新增或调整为：

1. `SnippetServiceTest`
   - 使用 Mockito mock `SnippetMapper`。
   - 验证创建片段时会补默认 `sortOrder`。
   - 验证标记掌握时会自动取消置顶。
   - 验证移动排序算法在三种边界下正确：无前后项、只有后项、只有前项、前后项都有。
   - 验证更新 OCR 和笔记时只写入对应字段。

2. `SnippetImageServiceTest`
   - 使用临时目录保存上传文件。
   - mock `SnippetImageMapper`。
   - 验证文件保存路径、URL 和 `createdAt` 默认值。

3. `DatabaseSchemaInitializerTest`
   - 优先测试 SQL/结构定义生成逻辑。
   - 不依赖真实公网 MySQL。
   - 对完整 MySQL DDL 行为保留为本地集成验证。

### 本地集成验证

在本地配置文件准备好数据库连接后，执行：

```bash
mvn -pl backend test
mvn -pl backend spring-boot:run
```

启动后观察日志，确认：

- `subject` 表被创建或已存在。
- `snippet` 表被创建或已存在。
- `snippet_image` 表被创建或已存在。
- 缺失字段会被补齐。

再通过现有前端或接口验证：

1. 创建科目成功并写入 MySQL。
2. 创建片段成功并写入 MySQL。
3. 查询片段时置顶优先、排序字段升序。
4. 保存 OCR 原文和个人笔记成功。
5. 切换置顶、掌握、删除片段行为保持不变。
6. 上传 OCR 图片后，图片记录写入 `snippet_image` 表。

## 实施范围

预计修改或新增：

- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/.gitignore` 或仓库根 `.gitignore`
- `backend/src/main/java/com/flashbrain/FlashBrainApplication.java`
- `backend/src/main/java/com/flashbrain/entity/Subject.java`
- `backend/src/main/java/com/flashbrain/entity/Snippet.java`
- `backend/src/main/java/com/flashbrain/entity/SnippetImage.java`
- `backend/src/main/java/com/flashbrain/mapper/SubjectMapper.java`
- `backend/src/main/java/com/flashbrain/mapper/SnippetMapper.java`
- `backend/src/main/java/com/flashbrain/mapper/SnippetImageMapper.java`
- `backend/src/main/java/com/flashbrain/service/SubjectService.java`
- `backend/src/main/java/com/flashbrain/service/SnippetService.java`
- `backend/src/main/java/com/flashbrain/service/SnippetImageService.java`
- `backend/src/main/java/com/flashbrain/config/DatabaseSchemaInitializer.java`
- 相关后端测试类

预计删除或停用：

- `backend/src/main/java/com/flashbrain/repository/SubjectRepository.java`
- `backend/src/main/java/com/flashbrain/repository/SnippetRepository.java`
- `backend/src/main/java/com/flashbrain/repository/SnippetImageRepository.java`
- JPA 专用仓储测试或注解

## 风险与缓解

- **公网 MySQL 连接不可用**：自动化测试不依赖公网 MySQL；启动验证单独执行并报告连接失败原因。
- **真实密码泄露风险**：真实密码只写本地不提交配置，并确保 `.gitignore` 覆盖。
- **字段类型不一致无法自动修复**：同步器只警告，不修改；需要人工 SQL 处理。
- **MyBatis-Plus 与 Java 11 / Spring Boot 2.7 版本兼容**：选择兼容 Spring Boot 2.x 的 MyBatis-Plus 版本。
- **实体字段新增但忘记更新同步器定义**：测试或启动验证应检查目标字段是否被纳入结构定义；后续可考虑反射增强降低遗漏概率。

## 验收标准

- 后端可以使用 MyBatis-Plus 编译通过。
- 后端不再依赖 Spring Data JPA 和 H2 作为主持久化方案。
- MySQL 连接配置从本地不提交配置读取。
- 空库启动时自动创建 `subject`、`snippet`、`snippet_image` 表。
- 已有表缺字段时，启动自动补充缺失字段。
- 已有字段类型不一致时只记录警告，不自动修改。
- 现有科目、片段、OCR 图片相关 API 行为保持兼容。
- `mvn -pl backend test` 通过，或如失败需明确说明失败原因。
