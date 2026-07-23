# 条码规则 clientRef 关联 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 前端通过 `clientRef`/`barcodeRuleRef` 在一次 saveTask 请求中关联新增条码规则和前置条件

**Architecture:** DTO 层新增两个 String 字段做客户端引用，`diffBarcodeRules` 保存规则后构建 clientRef→realId 映射，`diffPrerequisites` 用该映射解析 `barcodeRuleRef`，校验逻辑委托至 `TaskConfigValidator`

**Tech Stack:** Java 21, Spring Boot 3.5.10, MyBatis-Plus 3.5.9, JUnit 5 + AssertJ 3.27.7

## Global Constraints

- 解析与校验顺序见 spec 第 52-61 行
- 只新增规则（`id == null`）设置 `clientRef`
- `barcodeRuleRef` 和 `barcodeRuleId` 互斥，同时存在抛异常
- MATERIAL_TRACE 必须有 barcode 引用，非 MATERIAL_TRACE 不可有
- 校验放 `TaskConfigValidator`，解析放 `diffPrerequisites`

---

### Task 1: DTO 字段新增

**Files:**
- Modify: `src/main/java/com/tightening/dto/BarCodeRuleSaveItem.java`
- Modify: `src/main/java/com/tightening/dto/PrerequisiteSaveItem.java`

**Interfaces:**
- Produces: `BarCodeRuleSaveItem.clientRef: String` — 前端生成的客户端唯一标识
- Produces: `PrerequisiteSaveItem.barcodeRuleRef: String` — 引用 `BarCodeRuleSaveItem.clientRef`

- [ ] **Step 1: BarCodeRuleSaveItem 加 clientRef**

在 `BarCodeRuleSaveItem` 的 `seq` 字段后添加：

```java
private String clientRef;
```

- [ ] **Step 2: PrerequisiteSaveItem 加 barcodeRuleRef**

在 `PrerequisiteSaveItem` 的 `barcodeRuleId` 字段后添加：

```java
private String barcodeRuleRef;
```

- [ ] **Step 3: 验证编译**

```bash
mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tightening/dto/BarCodeRuleSaveItem.java src/main/java/com/tightening/dto/PrerequisiteSaveItem.java
git commit -m "feat: add clientRef and barcodeRuleRef fields for barcode-to-prerequisite correlation"
```

---

### Task 2: TaskConfigValidator 新增校验方法 + 测试

**Files:**
- Modify: `src/main/java/com/tightening/service/TaskConfigValidator.java`
- Modify: `src/test/java/com/tightening/service/TaskConfigValidatorTest.java`

**Interfaces:**
- Produces: `TaskConfigValidator.validateBarcodeRuleForPrerequisite(BarCodeMatchingRule rule, PrerequisiteType prerequisiteType)` — 校验条码规则与前置类型的匹配

- [ ] **Step 1: 写失败测试**

在 `TaskConfigValidatorTest` 的 `src/test/java/com/tightening/service/TaskConfigValidatorTest.java` 中添加 nested test class：

```java
@Nested
@DisplayName("validateBarcodeRuleForPrerequisite")
class ValidateBarcodeRuleForPrerequisite {

    @Test
    @DisplayName("MATERIAL_TRACE + null 规则 -> 抛异常")
    void shouldRejectMaterialTraceWithNullRule() {
        assertThatThrownBy(() -> validator.validateBarcodeRuleForPrerequisite(null, PrerequisiteType.MATERIAL_TRACE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MATERIAL_TRACE 前置必须关联条码规则");
    }

    @Test
    @DisplayName("MATERIAL_TRACE + PRODUCT_TRACE 规则 -> 抛异常")
    void shouldRejectMaterialTraceWithNonMaterialRule() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode());
        assertThatThrownBy(() -> validator.validateBarcodeRuleForPrerequisite(rule, PrerequisiteType.MATERIAL_TRACE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("必须是 MATERIAL_BARCODE 类型");
    }

    @Test
    @DisplayName("非 MATERIAL_TRACE + 非 null 规则 -> 抛异常")
    void shouldRejectNonMaterialTraceWithRule() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode());
        assertThatThrownBy(() -> validator.validateBarcodeRuleForPrerequisite(rule, PrerequisiteType.SAME_TRACE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("只有 MATERIAL_TRACE 前置可以关联条码规则");
    }

    @Test
    @DisplayName("SAME_TRACE + null 规则 -> 通过")
    void shouldAllowNonMaterialTraceWithNullRule() {
        assertThatCode(() -> validator.validateBarcodeRuleForPrerequisite(null, PrerequisiteType.SAME_TRACE))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MATERIAL_TRACE + MATERIAL_BARCODE 规则 -> 通过")
    void shouldAllowMaterialTraceWithMaterialRule() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode());
        assertThatCode(() -> validator.validateBarcodeRuleForPrerequisite(rule, PrerequisiteType.MATERIAL_TRACE))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("INSPECTION_CHAIN + null 规则 -> 通过")
    void shouldAllowInspectionChainWithNullRule() {
        assertThatCode(() -> validator.validateBarcodeRuleForPrerequisite(null, PrerequisiteType.INSPECTION_CHAIN))
            .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -pl . -Dtest="TaskConfigValidatorTest#ValidateBarcodeRuleForPrerequisite" -DfailIfNoTests=false
```
Expected: 编译失败（方法不存在）

