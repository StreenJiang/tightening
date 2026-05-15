# Tightening — 工业拧紧流程控制系统

## 项目概述

工业拧紧工具通信中间件。通过 Netty TCP 连接多种品牌的拧紧控制器（Atlas Copco PF 系列、FIT FTC6），接收拧紧数据并持久化到 SQLite，通过 REST API 暴露设备控制能力。

## 技术栈

| 组件 | 版本/选型 |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.10 |
| Netty | 4.1.129.Final |
| MyBatis-Plus | 3.5.9 |
| 数据库 | SQLite（通过 Flyway 管理迁移） |
| 构建 | Maven |
| 测试 | JUnit 5 + AssertJ 3.27.7 |

## 分层架构

```
controller/     REST API 入口（DeviceController, LoginController, UserAccountInfoController）
service/        业务逻辑（DeviceService, TighteningDataService, UserAccountInfoService）
device/         设备管理层（DeviceManager, DeviceHolder, 设备变更事件）
device/handler/ 设备协议实现层（继承链见下方）
netty/protocol/ Netty 帧编解码器（Atlas / FIT 两种协议）
mapper/         MyBatis-Plus Mapper（TighteningDataMapper, DeviceMapper, UserAccountInfoMapper）
entity/         JPA 实体（TighteningData, CurveData, Device, UserAccountInfo）
dto/            数据传输对象
constant/       枚举常量（设备类型、协议命令码、拧紧结果等）
config/         配置类（DeviceConfig, FitConfig, ToolCommonConfig）
util/           工具类（JsonUtils, Converter）
annotation/     自定义注解（FieldDescription）
```

## 设备处理继承链

```
DeviceHandler (interface)
├── connect(deviceId)       // 连接设备
├── disconnect(deviceId)    // 断开设备
└── getStatus(deviceId)     // 获取设备状态

TCPDeviceHandler (abstract)
├── Netty Bootstrap + NioEventLoopGroup 管理
├── ConcurrentHashMap<Long, DeviceHolder> 设备状态存储
├── CompletableFuture 响应等待机制（rspFutures / errorMsgFutures）
├── 连接失败自动重连（RECONNECT_INTERVAL_MS 间隔）
└── sendCmdAsync() — 异步命令发送 + 超时处理

ToolHandler (abstract)
├── enableToolOp / disableToolOp — 带冷却控制的启用/禁用
├── forceEnableToolOp / forceDisableToolOp — 绕过冷却
├── sendPSetOp — 参数集切换（pSetLock 保护）
├── changeToolState() — 通用状态变更（冷却 + 回调）
└── 抽象方法：enableTool, disableTool, sendPSetCmd

AtlasPFSeriesHandler               FitSeriesHandler
├── Atlas PF 系列协议编解码          ├── FIT FTC6 协议编解码
├── 无心跳（Atlas 协议层自带）       ├── IdleStateHandler + HeartbeatHandler
├── 连接→订阅数据的初始化流程        ├── 曲线数据重组 FitCurveDataReassembler
│                                   └── FitConfig 心跳配置
├── AtlasPF4000Handler              └── FitFTC6Handler
└── AtlasPF6000OPHandler
      (仅构造注入，无额外逻辑)
```

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

### 4. Tool enable/disable 冷却机制

```
enableToolOp(deviceId)
  → changeToolState(true, deviceId, bypass=false)
  → stateLock 锁内检查冷却
    - 如果当前 isToolEnabled==false 且想 enable → 放行（状态变更）
    - 如果当前 isToolEnabled==true 且想 enable → 检查 lastEnableTime 是否过冷却期
  → 调用 enableTool(deviceId) 异步发送
  → whenComplete: 成功后更新 isToolEnabled / lastEnableTime
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
```

## 关键设计决策与 TODO

### 已实现
- **DeviceHandlerFactory** — 通过 `List<DeviceHandler>` 自动注入所有实现，遍历匹配 handlerClass
- **静态 Provider 模式** — `DeviceType.handlerProvider` 由 `DeviceHandlerService.@PostConstruct` 注册，解决枚举与 Spring Bean 的循环依赖
- **冷却控制** — enable/disable 操作有 cooldown 保护（`toolCommonConfig.enableDisableCooldownMs`），防止客户端频繁切换
- **线程安全** — `ConcurrentHashMap` + `ReentrantLock` + `volatile` 字段
- **DeferredResult** — Controller 使用异步响应，不阻塞 Tomcat 线程

### TODO / 待完善
1. `connectToChannel` 重连逻辑：TODO 注释标注重连需完善（目前简单 schedule 递归）
2. `disconnect` 中 `channel.close().sync().addListener` 的 listener：TODO 标注需添加资源清理逻辑
3. `Bootstrap` 定义位置：TODO 标注考虑是否上提到更高抽象层
4. 重连失败没有退避策略（固定 `RECONNECT_INTERVAL_MS`）
5. `changeToolState()` 方法的 `action` 变量使用中文英文混在日志中，需统一
6. `rspFutures` 按 key 索引，同一个 deviceId + cmdType 可能并发冲突（如果连续发送同一类型命令）

## 协议实现

### Atlas Protocol
- 长度字段解码：`AtlasLengthDecoder`（继承 LengthFieldBasedFrameDecoder）
- 帧编解码：`AtlasFrameCodec` / `AtlasFrame`
- 初始化：`AtlasPFSeriesInitHandler` — 连接后发送订阅命令
- 数据处理：`AtlasPFSeriesInBoundHandler`

### FIT Protocol
- 帧解码：`FitFrameCodec` + `LengthFieldBasedFrameDecoder`
- 帧模型：`FitFrame`（命令工厂方法：enableTool, disableTool, sendPSet, sendHeartBeat）
- 初始化：`FitSeriesInitHandler`
- 数据处理：`FitSeriesInBoundHandler`
- 曲线重组：`FitCurveDataReassembler` — 将分片的曲线数据拼装为完整 `CurvePoint` 列表

## 数据库

SQLite 数据库，路径：`~/tightening_system/tightening.db`

Flyway 迁移文件：
- V1.0.1 — account_info 表
- V1.0.2 — device 表
- V1.0.3 — tightening_data 表
- V1.0.4 — curve_data 表

## 配置结构

- `application.yaml` — 主配置（SQLite, MyBatis-Plus, Flyway, Tomcat）
- `application-dev.yml` — 开发环境
- `application-standalone.yml` — 独立部署
- `DeviceConfig` — 设备扫描/连接线程池配置（`device-config.*`）
- `FitConfig` — FIT 协议心跳配置
- `ToolCommonConfig` — 工具通用配置（冷却时间）

## 编码约定

- 包名：`com.tightening.*`
- 使用 Lombok（`@Data`, `@Slf4j`, `@Getter`）
- 日志级别：`com.tightening: DEBUG`，`io.netty: WARN`
- 驼峰转换：`map-underscore-to-camel-case: true`
- Controller 路径：`/api/devices`, `/api/login` 等，只用 POST
- 异步响应使用 `DeferredResult` + `CompletableFuture`

## 运行

```bash
mvn spring-boot:run
# 默认 active profile: dev
# 服务端口: 8080
# 数据库自动通过 Flyway 迁移
```
