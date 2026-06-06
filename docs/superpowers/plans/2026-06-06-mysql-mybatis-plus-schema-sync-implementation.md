# MySQL MyBatis-Plus Schema Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 FlashBrain 后端从 Spring Data JPA + H2 迁移到 MySQL + MyBatis-Plus，并在应用启动时保守地自动创建缺失表和缺失字段。

**Architecture:** Controller 的 REST API 保持兼容，Service 层保留现有业务语义，底层 Repository 替换为 MyBatis-Plus Mapper。新增一个基于 `JdbcTemplate` 的启动同步器，使用显式表结构定义对 MySQL 执行“缺表建表、缺列加列”，但不删除列、不修改已有列类型。

**Tech Stack:** Java 11, Spring Boot 2.7.18, MyBatis-Plus 3.5.7, MySQL Connector/J 8.0.33, Maven, JUnit 5, Mockito, AssertJ.

---

## File Structure Map

- Modify: `backend/pom.xml` — 替换持久化依赖：移除 JPA/H2，加入 MyBatis-Plus/MySQL/JdbcTemplate 所需依赖。
- Modify: `backend/src/main/resources/application.yml` — 使用 MySQL + MyBatis-Plus 基础配置，不保存真实密码。
- Create: `backend/src/main/resources/application-local.example.yml` — 提供本地配置示例，不包含真实密码。
- Create locally only: `backend/src/main/resources/application-local.yml` — 放真实 MySQL 连接信息；该文件必须被 `.gitignore` 忽略，不提交。
- Modify: `.gitignore` — 忽略 `backend/src/main/resources/application-local.yml`。
- Modify: `backend/src/main/java/com/flashbrain/FlashBrainApplication.java` — 添加 `@MapperScan("com.flashbrain.mapper")`。
- Modify: `backend/src/main/java/com/flashbrain/entity/Subject.java` — 从 JPA 注解改为 MyBatis-Plus 注解。
- Modify: `backend/src/main/java/com/flashbrain/entity/Snippet.java` — 从 JPA 注解改为 MyBatis-Plus 注解，并标记长文本字段。
- Modify: `backend/src/main/java/com/flashbrain/entity/SnippetImage.java` — 从 JPA 注解改为 MyBatis-Plus 注解，移除 JPA 生命周期回调。
- Create: `backend/src/main/java/com/flashbrain/mapper/SubjectMapper.java` — `Subject` 的 MyBatis-Plus Mapper。
- Create: `backend/src/main/java/com/flashbrain/mapper/SnippetMapper.java` — `Snippet` 的 MyBatis-Plus Mapper。
- Create: `backend/src/main/java/com/flashbrain/mapper/SnippetImageMapper.java` — `SnippetImage` 的 MyBatis-Plus Mapper。
- Create: `backend/src/main/java/com/flashbrain/config/DatabaseSchemaInitializer.java` — MySQL 表结构启动同步器。
- Create: `backend/src/main/java/com/flashbrain/service/SubjectService.java` — 薄业务层，隔离 Controller 与 Mapper。
- Modify: `backend/src/main/java/com/flashbrain/controller/SubjectController.java` — 注入 `SubjectService`，不再直接使用 Repository。
- Modify: `backend/src/main/java/com/flashbrain/service/SnippetService.java` — 使用 `SnippetMapper` 替换 `SnippetRepository`。
- Modify: `backend/src/main/java/com/flashbrain/service/SnippetImageService.java` — 使用 `SnippetImageMapper` 替换 `SnippetImageRepository`，显式设置 `createdAt`。
- Delete: `backend/src/main/java/com/flashbrain/repository/SubjectRepository.java` — JPA Repository 不再使用。
- Delete: `backend/src/main/java/com/flashbrain/repository/SnippetRepository.java` — JPA Repository 不再使用。
- Delete: `backend/src/main/java/com/flashbrain/repository/SnippetImageRepository.java` — JPA Repository 不再使用。
- Delete: `backend/src/test/java/com/flashbrain/repository/SubjectRepositoryTest.java` — JPA `@DataJpaTest` 不再适用。
- Delete: `backend/src/test/java/com/flashbrain/repository/SnippetRepositoryTest.java` — JPA `@DataJpaTest` 不再适用。
- Create: `backend/src/test/java/com/flashbrain/config/DatabaseSchemaInitializerTest.java` — 验证显式表结构定义。
- Create: `backend/src/test/java/com/flashbrain/service/SubjectServiceTest.java` — 验证科目业务层。
- Create: `backend/src/test/java/com/flashbrain/service/SnippetServiceTest.java` — 验证片段业务语义。
- Create: `backend/src/test/java/com/flashbrain/service/SnippetImageServiceTest.java` — 验证图片保存记录逻辑。

---

