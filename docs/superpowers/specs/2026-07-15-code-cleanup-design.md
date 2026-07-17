# 代码清理 — 设计文档

> 设计日期: 2026-07-15
> 基于: `docs/superpowers/specs/2026-07-14-gap-tracker.md` N1, G5, G6, G7

---

## 1. N1 — 删除 Self-loop 后端逻辑

Self-loop 改为纯前端配置（与 CONTEXT.md 定义一致），后端不再做自动重启。

### 1.1 MissionOrchestrator.java

删除：
- `MAX_SELF_LOOPS` 常量
- `selfLoopCounts` Map 字段及所有引用
- `ApplicationEventPublisher publisher` 字段及构造参数（唯一用途是自循环事件发布）
- `LocalSettings settings` 字段及构造参数（唯一用途是 `settings.selfLoopEnabled()`）
- `shouldSelfLoop` 局部变量
- `isMissionOk()` 私有方法（仅在 onCompleted 自循环分支中调用）
- `handleRestart()` 方法（唯一 `@Async` `@EventListener` 方法）
- `MissionCompletedEvent` 发布逻辑

`onCompleted` 回调简化为：
```java
engine.onCompleted(recordId -> cleanup(missionId));
```

`onFaulted` 回调删除 `selfLoopCounts.remove(missionId)` 行。

`trigger()` 方法保留 ProductMission / bolts / productCode / partsCode 参数，不再包含自循环逻辑。

删除 import：`@Async`、`@EventListener`、`ApplicationEventPublisher`、`MissionCompletedEvent`。

### 1.2 LifecycleEngineFactory.java

`createEngine()` 删除 `boolean shouldSelfLoop` 参数。`MissionContext.builder()` 不再设置 `.shouldSelfLoop(...)`。

### 1.3 MissionContext.java

删除 `shouldSelfLoop` 字段（`@Builder.Default @Setter private boolean shouldSelfLoop`）。

### 1.4 LifecycleEngine.java

删除 `ctx.setShouldSelfLoop(false)` 调用（第 196 行，SkipScrew 路径）。

### 1.5 MissionCompletedEvent.java

删除整个文件。该 record 仅用于自循环重启事件，发布者和消费者都在 MissionOrchestrator 内部。

### 1.6 LocalSettings.java

**保留** `selfLoopEnabled` 字段不变。前端通过 getSettings API 读取，后端不再消费。

---

## 2. G5 — 枚举清理

### 2.1 删除 DeleteStatus.java

MyBatis-Plus 全局软删除已配置（`logic-delete-field: deleted`），`BaseEntity.deleted` 由框架自动处理，无需此枚举。

### 2.2 删除 TCPCommand.java

零引用。TOOL_ENABLE/DISABLE/PARAMETER_SET 与现有协议命令模型不匹配。

### 2.3 ProductMission 使用 InspectionScope 枚举

`ProductMission.java` 和 `ProductMissionDTO.java` 的 `inspectionScope` 从 `Integer` 改为 `InspectionScope`。

`InspectionScope` 枚举加 `@EnumValue` + `@JsonValue` 在 `code` 字段上（MyBatis-Plus 读写 + Jackson 序列化为整数），`@JsonCreator` 在 `fromCode()` 上（Jackson 反序列化）。`application.yaml` 的 `mybatis-plus.configuration` 段增加 `default-enum-type-handler: com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler`，MyBatis-Plus 3.5.16 自动接管所有 `@EnumValue` 枚举。

### 2.4 保留 → 已删除（2026-07-17 体检后）

- ~~**IoDeviceType**~~ — 已于 2026-07-17 删除。ARRANGER/SETTER_SELECTOR 激活时直接使用 integer 常量即可
- ~~**ReworkStatus**~~ — 已于 2026-07-17 删除。`mission_record.is_rework` 字段当前用 Integer，无需枚举

---

## 3. G6 — 清理死配置

### 3.1 保留 FieldDescription → 已删除（2026-07-17 体检后）

零引用且 i18n 方案未定，已于 2026-07-17 删除。后续 i18n 需要时按实际方案重新添加。

### 3.2 application.yaml

- 第 81 行：`com.tightening.repository: DEBUG` → `com.tightening.mapper: DEBUG`（修 bug：包 `repository` 不存在，SQL 日志从未输出）
- 第 26-28 行：删除 `spring.jpa.hibernate.ddl-auto: validate`（MyBatis-Plus 项目，无 JPA）
- 第 83-88 行：删除 `monitoring.*` 配置块（无 `@ConfigurationProperties`，零 Java 引用）

### 3.3 application-dev.yml

删除第 16 行空 `atlas:` 段（在 `tool-control:` 下）。

---

## 4. G7 — 删除 TCPDeviceHandler 空 close()

- 删除 `implements Closeable`
- 删除空的 `close()` 方法
- 删除 `import java.io.Closeable`

`DeviceHandler` 接口无 `close()`，外部零调用。`NioEventLoopGroup` 由 `NettyConfig` 统一管理生命周期。

---

## 变更汇总

| 项 | 文件 | 动作 |
|------|------|------|
| N1 | `MissionOrchestrator.java` | 删除自循环逻辑、publisher、isMissionOk、handleRestart |
| N1 | `LifecycleEngineFactory.java` | 删除 shouldSelfLoop 参数 |
| N1 | `MissionContext.java` | 删除 shouldSelfLoop 字段 |
| N1 | `LifecycleEngine.java` | 删除 setShouldSelfLoop(false) |
| N1 | `MissionCompletedEvent.java` | **删除** |
| G5 | `DeleteStatus.java` | **删除** |
| G5 | `TCPCommand.java` | **删除** |
| G5 | `InspectionScope.java` | 加 `@EnumValue`、`@JsonValue`、`@JsonCreator`，`fromCode` 改直接返回 |
| G5 | `ProductMission.java` | `Integer inspectionScope` → `InspectionScope inspectionScope` |
| G5 | `ProductMissionDTO.java` | `Integer inspectionScope` → `InspectionScope inspectionScope` |
| G5 | `application.yaml`（main+test） | 加 `default-enum-type-handler` |
| G6 | `application.yaml` | 修 repository→mapper，删 jpa 块，删 monitoring 块 |
| G6 | `application-dev.yml` | 删空 atlas 段 |
| G7 | `TCPDeviceHandler.java` | 删 Closeable、close()、import |