- [ ] **Step 3: 实现校验方法**

在 `TaskConfigValidator` 中添加：

```java
public void validateBarcodeRuleForPrerequisite(BarCodeMatchingRule rule, PrerequisiteType prerequisiteType) {
    if (PrerequisiteType.MATERIAL_TRACE == prerequisiteType) {
        if (rule == null) {
            throw new IllegalArgumentException("MATERIAL_TRACE 前置必须关联条码规则");
        }
        if (BarCodeRuleType.MATERIAL_BARCODE.getCode() != rule.getRuleType()) {
            throw new IllegalArgumentException("前置关联的条码规则必须是 MATERIAL_BARCODE 类型");
        }
    } else {
        if (rule != null) {
            throw new IllegalArgumentException("只有 MATERIAL_TRACE 前置可以关联条码规则");
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -pl . -Dtest="TaskConfigValidatorTest" -DfailIfNoTests=false
```
Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tightening/service/TaskConfigValidator.java src/test/java/com/tightening/service/TaskConfigValidatorTest.java
git commit -m "feat: add barcode rule prerequisite validation in TaskConfigValidator"
```

---

### Task 3: BarcodeDiffResult + diffBarcodeRules 改造

**Files:**
- Modify: `src/main/java/com/tightening/service/ProductTaskService.java`

**Interfaces:**
- Produces: `BarcodeDiffResult(List<BarCodeMatchingRule> rules, Map<String, Long> clientRefMap)` — private record
- Consumes: `BarCodeRuleSaveItem.clientRef` (from Task 1)

- [ ] **Step 1: 添加 BarcodeDiffResult record 和 import**

在 `ProductTaskService` 类内部末尾（`}` 前）添加 record，并在文件顶部添加 `HashMap` import：

```java
import java.util.HashMap;

