# Stage 5: 触发阶段 (Trigger Phase) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Task 生命周期的触发入口 — 产品码校验、物料码校验、触发管道、SkipScrew 快速通道、CheckCanActivate 门控。

**Architecture:** 两条独立路径：(1) 引擎外的 REST 校验端点（纯查询，复用已有 `BarCodeMatchingRule` 位置匹配）；(2) 引擎内的触发管道（TriggerRequest → 3 个触发 Capability → CheckCanActivate → onTriggered → 正常生命周期）。触发 Capability 独立于 4 阶段管道，由引擎单独执行。

**Tech Stack:** Java 21, Spring Boot 3.5.10, JUnit 5 + AssertJ 3.27.7, MyBatis-Plus 3.5.9

**Spec:** `docs/superpowers/specs/2026-06-29-stage-5-trigger-phase-design.md`

## Global Constraints

- Java 21
- 包名 `com.tightening.*`
- 使用 Lombok（`@Slf4j`, `@Getter`, `@Setter`）
- 测试用 JUnit 5 + AssertJ，`mvn test` 验证
- 触发 Capability 不注册到 PipelineDefinition 的 4 阶段管道
- 条码仅在 TaskContext 内存中持有，不持久化
- 已有实体（BarCodeMatchingRule, ProductTask）不变更

---

### Task 1: TaskContext 新增条码字段

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/TaskContext.java`

**Interfaces:**
- Produces: `TaskContext.getProductCode(): String`, `TaskContext.getPartsCode(): String`, `TaskContext.setProductCode(String)`, `TaskContext.setPartsCode(String)`

- [ ] **Step 1: 添加字段并验证编译**

在 `TaskContext` 的 Builder 中添加两个字段：

```java
// 在 @Builder.Default private final List<TighteningData> tighteningDataList 之后
/** 产品追溯码（触发阶段写入，生命周期内不可变） */
@Builder.Default @Setter
private String productCode = null;
/** 物料码（触发阶段写入，生命周期内不可变） */
@Builder.Default @Setter
private String partsCode = null;
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -q 2>&1
```
Expected: 无错误输出

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tightening/lifecycle/TaskContext.java
git commit -m "$(cat <<'EOF'
feat: add productCode and partsCode fields to TaskContext

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: TriggerRequest 消息类型

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/message/InboundCommand.java`

**Interfaces:**
- Produces: `InboundCommand.TriggerRequest(String productCode, String partsCode)`

- [ ] **Step 1: 添加 TriggerRequest**

在 `InboundCommand.java` 的 sealed 接口中新增 record，在 `InterruptTask` 之后：

```java
/** 触发激活请求 — 携带条码信息，投递到引擎 inbox */
record TriggerRequest(
    @Nullable String productCode,
    @Nullable String partsCode
) implements InboundCommand {}
```

添加 import：

```java
import org.springframework.lang.Nullable;
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -q 2>&1
```
Expected: 无错误输出

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tightening/lifecycle/message/InboundCommand.java
git commit -m "$(cat <<'EOF'
feat: add TriggerRequest message type for InboundCommand

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: TriggerCapability 标记接口 + BarcodeMatcher 工具类

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/TriggerCapability.java`
- Create: `src/main/java/com/tightening/util/BarcodeMatcher.java`
- Create: `src/test/java/com/tightening/util/BarcodeMatcherTest.java`

**Interfaces:**
- Produces: `TriggerCapability` (extends `Capability`, 标记接口), `BarcodeMatcher.matches(BarCodeMatchingRule, String): boolean`

- [ ] **Step 1: 创建 TriggerCapability**

```java
package com.tightening.lifecycle.capability;

/** 触发阶段的 Capability，不属于 4 阶段管道。继承 Capability 但不用于 PipelineDefinition 注册。 */
public interface TriggerCapability extends Capability {
}
```

- [ ] **Step 2: 创建 BarcodeMatcher**

```java
package com.tightening.util;

import com.tightening.entity.BarCodeMatchingRule;

public final class BarcodeMatcher {

    private BarcodeMatcher() {}

    /**
     * 对条码做位置匹配。
     * keyStartPosition/keyEndPosition 均未配置 → 无位置约束，放行；
     * expectedLength 非空 → 先检查长度。
     */
    public static boolean matches(BarCodeMatchingRule rule, String code) {
        if (code == null) return false;
        if (rule.getExpectedLength() != null && code.length() != rule.getExpectedLength()) return false;
        if (rule.getKeyStartPosition() == null || rule.getKeyEndPosition() == null) return true;
        int end = Math.min(rule.getKeyEndPosition(), code.length());
        int start = Math.min(rule.getKeyStartPosition(), end);
        return code.substring(start, end).equals(rule.getKeyChar());
    }
}
```

- [ ] **Step 3: 写 BarcodeMatcher 测试**

```java
package com.tightening.util;

import com.tightening.entity.BarCodeMatchingRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BarcodeMatcher")
class BarcodeMatcherTest {

    @Test
    @DisplayName("code 为 null → false")
    void nullCode() {
        assertThat(BarcodeMatcher.matches(new BarCodeMatchingRule(), null)).isFalse();
    }

    @Test
    @DisplayName("位置匹配成功 → true")
    void positionMatch() {
        var rule = new BarCodeMatchingRule()
                .setKeyStartPosition(0).setKeyEndPosition(3).setKeyChar("ABC");
        assertThat(BarcodeMatcher.matches(rule, "ABC123")).isTrue();
    }

    @Test
    @DisplayName("位置不匹配 → false")
    void positionNoMatch() {
        var rule = new BarCodeMatchingRule()
                .setKeyStartPosition(0).setKeyEndPosition(3).setKeyChar("XYZ");
        assertThat(BarcodeMatcher.matches(rule, "ABC123")).isFalse();
    }

    @Test
    @DisplayName("仅 expectedLength — 长度符合则通过，不符合则拒绝")
    void expectedLengthOnly() {
        var rule = new BarCodeMatchingRule().setExpectedLength(6);
        assertThat(BarcodeMatcher.matches(rule, "ABC123")).isTrue();
        assertThat(BarcodeMatcher.matches(rule, "ABC")).isFalse();
    }