### Task 1: Dependencies and Configuration

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.example.yml`
- Create locally only: `backend/src/main/resources/application-local.yml`
- Modify: `.gitignore`
- Modify: `backend/src/main/java/com/flashbrain/FlashBrainApplication.java`

- [ ] **Step 1: Update Maven dependencies**

Replace the `<dependencies>` block in `backend/pom.xml` with:

```xml
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>3.5.7</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>8.0.33</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
```

- [ ] **Step 2: Replace application configuration**

Replace `backend/src/main/resources/application.yml` with:

```yaml
spring:
  application:
    name: flashbrain-backend
  config:
    import: optional:classpath:application-local.yml
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: ${DB_URL:jdbc:mysql://localhost:3306/flashbrain?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto

# OCR 服务配置
ocr:
  server:
    url: http://localhost:8093/ocr
  timeout:
    connect: 500000
    read: 1500000
```

- [ ] **Step 3: Add a committed local configuration example**

Create `backend/src/main/resources/application-local.example.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://218.244.156.77:3306/flashbrain?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: change-me
```

- [ ] **Step 4: Add the real local configuration file without committing it**

Create `backend/src/main/resources/application-local.yml` locally with the real connection details supplied by the user:

```yaml
spring:
  datasource:
    url: jdbc:mysql://218.244.156.77:3306/flashbrain?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: <local-password>
```

Do not include this file in any commit.

- [ ] **Step 5: Ignore the real local configuration file**

Append this line to `.gitignore` if it is not already present:

```gitignore
backend/src/main/resources/application-local.yml
```

- [ ] **Step 6: Enable mapper scanning**

Replace `backend/src/main/java/com/flashbrain/FlashBrainApplication.java` with:

```java
package com.flashbrain;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.flashbrain.mapper")
public class FlashBrainApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlashBrainApplication.class, args);
    }
}
```

- [ ] **Step 7: Run compile and capture expected failure state**

Run:

```bash
mvn -pl backend test
```

Expected now: compilation fails because JPA annotations and Repository imports are still present. Continue to Task 2.

- [ ] **Step 8: Commit dependency/config changes**

Run:

```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/main/resources/application-local.example.yml .gitignore backend/src/main/java/com/flashbrain/FlashBrainApplication.java
git commit -m "build: configure mysql and mybatis-plus"
```

---

### Task 2: Entity Annotations and Mapper Interfaces

**Files:**
- Modify: `backend/src/main/java/com/flashbrain/entity/Subject.java`
- Modify: `backend/src/main/java/com/flashbrain/entity/Snippet.java`
- Modify: `backend/src/main/java/com/flashbrain/entity/SnippetImage.java`
- Create: `backend/src/main/java/com/flashbrain/mapper/SubjectMapper.java`
- Create: `backend/src/main/java/com/flashbrain/mapper/SnippetMapper.java`
- Create: `backend/src/main/java/com/flashbrain/mapper/SnippetImageMapper.java`

- [ ] **Step 1: Convert `Subject` to MyBatis-Plus**

Replace `backend/src/main/java/com/flashbrain/entity/Subject.java` with:

```java
package com.flashbrain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("subject")
@Data
@NoArgsConstructor
public class Subject {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("parent_id")
    private Long parentId;

    @TableField("icon")
    private String icon;

    @TableField("is_deleted")
    private Boolean isDeleted = false;
}
```

- [ ] **Step 2: Convert `Snippet` to MyBatis-Plus**

Replace `backend/src/main/java/com/flashbrain/entity/Snippet.java` with:

```java
package com.flashbrain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("snippet")
@Data
@NoArgsConstructor
public class Snippet {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("subject_id")
    private Long subjectId;

    @TableField("title")
    private String title;

    @TableField("image_path")
    private String imagePath;

    @TableField("ocr_text")
    private String ocrText;

    @TableField("note_content")
    private String noteContent;

    @TableField("sort_order")
    private Double sortOrder;

    @TableField("is_pinned")
    private Boolean isPinned = false;

    @TableField("is_mastered")
    private Boolean isMastered = false;
}
```

- [ ] **Step 3: Convert `SnippetImage` to MyBatis-Plus**

Replace `backend/src/main/java/com/flashbrain/entity/SnippetImage.java` with:

```java
package com.flashbrain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("snippet_image")
@Data
@NoArgsConstructor
public class SnippetImage {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("snippet_id")
    private Long snippetId;

    @TableField("original_filename")
    private String originalFilename;

    @TableField("stored_filename")
    private String storedFilename;

