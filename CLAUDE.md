# Tightening — 工业拧紧流程控制系统

## 项目概述

工业拧紧工具通信中间件。通过 Netty TCP 连接多种品牌的拧紧控制器（Atlas Copco PF 系列、FIT FTC6、速动 X7），接收拧紧数据并持久化到 SQLite，通过 REST API 暴露设备控制能力，内置任务生命周期引擎驱动工位拧紧流程。

**多模块规划**：后续将增加 server Maven module，作为管理端供开发者/管理员登录进行客户配置和系统管理。通过 profile 或配置决定启动拧紧控制系统还是管理端。两个模块共享领域模型和公共服务。

## 质量准则

所有设计和代码实现须对照以下六个维度审查：

| 维度 | 含义 |
|------|------|
| **高可维护性** | 模块职责单一，修改影响范围小，新人可快速定位 |
| **高可读性** | 命名自解释，逻辑直白，不藏隐式约定 |
| **高内聚** | 相关行为聚合一处，不跨层散落 |
| **低耦合** | 模块间通过接口/事件通信，不直接依赖实现类 |
| **高性能** | 关键路径避免锁竞争、不必要拷贝和阻塞 |
| **轻量** | 最小化依赖，代码量克制，不过度抽象 |

## 技术栈

| 组件 | 版本/选型 |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.10 |
| Netty | 4.1.129.Final |
| MyBatis-Plus | 3.5.16 |
| 数据库 | SQLite 3.53.2.0（通过 Flyway 管理迁移） |
| 构建 | Maven |
| 测试 | JUnit 5 + AssertJ 3.27.7 |
| PLC4X | 0.13.1（Modbus TCP / Siemens S7 驱动） |
| jSerialComm | 2.11.4（串口通信） |

## 分层架构

```
controller/          REST API 入口（Device, Login, UserAccountInfo, Sse, ProductTask, TaskLifecycle）
service/             业务逻辑（Device, TighteningData, CurveData, Sse, WorkplaceStatus, Barcode,
                     BoltDeviceBinding, ProductTask, TaskRecord, ExportTask 等）
device/              设备管理层
  device/contract/     IDevice / ITool / ToolAdapter — 面向生命周期引擎的工具抽象
  device/handler/      DeviceHandler → TCPDeviceHandler → ToolHandler 继承链
  device/handler/impl/ 具体协议实现（AtlasPFSeries, FitSeries, SudongSeries, SudongX7）
  device/event/        设备变更事件
  device/type/         设备类型模型（TCPDevice, SerialPortDevice, Arranger）
  DeviceHolder.java    Channel + 状态持有者
  DeviceManager.java   设备生命周期管理 + 扫描调度
  DeviceRegistry.java  ITool 注册中心，监听 DeviceChangeEvent 维护工具注册表
lifecycle/            任务生命周期引擎
  capability/          可插拔的管道能力单元（30+ Capability 实现）
  message/             引擎消息类型（InboundCommand / DeviceEvent / EngineInternal）
  monitor/             持久化监视器（DeviceConnectionMonitor, LockStateMonitor 等）
  LifecycleEngine.java Actor 模型驱动的状态机
  TaskOrchestrator.java 多引擎协调器
  PipelineDefinition.java 阶段→子状态→能力映射表
  TaskContext.java     任务上下文（携带当前阶段、螺栓状态、设备注册表等）
export/               数据导出（事件驱动 outbox 模式：ExportTaskCreatedEvent → ExportWorker）
netty/protocol/       Netty 帧编解码器（Atlas / FIT / 速动 X7 三种协议）
mapper/               MyBatis-Plus Mapper
entity/               JPA 实体（TighteningData, CurveData, Device, TaskRecord, BoltDeviceBinding 等）
dto/                  数据传输对象
constant/             枚举常量（含 atlas/ fit/ sudongx7/ 子包）
judgment/             拧紧结果判定策略（AtlasJudgment, FitJudgment, SudongJudgment）
config/               配置类（DeviceConfig, FitConfig, ToolCommonConfig, JudgmentConfig, NettyConfig）
util/                 工具类（JsonUtils, Converter, BarcodeMatcher, Crc16Utils）
```

## 设备处理继承链