    @Test
    @DisplayName("无任何约束 → 任意匹配")
    void noConstraints() {
        assertThat(BarcodeMatcher.matches(new BarCodeMatchingRule(), "ANYTHING")).isTrue();
    }
}
```

- [ ] **Step 4: 运行测试**

```bash
mvn test -Dtest=BarcodeMatcherTest -DfailIfNoTests=false 2>&1
```
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tightening/lifecycle/capability/TriggerCapability.java \
        src/main/java/com/tightening/util/BarcodeMatcher.java \
        src/test/java/com/tightening/util/BarcodeMatcherTest.java
git commit -m "$(cat <<'EOF'
feat: add TriggerCapability marker interface and BarcodeMatcher utility

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

### Task 4: BarcodeValidationService — 条码匹配逻辑

**Files:**
- Create: `src/main/java/com/tightening/service/BarcodeValidationService.java`
- Create: `src/test/java/com/tightening/service/BarcodeValidationServiceTest.java`

**Interfaces:**
- Consumes: `BarCodeMatchingRuleService.listByTaskId(Long)`, `BarCodeMatchingRule` 实体
- Produces: `BarcodeValidationService.validateProductCode(Long taskId, String code): ProductCodeResult`, `BarcodeValidationService.validatePartsCode(Long taskId, String code): boolean`

**ProductCodeResult** 为内部 record：

```java
public record ProductCodeResult(boolean matched, Long suggestedTaskId) {
    public static ProductCodeResult matched() { return new ProductCodeResult(true, null); }
    public static ProductCodeResult notMatched() { return new ProductCodeResult(false, null); }
    public static ProductCodeResult wrongTask(Long id) { return new ProductCodeResult(false, id); }
}
```

- [ ] **Step 1: 写测试**

```java
package com.tightening.service;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.entity.BarCodeMatchingRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BarcodeValidationService")
class BarcodeValidationServiceTest {

    @Mock BarCodeMatchingRuleService ruleService;

    BarcodeValidationService service;

    @BeforeEach
    void setUp() { service = new BarcodeValidationService(ruleService); }

    @Nested
    @DisplayName("validateProductCode")
    class ValidateProductCode {

        @Test
        @DisplayName("无 PRODUCT_TRACE 规则 → matched")
        void noProductTraceRule() {
            when(ruleService.listByTaskId(1L)).thenReturn(List.of());

            var result = service.validateProductCode(1L, "ABC123");

            assertThat(result.matched()).isTrue();
            assertThat(result.suggestedTaskId()).isNull();
        }

        @Test
        @DisplayName("有规则且位置匹配 → matched")
        void positionMatch() {
            var rule = new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode())
                    .setKeyStartPosition(0).setKeyEndPosition(2).setKeyChar("AB")
                    .setExpectedLength(6).setProductTaskId(1L);
            when(ruleService.listByTaskId(1L)).thenReturn(List.of(rule));

            var result = service.validateProductCode(1L, "ABC123");

            assertThat(result.matched()).isTrue();
        }