    @TableField("url")
    private String url;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: Create `SubjectMapper`**

Create `backend/src/main/java/com/flashbrain/mapper/SubjectMapper.java`:

```java
package com.flashbrain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashbrain.entity.Subject;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SubjectMapper extends BaseMapper<Subject> {
}
```

- [ ] **Step 5: Create `SnippetMapper`**

Create `backend/src/main/java/com/flashbrain/mapper/SnippetMapper.java`:

```java
package com.flashbrain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashbrain.entity.Snippet;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SnippetMapper extends BaseMapper<Snippet> {
}
```

- [ ] **Step 6: Create `SnippetImageMapper`**

Create `backend/src/main/java/com/flashbrain/mapper/SnippetImageMapper.java`:

```java
package com.flashbrain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashbrain.entity.SnippetImage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SnippetImageMapper extends BaseMapper<SnippetImage> {
}
```

- [ ] **Step 7: Run compile and capture expected failure state**

Run:

```bash
mvn -pl backend test
```

Expected now: compilation still fails because services/controllers/tests still import `repository` classes. Continue to Task 3.

- [ ] **Step 8: Commit entity and mapper changes**

Run:

```bash
git add backend/src/main/java/com/flashbrain/entity/Subject.java backend/src/main/java/com/flashbrain/entity/Snippet.java backend/src/main/java/com/flashbrain/entity/SnippetImage.java backend/src/main/java/com/flashbrain/mapper/SubjectMapper.java backend/src/main/java/com/flashbrain/mapper/SnippetMapper.java backend/src/main/java/com/flashbrain/mapper/SnippetImageMapper.java
git commit -m "feat: add mybatis-plus entity mappings"
```

---

### Task 3: Database Schema Initializer

**Files:**
- Create: `backend/src/main/java/com/flashbrain/config/DatabaseSchemaInitializer.java`
- Create: `backend/src/test/java/com/flashbrain/config/DatabaseSchemaInitializerTest.java`

- [ ] **Step 1: Write failing tests for schema definitions**

Create `backend/src/test/java/com/flashbrain/config/DatabaseSchemaInitializerTest.java`:

```java
package com.flashbrain.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseSchemaInitializerTest {

    @Test
    void shouldDefineAllApplicationTables() {
        Map<String, DatabaseSchemaInitializer.TableDefinition> tables = DatabaseSchemaInitializer.tableDefinitions()
                .stream()
                .collect(Collectors.toMap(DatabaseSchemaInitializer.TableDefinition::getName, table -> table));

        assertThat(tables.keySet()).containsExactlyInAnyOrder("subject", "snippet", "snippet_image");
    }

    @Test
    void shouldDefineSnippetLongTextColumns() {
        DatabaseSchemaInitializer.TableDefinition snippet = DatabaseSchemaInitializer.tableDefinitions()
                .stream()
                .filter(table -> table.getName().equals("snippet"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("snippet table definition missing"));

        assertThat(snippet.getColumns()).containsKeys("ocr_text", "note_content");
        assertThat(snippet.getColumns().get("ocr_text").getExpectedType()).isEqualTo("longtext");
        assertThat(snippet.getColumns().get("note_content").getExpectedType()).isEqualTo("longtext");
    }

    @Test
    void shouldDefineBooleanColumnsAsTinyint() {
        DatabaseSchemaInitializer.TableDefinition subject = DatabaseSchemaInitializer.tableDefinitions()
                .stream()
                .filter(table -> table.getName().equals("subject"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("subject table definition missing"));

        DatabaseSchemaInitializer.TableDefinition snippet = DatabaseSchemaInitializer.tableDefinitions()
                .stream()
                .filter(table -> table.getName().equals("snippet"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("snippet table definition missing"));

        assertThat(subject.getColumns().get("is_deleted").getExpectedType()).isEqualTo("tinyint(1)");
        assertThat(snippet.getColumns().get("is_pinned").getExpectedType()).isEqualTo("tinyint(1)");
        assertThat(snippet.getColumns().get("is_mastered").getExpectedType()).isEqualTo("tinyint(1)");
    }
}
```

- [ ] **Step 2: Run the schema initializer tests and verify they fail**

Run:

```bash
mvn -pl backend -Dtest=DatabaseSchemaInitializerTest test
```

Expected: FAIL because `DatabaseSchemaInitializer` does not exist yet.

- [ ] **Step 3: Implement `DatabaseSchemaInitializer`**

Create `backend/src/main/java/com/flashbrain/config/DatabaseSchemaInitializer.java`:

```java
package com.flashbrain.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class DatabaseSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        initializeSchema();
    }

    void initializeSchema() {
        for (TableDefinition table : tableDefinitions()) {
            ensureTable(table);
        }
    }

    private void ensureTable(TableDefinition table) {
        if (!tableExists(table.getName())) {
            log.info("Creating missing table: {}", table.getName());
            jdbcTemplate.execute(table.getCreateSql());
            return;
        }

        Map<String, ExistingColumn> existingColumns = loadExistingColumns(table.getName());
        for (ColumnDefinition column : table.getColumns().values()) {
            ExistingColumn existing = existingColumns.get(column.getName());
            if (existing == null) {
                log.info("Adding missing column: {}.{}", table.getName(), column.getName());
                jdbcTemplate.execute("ALTER TABLE `" + table.getName() + "` ADD COLUMN " + column.getAddSql());
                continue;
            }
            if (!existing.getColumnType().equalsIgnoreCase(column.getExpectedType())) {
                log.warn("Column type mismatch for {}.{}: database={}, expected={}. Automatic type changes are disabled.",
                        table.getName(), column.getName(), existing.getColumnType(), column.getExpectedType());
            }
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private Map<String, ExistingColumn> loadExistingColumns(String tableName) {
        List<ExistingColumn> columns = jdbcTemplate.query(
                "SELECT column_name, column_type FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ?",
                (rs, rowNum) -> new ExistingColumn(
                        rs.getString("column_name").toLowerCase(Locale.ROOT),
                        rs.getString("column_type").toLowerCase(Locale.ROOT)
                ),
                tableName
        );
        Map<String, ExistingColumn> byName = new LinkedHashMap<>();
        for (ExistingColumn column : columns) {
            byName.put(column.getName(), column);
        }
        return byName;
    }

    public static List<TableDefinition> tableDefinitions() {
        List<TableDefinition> tables = new ArrayList<>();

        tables.add(new TableDefinition(
                "subject",
                "CREATE TABLE `subject` ("
                        + "`id` BIGINT NOT NULL AUTO_INCREMENT,"
                        + "`name` VARCHAR(255) NULL,"
                        + "`parent_id` BIGINT NULL,"
                        + "`icon` VARCHAR(255) NULL,"
                        + "`is_deleted` TINYINT(1) NULL DEFAULT 0,"
                        + "PRIMARY KEY (`id`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                columns(
                        column("id", "bigint", "`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY"),
                        column("name", "varchar(255)", "`name` VARCHAR(255) NULL"),
                        column("parent_id", "bigint", "`parent_id` BIGINT NULL"),
                        column("icon", "varchar(255)", "`icon` VARCHAR(255) NULL"),
                        column("is_deleted", "tinyint(1)", "`is_deleted` TINYINT(1) NULL DEFAULT 0")
                )
        ));

        tables.add(new TableDefinition(
                "snippet",
                "CREATE TABLE `snippet` ("
                        + "`id` BIGINT NOT NULL AUTO_INCREMENT,"
                        + "`subject_id` BIGINT NULL,"
                        + "`title` VARCHAR(255) NULL,"
                        + "`image_path` VARCHAR(255) NULL,"
                        + "`ocr_text` LONGTEXT NULL,"
                        + "`note_content` LONGTEXT NULL,"
                        + "`sort_order` DOUBLE NULL,"
                        + "`is_pinned` TINYINT(1) NULL DEFAULT 0,"
                        + "`is_mastered` TINYINT(1) NULL DEFAULT 0,"
                        + "PRIMARY KEY (`id`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                columns(
                        column("id", "bigint", "`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY"),
                        column("subject_id", "bigint", "`subject_id` BIGINT NULL"),
                        column("title", "varchar(255)", "`title` VARCHAR(255) NULL"),
                        column("image_path", "varchar(255)", "`image_path` VARCHAR(255) NULL"),
                        column("ocr_text", "longtext", "`ocr_text` LONGTEXT NULL"),
                        column("note_content", "longtext", "`note_content` LONGTEXT NULL"),
                        column("sort_order", "double", "`sort_order` DOUBLE NULL"),
                        column("is_pinned", "tinyint(1)", "`is_pinned` TINYINT(1) NULL DEFAULT 0"),
                        column("is_mastered", "tinyint(1)", "`is_mastered` TINYINT(1) NULL DEFAULT 0")
                )
        ));

        tables.add(new TableDefinition(
                "snippet_image",
                "CREATE TABLE `snippet_image` ("
                        + "`id` BIGINT NOT NULL AUTO_INCREMENT,"
                        + "`snippet_id` BIGINT NULL,"
                        + "`original_filename` VARCHAR(255) NULL,"
                        + "`stored_filename` VARCHAR(255) NULL,"
                        + "`url` VARCHAR(255) NULL,"
                        + "`created_at` DATETIME NULL,"
                        + "PRIMARY KEY (`id`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                columns(
                        column("id", "bigint", "`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY"),
                        column("snippet_id", "bigint", "`snippet_id` BIGINT NULL"),
                        column("original_filename", "varchar(255)", "`original_filename` VARCHAR(255) NULL"),
                        column("stored_filename", "varchar(255)", "`stored_filename` VARCHAR(255) NULL"),
                        column("url", "varchar(255)", "`url` VARCHAR(255) NULL"),
                        column("created_at", "datetime", "`created_at` DATETIME NULL")
                )
        ));

        return Collections.unmodifiableList(tables);
    }

    private static Map<String, ColumnDefinition> columns(ColumnDefinition... columns) {
        Map<String, ColumnDefinition> map = new LinkedHashMap<>();
        for (ColumnDefinition column : columns) {
            map.put(column.getName(), column);
        }
        return Collections.unmodifiableMap(map);
    }

    private static ColumnDefinition column(String name, String expectedType, String addSql) {
        return new ColumnDefinition(name, expectedType, addSql);
    }

    public static class TableDefinition {
        private final String name;
        private final String createSql;
        private final Map<String, ColumnDefinition> columns;

        TableDefinition(String name, String createSql, Map<String, ColumnDefinition> columns) {
            this.name = name;
            this.createSql = createSql;
            this.columns = columns;
        }

        public String getName() {
            return name;
        }

        public String getCreateSql() {
            return createSql;
        }

        public Map<String, ColumnDefinition> getColumns() {
            return columns;
        }
    }

    public static class ColumnDefinition {
        private final String name;
        private final String expectedType;
        private final String addSql;

        ColumnDefinition(String name, String expectedType, String addSql) {
            this.name = name;
            this.expectedType = expectedType;
            this.addSql = addSql;
        }

        public String getName() {
            return name;
        }

        public String getExpectedType() {
            return expectedType;
        }

        public String getAddSql() {
            return addSql;
        }
    }

    private static class ExistingColumn {
        private final String name;
        private final String columnType;

        ExistingColumn(String name, String columnType) {
            this.name = name;
            this.columnType = columnType;
        }

        String getName() {
            return name;
        }

        String getColumnType() {
            return columnType;
        }
    }
}
```

- [ ] **Step 4: Run schema initializer tests and verify they pass**

Run:

```bash
mvn -pl backend -Dtest=DatabaseSchemaInitializerTest test
```

Expected: PASS.

- [ ] **Step 5: Commit schema initializer**

Run:

```bash
git add backend/src/main/java/com/flashbrain/config/DatabaseSchemaInitializer.java backend/src/test/java/com/flashbrain/config/DatabaseSchemaInitializerTest.java
git commit -m "feat: add mysql schema initializer"
```

---

### Task 4: Subject Service Migration

**Files:**
- Create: `backend/src/main/java/com/flashbrain/service/SubjectService.java`
- Modify: `backend/src/main/java/com/flashbrain/controller/SubjectController.java`
- Create: `backend/src/test/java/com/flashbrain/service/SubjectServiceTest.java`

- [ ] **Step 1: Write failing `SubjectService` tests**

Create `backend/src/test/java/com/flashbrain/service/SubjectServiceTest.java`:

```java
package com.flashbrain.service;

