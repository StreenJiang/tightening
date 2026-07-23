# Device / Protocol 包优化设计

**日期**: 2026-06-15  
**范围**: `device/` (17 文件) + `netty/protocol/` (18 文件)

---

## 已修复的缺陷（记录，不涉及新设计）

7 个缺陷已在审查过程中修复：TOCTOU 竞态、disconnect 无效 listener、FitFrameCodec 空帧丢弃、AtlasFrame NPE、CurveDataSamples NPE、异常静默吞掉、包1丢失永不超时。详见 plan 文档。

---

## 设计决策 1: InBoundHandler 协议-持久化解耦

### 现状

`AtlasPFSeriesInBoundHandler` 和 `FitSeriesInBoundHandler` 在 Netty pipeline 回调中直接调用 `tighteningDataService.save()`。协议解析层依赖了持久化层。

### 决策

在 `ToolHandler` 上新增回调方法，InBoundHandler 解析 DTO 后委托给 `deviceHandler.handle*()`：

```
InBoundHandler → 解析 DTO → deviceHandler.handleTighteningData(dto, channel)
                                  ├── Converter.dto2Entity
                                  ├── tighteningDataService.save()
                                  └── (将来) SSE 推送、task record 更新
```

- `handleTighteningData(TighteningDataDTO, Channel)` — 拧紧数据
- `handleCurveData(CurveDataDTO, Channel)` — 曲线数据
- `handleAlarm(String, long)` — 报警

### 对比的替代方案

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| ToolHandler 回调方法 | 改动小、无新类、执行顺序明确 | ToolHandler 承担更多职责 | ✅ 选用 |
| Spring 事件总线 | 完全解耦、可扩展 | 回调数量固定（3种），不需要可扩展性；事件执行顺序不透明；增加理解成本 | ❌ |
| 监听器接口 | 类型安全 | 需要注册机制、3 个新接口类、过度抽象 | ❌ |

### 理由

- 回调种类固定（拧紧数据、曲线数据、报警），不是开放式扩展点
- 回调有执行顺序（先 save 再推送），事件总线顺序隐式
- ToolHandler 已持有 `TighteningDataService`，是自然的聚合点
- 改动范围：3 个文件，不新增任何类
- 如果将来真需要 10 个消费者，再升级为事件机制不迟

---

## 设计决策 2: 空壳子类替换为 getSupportedTypes() 注册

### 现状

`AtlasPF4000Handler`、`AtlasPF6000OPHandler`、`FitFTC6Handler` 是纯空壳子类，仅通过 `DeviceType.handlerClass` + `isInstance` 匹配。

### 决策（待差异化需求触发时实施）

1. `DeviceHandler` 接口新增 `Set<DeviceType> getSupportedTypes()`
2. `AtlasPFSeriesHandler` 返回 `{ATLAS_PF4000, ATLAS_PF6000_OP}`
3. `FitSeriesHandler` 返回 `{FIT_FTC6}`
4. `DeviceHandlerFactory` 遍历 `getSupportedTypes()` 注册
5. 删除 3 个空壳类 + `DeviceType.handlerClass` 字段

### 关键验证

- 同一 `AtlasPFSeriesHandler` 实例同时注册到两个 DeviceType 是安全的：`TCPDeviceHandler` 内部用 `ConcurrentHashMap<Long, DeviceHolder>` 按 deviceId 管理设备，本身设计为一个实例处理多台设备
- 将来子型号需要特殊逻辑时：新建子类覆盖 `getSupportedTypes()` 只返回自己，同时从父类移除该枚举值

### 对比的替代方案

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| getSupportedTypes() | 声明式、无空类、可扩展 | 需改接口 | ✅ 选用 |
| Map 配置注入 | 完全外部化 | 运行时类型安全差、需额外 Config 类 | ❌ |
| 保持现状 | 不改 | 每增设备建空文件、isInstance 匹配隐晦 | ❌ |

### 理由

延后实施（等有子型号差异化需求时）。当前空壳类虽然不优雅，但功能正确，改动涉及 5+ 文件包括测试，不值得在没有实际需求时重构。

---

## 设计决策 3: EventLoopGroup 从每实例独立改为共享

### 现状

`TCPDeviceHandler` 构造函数中 `new NioEventLoopGroup()`，目前 3 个 handler 实例 = 3 个 Group。

### 决策

提取共享 `NioEventLoopGroup` 为 Spring Bean，注入到所有 handler。与决策 2 关联——改为注册模式后只剩 2 个 handler 实例（一个 Atlas、一个 FIT）。共享 Group 固定 4 线程。

### 对比的替代方案

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| 共享 EventLoopGroup Bean | 减少线程、配置集中 | 构造器签名变更 | ✅ 选用 |
| 保持独立 | 隔离性最强 | 线程浪费 | ❌ |
| 按需懒创建 | 灵活 | 复杂度增加，收益小 | ❌ |

### 理由

延后实施。与决策 2 一起做，减少重复改动。优先级低，当前线程数在可接受范围。

---

## 设计决策 4: CONNECT case fall-through 显式化

### 现状

`AtlasPFSeriesInBoundHandler.handlePositiveOrNegativeResult` 中 CONNECT case 重映射 key 后依赖 fall-through 到 DISABLE 分支执行 `addResultFuture`。

### 决策

改为每个 case 自包含，CONNECT case 显式调用 `addResultFuture + break`。

### 理由

单行改动，消除隐蔽依赖，防止未来在 CONNECT 和 DISABLE 之间插入新 case 时出错。

---

## 明确不修改的设计点

| 项目 | 原因 |
|------|------|
| rev7→rev2 链式解析 (#19) | 结构清晰映射协议演进，性能影响可忽略 (~60μs vs SQLite ~1-5ms) |
| self=this 构造器逃逸 (#14) | 不影响正确性，handler 仅在连接建立后被 Netty 调用 |
| 重连固定间隔 (#22) | 协议规定 |
| enable/disable 共用 key (#8) | 协议规定，响应帧不区分 enable/disable |
