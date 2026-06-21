# Dependency Version Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 升级 4 个非大版本依赖到最新稳定版，验证所有测试通过。

**Architecture:** 仅修改 pom.xml 中 4 个 version property，无代码变更。

**Tech Stack:** Java 21, Maven 3.9, Spring Boot 3.5.10

---

### Task 1: Bump dependency versions in pom.xml

**Files:**
- Modify: `pom.xml:37-41`

- [ ] **Step 1: 修改 4 个版本属性**

```xml
<mybatis-plus.version>3.5.16</mybatis-plus.version>
<sqlite.version>3.53.2.0</sqlite.version>
<jserialcomm.version>2.11.4</jserialcomm.version>
<commons-lang3.version>3.20.0</commons-lang3.version>
```

旧值：
```xml
<mybatis-plus.version>3.5.9</mybatis-plus.version>
<sqlite.version>3.49.1.0</sqlite.version>
<jserialcomm.version>2.10.2</jserialcomm.version>
<commons-lang3.version>3.18.0</commons-lang3.version>
```

- [ ] **Step 2: 运行全部测试验证**

Run: `./mvnw clean test`
Expected: BUILD SUCCESS, 310 tests passed

- [ ] **Step 3: 提交（用户手动执行 /git-commit）**