import com.flashbrain.entity.Subject;
import com.flashbrain.mapper.SubjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock
    private SubjectMapper subjectMapper;

    @InjectMocks
    private SubjectService subjectService;

    @Test
    void shouldListSubjects() {
        Subject subject = new Subject();
        subject.setName("Java");
        when(subjectMapper.selectList(null)).thenReturn(Collections.singletonList(subject));

        List<Subject> result = subjectService.getAll();

        assertThat(result).containsExactly(subject);
    }

    @Test
    void shouldCreateSubject() {
        Subject subject = new Subject();
        subject.setName("MySQL");

        Subject result = subjectService.create(subject);

        verify(subjectMapper).insert(subject);
        assertThat(result).isSameAs(subject);
    }
}
```

- [ ] **Step 2: Run subject service tests and verify they fail**

Run:

```bash
mvn -pl backend -Dtest=SubjectServiceTest test
```

Expected: FAIL because `SubjectService` does not exist yet.

- [ ] **Step 3: Implement `SubjectService`**

Create `backend/src/main/java/com/flashbrain/service/SubjectService.java`:

```java
package com.flashbrain.service;

import com.flashbrain.entity.Subject;
import com.flashbrain.mapper.SubjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubjectService {

    @Autowired
    private SubjectMapper subjectMapper;

    public List<Subject> getAll() {
        return subjectMapper.selectList(null);
    }

    public Subject create(Subject subject) {
        subjectMapper.insert(subject);
        return subject;
    }
}
```

- [ ] **Step 4: Update `SubjectController`**

Replace `backend/src/main/java/com/flashbrain/controller/SubjectController.java` with:

```java
package com.flashbrain.controller;

