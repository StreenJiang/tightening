# Task Save 返回完整数据 + BoltPartsBarcodeSaveItem barcodeRuleRef 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `saveTask` 返回完整的 `ProductTaskSaveDTO`（所有新建子实体带 DB ID），同时 `BoltPartsBarcodeSaveItem` 支持 `barcodeRuleRef` 解析

**Architecture:** 现有 save items 已继承 `BaseDTO`（含 `id`），无需新建响应类。在每个 diff 方法中，save/update 后将 `entity.getId()` 回写到对应的 DTO item。`BarcodeDiffResult` 从 `saveTask` 逐层透传到 `diffPartsBarcodes` 以解析 `barcodeRuleRef`

**Tech Stack:** Java 21, Spring Boot 3.5.10, MyBatis-Plus 3.5.9, JUnit 5 + AssertJ 3.27.7, Mockito

## Global Constraints

- 零新类，复用现有 DTO
- 校验逻辑、事务边界不动
- 只回填 `id`，不回填 `createTime`/`modifyTime` 等其他 BaseEntity 字段
- `barcodeRuleRef` 和 `barCodeMatchingRuleId` 互斥（同 PrerequisiteSaveItem 模式）
- `barcodeRuleRef` 仅用于新增条码规则（`clientRef` → 真实 ID），已有规则直接用 `barCodeMatchingRuleId`

---

### Task 1: BoltPartsBarcodeSaveItem 新增 barcodeRuleRef 字段

**Files:**
- Modify: `src/main/java/com/tightening/dto/BoltPartsBarcodeSaveItem.java`

**Interfaces:**
- Produces: `BoltPartsBarcodeSaveItem.barcodeRuleRef` — `String`, 与 `barCodeMatchingRuleId` 互斥

- [ ] **Step 1: 添加字段**