        @Test
        @DisplayName("有规则但不匹配当前 Task → 查其它 Task")
        void wrongTask() {
            var rule1 = new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode())
                    .setKeyStartPosition(0).setKeyEndPosition(1).setKeyChar("X")
                    .setExpectedLength(6).setProductTaskId(1L);
            when(ruleService.listByTaskId(1L)).thenReturn(List.of(rule1));
            // 模拟全库查询：只有 task 2 匹配
            when(ruleService.list()).thenReturn(List.of(
                rule1,
                new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode())
                    .setKeyStartPosition(0).setKeyEndPosition(1).setKeyChar("A")
                    .setExpectedLength(6).setProductTaskId(2L)
            ));

            var result = service.validateProductCode(1L, "ABC123");

            assertThat(result.matched()).isFalse();
            assertThat(result.suggestedTaskId()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("validatePartsCode")
    class ValidatePartsCode {

        @Test
        @DisplayName("无 PARTS_BARCODE 规则 → true")
        void noPartsRule() {
            when(ruleService.listByTaskId(1L)).thenReturn(List.of());

            assertThat(service.validatePartsCode(1L, "MAT456")).isTrue();
        }

        @Test
        @DisplayName("有规则且匹配 → true")
        void match() {
            var rule = new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.PARTS_BARCODE.getCode())
                    .setKeyStartPosition(0).setKeyEndPosition(2).setKeyChar("MA")
                    .setExpectedLength(6).setProductTaskId(1L);
            when(ruleService.listByTaskId(1L)).thenReturn(List.of(rule));

            assertThat(service.validatePartsCode(1L, "MAT456")).isTrue();
        }

        @Test
        @DisplayName("有规则但不匹配 → false")
        void noMatch() {
            var rule = new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.PARTS_BARCODE.getCode())
                    .setKeyStartPosition(0).setKeyEndPosition(2).setKeyChar("XX")
                    .setExpectedLength(6).setProductTaskId(1L);
            when(ruleService.listByTaskId(1L)).thenReturn(List.of(rule));

            assertThat(service.validatePartsCode(1L, "MAT456")).isFalse();
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=BarcodeValidationServiceTest -DfailIfNoTests=false 2>&1
```
Expected: FAIL (class not found)

- [ ] **Step 3: 实现 BarcodeValidationService**

```java
package com.tightening.service;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.entity.BarCodeMatchingRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.tightening.util.BarcodeMatcher;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BarcodeValidationService {

    private final BarCodeMatchingRuleService ruleService;

    public record ProductCodeResult(boolean matched, Long suggestedTaskId) {
        public static ProductCodeResult matched() { return new ProductCodeResult(true, null); }
        public static ProductCodeResult notMatched() { return new ProductCodeResult(false, null); }
        public static ProductCodeResult wrongTask(Long id) { return new ProductCodeResult(false, id); }
    }

    public ProductCodeResult validateProductCode(Long taskId, String productCode) {
        List<BarCodeMatchingRule> rules = ruleService.listByTaskId(taskId).stream()
                .filter(r -> r.getRuleType() == BarCodeRuleType.PRODUCT_TRACE.getCode())
                .toList();

        if (rules.isEmpty()) return ProductCodeResult.matched();
        if (rules.stream().anyMatch(r -> BarcodeMatcher.matches(r, productCode))) return ProductCodeResult.matched();

        // 不匹配 → 查其它 Task
        List<BarCodeMatchingRule> allRules = ruleService.list().stream()
                .filter(r -> r.getRuleType() == BarCodeRuleType.PRODUCT_TRACE.getCode())
                .filter(r -> !r.getProductTaskId().equals(taskId))
                .toList();

        return allRules.stream()
                .filter(r -> BarcodeMatcher.matches(r, productCode))
                .findFirst()
                .map(r -> ProductCodeResult.wrongTask(r.getProductTaskId()))
                .orElse(ProductCodeResult.notMatched());
    }

    public boolean validatePartsCode(Long taskId, String partsCode) {
        List<BarCodeMatchingRule> rules = ruleService.listByTaskId(taskId).stream()
                .filter(r -> r.getRuleType() == BarCodeRuleType.PARTS_BARCODE.getCode())
                .toList();

        if (rules.isEmpty()) return true;
        return rules.stream().anyMatch(r -> BarcodeMatcher.matches(r, partsCode));
    }

}
```

所有条码匹配逻辑委托给 `BarcodeMatcher.matches()`。Service 内部的 `matches()` 方法删除。

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest=BarcodeValidationServiceTest -DfailIfNoTests=false 2>&1
```
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tightening/service/BarcodeValidationService.java \
        src/test/java/com/tightening/service/BarcodeValidationServiceTest.java
git commit -m "$(cat <<'EOF'
feat: add BarcodeValidationService with position-based matching

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: DTO — 校验请求/响应与触发请求

**Files:**
- Create: `src/main/java/com/tightening/dto/ValidateProductBarcodeRequest.java`
- Create: `src/main/java/com/tightening/dto/ValidatePartsBarcodeRequest.java`
- Create: `src/main/java/com/tightening/dto/TriggerRequestDto.java`
- Create: `src/main/java/com/tightening/dto/BarcodeValidationResult.java`

**Interfaces:**
- Produces: 4 个 record 供 Controller 使用

- [ ] **Step 1: 创建 DTO**

```java
// ValidateProductBarcodeRequest.java
package com.tightening.dto;

public record ValidateProductBarcodeRequest(String productCode) {}
```

```java
// ValidatePartsBarcodeRequest.java
package com.tightening.dto;

public record ValidatePartsBarcodeRequest(String partsCode) {}
```

```java
// TriggerRequestDto.java
package com.tightening.dto;

import org.springframework.lang.Nullable;

public record TriggerRequestDto(
    @Nullable String productCode,
    @Nullable String partsCode
) {}
```

```java
// BarcodeValidationResult.java
package com.tightening.dto;

import org.springframework.lang.Nullable;

public record BarcodeValidationResult(
    String result,
    @Nullable String reason,
    @Nullable Long suggestedTaskId
) {
    public static BarcodeValidationResult matched() {
        return new BarcodeValidationResult("MATCHED", null, null);
    }
    public static BarcodeValidationResult wrongTask(Long id) {
        return new BarcodeValidationResult("WRONG_MISSION", null, id);
    }
    public static BarcodeValidationResult notMatched() {
        return new BarcodeValidationResult("NOT_MATCHED", "产品追溯码不匹配", null);
    }
    public static BarcodeValidationResult pass() {
        return new BarcodeValidationResult("PASS", null, null);
    }
    public static BarcodeValidationResult fail(String reason) {
        return new BarcodeValidationResult("FAIL", reason, null);
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -q 2>&1
```
Expected: 无错误输出

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tightening/dto/ValidateProductBarcodeRequest.java \
        src/main/java/com/tightening/dto/ValidatePartsBarcodeRequest.java \
        src/main/java/com/tightening/dto/TriggerRequestDto.java \
        src/main/java/com/tightening/dto/BarcodeValidationResult.java
git commit -m "$(cat <<'EOF'
feat: add barcode validation and trigger request DTOs

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: ProductBarCodeCheck Capability + 测试

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/ProductBarCodeCheck.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/ProductBarCodeCheckTest.java`

**Interfaces:**
- Consumes: `BarCodeMatchingRuleService.listByTaskId(Long)`, `TaskContext.getProductCode()`, `TaskContext.getProductTaskId()`
- Produces: `ProductBarCodeCheck` (implements `TriggerCapability`, 触发阶段专用)

- [ ] **Step 1: 写测试**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.ProductTask;
import com.tightening.lifecycle.TaskContext;
import com.tightening.service.BarCodeMatchingRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.tightening.lifecycle.capability.CapabilityResult.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductBarCodeCheck")
class ProductBarCodeCheckTest {

    @Mock BarCodeMatchingRuleService ruleService;
    ProductBarCodeCheck cap;

    @BeforeEach
    void setUp() { cap = new ProductBarCodeCheck(ruleService); }

    @Test
    @DisplayName("id")
    void id() { assertThat(cap.id()).isEqualTo("ProductBarCodeCheck"); }

    @Test
    @DisplayName("无 PRODUCT_TRACE 规则 → Skip")
    void noRule() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask().setId(1L))
                .productCode("ABC").build();
        when(ruleService.listByTaskId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.PARTS_BARCODE.getCode())));

        assertThat(cap.execute(ctx)).isEqualTo(Skip);
    }

    @Test
    @DisplayName("有规则 + productCode 为空 → Fail")
    void rulePresentNoCode() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask().setId(1L))
                .productCode(null).build();
        when(ruleService.listByTaskId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode())
                        .setKeyStartPosition(0).setKeyEndPosition(3).setKeyChar("ABC")));

        assertThat(cap.execute(ctx)).isEqualTo(Fail);
    }

    @Test
    @DisplayName("有规则 + 匹配 → Pass")
    void ruleMatch() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask().setId(1L))
                .productCode("ABC123").build();
        when(ruleService.listByTaskId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode())
                        .setKeyStartPosition(0).setKeyEndPosition(3).setKeyChar("ABC")));

        assertThat(cap.execute(ctx)).isEqualTo(Pass);
    }

    @Test
    @DisplayName("有规则 + 不匹配 → Fail")
    void ruleNoMatch() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask().setId(1L))
                .productCode("XYZ123").build();
        when(ruleService.listByTaskId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode())
                        .setKeyStartPosition(0).setKeyEndPosition(3).setKeyChar("ABC")));

        assertThat(cap.execute(ctx)).isEqualTo(Fail);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=ProductBarCodeCheckTest -DfailIfNoTests=false 2>&1
```
Expected: FAIL

- [ ] **Step 3: 实现 ProductBarCodeCheck**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.lifecycle.TaskContext;
import com.tightening.service.BarCodeMatchingRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ProductBarCodeCheck implements TriggerCapability {

    private final BarCodeMatchingRuleService ruleService;

    @Override public String id() { return "ProductBarCodeCheck"; }
    @Override public Stage stage() { return Stage.VALIDATION; }
    @Override public SubState subState() { return SubState.VALIDATING; }
    @Override public int priority() { return 1; }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        List<BarCodeMatchingRule> rules = ruleService.listByTaskId(ctx.getProductTaskId()).stream()
                .filter(r -> r.getRuleType() == BarCodeRuleType.PRODUCT_TRACE.getCode())
                .toList();

        if (rules.isEmpty()) {
            log.debug("No PRODUCT_TRACE rule for task {}", ctx.getProductTaskId());
            return CapabilityResult.Skip;
        }

        String code = ctx.getProductCode();
        if (code == null || code.isEmpty()) {
            log.warn("PRODUCT_TRACE rule configured but no productCode provided");
            return CapabilityResult.Fail;
        }

        boolean matched = rules.stream().anyMatch(r -> BarcodeMatcher.matches(r, code));
        if (!matched) {
            log.warn("Product code '{}' does not match any rule", code);
            return CapabilityResult.Fail;
        }

        return CapabilityResult.Pass;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest=ProductBarCodeCheckTest -DfailIfNoTests=false 2>&1
```
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tightening/lifecycle/capability/ProductBarCodeCheck.java \
        src/test/java/com/tightening/lifecycle/capability/ProductBarCodeCheckTest.java
git commit -m "$(cat <<'EOF'
feat: add ProductBarCodeCheck trigger capability

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: PartsBarCodeMatching Capability + 测试

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/PartsBarCodeMatching.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/PartsBarCodeMatchingTest.java`

**Interfaces:**
- Consumes: `BarCodeMatchingRuleService.listByTaskId(Long)`, `TaskContext.getPartsCode()`
- Produces: `PartsBarCodeMatching`

- [ ] **Step 1: 写测试**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.ProductTask;
import com.tightening.lifecycle.TaskContext;
import com.tightening.service.BarCodeMatchingRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.tightening.lifecycle.capability.CapabilityResult.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartsBarCodeMatching")
class PartsBarCodeMatchingTest {

    @Mock BarCodeMatchingRuleService ruleService;
    PartsBarCodeMatching cap;

    @BeforeEach
    void setUp() { cap = new PartsBarCodeMatching(ruleService); }

    @Test
    @DisplayName("id")
    void id() { assertThat(cap.id()).isEqualTo("PartsBarCodeMatching"); }

    @Test
    @DisplayName("无 PARTS_BARCODE 规则 → Skip")
    void noRule() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask().setId(1L))
                .partsCode("MAT").build();
        when(ruleService.listByTaskId(1L)).thenReturn(List.of());

        assertThat(cap.execute(ctx)).isEqualTo(Skip);
    }

    @Test
    @DisplayName("有规则 + partsCode 为空 → Fail")
    void rulePresentNoCode() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask().setId(1L))
                .partsCode(null).build();
        when(ruleService.listByTaskId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.PARTS_BARCODE.getCode())
                        .setKeyStartPosition(0).setKeyEndPosition(3).setKeyChar("MAT")));

        assertThat(cap.execute(ctx)).isEqualTo(Fail);
    }

    @Test
    @DisplayName("有规则 + 匹配 → Pass")
    void ruleMatch() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask().setId(1L))
                .partsCode("MAT456").build();
        when(ruleService.listByTaskId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.PARTS_BARCODE.getCode())
                        .setKeyStartPosition(0).setKeyEndPosition(3).setKeyChar("MAT")));

        assertThat(cap.execute(ctx)).isEqualTo(Pass);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=PartsBarCodeMatchingTest -DfailIfNoTests=false 2>&1