import com.flashbrain.entity.Subject;
import com.flashbrain.service.SubjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    @Autowired
    private SubjectService subjectService;

    @GetMapping
    public List<Subject> getAll() {
        return subjectService.getAll();
    }

    @PostMapping
    public Subject create(@RequestBody Subject subject) {
        return subjectService.create(subject);
    }
}
```

- [ ] **Step 5: Run subject service tests and verify they pass**

Run:

```bash
mvn -pl backend -Dtest=SubjectServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit subject migration**

Run:

```bash
git add backend/src/main/java/com/flashbrain/service/SubjectService.java backend/src/main/java/com/flashbrain/controller/SubjectController.java backend/src/test/java/com/flashbrain/service/SubjectServiceTest.java
git commit -m "feat: migrate subjects to mybatis-plus"
```

---

### Task 5: Snippet Service Migration

**Files:**
- Modify: `backend/src/main/java/com/flashbrain/service/SnippetService.java`
- Create: `backend/src/test/java/com/flashbrain/service/SnippetServiceTest.java`

- [ ] **Step 1: Write failing `SnippetService` tests**

Create `backend/src/test/java/com/flashbrain/service/SnippetServiceTest.java`:

```java
package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flashbrain.entity.Snippet;
import com.flashbrain.mapper.SnippetMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnippetServiceTest {

    @Mock
    private SnippetMapper snippetMapper;

    @InjectMocks
    private SnippetService snippetService;

    @Test
    void shouldQuerySnippetsBySubjectWithPinAndSortOrder() {
        Snippet snippet = new Snippet();
        snippet.setSubjectId(7L);
        when(snippetMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(snippet));

        List<Snippet> result = snippetService.getSnippetsBySubject(7L);

        assertThat(result).containsExactly(snippet);
        ArgumentCaptor<QueryWrapper<Snippet>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(snippetMapper).selectList(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("subject_id");
        assertThat(sqlSegment).contains("ORDER BY is_pinned DESC,sort_order ASC");
    }

    @Test
    void shouldSetDefaultSortOrderWhenCreatingSnippet() {
        Snippet snippet = new Snippet();
        snippet.setTitle("New");

        Snippet result = snippetService.createSnippet(snippet);

        verify(snippetMapper).insert(snippet);
        assertThat(result.getSortOrder()).isNotNull();
    }

    @Test
    void shouldMoveSnippetBetweenOrders() {
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        when(snippetMapper.selectById(1L)).thenReturn(snippet);

        Snippet result = snippetService.moveSnippet(1L, 100.0, 300.0);

        assertThat(result.getSortOrder()).isEqualTo(200.0);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldMoveSnippetBeforeFirstOrder() {
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        when(snippetMapper.selectById(1L)).thenReturn(snippet);

        Snippet result = snippetService.moveSnippet(1L, null, 300.0);

        assertThat(result.getSortOrder()).isEqualTo(150.0);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldMoveSnippetAfterLastOrder() {
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        when(snippetMapper.selectById(1L)).thenReturn(snippet);

        Snippet result = snippetService.moveSnippet(1L, 300.0, null);

        assertThat(result.getSortOrder()).isEqualTo(1300.0);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldMoveFirstSnippetToDefaultOrder() {
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        when(snippetMapper.selectById(1L)).thenReturn(snippet);

        Snippet result = snippetService.moveSnippet(1L, null, null);

        assertThat(result.getSortOrder()).isEqualTo(1000.0);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldToggleMasteredAndUnpinWhenMarkedMastered() {
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        snippet.setIsPinned(true);
        snippet.setIsMastered(false);
        when(snippetMapper.selectById(1L)).thenReturn(snippet);

        Snippet result = snippetService.toggleMastered(1L);

        assertThat(result.getIsMastered()).isTrue();
        assertThat(result.getIsPinned()).isFalse();
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldThrowWhenSnippetMissing() {
        when(snippetMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> snippetService.updateNote(99L, "note"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Snippet not found");
    }
}
```