```
DeviceHandler (interface)
├── connect(deviceId), disconnect(deviceId), getStatus(deviceId)
└── getSupportedTypes()

TCPDeviceHandler (abstract) — Netty Bootstrap + NioEventLoopGroup 管理
├── ConcurrentHashMap<Long, DeviceHolder> 设备状态存储
├── CompletableFuture 响应等待机制（rspFutures / errorMsgFutures）
├── 连接失败自动重连（RECONNECT_INTERVAL_MS 间隔）
└── sendCmdAsync() — 异步命令发送 + 超时处理

ToolHandler (abstract) — lock/unlock 控制 + PSet 切换 + 数据回调
├── lock / unlock / forceLock / forceUnlock — 带冷却控制的启停
├── sendPSetOp — 参数集切换（pSetLock 保护）
├── handleTighteningData / handleCurveData → ToolAdapter 回调
└── 抽象方法：unlockTool, lockTool, sendPSetCmd

AtlasPFSeriesHandler          FitSeriesHandler           SudongSeriesHandler (abstract)
├── Atlas PF 协议编解码         ├── FIT FTC6 协议编解码      └── SudongX7Handler
├── 无心跳（Atlas 自带）        ├── IdleStateHandler           ├── 速动 X7 协议编解码
├── 连接→订阅数据初始化         │   + HeartbeatHandler          ├── CRC16 校验
│                              ├── FitCurveDataReassembler      └── 连接→握手→订阅初始化
│                              └── FitConfig 心跳配置
```

**旧版子类 AtlasPF4000Handler / AtlasPF6000OPHandler / FitFTC6Handler 已移除** — SeriesHandler 直接处理对应设备类型，不再需要仅含构造注入的空壳子类。

## 核心流程

### 1. 启动流程

```
DeviceHandlerService.@PostConstruct
  → DeviceType.initProvider(handlerFactory::getHandler)  // 解决循环依赖
  → DeviceManager 就绪，等待首次用户登录

用户登录 → DeviceManager.userLoggedIn(devices)
  → start() → 调度 scanAndConnect() 定时扫描
  → 未连接的设备提交到 connectExecutor 线程池连接
```

### 2. 设备连接流程

```
TCPDeviceHandler.connect(deviceId)
  → 创建/获取 DeviceHolder
  → 设置状态 CONNECTING
  → Bootstrap.connect(ip, port)
  → 成功: 设置 Channel 属性 (DEVICE_ID, DEVICE_HOLDER, MANUALLY_CLOSE)
  → 失败: eventLoop.schedule() 延迟重连
```

### 3. 命令发送流程

```
sendCmdAsync(cmdSupplier, reqCmdStr, key, deviceId, needRsp)
  → channel.writeAndFlush(cmd)
  → 成功 + needRsp: 注册 CompletableFuture 到 rspFutures，设置超时
  → 成功 + !needRsp: 直接 complete(true)
  → 失败: complete(false)
  → 超时: complete(false) + channel.close()
  → 完成时自动从 rspFutures 清理（防止内存泄漏）
```

### 4. Tool lock/unlock 冷却机制

```
unlock(deviceId) / lock(deviceId) → changeToolState(targetEnabled, deviceId, bypassCooldown)
  → stateLock 锁内检查冷却
    - 状态变更（oppositeState=true）→ 放行
    - 状态不变（oppositeState=false）→ 检查 lastLockTime/lastUnlockTime 是否过冷却期
  → 调用 unlockTool(deviceId) / lockTool(deviceId) 异步发送
  → whenComplete: 成功后更新 isUnlocked / lastUnlockTime 或 lastLockTime
  → forceUnlock/forceLock 绕过冷却检查
```

### 5. Fit 系列设备心跳流程

```
IdleStateHandler (WRITER_IDLE) 超时
  → HeartbeatHandler.onIdleTimeout()
  → heartbeatTriggered=true, retryCount++
  → heartbeatFunc.apply(deviceId) → sendHeartbeat()
  → 成功: retryCount=0, heartbeatTriggered=false
  → 失败: 等待下次 IdleState 触发重试
  → retryCount >= maxRetryCount → ctx.close()
```

### 6. 设备变更事件

