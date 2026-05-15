# Backend Entity and Repository Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Subject 和 Snippet 实体及其 JPA Repository，并验证 CRUD 功能。

**Architecture:** 使用 Spring Data JPA 进行持久化，Lombok 简化实体代码。Subject 支持树状结构。

**Tech Stack:** Spring Boot 3, Spring Data JPA, Lombok, H2 Database (for testing).

---

### Task 1: 实现 Subject 实体和 Repository

**Files:**
- Create: `backend/src/main/java/com/flashbrain/entity/Subject.java`
- Create: `backend/src/main/java/com/flashbrain/repository/SubjectRepository.java`
- Create: `backend/src/test/java/com/flashbrain/repository/SubjectRepositoryTest.java`

- [ ] **Step 1: 编写失败的 Subject 保存测试**

```java
package com.flashbrain.repository;

import com.flashbrain.entity.Subject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class SubjectRepositoryTest {

    @Autowired
    private SubjectRepository subjectRepository;

    @Test
    public void shouldSaveSubject() {
        Subject subject = new Subject();
        subject.setName("Java");
        subject.setIcon("java-icon");

        Subject saved = subjectRepository.save(subject);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Java");
    }
}
```

- [ ] **Step 2: 运行测试并验证失败**

Run: `mvn test -pl backend -Dtest=SubjectRepositoryTest`
Expected: 编译失败，因为 Subject 和 SubjectRepository 类不存在。

- [ ] **Step 3: 编写 Subject 实体（包含树状结构字段）**

```java
package com.flashbrain.entity;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class Subject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Long parentId;

    private String icon;

    private Boolean isDeleted = false;
}
```

- [ ] **Step 4: 编写 SubjectRepository 接口**

```java
package com.flashbrain.repository;

import com.flashbrain.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
}
```

- [ ] **Step 5: 再次运行测试验证通过**

Run: `mvn test -pl backend -Dtest=SubjectRepositoryTest`
Expected: PASS

- [ ] **Step 6: Git Add**

```bash
git add backend/src/main/java/com/flashbrain/entity/Subject.java backend/src/main/java/com/flashbrain/repository/SubjectRepository.java backend/src/test/java/com/flashbrain/repository/SubjectRepositoryTest.java
```

---

### Task 2: 实现 Snippet 实体和 Repository

**Files:**
- Create: `backend/src/main/java/com/flashbrain/entity/Snippet.java`
- Create: `backend/src/main/java/com/flashbrain/repository/SnippetRepository.java`
- Create: `backend/src/test/java/com/flashbrain/repository/SnippetRepositoryTest.java`

- [ ] **Step 1: 编写失败的 Snippet 保存测试**

```java
package com.flashbrain.repository;

import com.flashbrain.entity.Snippet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class SnippetRepositoryTest {

    @Autowired
    private SnippetRepository snippetRepository;

    @Test
    public void shouldSaveSnippet() {
        Snippet snippet = new Snippet();
        snippet.setTitle("Spring Data JPA");
        snippet.setSubjectId(1L);
        snippet.setOcrText("OCR Content");
        snippet.setNoteContent("Note Content");
        snippet.setSortOrder(1.0);
        snippet.setIsMastered(false);

        Snippet saved = snippetRepository.save(snippet);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("Spring Data JPA");
    }
}
```

- [ ] **Step 2: 运行测试并验证失败**

Run: `mvn test -pl backend -Dtest=SnippetRepositoryTest`
Expected: 编译失败，因为 Snippet 和 SnippetRepository 类不存在。

- [ ] **Step 3: 编写 Snippet 实体**

```java
package com.flashbrain.entity;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class Snippet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long subjectId;

    private String title;

    private String imagePath;

    @Column(columnDefinition = "TEXT")
    private String ocrText;

    @Column(columnDefinition = "TEXT")
    private String noteContent;

    private Double sortOrder;

    private Boolean isMastered = false;
}
```

- [ ] **Step 4: 编写 SnippetRepository 接口**

```java
package com.flashbrain.repository;

import com.flashbrain.entity.Snippet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnippetRepository extends JpaRepository<Snippet, Long> {
}
```

- [ ] **Step 5: 再次运行测试验证通过**

Run: `mvn test -pl backend -Dtest=SnippetRepositoryTest`
Expected: PASS

- [ ] **Step 6: Git Add**

```bash
git add backend/src/main/java/com/flashbrain/entity/Snippet.java backend/src/main/java/com/flashbrain/repository/SnippetRepository.java backend/src/test/java/com/flashbrain/repository/SnippetRepositoryTest.java
```
