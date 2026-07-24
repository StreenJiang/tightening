# ExportWorker 零轮询优化设计

## 问题

`ExportWorker.processPending()` 使用 `@Scheduled(fixedDelay = 5000)` 每 5 秒轮询一次 `export_task` 表（Outbox），即使没有待处理任务也执行 SQL 查询。导出任务创建后不需要秒级响应，且 Outbox 的持久化由 DB 表保证，轮询并非持久化的必要条件。

## 方案

**零轮询**：完全去掉定时轮询，改为 Spring Event 触发 + 启动兜底。

- `ExportTaskService.createTask()` 写入后发布 `ExportTaskCreatedEvent`
- `ExportWorker` 通过 `@EventListener` 监听事件，即时处理
- `@PostConstruct` 启动时一次性追平遗留任务
- `doProcess()` 内部用 while 循环处理完所有 PENDING 任务后才退出

### 通知机制

Spring ApplicationEvent 发布/订阅。`ExportTaskService` 发布事件，`ExportWorker` 监听。`ExportData` 和 `LifecycleEngineFactory` 零改动。虽然当前只有一个消费者，但 Event 方式让 `ExportTaskService` 不需要知道谁在消费——符合低耦合原则。

### 防重入

`AtomicBoolean` 防止并发执行。`@EventListener` 和 `@PostConstruct` 可能在启动时同时触发——第二个到达者看到 `processing=true` 直接返回，第一个的 while 循环会扫到所有任务。`markProcessing` 的 DB 层乐观锁（`WHERE status = PENDING`）防止重复处理同一条任务。

## 改动清单

### 1. ExportWorker — 核心改动

**文件**: `src/main/java/com/tightening/export/ExportWorker.java`

- 删除 `@Scheduled(fixedDelay = 5000)` 的 `processPending()` 方法
- 新增 `@PostConstruct init()` — 启动时追平遗留任务
- 新增 `@Async` + `@EventListener` 监听 `ExportTaskCreatedEvent`，异步触发 `doProcess()`（不阻塞引擎）
- 新增 `AtomicBoolean processing` 防重入
- `doProcess()` 改为 while 循环，一批批处理直到队列清空
- 提取 `processOne(task)` 方法，`doProcess` 专注循环控制
- `cleanupOldTasks()` 保留不动（独立职责，每天 3 点清理过期任务）

### 2. ExportTaskService — 发布事件

**文件**: `src/main/java/com/tightening/service/ExportTaskService.java`

- 注入 `ApplicationEventPublisher`
- `createTask()` 写入后发布 `ExportTaskCreatedEvent`

### 3. ExportTaskCreatedEvent — 新增事件

**文件**: `src/main/java/com/tightening/event/ExportTaskCreatedEvent.java`（新文件）

- 简单 record，无字段，纯信号语义

## 边界情况

| 场景 | 处理 |
|---|---|
| Worker 正在处理中，又来新任务 | `AtomicBoolean` 跳过，第一个调用者的 while 循环会扫到新任务 |
| 进程崩溃重启，遗留 PENDING 任务 | `@PostConstruct` 一次性追平 |
| 启动时既有 @PostConstruct 又有 @EventListener | `AtomicBoolean` 保护，先到先处理 |
| 处理失败回退到 PENDING | 同一轮 while 循环的下一次 `findPending` 自动捡起（retryCount 递增，超过 maxRetries 后永久标记 FAILED） |
| 启动时 DB 不可用 | `@PostConstruct` 异常被 Spring 容器吞掉，进程不阻塞；下次事件触发时处理 |

## 不改的

- `ExportData` — 零改动
- `LifecycleEngineFactory` — 零改动
- `ExportTaskService` 方法签名不变
- `Exporter` 接口和实现不变
- `cleanupOldTasks()` 定时清理不动