```
DeviceChangeEvent 发布（事务提交后）
  → DeviceManager.handleDeviceChange() @TransactionalEventListener
  → ADD:    添加 handler 到 deviceHandlers
  → UPDATE: 先 removeDevice (断开旧连接) → addDevice (重新添加)
  → DELETE: removeDevice (断开连接并移除)

  → DeviceRegistry.onDeviceChange() @TransactionalEventListener
  → ADD:    handlerFactory.getHandler → new ToolAdapter(handler, device) → tools.put
  → UPDATE: tools.remove → 重新 registerTool
  → DELETE: tools.remove
```

### 7. 任务生命周期引擎

```
用户触发 → POST /api/task-lifecycle/trigger {productCode, partsCode}
  → TaskOrchestrator.createEngine() → LifecycleEngineFactory 组装
  → LifecycleEngine.start(ctx) → Actor 线程启动
  → TriggerCapability 管道执行（产品条码校验、零件条码匹配、工位配置检查等）
  → 通过后进入 VALIDATION → OPERATION → FINALIZATION 阶段
  → 每个阶段/子状态执行对应的 Capability 列表
  → 拧紧数据到达（TIGHTENING_RECEIVED 等待点）→ advancePipeline()
  → 完成或失败 → onCompleted / onFaulted 回调 → SSE 推送状态
```

### 8. 拧紧数据路由

```
InBoundHandler 解析帧 → ToolHandler.handleTighteningData(dto, channel)
  → ToolAdapter.fireTighteningData(dto) → DataRouter.routeTighteningData()
  → LifecycleEngine.postMessage(TighteningDataReceived) → Actor inbox
  → Actor 线程处理 → advancePipeline() 推进到 JUDGING → STORING 等子状态
```

### 9. 导出流程（事件驱动 Outbox）

```
拧紧数据保存 → 发布 ExportTaskCreatedEvent
  → ExportWorker @TransactionalEventListener（事务提交后）
  → ExporterRegistry 匹配所属 Exporter（StandardExcelExporter / TxtExporter / PlcResultSender / OuterDatabaseStorer）
  → Exporter.export() 执行导出
  → 更新 ExportTask 状态
```

## 关键设计决策

### 已实现
- **DeviceHandlerFactory** — 通过 `List<DeviceHandler>` 自动注入所有实现，遍历匹配 handlerClass
- **静态 Provider 模式** — `DeviceType.handlerProvider` 由 `DeviceHandlerService.@PostConstruct` 注册，解决枚举与 Spring Bean 的循环依赖
- **冷却控制** — lock/unlock 操作有 cooldown 保护（`toolCommonConfig.lockUnlockCooldownMs`），防止客户端频繁切换
- **线程安全** — `ConcurrentHashMap` + `ReentrantLock` + `volatile` 字段
- **DeferredResult** — Controller 使用异步响应，不阻塞 Tomcat 线程
- **SSE 推送** — `SseService` + `SseController` 向 UI 推送工位状态、设备连接状态、拧紧结果
- **DeviceRegistry** — `ConcurrentHashMap<Long, ITool>` 注册中心，通过 `@TransactionalEventListener` 监听设备变更自动维护
- **ToolAdapter** — 实现 `ITool`，包装 `ToolHandler`，向外部（生命周期引擎）暴露统一工具操作接口
- **任务生命周期引擎** — Actor 模型单线程处理 Inbox 消息，`PipelineDefinition` 声明式定义阶段/子状态/Capability 映射
- **Capability 模式** — 每个管道步骤作为独立 Capability（`precondition → execute → onError`），可组合、可排序
- **崩溃恢复** — 每个管道推进步保存 `ContextCheckpoint` 到 DB，Actor 线程 uncaught exception 自动 markFaulted
- **事件驱动导出** — `ExportTaskCreatedEvent` 事务后异步处理，解耦数据存储与导出

### TODO / 待完善
1. `connectToChannel` 重连逻辑：TODO 注释标注重连需完善（目前简单 schedule 递归）
2. `disconnect` 中 `channel.close().sync().addListener` 的 listener：TODO 标注需添加资源清理逻辑
3. `Bootstrap` 定义位置：TODO 标注考虑是否上提到更高抽象层
4. 重连失败没有退避策略（固定 `RECONNECT_INTERVAL_MS`）
5. `rspFutures` 按 key 索引，同一个 deviceId + cmdType 可能并发冲突（如果连续发送同一类型命令）

## 协议实现