```
Expected: FAIL

- [ ] **Step 3: 实现**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.lifecycle.TaskContext;
import com.tightening.service.BarCodeMatchingRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class PartsBarCodeMatching implements TriggerCapability {

    private final BarCodeMatchingRuleService ruleService;

    @Override public String id() { return "PartsBarCodeMatching"; }
    @Override public Stage stage() { return Stage.VALIDATION; }
    @Override public SubState subState() { return SubState.VALIDATING; }
    @Override public int priority() { return 2; }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        List<BarCodeMatchingRule> rules = ruleService.listByTaskId(ctx.getProductTaskId()).stream()
                .filter(r -> r.getRuleType() == BarCodeRuleType.PARTS_BARCODE.getCode())
                .toList();

        if (rules.isEmpty()) {
            log.debug("No PARTS_BARCODE rule for task {}", ctx.getProductTaskId());
            return CapabilityResult.Skip;
        }

        String code = ctx.getPartsCode();
        if (code == null || code.isEmpty()) {
            log.warn("PARTS_BARCODE rule configured but no partsCode provided");
            return CapabilityResult.Fail;
        }

        boolean matched = rules.stream().anyMatch(r -> BarcodeMatcher.matches(r, code));
        if (!matched) {
            log.warn("Parts code '{}' does not match any rule", code);
            return CapabilityResult.Fail;
        }

        return CapabilityResult.Pass;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest=PartsBarCodeMatchingTest -DfailIfNoTests=false 2>&1
```
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tightening/lifecycle/capability/PartsBarCodeMatching.java \
        src/test/java/com/tightening/lifecycle/capability/PartsBarCodeMatchingTest.java