- [ ] **Step 2: Run snippet service tests and verify they fail**

Run:

```bash
mvn -pl backend -Dtest=SnippetServiceTest test
```

Expected: FAIL because `SnippetService` still imports `SnippetRepository`.

- [ ] **Step 3: Implement MyBatis-Plus `SnippetService`**

Replace `backend/src/main/java/com/flashbrain/service/SnippetService.java` with:

```java
package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flashbrain.entity.Snippet;
import com.flashbrain.mapper.SnippetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SnippetService {

    @Autowired
    private SnippetMapper snippetMapper;

    /**
     * 实现浮点排序逻辑 (Lexical Ordering)
     * new_order = (prev_order + next_order) / 2
     */
    @Transactional
    public Snippet moveSnippet(Long id, Double prevOrder, Double nextOrder) {
        Snippet snippet = findSnippet(id);

        double newOrder;
        if (prevOrder == null && nextOrder == null) {
            newOrder = 1000.0;
        } else if (prevOrder == null) {
            newOrder = nextOrder / 2.0;
        } else if (nextOrder == null) {
            newOrder = prevOrder + 1000.0;
        } else {
            newOrder = (prevOrder + nextOrder) / 2.0;
        }

        snippet.setSortOrder(newOrder);
        snippetMapper.updateById(snippet);
        return snippet;
    }

    /**
     * 物理搬家逻辑：修改所属科目
     */
    @Transactional
    public Snippet archiveSnippet(Long id, Long newSubjectId) {
        Snippet snippet = findSnippet(id);

        snippet.setSubjectId(newSubjectId);
        snippet.setSortOrder(0.0);
        snippetMapper.updateById(snippet);
        return snippet;
    }

    public List<Snippet> getSnippetsBySubject(Long subjectId) {
        QueryWrapper<Snippet> query = new QueryWrapper<Snippet>()
                .eq("subject_id", subjectId)
                .orderByDesc("is_pinned")
                .orderByAsc("sort_order");
        return snippetMapper.selectList(query);
    }

    @Transactional
    public Snippet createSnippet(Snippet snippet) {
        if (snippet.getSortOrder() == null) {
            snippet.setSortOrder(System.currentTimeMillis() / 1000.0);
        }
        snippetMapper.insert(snippet);
        return snippet;
    }

    @Transactional
    public Snippet updateSnippet(Long id, Snippet detail) {
        Snippet snippet = findSnippet(id);
        snippet.setTitle(detail.getTitle());
        snippet.setOcrText(detail.getOcrText());
        snippet.setNoteContent(detail.getNoteContent());
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public Snippet updateOcr(Long id, String ocrText) {
        Snippet snippet = findSnippet(id);
        snippet.setOcrText(ocrText);
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public Snippet updateNote(Long id, String noteContent) {
        Snippet snippet = findSnippet(id);
        snippet.setNoteContent(noteContent);
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public Snippet togglePin(Long id) {
        Snippet snippet = findSnippet(id);
        snippet.setIsPinned(!Boolean.TRUE.equals(snippet.getIsPinned()));
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public Snippet toggleMastered(Long id) {
        Snippet snippet = findSnippet(id);
        snippet.setIsMastered(!Boolean.TRUE.equals(snippet.getIsMastered()));
        if (Boolean.TRUE.equals(snippet.getIsMastered())) {
            snippet.setIsPinned(false);
        }
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public void deleteSnippet(Long id) {
        snippetMapper.deleteById(id);
    }

    private Snippet findSnippet(Long id) {
        Snippet snippet = snippetMapper.selectById(id);
        if (snippet == null) {
            throw new RuntimeException("Snippet not found");
        }
        return snippet;
    }
}
```