### Atlas Protocol
- 长度字段解码：`AtlasLengthDecoder`（继承 LengthFieldBasedFrameDecoder）
- 帧编解码：`AtlasFrameCodec` / `AtlasFrame`
- 初始化：`AtlasPFSeriesInitHandler` — 连接后发送订阅命令
- 数据处理：`AtlasPFSeriesInBoundHandler`
- 数据解析：`AtlasTighteningDataParser`, `AtlasCurveDataParser`, `AtlasDataUtils`

### FIT Protocol
- 帧解码：`FitFrameCodec` + `LengthFieldBasedFrameDecoder`
- 帧模型：`FitFrame`（命令工厂方法：enableTool, disableTool, sendPSet, sendHeartBeat）
- 初始化：`FitSeriesInitHandler`
- 数据处理：`FitSeriesInBoundHandler`
- 曲线重组：`FitCurveDataReassembler` — 将分片的曲线数据拼装为完整 `CurvePoint` 列表
- 数据解析：`FitTighteningDataParser`, `FitCurveDataParser`, `FitDataUtils`

### 速动 X7 Protocol
- 帧解码：`SudongX7FrameDecoder` + `SudongX7FrameCodec`
- 帧模型：`SudongX7Frame`
- 初始化：`SudongX7InitHandler` — 连接后握手 + 订阅
- 数据处理：`SudongX7InBoundHandler`
- 数据解析：`SudongX7TighteningDataParser`
- CRC16 校验：`Crc16Utils`

## 数据库

SQLite 数据库，路径：`~/tightening_system/tightening.db`

Flyway 迁移文件（`src/main/resources/db/migration/`）：
- V1.0.1 — account_info 表
- V1.0.2 — device 表
- V1.0.3 — tightening_data 表
- V1.0.4 — curve_data 表
- V1.0.6 — 重命名并添加 Atlas 列
- V1.0.7 — task_record 表
- V1.0.8 — product_task 表
- V1.0.9 — product_side 表
- V1.0.10 — product_bolt 表
- V1.0.11~V1.0.12 — task_record 可空 + 崩溃恢复
- V1.0.13 — export_task 表（事件驱动导出）
- V1.0.14 — barcode_matching 分段
- V1.0.15~V1.0.23 — 条码规则、任务配置、重命名（mission → task）等增量迁移

## 配置结构

- `application.yaml` — 主配置（SQLite, MyBatis-Plus, Flyway, Tomcat）
- `application-dev.yml` — 开发环境
- `application-standalone.yml` — 独立部署
- `DeviceConfig` — 设备扫描/连接线程池配置（`device-config.*`）
- `FitConfig` — FIT 协议心跳配置（`tool-control.atlas.fit.*`）
- `ToolCommonConfig` — 工具通用配置（`tool-control.common.lock-unlock-cooldown-ms`, `cmd-timeout-ms`）
- `JudgmentConfig` — 拧紧判定容差配置
- `NettyConfig` — Netty NioEventLoopGroup Bean 定义

## 编码约定

- 包名：`com.tightening.*`
- 使用 Lombok（`@Data`, `@Slf4j`, `@Getter`）
- 日志级别：`com.tightening: DEBUG`，`io.netty: WARN`
- 驼峰转换：`map-underscore-to-camel-case: true`
- Controller 路径：`/api/devices`, `/api/login` 等，只用 POST
- 异步响应使用 `DeferredResult` + `CompletableFuture`
- **严禁全限定类名**：禁止在方法体内使用 `com.foo.bar.SomeClass` 或 `java.util.List` 等全限定名直接声明/接收类型。所有类型必须通过文件顶部的 `import` 语句导入，方法体内只用短名。唯一例外是同一个类名存在多个版本冲突且无法重命名时（极少见）

## Agent skills

### Issue tracker

Issues live in GitHub Issues for `StreenJiang/tightening`，通过 `gh` CLI 操作。See `docs/agents/issue-tracker.md`.

### Triage labels

五个分类标签使用默认名称：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。See `docs/agents/triage-labels.md`.

### Domain docs

Single-context 仓库 — 根目录 `CONTEXT.md` + `docs/adr/`。See `docs/agents/domain.md`.

## 运行

```bash
mvn spring-boot:run
# 默认 active profile: dev
# 服务端口: 8080
# 数据库自动通过 Flyway 迁移
```