git commit -m "$(cat <<'EOF'
feat: add PartsBarCodeMatching trigger capability

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: SkipScrewCheck Capability + 测试

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/SkipScrewCheck.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/SkipScrewCheckTest.java`

**Interfaces:**
- Consumes: `TaskContext.getTaskData().getSkipScrew()`
- Produces: `SkipScrewCheck` — 当 task.skipScrew=true 时返回 Interrupt

- [ ] **Step 1: 写测试**

```java
package com.tightening.lifecycle.capability;

import com.tightening.entity.ProductTask;
import com.tightening.lifecycle.TaskContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tightening.lifecycle.capability.CapabilityResult.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkipScrewCheck")
class SkipScrewCheckTest {

    SkipScrewCheck cap;

    @BeforeEach
    void setUp() { cap = new SkipScrewCheck(); }

    @Test
    @DisplayName("id")
    void id() { assertThat(cap.id()).isEqualTo("SkipScrewCheck"); }

    @Test
    @DisplayName("skipScrew=false → precondition false")
    void notEnabled() {
        var ctx = TaskContext.builder()
                .taskData(new ProductTask().setSkipScrew(0)).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("skipScrew=true → precondition true, execute returns Interrupt")
    void enabled() {
        var ctx = TaskContext.builder()
                .taskData(new ProductTask().setSkipScrew(1)).build();
        assertThat(cap.precondition(ctx)).isTrue();
        assertThat(cap.execute(ctx)).isEqualTo(Interrupt);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=SkipScrewCheckTest -DfailIfNoTests=false 2>&1
```
Expected: FAIL

- [ ] **Step 3: 实现**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SkipScrewCheck implements TriggerCapability {

    @Override public String id() { return "SkipScrewCheck"; }
    @Override public Stage stage() { return Stage.VALIDATION; }
    @Override public SubState subState() { return SubState.VALIDATING; }
    @Override public int priority() { return 3; }

    @Override
    public boolean precondition(TaskContext ctx) {
        return ctx.getTaskData() != null
            && Integer.valueOf(1).equals(ctx.getTaskData().getSkipScrew());
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        log.info("SkipScrew fast track for task {}", ctx.getProductTaskId());
        return CapabilityResult.Interrupt;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest=SkipScrewCheckTest -DfailIfNoTests=false 2>&1
```
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tightening/lifecycle/capability/SkipScrewCheck.java \
        src/test/java/com/tightening/lifecycle/capability/SkipScrewCheckTest.java
git commit -m "$(cat <<'EOF'
feat: add SkipScrewCheck trigger capability

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: LifecycleEngine — 触发管道 + CheckCanActivate + onTriggered

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngine.java`

**Interfaces:**
- Consumes: `List<TriggerCapability>`（触发 Capability 列表，构造注入）
- Produces: `LifecycleEngine.onTriggered(Consumer)`, `LifecycleEngine.handleTriggerRequest()`, `LifecycleEngine.executeTriggerPipeline()`, `LifecycleEngine.checkCanActivate()`

- [ ] **Step 1: 修改构造器，接收触发 Capability 列表**

在已有构造器参数末尾新增 `List<TriggerCapability> triggerCapabilities`，保存为 `private final List<TriggerCapability> triggerCaps` 字段：

```java
private final List<TriggerCapability> triggerCaps;
private final BarCodeMatchingRuleService barCodeMatchingRuleService;
private Consumer<Long> onTriggered;

public LifecycleEngine(PipelineDefinition pipeline, TaskRecordService taskRecordService,
                       List<Capability> capabilities, List<PersistentMonitor> monitors,
                       List<TriggerCapability> triggerCapabilities,
                       BarCodeMatchingRuleService barCodeMatchingRuleService) {
    this.pipeline = pipeline;
    this.taskRecordService = taskRecordService;
    this.monitors = monitors != null ? monitors : List.of();
    this.triggerCaps = triggerCapabilities != null ? triggerCapabilities : List.of();
    this.barCodeMatchingRuleService = barCodeMatchingRuleService;
    pipeline.registerCapabilities(capabilities).sortByPriority();
    registerDefaultHandlers();
}
```

**重要行为变更**: `LifecycleEngine.start()` 不再自动往 inbox 投递 `ActivateTask`。`start()` 仅启动 actor 线程。初始化消息由调用方显式投递：`startTask` 路径投递 `ActivateTask`；`trigger` 路径投递 `TriggerRequest`。需修改 `start()` 源码，删除其中 `inbox.offer(new InboundCommand.ActivateTask(...))` 逻辑。

- [ ] **Step 2: 注册 TriggerRequest handler**

在 `registerDefaultHandlers()` 中添加：

```java
registerHandler(InboundCommand.TriggerRequest.class, this::handleTriggerRequest);
```

- [ ] **Step 3: 实现 onTriggered 回调**

```java
public void onTriggered(Consumer<Long> callback) { this.onTriggered = callback; }
```

- [ ] **Step 4: 实现 handleTriggerRequest**

```java
void handleTriggerRequest(InboundMessage msg, TaskContext ctx, LifecycleEngine engine) {
    var cmd = (InboundCommand.TriggerRequest) msg;
    log.info("Trigger request: productCode={}, partsCode={}", cmd.productCode(), cmd.partsCode());

    ctx.setProductCode(cmd.productCode());
    ctx.setPartsCode(cmd.partsCode());

    CapabilityResult triggerResult = executeTriggerPipeline(ctx);
    if (triggerResult == CapabilityResult.Fail) {
        log.warn("Trigger pipeline failed");
        if (onFaulted != null) onFaulted.accept("Trigger validation failed");
        shutdown();
        return;
    }

    if (triggerResult == CapabilityResult.Interrupt) {
        // SkipScrew fast track — 不绑定设备，直接创建 OK TaskRecord 进 FINALIZATION
        log.info("SkipScrew fast track — entering FINALIZATION");
        handleActivateTaskSkipScrew(ctx);
        return;
    }

    // trigger pipeline passed
    if (!checkCanActivate(ctx)) {
        log.warn("CheckCanActivate failed");
        if (onFaulted != null) onFaulted.accept("CheckCanActivate failed");
        shutdown();
        return;
    }

    log.info("Trigger passed, entering lifecycle");
    if (onTriggered != null) onTriggered.accept(ctx.getProductTaskId());
    // 正常进入生命周期
    handleActivateTaskInternal(ctx);
}
```

- [ ] **Step 5: 实现 executeTriggerPipeline**

```java
private CapabilityResult executeTriggerPipeline(TaskContext ctx) {
    for (TriggerCapability cap : triggerCaps) {
        if (!cap.precondition(ctx)) continue;
        try {
            CapabilityResult result = cap.execute(ctx);
            switch (result) {
                case Pass, Skip -> {}
                case Fail -> { return CapabilityResult.Fail; }
                case Interrupt -> { return CapabilityResult.Interrupt; }
            }
        } catch (Exception e) {
            ErrorAction action = cap.onError(ctx, e);
            log.error("Trigger capability {} error: {}", cap.id(), e.getMessage());
            if (action == ErrorAction.FAIL_STAGE) return CapabilityResult.Fail;
        }
    }
    return CapabilityResult.Pass;
}
```

- [ ] **Step 6: 实现 checkCanActivate**

```java
private boolean checkCanActivate(TaskContext ctx) {
    // 最终门控：触发管道已校验匹配，此处仅检查"有规则但调用方未提供码"的兜底场景
    // 查询 PRODUCT_TRACE / PARTS_BARCODE 规则，确认需要的码都已提供
    // ruleService 已在构造器中作为 barCodeMatchingRuleService 注入（LifecycleEngine 新增字段）
    List<BarCodeMatchingRule> rules = barCodeMatchingRuleService.listByTaskId(ctx.getProductTaskId());
    boolean hasProductRule = rules.stream().anyMatch(
            r -> r.getRuleType() == BarCodeRuleType.PRODUCT_TRACE.getCode());
    boolean hasPartsRule = rules.stream().anyMatch(
            r -> r.getRuleType() == BarCodeRuleType.PARTS_BARCODE.getCode());
    if (hasProductRule && (ctx.getProductCode() == null || ctx.getProductCode().isEmpty())) {
        log.warn("CheckCanActivate: PRODUCT_TRACE rule configured but no productCode");
        return false;
    }
    if (hasPartsRule && (ctx.getPartsCode() == null || ctx.getPartsCode().isEmpty())) {
        log.warn("CheckCanActivate: PARTS_BARCODE rule configured but no partsCode");
        return false;
    }
    return true;
}
```

- [ ] **Step 7: 实现 handleActivateTaskInternal（从 handleActivateTask 提取）**

```java
private void handleActivateTaskInternal(TaskContext ctx) {
    int boltCount = ctx.getBoltConfigs().size();
    BoltState[] states = new BoltState[boltCount];
    Arrays.fill(states, BoltState.PENDING);
    ctx.setBoltStates(states);
    ctx.setCurrentStage(Stage.VALIDATION);
    ctx.setCurrentSubState(SubState.VALIDATING);
    postMessage(new InboundCommand.AdvancePipeline());
}

private void handleActivateTaskSkipScrew(TaskContext ctx) {
    // 创建 OK TaskRecord — createRecord 默认设 taskResult=NG，需后续 markAsOk
    var record = taskRecordService.createRecord(
            ctx.getProductTaskId(), ctx.getProductCode(), 0);
    taskRecordService.markAsOk(record.getId());
    ctx.setTaskRecord(record);
    ctx.setCurrentStage(Stage.FINALIZATION);
    ctx.setCurrentSubState(SubState.CLEANING_TASKS);
    ctx.setShouldSelfLoop(false);
    postMessage(new InboundCommand.AdvancePipeline());
}
```

`handleActivateTask` 原有逻辑改为调用 `handleActivateTaskInternal`：

```java
void handleActivateTask(InboundMessage msg, TaskContext ctx, LifecycleEngine engine) {
    var cmd = (InboundCommand.ActivateTask) msg;
    log.info("Engine activating task: {}", cmd.taskData().getId());
    handleActivateTaskInternal(ctx);
}
```

- [ ] **Step 8: 验证编译**

```bash
mvn compile -q 2>&1
```
Expected: 无错误输出

- [ ] **Step 9: 运行全部测试确认无回归**

```bash
mvn test -DfailIfNoTests=false 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/tightening/lifecycle/LifecycleEngine.java
git commit -m "$(cat <<'EOF'
feat: add trigger pipeline, CheckCanActivate, and onTriggered to LifecycleEngine

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 10: LifecycleEngineFactory — 注入触发 Capability

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java`

**Interfaces:**
- Consumes: `BarCodeMatchingRuleService`
- Produces: `createEngine()` 传入触发 Capability 列表 + productCode/partsCode

- [ ] **Step 1: 修改 LifecycleEngineFactory**

添加 `BarCodeMatchingRuleService` 依赖，修改 `createEngine` 方法：

```java
@Component
@RequiredArgsConstructor
public class LifecycleEngineFactory {

    private final TaskRecordService taskRecordService;
    private final TighteningDataService tighteningDataService;
    private final ExportTaskService exportTaskService;
    private final LocalSettings settings;
    private final Map<DeviceType, JudgmentStrategy> judgmentStrategies;
    private final BarCodeMatchingRuleService barcodeRuleService;  // 新增

    public LifecycleEngine createEngine(
            ProductTask task,
            List<ProductBolt> bolts,
            Map<Long, ITool> deviceMap,
            boolean shouldSelfLoop,
            String productCode,     // 新增，可为 null
            String partsCode        // 新增，可为 null
    ) {

        TaskContext ctx = TaskContext.builder()
            .productTaskId(task.getId())
            .taskData(task)
            .boltConfigs(bolts)
            .deviceRegistry(deviceMap)
            .shouldSelfLoop(shouldSelfLoop)
            .productCode(productCode)
            .partsCode(partsCode)
            .build();

        PipelineDefinition pipeline = PipelineDefinition.createDefault();

        List<Capability> capabilities = List.of( /* 与之前相同 */ );

        // 触发 Capability — 不在 4 阶段管道中，由 TriggerCapability 类型隔离
        List<TriggerCapability> triggerCapabilities = List.of(
            new ProductBarCodeCheck(barcodeRuleService),
            new PartsBarCodeMatching(barcodeRuleService),
            new SkipScrewCheck()
        );

        List<PersistentMonitor> monitors = List.of(
            new LockStateMonitor(),
            new DeviceConnectionMonitor()
        );

        LifecycleEngine engine = new LifecycleEngine(
            pipeline, taskRecordService, capabilities, monitors, triggerCapabilities,
            barcodeRuleService);

        engine.initContext(ctx);
        engine.onFaulted(reason -> { });
        engine.onCompleted(recordId -> { });

        return engine;
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -q 2>&1
```
Expected: 无错误输出

- [ ] **Step 3: 运行全部测试**

```bash
mvn test -DfailIfNoTests=false 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java
git commit -m "$(cat <<'EOF'
feat: inject trigger capabilities and barcode fields into LifecycleEngineFactory

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 11: TaskOrchestrator — trigger() 方法

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/TaskOrchestrator.java`
- Modify: `src/main/java/com/tightening/lifecycle/TaskCompletedEvent.java` — 新增 `@Nullable String productCode, @Nullable String partsCode` 字段，所有现有发布点传 null

**Interfaces:**
- Consumes: `LifecycleEngineFactory`, `DeviceRegistry`
- Produces: `TaskOrchestrator.trigger(ProductTask, List<ProductBolt>, String productCode, String partsCode): LifecycleEngine`

- [ ] **Step 1: 新增 `trigger()` 方法**

原 `startTask` 方法保留（后续 stage 可从 trigger 调用）。新增 `trigger()` 作为触发阶段入口：

```java
public LifecycleEngine trigger(ProductTask task, List<ProductBolt> bolts,
                                String productCode, String partsCode) {
    Long taskId = task.getId();
    if (activeEngines.containsKey(taskId)) {
        log.warn("Task {} already active", taskId);
        return null;
    }

    int loopCount = selfLoopCounts.getOrDefault(taskId, 0);
    if (loopCount >= MAX_SELF_LOOPS) {
        log.warn("Task {} reached maxSelfLoops", taskId);
        selfLoopCounts.remove(taskId);
        return null;
    }
    selfLoopCounts.put(taskId, loopCount + 1);

    boolean shouldSelfLoop = settings.selfLoopEnabled();
    Map<Long, ITool> devices = deviceRegistry.getAllTools().stream()
            .collect(Collectors.toMap(ITool::id, t -> t));
    LifecycleEngine engine = factory.createEngine(
            task, bolts, devices, shouldSelfLoop,
            productCode, partsCode);

    engine.onTriggered(mId -> {
        // 触发通过 — 建立 device→task 路由映射，启动监控（Actor 线程内安全）
        engine.getContext().getDeviceRegistry().keySet()
                .forEach(deviceId -> deviceToTaskId.put(deviceId, mId));
        engine.startMonitorTicks();
    });

    engine.onCompleted(recordId -> {
        boolean ok = isTaskOk(engine);
        TaskContext ctx = engine.getContext();
        cleanup(taskId);
        if (shouldSelfLoop && ok) {
            publisher.publishEvent(new TaskCompletedEvent(
                    taskId, task, bolts, true,
                    ctx != null ? ctx.getProductCode() : null,
                    ctx != null ? ctx.getPartsCode() : null));
        } else {
            selfLoopCounts.remove(taskId);
        }
    });

    engine.onFaulted(reason -> {
        cleanup(taskId);
        selfLoopCounts.remove(taskId);
        log.warn("Task {} trigger faulted: {}", taskId, reason);
    });

    LifecycleEngine prev = activeEngines.putIfAbsent(taskId, engine);
    if (prev != null) {
        // 并发触发防护 — 另一个请求先到，丢弃当前引擎
        engine.shutdown();
        log.warn("Concurrent trigger for task {}, rejected", taskId);
        return null;
    }
    engine.start(engine.getContext());
    engine.postMessage(new InboundCommand.TriggerRequest(productCode, partsCode));
    log.info("Task {} trigger posted (selfLoop={}, loopCount={})",
            taskId, shouldSelfLoop, loopCount);
    return engine;
}
```

**修复自循环路径** — `handleRestart` 改为调用 `trigger()`。自循环时：无 PRODUCT_TRACE 规则 → event.productCode 为 null → Capability Skip；有规则 → event.productCode 非 null → Capability 正常校验。partsCode 同理。

`TaskCompletedEvent` 新增两个字段（`@Nullable String productCode, @Nullable String partsCode`），onCompleted 回调发布事件时传入 `ctx.getProductCode()` 和 `ctx.getPartsCode()`。

```java
@Async
@EventListener
void handleRestart(TaskCompletedEvent event) {
    if (!event.ok()) return;
    log.info("Restarting task {} (loop {})", event.taskId(),
            selfLoopCounts.getOrDefault(event.taskId(), 0));
    trigger(event.task(), event.bolts(), event.productCode(), event.partsCode());
}
```

**修复 `startTask`** — 仍被旧流程使用（兼容），更新 `createEngine` 调用和初始化逻辑：

```java
boolean shouldSelfLoop = settings.selfLoopEnabled();
LifecycleEngine engine = factory.createEngine(
        task, bolts, devices, shouldSelfLoop, null, null);
// ... onCompleted/onFaulted 回调保持不变，但 TaskCompletedEvent 发布时传 null productCode/partsCode ...
engine.start(engine.getContext());
// start() 不再自动投递 ActivateTask — 调用方显式投递
engine.postMessage(new InboundCommand.ActivateTask(
        task, List.of(), bolts, List.of()));
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -q 2>&1
```
Expected: 无错误输出

- [ ] **Step 3: 运行全部测试**

```bash
mvn test -DfailIfNoTests=false 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tightening/lifecycle/TaskOrchestrator.java
git commit -m "$(cat <<'EOF'
feat: add trigger() with device binding, putIfAbsent, and self-loop barcode reuse

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 12: TaskLifecycleController — 新端点

**Files:**
- Modify: `src/main/java/com/tightening/controller/TaskLifecycleController.java`

**Interfaces:**
- Consumes: `BarcodeValidationService`, `TaskOrchestrator`, `ProductTaskService`, `ProductBoltService`
- Produces: `POST /validate-product-barcode`, `POST /validate-parts-barcode`, `POST /trigger`

- [ ] **Step 1: 重写 Controller**

```java
package com.tightening.controller;

import com.tightening.dto.*;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductTask;
import com.tightening.lifecycle.TaskContext;
import com.tightening.lifecycle.TaskOrchestrator;
import com.tightening.service.BarcodeValidationService;
import com.tightening.service.ProductBoltService;
import com.tightening.service.ProductTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskLifecycleController {

    private final TaskOrchestrator orchestrator;
    private final ProductTaskService taskService;
    private final ProductBoltService boltService;
    private final BarcodeValidationService barcodeService;

    @PostMapping("/{id}/validate-product-barcode")
    public ResponseEntity<ApiResponse<BarcodeValidationResult>> validateProductBarcode(
            @PathVariable Long id,
            @RequestBody ValidateProductBarcodeRequest req) {
        var result = barcodeService.validateProductCode(id, req.productCode());
        if (result.matched()) {
            return ResponseEntity.ok(ApiResponse.ok(BarcodeValidationResult.matched()));
        }
        if (result.suggestedTaskId() != null) {
            return ResponseEntity.ok(ApiResponse.ok(
                    BarcodeValidationResult.wrongTask(result.suggestedTaskId())));
        }
        return ResponseEntity.ok(ApiResponse.ok(BarcodeValidationResult.notMatched()));
    }

    @PostMapping("/{id}/validate-parts-barcode")
    public ResponseEntity<ApiResponse<BarcodeValidationResult>> validatePartsBarcode(
            @PathVariable Long id,
            @RequestBody ValidatePartsBarcodeRequest req) {
        boolean pass = barcodeService.validatePartsCode(id, req.partsCode());
        if (pass) {
            return ResponseEntity.ok(ApiResponse.ok(BarcodeValidationResult.pass()));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                BarcodeValidationResult.fail("物料码不匹配")));
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<ApiResponse<String>> trigger(
            @PathVariable Long id,
            @RequestBody TriggerRequestDto req) {
        if (orchestrator.getActiveEngine(id).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("task already active: " + id));
        }
        ProductTask task = taskService.getById(id);
        if (task == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("task not found: " + id));
        }
        List<ProductBolt> bolts = boltService.listByTaskId(id);
        if (bolts.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("task has no bolts: " + id));
        }
        var engine = orchestrator.trigger(task, bolts,
                req.productCode(), req.partsCode());
        if (engine == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("task already active: " + id));
        }
        return ResponseEntity.accepted()
                .body(ApiResponse.ok("trigger request accepted"));
    }

    // 原有端点保留
    @PostMapping("/{id}/interrupt")
    public ResponseEntity<ApiResponse<String>> interruptTask(@PathVariable Long id) {
        var engine = orchestrator.getActiveEngine(id);
        if (engine.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("no active task: " + id));
        }
        engine.get().interrupt("user interrupt");
        return ResponseEntity.ok(ApiResponse.ok("interrupted"));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TaskStatus>> getTaskStatus(@PathVariable Long id) {
        var engineOpt = orchestrator.getActiveEngine(id);
        if (engineOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    new TaskStatus("idle", null, null, 0, 0, null)));
        }
        var engine = engineOpt.get();
        TaskContext ctx = engine.getContext();
        String status = engine.isAlive() ? "running" : "finished";
        String stage = ctx != null && ctx.getCurrentStage() != null
                ? ctx.getCurrentStage().name() : null;
        String subState = ctx != null && ctx.getCurrentSubState() != null
                ? ctx.getCurrentSubState().name() : null;
        int currentBoltIndex = ctx != null ? ctx.getCurrentBoltIndex() : 0;
        int totalBolts = ctx != null ? ctx.totalBolts() : 0;
        Long taskRecordId = ctx != null && ctx.getTaskRecord() != null
                ? ctx.getTaskRecord().getId() : null;
        return ResponseEntity.ok(ApiResponse.ok(
                new TaskStatus(status, stage, subState, currentBoltIndex, totalBolts, taskRecordId)));
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -q 2>&1
```
Expected: 无错误输出

- [ ] **Step 3: 运行全部测试**

```bash
mvn test -DfailIfNoTests=false 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tightening/controller/TaskLifecycleController.java
git commit -m "$(cat <<'EOF'
feat: add trigger endpoints to TaskLifecycleController

Replace /activate with /validate-product-barcode, /validate-parts-barcode, /trigger

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 13: 集成验证 — 全量测试 + 编译

- [ ] **Step 1: 运行全量测试**

```bash
mvn test -DfailIfNoTests=false 2>&1
```
Expected: `BUILD SUCCESS`，`Tests run: N, Failures: 0, Errors: 0`

- [ ] **Step 2: 确认应用可启动**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 &
sleep 12 && kill %1 2>/dev/null; wait 2>/dev/null
```
Expected: 启动日志中无 ERROR

- [ ] **Step 3: 清理 SDD progress**

```bash
rm -f /Users/streen/IdeaProjects/tightening/.superpowers/sdd/task-*.md
```
(Stage 4 的旧任务文件)

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat: complete Stage 5 — trigger phase with barcode validation pipeline

Includes ProductBarCodeCheck, PartsBarCodeMatching, SkipScrewCheck capabilities,
BarcodeValidationService, new REST endpoints, and trigger pipeline in LifecycleEngine.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```