```java
// BoltPartsBarcodeSaveItem.java，在 barCodeMatchingRuleId 之后添加
private String barcodeRuleRef;
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

---

### Task 2: saveTask 返回 ProductTaskSaveDTO + ID 回填

**Files:**
- Modify: `src/main/java/com/tightening/service/ProductTaskService.java:85-105`

**Interfaces:**
- Consumes: `BoltPartsBarcodeSaveItem.barcodeRuleRef` (from Task 1)
- Produces: `ProductTaskService.saveTask` 返回 `ProductTaskSaveDTO` 而非 `Long`

- [ ] **Step 1: 修改 saveTask 返回类型并回填 task ID**

`saveTask` 方法（line 85-105）改为：

```java
@Transactional
public ProductTaskSaveDTO saveTask(ProductTaskSaveDTO dto, Map<String, byte[]> imageMap) {
    ProductTask task = Converter.dto2Entity(dto, ProductTask::new);
    if (task.getInspectionScope() == null) task.setInspectionScope(InspectionScope.NONE);
    saveOrUpdate(task);
    Long taskId = task.getId();
    dto.setId(taskId);

    validator.validateInspectionScope(task, dto.getInspectionBoundTaskIds());

    syncInspectionBindings(taskId, dto.getInspectionBoundTaskIds());

    BarcodeDiffResult barcodeResult = diffBarcodeRules(taskId, dto.getBarcodeRules());
    validator.validateBarcodeRules(barcodeResult.rules());

    diffPrerequisites(taskId, dto.getPrerequisites(), barcodeResult);
    validator.validateInspectionChainSelfInspection(task, dto.getPrerequisites());

    diffSides(taskId, dto.getSides(), imageMap, barcodeResult);

    return dto;
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

---

### Task 3: diffBarcodeRules 回填 ID

**Files:**
- Modify: `src/main/java/com/tightening/service/ProductTaskService.java:123-165`

**Interfaces:**
- Consumes: 现有 `BarCodeRuleSaveItem.setId()` (继承自 BaseDTO)
- Produces: `diffBarcodeRules` 返回的 `BarcodeDiffResult` 不变，但 dto items 的 `id` 已被填充

- [ ] **Step 1: 新增时回填 ID**

`diffBarcodeRules` 方法中，`barcodeRuleService.save(entity)` 后添加回填：

```java
if (item.getId() != null) {
    dtoIds.add(item.getId());
    barcodeRuleService.updateById(entity);
} else {
    barcodeRuleService.save(entity);
    item.setId(entity.getId());          // ← 新增
    if (item.getClientRef() != null) {
        clientRefMap.put(item.getClientRef(), entity.getId());
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

---

### Task 4: diffPrerequisites 回填 ID

**Files:**
- Modify: `src/main/java/com/tightening/service/ProductTaskService.java:167-206`

**Interfaces:**
- Consumes: 现有 `PrerequisiteSaveItem.setId()` (继承自 BaseDTO)
- Produces: dto items 的 `id` 被填充

- [ ] **Step 1: 新增时回填 ID**

`diffPrerequisites` 方法中，save/update 部分改为：

```java
if (item.getId() != null) {
    dtoIds.add(item.getId());
    prerequisiteService.updateById(entity);
} else {
    prerequisiteService.save(entity);
    item.setId(entity.getId());          // ← 新增
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

---

### Task 5: diffSides → diffBolts 透传 BarcodeDiffResult + ID 回填

**Files:**
- Modify: `src/main/java/com/tightening/service/ProductTaskService.java:102` (调用点), `231-269` (diffSides), `271-301` (diffBolts)

**Interfaces:**
- Consumes: `BarcodeDiffResult` (private record, line 383)
- Produces: `diffSides(Long, List<ProductSideSaveItem>, Map<String,byte[]>, BarcodeDiffResult)`, `diffBolts(Long, Long, List<ProductBoltSaveItem>, BarcodeDiffResult)`

- [ ] **Step 1: 修改 diffSides 签名并回填 side ID**

```java
private void diffSides(Long taskId, List<ProductSideSaveItem> dtoSides,
                        Map<String, byte[]> imageMap, BarcodeDiffResult barcodeResult) {
    // ... existing code ...
    if (sideItem.getId() != null) {
        dtoSideIds.add(sideItem.getId());
        sideService.updateById(sideEntity);
    } else {
        sideService.save(sideEntity);
        sideItem.setId(sideEntity.getId());  // ← 新增
    }
    // ... image handling unchanged ...
    diffBolts(sideEntity.getId(), taskId, sideItem.getBolts(), barcodeResult);  // ← 新增参数
}
```

- [ ] **Step 2: 修改 diffBolts 签名并回填 bolt ID**

```java
private void diffBolts(Long sideId, Long taskId, List<ProductBoltSaveItem> dtoBolts,
                        BarcodeDiffResult barcodeResult) {
    // ... existing code ...
    if (boltItem.getId() != null) {
        dtoBoltIds.add(boltItem.getId());
        boltService.updateById(boltEntity);
    } else {
        boltService.saveBolt(boltEntity, taskId);
        boltItem.setId(boltEntity.getId());  // ← 新增
    }

    diffDeviceBindings(boltEntity.getId(), boltItem.getDeviceBindings());
    diffPartsBarcodes(boltEntity.getId(), boltItem.getPartsBarcodes(), barcodeResult);  // ← 新增参数
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

---

### Task 6: diffDeviceBindings 回填 ID

**Files:**
- Modify: `src/main/java/com/tightening/service/ProductTaskService.java:303-329`

**Interfaces:**
- Consumes: 现有 `BoltDeviceBindingSaveItem.setId()` (继承自 BaseDTO)
- Produces: dto items 的 `id` 被填充

- [ ] **Step 1: 新增时回填 ID**

```java
if (item.getId() != null) {
    dtoIds.add(item.getId());
    deviceBindingService.updateById(entity);
} else {
    deviceBindingService.save(entity);
    item.setId(entity.getId());          // ← 新增
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

---

### Task 7: diffPartsBarcodes 支持 barcodeRuleRef 解析 + ID 回填

**Files:**
- Modify: `src/main/java/com/tightening/service/ProductTaskService.java:331-357`

**Interfaces:**
- Consumes: `BoltPartsBarcodeSaveItem.barcodeRuleRef` (from Task 1), `BarcodeDiffResult`
- Produces: `diffPartsBarcodes(Long, List<BoltPartsBarcodeSaveItem>, BarcodeDiffResult)`

- [ ] **Step 1: 添加 barcodeRuleRef 解析 + ID 回填**

```java
private void diffPartsBarcodes(Long boltId, List<BoltPartsBarcodeSaveItem> dtoItems,
                                BarcodeDiffResult barcodeResult) {
    List<BoltPartsBarcode> existing = partsBarcodeService.lambdaQuery()
            .eq(BoltPartsBarcode::getProductBoltId, boltId)
            .eq(BoltPartsBarcode::getDeleted, 0)
            .list();

    Set<Long> dtoIds = new HashSet<>();

    if (dtoItems != null) {
        for (BoltPartsBarcodeSaveItem item : dtoItems) {
            // barcodeRuleRef 解析
            if (item.getBarcodeRuleRef() != null) {
                if (item.getBarCodeMatchingRuleId() != null) {
                    throw new IllegalArgumentException("barcodeRuleRef 和 barCodeMatchingRuleId 不能同时存在");
                }
                Long resolvedId = barcodeResult.clientRefMap().get(item.getBarcodeRuleRef());
                if (resolvedId == null) {
                    throw new IllegalArgumentException("找不到 clientRef='" + item.getBarcodeRuleRef() + "' 对应的条码规则");
                }
                item.setBarCodeMatchingRuleId(resolvedId);
            }

            BoltPartsBarcode entity = Converter.dto2Entity(item, BoltPartsBarcode::new);
            entity.setProductBoltId(boltId);
            if (item.getId() != null) {
                dtoIds.add(item.getId());
                partsBarcodeService.updateById(entity);
            } else {
                partsBarcodeService.save(entity);
                item.setId(entity.getId());          // ← 新增
            }
        }
    }

    for (BoltPartsBarcode ex : existing) {
        if (!dtoIds.contains(ex.getId())) {
            partsBarcodeService.removeById(ex.getId());
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

---

### Task 8: Controller 返回 ProductTaskSaveDTO

**Files:**
- Modify: `src/main/java/com/tightening/controller/ProductTaskController.java:74-111`

**Interfaces:**
- Consumes: `ProductTaskService.saveTask` 新返回类型 (from Task 2)
- Produces: `create` 和 `update` 返回 `ApiResponse<ProductTaskSaveDTO>`

- [ ] **Step 1: 修改 create 方法**

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ApiResponse<ProductTaskSaveDTO>> create(HttpServletRequest request) {
    try {
        ProductTaskSaveDTO dto = parseDto(request);
        ProductTaskSaveDTO result = taskService.saveTask(dto, extractImages(request));
        return ResponseEntity.ok(ApiResponse.ok(result));
    } catch (DuplicateKeyException e) {
        return ResponseEntity.ok(ApiResponse.fail("任务名称已存在"));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.ok(ApiResponse.fail(e.getMessage()));
    } catch (RuntimeException e) {
        log.error("Create task failed", e);
        return ResponseEntity.ok(ApiResponse.fail("新增失败: " + unwrapCause(e)));
    } catch (Exception e) {
        log.error("Create task failed", e);
        return ResponseEntity.ok(ApiResponse.fail("新增失败"));
    }
}
```

- [ ] **Step 2: 修改 update 方法**

```java
@PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ApiResponse<ProductTaskSaveDTO>> update(@PathVariable Long id, HttpServletRequest request) {
    try {
        ProductTaskSaveDTO dto = parseDto(request);
        dto.setId(id);
        ProductTaskSaveDTO result = taskService.saveTask(dto, extractImages(request));
        return ResponseEntity.ok(ApiResponse.ok(result));
    } catch (DuplicateKeyException e) {
        return ResponseEntity.ok(ApiResponse.fail("任务名称已存在"));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.ok(ApiResponse.fail(e.getMessage()));
    } catch (RuntimeException e) {
        log.error("Update task failed: id={}", id, e);
        return ResponseEntity.ok(ApiResponse.fail("更新失败: " + unwrapCause(e)));
    } catch (Exception e) {
        log.error("Update task failed: id={}", id, e);
        return ResponseEntity.ok(ApiResponse.fail("更新失败"));
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

---

### Task 9: 测试

**Files:**
- Modify: `src/test/java/com/tightening/controller/ProductTaskControllerTest.java`

- [ ] **Step 1: 添加 create 返回完整 DTO 的测试**

在 `ProductTaskControllerTest` 中添加：

```java
@Test
void create_shouldReturnSavedDto() throws Exception {
    ProductTaskSaveDTO input = new ProductTaskSaveDTO().setName("test");
    ProductTaskSaveDTO saved = new ProductTaskSaveDTO().setName("test").setId(1L);
    when(taskService.saveTask(any(), any())).thenReturn(saved);

    // 构造 mock multipart request
    MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
    request.addParameter("dto", JsonUtils.toJson(input));

    ResponseEntity<ApiResponse<ProductTaskSaveDTO>> response = controller.create(request);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().data().getId()).isEqualTo(1L);
    assertThat(response.getBody().data().getName()).isEqualTo("test");
}
```

- [ ] **Step 2: 运行测试**

Run: `mvn test -pl . -Dtest=ProductTaskControllerTest -q`
Expected: PASS

- [ ] **Step 3: 运行全部测试**

Run: `mvn test -q`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 4: Commit**
