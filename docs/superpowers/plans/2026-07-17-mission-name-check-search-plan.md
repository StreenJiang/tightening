# 任务名称重复检测 & 列表搜索 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 ProductMissionController 新增名称重复检测接口和列表名称搜索功能，加数据库唯一约束兜底。

**Architecture:** 在 Service 层新增 `isNameDuplicate` 查询、扩展现有 `listByPage` 加 name 参数；Controller 层新增 `checkName` 端点、`list` 加 name 参数、`create`/`update` 捕获 DuplicateKeyException + 通用 Exception 兜底。数据库加部分唯一索引 `WHERE deleted = 0`。

**Tech Stack:** Java 21, Spring Boot 3.5.10, MyBatis-Plus 3.5.9, SQLite + Flyway, JUnit 5 + AssertJ + Mockito

## Global Constraints

- 软删除字段 `deleted`，唯一约束仅对 `deleted = 0` 记录生效
- 名称搜索使用 `LIKE '%xxx%'` 包含匹配
- Controller 路径统一返回 `ResponseEntity<ApiResponse<T>>`
- 测试使用 JUnit 5 + AssertJ + Mockito（controller 测试用 MockitoExtension）

---

### Task 1: Flyway 数据库迁移 — 名称唯一索引

**Files:**
- Create: `src/main/resources/db/migration/V1.0.16__add_mission_name_unique.sql`

**Produces:**
- 唯一索引 `idx_product_mission_name`，只约束未删除记录

- [ ] **Step 1: 创建迁移文件**

```sql
CREATE UNIQUE INDEX idx_product_mission_name ON product_mission(name) WHERE deleted = 0;
```

- [ ] **Step 2: 验证迁移可执行**

Run: `mvn flyway:migrate -Dflyway.url=jdbc:sqlite:~/tightening_system/tightening.db`
Expected: 迁移成功，无错误输出

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V1.0.16__add_mission_name_unique.sql
git commit -m "feat: add unique index on product_mission.name for non-deleted rows"
```

---

### Task 2: Service 层 — 新增 isNameDuplicate + 扩展 listByPage

**Files:**
- Modify: `src/main/java/com/tightening/service/ProductMissionService.java`

**Produces:**
- `boolean isNameDuplicate(String name, Long excludeId)` — 检查名称是否重复，可选排除指定 ID
- `Page<ProductMission> listByPage(int page, int size, String name)` — 改为 3 参数，name 非空时 LIKE 匹配

- [ ] **Step 1: 新增 isNameDuplicate 方法**

在 `ProductMissionService` 末尾添加：

```java
public boolean isNameDuplicate(String name, Long excludeId) {
    return lambdaQuery()
            .eq(ProductMission::getName, name)
            .ne(excludeId != null, ProductMission::getId, excludeId)
            .count() > 0;
}
```

- [ ] **Step 2: 修改 listByPage 方法签名，加 name 参数**

将现有的 `listByPage(int page, int size)` 改为：

```java
public Page<ProductMission> listByPage(int page, int size, String name) {
    int safePage = Math.min(Math.max(1, page), 1000);
    int safeSize = Math.min(Math.max(1, size), 500);
    var wrapper = lambdaQuery()
            .orderByDesc(ProductMission::getId);
    if (name != null && !name.isBlank()) {
        wrapper.like(ProductMission::getName, name);
    }
    return wrapper.page(new Page<>(safePage, safeSize));
}
```

- [ ] **Step 3: 提交验证编译通过**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tightening/service/ProductMissionService.java
git commit -m "feat: add isNameDuplicate and name filter to listByPage"
```

---

### Task 3: Controller 层 — checkName 端点 + list 加 name + DuplicateKeyException 处理

**Files:**
- Modify: `src/main/java/com/tightening/controller/ProductMissionController.java`

**Consumes:**
- `ProductMissionService.isNameDuplicate(String name, Long excludeId)` — 返回 boolean
- `ProductMissionService.listByPage(int page, int size, String name)` — 返回 `Page<ProductMission>`

**Produces:**
- `GET /api/missions/check-name` — 名称重复检测
- `GET /api/missions` — 新增可选 `name` 参数
- `POST /api/missions` — 捕获 `DuplicateKeyException` + 通用 `Exception`
- `PUT /api/missions/{id}` — 捕获 `DuplicateKeyException` + 通用 `Exception`

- [ ] **Step 1: 添加 DuplicateKeyException import**

在 import 区域增加：

```java
import org.springframework.dao.DuplicateKeyException;
```

- [ ] **Step 2: 修改 list 方法签名加 name 参数**

将 `list` 方法改为：

```java
@GetMapping
public ResponseEntity<ApiResponse<List<ProductMissionDTO>>> list(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "100") int size,
                                                    @RequestParam(required = false) String name) {
    var resultPage = missionService.listByPage(page, size, name);
    return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(resultPage.getRecords(), ProductMissionDTO::new)));
}
```

- [ ] **Step 3: 新增 checkName 方法**

在 `get` 方法之后插入：

```java
@GetMapping("/check-name")
public ResponseEntity<ApiResponse<Boolean>> checkName(@RequestParam String name,
                                                       @RequestParam(required = false) Long excludeId) {
    boolean exists = missionService.isNameDuplicate(name, excludeId);
    return ResponseEntity.ok(ApiResponse.ok(exists));
}
```

- [ ] **Step 4: create 方法加异常处理（与 update 保持一致）**

将 `create` 方法改为：

