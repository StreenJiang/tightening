# PageResult — 通用分页响应 DTO 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `PageResult<T>` 通用分页 DTO，让 `ProductMissionController.list` 返回分页元数据。

**Architecture:** 新建 `PageResult` record（含 `of()` 静态工厂），修改 `ProductMissionController.list` 的返回类型和方法体，更新两个测试用例的断言。

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus Page

## Global Constraints

- 字段命名对齐 MyBatis-Plus `Page` getter：`records` / `total` / `size` / `current`
- 使用 Java `record`，与现有 `ApiResponse` 风格一致
- 包位置：`com.tightening.dto`，与 `ApiResponse` 同级

---

### Task 1: 新建 PageResult.java

**Files:**
- Create: `src/main/java/com/tightening/dto/PageResult.java`

**Interfaces:**
- Produces: `PageResult<T>(List<T> records, long total, long size, long current)` — record 构造器
- Produces: `PageResult.of(Page<?> page, List<T> records)` — 静态工厂

- [ ] **Step 1: 创建 PageResult.java**

```java
package com.tightening.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public record PageResult<T>(
    List<T> records,
    long total,
    long size,
    long current
) {
    public static <T> PageResult<T> of(Page<?> page, List<T> records) {
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tightening/dto/PageResult.java
git commit -m "feat: add PageResult generic pagination DTO"
```

---

### Task 2: 修改 ProductMissionController.list

**Files:**
- Modify: `src/main/java/com/tightening/controller/ProductMissionController.java:48-53`

**Interfaces:**
- Consumes: `PageResult.of(Page<?> page, List<T> records)` from Task 1
- Produces: `ResponseEntity<ApiResponse<PageResult<ProductMissionDTO>>> list(int page, int size, String name)`

- [ ] **Step 1: 修改 list 方法**

将第 48-53 行替换为：

```java
    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<ProductMissionDTO>>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "100") int size,
                                                        @RequestParam(required = false) String name) {
        var resultPage = missionService.listByPage(page, size, name);
        var dtos = Converter.entity2Dto(resultPage.getRecords(), ProductMissionDTO::new);
        return ResponseEntity.ok(ApiResponse.ok(PageResult.of(resultPage, dtos)));
    }
```

添加 import：

```java
import com.tightening.dto.PageResult;
```

> `import java.util.List;` 保留不删 — `listPrerequisites`、`listInspectionBindings` 等方法仍返回 `List<DTO>`。

- [ ] **Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tightening/controller/ProductMissionController.java
git commit -m "feat: return PageResult in ProductMissionController.list"
```

---

### Task 3: 更新测试用例

**Files:**
- Modify: `src/test/java/com/tightening/controller/ProductMissionControllerTest.java:37-54`

**Interfaces:**
- Consumes: `PageResult.of(Page<?> page, List<T> records)` from Task 1

- [ ] **Step 1: 更新 list_shouldReturnOk 测试**

将第 37-45 行替换为：

```java
    @Test
    void list_shouldReturnOk() {
        when(missionService.listByPage(eq(1), eq(100), isNull())).thenReturn(new Page<>(1, 100));

        ResponseEntity<ApiResponse<PageResult<ProductMissionDTO>>> response = controller.list(1, 100, null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(200);
        assertThat(response.getBody().data().total()).isEqualTo(0);
        assertThat(response.getBody().data().size()).isEqualTo(100);
        assertThat(response.getBody().data().current()).isEqualTo(1);
    }
```

- [ ] **Step 2: 更新 list_shouldPassNameToService_whenNameProvided 测试**

将第 47-54 行替换为：

```java
    @Test
    void list_shouldPassNameToService_whenNameProvided() {
        when(missionService.listByPage(1, 100, "Test")).thenReturn(new Page<>(1, 100));

        ResponseEntity<ApiResponse<PageResult<ProductMissionDTO>>> response = controller.list(1, 100, "Test");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(200);
        assertThat(response.getBody().data().total()).isEqualTo(0);
        assertThat(response.getBody().data().size()).isEqualTo(100);
        assertThat(response.getBody().data().current()).isEqualTo(1);
    }
```

添加 import：

```java
import com.tightening.dto.PageResult;
```

> `import java.util.List;` 保留不删 — 其他测试方法使用 `List.of()`。

- [ ] **Step 3: 运行测试验证**

Run: `mvn test -Dtest=ProductMissionControllerTest -q`
Expected: Tests run: 18, Failures: 0, BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/tightening/controller/ProductMissionControllerTest.java
git commit -m "test: update list tests for PageResult return type"
```

---

### Final Verification

- [ ] 运行全部测试：`mvn test -q`
- [ ] 确认 BUILD SUCCESS，所有测试通过