- [ ] **Step 4: Run snippet service tests and verify they pass**

Run:

```bash
mvn -pl backend -Dtest=SnippetServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit snippet migration**

Run:

```bash
git add backend/src/main/java/com/flashbrain/service/SnippetService.java backend/src/test/java/com/flashbrain/service/SnippetServiceTest.java
git commit -m "feat: migrate snippets to mybatis-plus"
```

---

### Task 6: Snippet Image Service Migration

**Files:**
- Modify: `backend/src/main/java/com/flashbrain/service/SnippetImageService.java`
- Create: `backend/src/test/java/com/flashbrain/service/SnippetImageServiceTest.java`

- [ ] **Step 1: Write failing `SnippetImageService` test**

Create `backend/src/test/java/com/flashbrain/service/SnippetImageServiceTest.java`:

```java
package com.flashbrain.service;

import com.flashbrain.entity.SnippetImage;
import com.flashbrain.mapper.SnippetImageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SnippetImageServiceTest {

    @Mock
    private SnippetImageMapper snippetImageMapper;

    @InjectMocks
    private SnippetImageService snippetImageService;

    @TempDir
    Path uploadDir;

    @Test
    void shouldSaveImageFileAndInsertImageRecord() throws Exception {
        ReflectionTestUtils.setField(snippetImageService, "uploadDir", uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "hello world.png", "image/png", "image-content".getBytes());

        SnippetImage result = snippetImageService.saveImage(12L, file);

        ArgumentCaptor<SnippetImage> captor = ArgumentCaptor.forClass(SnippetImage.class);
        verify(snippetImageMapper).insert(captor.capture());
        SnippetImage inserted = captor.getValue();

        assertThat(inserted.getSnippetId()).isEqualTo(12L);
        assertThat(inserted.getOriginalFilename()).isEqualTo("hello world.png");
        assertThat(inserted.getStoredFilename()).endsWith("-hello_world.png");
        assertThat(inserted.getUrl()).startsWith("/uploads/ocr-images/12/");
        assertThat(inserted.getCreatedAt()).isNotNull();
        assertThat(result).isSameAs(inserted);
        assertThat(Files.exists(uploadDir.resolve("ocr-images").resolve("12").resolve(inserted.getStoredFilename()))).isTrue();
    }
}
```

- [ ] **Step 2: Run image service tests and verify they fail**

Run:

```bash
mvn -pl backend -Dtest=SnippetImageServiceTest test
```

Expected: FAIL because `SnippetImageService` still imports `SnippetImageRepository`.

- [ ] **Step 3: Implement MyBatis-Plus `SnippetImageService`**

Replace `backend/src/main/java/com/flashbrain/service/SnippetImageService.java` with:

```java
package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flashbrain.entity.SnippetImage;
import com.flashbrain.mapper.SnippetImageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SnippetImageService {

    @Autowired
    private SnippetImageMapper snippetImageMapper;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public SnippetImage saveImage(Long snippetId, MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename() == null ? "ocr-image" : file.getOriginalFilename();
        String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String storedFilename = UUID.randomUUID() + "-" + safeFilename;

        Path snippetDir = Paths.get(uploadDir, "ocr-images", String.valueOf(snippetId)).toAbsolutePath().normalize();
        Files.createDirectories(snippetDir);

        Path targetPath = snippetDir.resolve(storedFilename).normalize();
        Files.write(targetPath, file.getBytes());

        SnippetImage image = new SnippetImage();
        image.setSnippetId(snippetId);
        image.setOriginalFilename(originalFilename);
        image.setStoredFilename(storedFilename);
        image.setUrl("/uploads/ocr-images/" + snippetId + "/" + storedFilename);
        image.setCreatedAt(LocalDateTime.now());
        snippetImageMapper.insert(image);
        return image;
    }

    public List<SnippetImage> getImages(Long snippetId) {
        QueryWrapper<SnippetImage> query = new QueryWrapper<SnippetImage>()
                .eq("snippet_id", snippetId)
                .orderByAsc("created_at");
        return snippetImageMapper.selectList(query);
    }
}
```

- [ ] **Step 4: Run image service tests and verify they pass**

Run:

```bash
mvn -pl backend -Dtest=SnippetImageServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit image service migration**