// 类内部末尾:
private record BarcodeDiffResult(List<BarCodeMatchingRule> rules, Map<String, Long> clientRefMap) {}
```

- [ ] **Step 2: 改造 diffBarcodeRules 返回 BarcodeDiffResult**

修改 `diffBarcodeRules` 方法签名和内部逻辑，在循环内构建 `clientRefMap`：

```java
private BarcodeDiffResult diffBarcodeRules(Long taskId, List<BarCodeRuleSaveItem> dtoItems) {
    List<BarCodeMatchingRule> existing = barcodeRuleService.lambdaQuery()
            .eq(BarCodeMatchingRule::getProductTaskId, taskId)
            .eq(BarCodeMatchingRule::getDeleted, 0)
            .list();

    Set<Long> dtoIds = new HashSet<>();
    List<BarCodeMatchingRule> result = new ArrayList<>();
    Map<String, Long> clientRefMap = new HashMap<>();

    if (dtoItems != null) {
        for (BarCodeRuleSaveItem item : dtoItems) {
            BarCodeMatchingRule entity = Converter.dto2Entity(item, BarCodeMatchingRule::new);
            entity.setProductTaskId(taskId);
            entity.setRuleType(item.getRuleType().getCode());
            validator.validateKeyCharLength(entity);
            if (BarCodeRuleType.PRODUCT_TRACE.getCode() == entity.getRuleType()) {
                validator.validateProductTraceUnique(taskId, entity.getId());
            }
            if (item.getId() != null) {
                dtoIds.add(item.getId());
                barcodeRuleService.updateById(entity);
            } else {
                barcodeRuleService.save(entity);
                if (item.getClientRef() != null) {
                    clientRefMap.put(item.getClientRef(), entity.getId());
                }
            }
            result.add(entity);
        }
    }

    for (BarCodeMatchingRule ex : existing) {
        if (!dtoIds.contains(ex.getId())) {
            barcodeRuleService.removeById(ex.getId());
        }
    }

    return new BarcodeDiffResult(result, clientRefMap);
}
```

- [ ] **Step 3: 验证编译**

```bash
mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tightening/service/ProductTaskService.java
git commit -m "feat: return BarcodeDiffResult from diffBarcodeRules with clientRef mapping"
```

---

### Task 4: diffPrerequisites 改造 + saveTask 接线

**Files:**
- Modify: `src/main/java/com/tightening/service/ProductTaskService.java`

**Interfaces:**
- Consumes: `BarcodeDiffResult` (from Task 3), `PrerequisiteSaveItem.barcodeRuleRef` (from Task 1), `TaskConfigValidator.validateBarcodeRuleForPrerequisite` (from Task 2)
- Produces: 无新接口

- [ ] **Step 1: 修改 diffPrerequisites 签名并添加解析/校验逻辑**

将 `diffPrerequisites` 签名改为接收 `BarcodeDiffResult`，并在 for 循环内添加 barcode 解析逻辑（在 `entity.setPrerequisiteType` 之后，`save/update` 之前）：

```java
private void diffPrerequisites(Long taskId, List<PrerequisiteSaveItem> dtoItems, BarcodeDiffResult barcodeResult) {
    List<TaskPrerequisite> existing = prerequisiteService.lambdaQuery()
            .eq(TaskPrerequisite::getTaskId, taskId)
            .eq(TaskPrerequisite::getDeleted, 0)
            .list();

    Set<Long> dtoIds = new HashSet<>();

    if (dtoItems != null && !dtoItems.isEmpty()) {
        List<Long> targetIds = dtoItems.stream()
                .map(PrerequisiteSaveItem::getPrerequisiteTaskId).toList();
        List<ProductTask> targets = lambdaQuery().in(ProductTask::getId, targetIds).list();
        Map<Long, ProductTask> targetMap = targets.stream()
                .collect(java.util.stream.Collectors.toMap(ProductTask::getId, m -> m));

        for (PrerequisiteSaveItem item : dtoItems) {
            TaskPrerequisite entity = Converter.dto2Entity(item, TaskPrerequisite::new);
            entity.setTaskId(taskId);
            entity.setPrerequisiteType(item.getPrerequisiteType().getCode());
            validator.validateNoCircularDependency(taskId, item.getPrerequisiteTaskId());
            validator.validatePrerequisiteType(targetMap.get(item.getPrerequisiteTaskId()), item.getPrerequisiteType());

            // Resolve barcodeRuleId
            Long resolvedBarcodeRuleId;
            if (item.getBarcodeRuleRef() != null) {
                if (item.getBarcodeRuleId() != null) {
                    throw new IllegalArgumentException("barcodeRuleRef 和 barcodeRuleId 不能同时存在");
                }
                resolvedBarcodeRuleId = barcodeResult.clientRefMap().get(item.getBarcodeRuleRef());
                if (resolvedBarcodeRuleId == null) {
                    throw new IllegalArgumentException("找不到 clientRef='" + item.getBarcodeRuleRef() + "' 对应的条码规则");
                }
                entity.setBarcodeRuleId(resolvedBarcodeRuleId);
            } else {
                resolvedBarcodeRuleId = item.getBarcodeRuleId();
            }

            // Validate barcode rule for prerequisite type
            BarCodeMatchingRule rule = null;
            if (resolvedBarcodeRuleId != null) {
                rule = barcodeResult.rules().stream()
                        .filter(r -> resolvedBarcodeRuleId.equals(r.getId()))
                        .findFirst().orElse(null);
            }
            validator.validateBarcodeRuleForPrerequisite(rule, item.getPrerequisiteType());

            if (item.getId() != null) {
                dtoIds.add(item.getId());
                prerequisiteService.updateById(entity);
            } else {
                prerequisiteService.save(entity);
            }
        }
    }

    for (TaskPrerequisite ex : existing) {
        if (!dtoIds.contains(ex.getId())) {
            prerequisiteService.removeById(ex.getId());
        }
    }
}
```

- [ ] **Step 2: 修改 saveTask 接线**

在 `saveTask` 中，将 line 94-97 改为：

```java
BarcodeDiffResult barcodeResult = diffBarcodeRules(taskId, dto.getBarcodeRules());
validator.validateBarcodeRules(barcodeResult.rules());

diffPrerequisites(taskId, dto.getPrerequisites(), barcodeResult);
```

- [ ] **Step 3: 验证编译**

```bash
mvn compile -q
```

- [ ] **Step 4: 运行全部测试**

```bash
mvn test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tightening/service/ProductTaskService.java
git commit -m "feat: resolve barcodeRuleRef in diffPrerequisites via clientRef mapping"
```