```java
@PostMapping
public ResponseEntity<ApiResponse<String>> create(@RequestBody ProductMissionDTO dto) {
    ProductMission entity = Converter.dto2Entity(dto, ProductMission::new);
    try {
        missionService.saveOrUpdate(entity);
        return ResponseEntity.ok(ApiResponse.ok(String.valueOf(entity.getId())));
    } catch (DuplicateKeyException e) {
        return ResponseEntity.ok(ApiResponse.fail("任务名称已存在"));
    } catch (Exception e) {
        log.error("Create mission failed", e);
        return ResponseEntity.ok(ApiResponse.fail("创建失败"));
    }
}
```

- [ ] **Step 5: update 方法细化异常处理**

将 `update` 方法中的 catch 改为区分 DuplicateKeyException：

```java
@PutMapping("/{id}")
public ResponseEntity<ApiResponse<String>> update(@PathVariable Long id, @RequestBody ProductMissionDTO dto) {
    try {
        ProductMission entity = Converter.dto2Entity(dto, ProductMission::new);
        entity.setId(id);
        missionService.saveOrUpdate(entity);
        return ResponseEntity.ok(ApiResponse.ok(String.valueOf(entity.getId())));
    } catch (DuplicateKeyException e) {
        return ResponseEntity.ok(ApiResponse.fail("任务名称已存在"));
    } catch (Exception e) {
        log.error("Update mission failed: id={}", id, e);
        return ResponseEntity.ok(ApiResponse.fail("更新失败"));
    }
}
```

- [ ] **Step 6: 提交验证编译通过**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/tightening/controller/ProductMissionController.java
git commit -m "feat: add check-name endpoint, name search in list, DuplicateKeyException handling"
```

---

### Task 4: 单元测试

**Files:**
- Modify: `src/test/java/com/tightening/controller/ProductMissionControllerTest.java`

**Consumes:**
- `ProductMissionController.checkName(String name, Long excludeId)` — 返回 `ResponseEntity<ApiResponse<Boolean>>`
- `ProductMissionController.list(int page, int size, String name)` — 新签名
- `ProductMissionController.create(ProductMissionDTO dto)` — 含异常处理
- `ProductMissionController.update(Long id, ProductMissionDTO dto)` — 含异常处理

- [ ] **Step 1: 添加 import**

在 import 区域增加：

```java
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DuplicateKeyException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
```

- [ ] **Step 2: 新增 checkName 测试**

```java
@Test
void checkName_shouldReturnTrue_whenDuplicateExists() {
    when(missionService.isNameDuplicate("Test", null)).thenReturn(true);

    ResponseEntity<ApiResponse<Boolean>> response = controller.checkName("Test", null);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().data()).isTrue();
}

@Test
void checkName_shouldReturnFalse_whenNameAvailable() {
    when(missionService.isNameDuplicate("NewMission", null)).thenReturn(false);

    ResponseEntity<ApiResponse<Boolean>> response = controller.checkName("NewMission", null);

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().data()).isFalse();
}

@Test
void checkName_shouldExcludeSelf_whenEditing() {
    when(missionService.isNameDuplicate("Test", 1L)).thenReturn(false);

    ResponseEntity<ApiResponse<Boolean>> response = controller.checkName("Test", 1L);

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().data()).isFalse();
}
```

- [ ] **Step 3: 更新 list 测试加 name 参数**

将现有 `list_shouldReturnOk` 替换为：

```java
@Test
void list_shouldReturnOk() {
    when(missionService.listByPage(1, 100, null)).thenReturn(new Page<>(1, 100));

    ResponseEntity<ApiResponse<List<ProductMissionDTO>>> response = controller.list(1, 100, null);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo(200);
}

@Test
void list_shouldPassNameToService_whenNameProvided() {
    when(missionService.listByPage(1, 100, "Test")).thenReturn(new Page<>(1, 100));

    ResponseEntity<ApiResponse<List<ProductMissionDTO>>> response = controller.list(1, 100, "Test");
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo(200);
}
```

- [ ] **Step 4: 新增 create 异常处理测试**

```java
@Test
void create_shouldReturnFail_whenDuplicateKeyException() {
    ProductMissionDTO dto = new ProductMissionDTO();
    dto.setName("Duplicate");

    doThrow(new DuplicateKeyException("UNIQUE constraint")).when(missionService).saveOrUpdate(any());

    ResponseEntity<ApiResponse<String>> response = controller.create(dto);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).isEqualTo("任务名称已存在");
}
```

- [ ] **Step 5: 新增 update 异常处理测试**

```java
@Test
void update_shouldReturnFail_whenDuplicateKeyException() {
    ProductMissionDTO dto = new ProductMissionDTO();
    dto.setName("Duplicate");

    doThrow(new DuplicateKeyException("UNIQUE constraint")).when(missionService).saveOrUpdate(any());

    ResponseEntity<ApiResponse<String>> response = controller.update(1L, dto);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).isEqualTo("任务名称已存在");
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn test -pl . -Dtest=ProductMissionControllerTest -q`
Expected: Tests run: X, Failures: 0, Errors: 0

- [ ] **Step 7: Commit**

```bash
git add src/test/java/com/tightening/controller/ProductMissionControllerTest.java
git commit -m "test: add checkName, name search, and DuplicateKeyException tests"
```

---

### Task 5: 集成验证

- [ ] **Step 1: 运行全部测试**

Run: `mvn test -q`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 2: 启动应用验证端点**

Run: `mvn spring-boot:run`
然后在另一个终端验证：

```bash
# 名称检测
curl "http://localhost:8080/api/missions/check-name?name=test"
# 列表搜索
curl "http://localhost:8080/api/missions?name=test"
```

Expected: 两个端点均返回 200 和有效 JSON