Run:

```bash
git add backend/src/main/java/com/flashbrain/service/SnippetImageService.java backend/src/test/java/com/flashbrain/service/SnippetImageServiceTest.java
git commit -m "feat: migrate snippet images to mybatis-plus"
```

---

### Task 7: Remove JPA Repositories and Legacy Repository Tests

**Files:**
- Delete: `backend/src/main/java/com/flashbrain/repository/SubjectRepository.java`
- Delete: `backend/src/main/java/com/flashbrain/repository/SnippetRepository.java`
- Delete: `backend/src/main/java/com/flashbrain/repository/SnippetImageRepository.java`
- Delete: `backend/src/test/java/com/flashbrain/repository/SubjectRepositoryTest.java`
- Delete: `backend/src/test/java/com/flashbrain/repository/SnippetRepositoryTest.java`

- [ ] **Step 1: Delete JPA repository classes**

Remove these files:

```text
backend/src/main/java/com/flashbrain/repository/SubjectRepository.java
backend/src/main/java/com/flashbrain/repository/SnippetRepository.java
backend/src/main/java/com/flashbrain/repository/SnippetImageRepository.java
```

- [ ] **Step 2: Delete JPA repository tests**

Remove these files:

```text
backend/src/test/java/com/flashbrain/repository/SubjectRepositoryTest.java
backend/src/test/java/com/flashbrain/repository/SnippetRepositoryTest.java
```

- [ ] **Step 3: Verify no JPA imports remain in backend source**

Run:

```bash
rg "javax\.persistence|JpaRepository|DataJpaTest|com\.flashbrain\.repository" backend/src
```

Expected: no matches.

- [ ] **Step 4: Run all backend tests**

Run:

```bash
mvn -pl backend test
```

Expected: PASS.

- [ ] **Step 5: Commit repository removal**

Run:

```bash
git add -A backend/src/main/java/com/flashbrain/repository backend/src/test/java/com/flashbrain/repository
git commit -m "refactor: remove jpa repositories"
```

---

### Task 8: Local MySQL Startup Verification

**Files:**
- No committed source changes expected unless verification exposes a bug.
- Use local-only file: `backend/src/main/resources/application-local.yml`.

- [ ] **Step 1: Confirm local config is untracked and ignored**

Run:

```bash
git status --short backend/src/main/resources/application-local.yml
```

Expected: no output, because the file is ignored.

- [ ] **Step 2: Run backend tests**

Run:

```bash
mvn -pl backend test
```

Expected: PASS.

- [ ] **Step 3: Start backend against MySQL**

Run:

```bash
mvn -pl backend spring-boot:run
```

Expected: application starts successfully or fails with a clear external MySQL connection error. If it starts, logs include messages for existing or created tables. Stop the process with `Ctrl+C` after validation.

- [ ] **Step 4: If startup fails because of schema SQL, fix and retest**

For SQL syntax or MyBatis mapping failures, update the exact failing file, rerun:

```bash
mvn -pl backend test
mvn -pl backend spring-boot:run
```

Expected: tests pass and startup proceeds past schema initialization.

- [ ] **Step 5: Commit verification fixes if any**

If Step 4 required source changes, commit only those changes:

```bash
git add backend/src/main/java backend/src/test/java backend/src/main/resources/application.yml backend/pom.xml
git commit -m "fix: align mysql schema initialization"
```

If no source changes were needed, do not create an empty commit.

---

### Task 9: Final Diff Review and Completion

**Files:**
- Review all changed files related to this plan.

- [ ] **Step 1: Check working tree**

Run:

```bash
git status --short
```

Expected: only pre-existing unrelated changes remain uncommitted, plus the ignored local config file is not shown. No task-related source files should be unstaged.

- [ ] **Step 2: Run final backend test suite**

Run:

```bash
mvn -pl backend test
```

Expected: PASS.

- [ ] **Step 3: Review committed diff on the feature branch**

Run:

```bash
git log --oneline --decorate --max-count=10
git diff master...HEAD -- backend pom.xml docs/superpowers
```

Expected: diff contains the spec, plan, MySQL/MyBatis-Plus migration, schema initializer, mapper/service changes, and tests. It must not contain `frontend/dist/` or unrelated frontend layout changes.

- [ ] **Step 4: Report completion with evidence**

Final report must include:

```text
- Tests run: mvn -pl backend test
- Result: PASS or exact failure summary
- MySQL startup verification: PASS, skipped with reason, or failed with exact external connection error
- Branch name
- Important commits
- Unrelated pre-existing changes left untouched
```

Do not claim MySQL startup works unless `mvn -pl backend spring-boot:run` actually started past schema initialization.

---

## Self-Review

- Spec coverage: dependency migration, MySQL config, local secret handling, entity mappings, Mapper migration, schema initializer, Service behavior preservation, JPA removal, tests, and verification are each covered by tasks above.
- Placeholder scan: plan reviewed for incomplete markers and vague implementation instructions; none remain. Each code-writing step includes concrete code.
- Type consistency: mapper names, service method names, entity property names, and database column names match across tasks.
