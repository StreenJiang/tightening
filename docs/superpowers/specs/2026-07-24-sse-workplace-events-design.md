# SSE 工位事件规范设计

> 2026-07-24 | 前后端统一 SSE 协议

## 目标

统一前后端 SSE 线格式、事件类型、架构模式。后端新增 `/api/workplace/events` 端点，推送任务生命周期事件；同时升级现有 `/api/events` 端点格式对齐新规范。

## 1. SSE 线格式

严格遵循 W3C SSE 协议，`event:` 做路由，`data:` 做数据：

```
event: <事件类型>
data: <JSON 负载>
```

- `event:` — 事件类型标识，浏览器 `addEventListener(type, handler)` 原生路由
- `data:` — 纯业务 JSON，不重复包装 type；timestamp 缩为 `ts` 放 payload 内
- 不再使用 `SseEmitter.event().name()` 之外的其他包装层

### 示例

```
event: bolt:result
data: {"sideId":1,"seq":3,"torque":5.23,"angle":32.1,"result":"ok","ts":"2026-07-24T10:30:00"}
```

### 类型命名规范

`domain:action`，小写、冒号分隔。领域在前，动作在后。

## 2. 事件目录（共 5 个，精简自原 10 个）

> `bolt:active`、`bolt:status`、`side:switch` 由前端本地管理；
> `barcode:scan`、`session:identity`、`task:mode` 后续按需添加。

### 任务层

| Event | Payload | 触发时机 |
|-------|---------|---------|
| `task:control` | `{ control: "stopped", reason, ts }` | 引擎 faulted |

### 拧紧/曲线

| Event | Payload | 触发时机 |
|-------|---------|---------|
| `tightening:data` | `TighteningDataDTO`（全量） | 拧紧判定完成（JUDGING 后） |
| `curve:data` | `CurveDataDTO`（全量） | 曲线保存并绑定到 tightening 记录后 |

### 设备/工位

| Event | Payload | 触发时机 |
|-------|---------|---------|
| `device:status` | `{ "<deviceId>": true\|false, ..., ts }` | 设备连接状态变化 |
| `workplace:status` | `{ status: "UNACTIVATED" \| "OPERATION_ENABLE" \| "OPERATION_DISABLE", lockReasons: [...], ts }` | 工位启停状态变更 |

## 3. 后端架构

### 3.1 端点

| 端点 | 用途 | 单客户端？ |
|------|------|----------|
| `GET /api/events` | 设备状态事件 | 是 |
| `GET /api/workplace/events` | 工位/任务生命周期事件 | 是 |

两个 emitter 独立管理，新连接自动替换旧连接。

### 3.2 SseService 改造

```java
@Service
public class SseService {
    private volatile SseEmitter deviceEmitter;
    private volatile SseEmitter workplaceEmitter;
    // 各自独立的心跳定时器

    public SseEmitter createDeviceEmitter()  // 旧 create()
    public SseEmitter createWorkplaceEmitter()
    public void emitDevice(String type, Object data)
    public void emitWorkplace(String type, Object data)
    public void closeDevice()
    public void closeWorkplace()
}
```

核心方法：

```java
public void emitWorkplace(String type, Object data) {
    SseEmitter current = this.workplaceEmitter;
    if (current == null) return;
    try {
        current.send(SseEmitter.event().name(type).data(data));
    } catch (IOException e) {
        log.warn("SSE workplace emit failed: {}", e.getMessage());
    }
}
```

`data` 参数由调用方构造为 Map 或 record，Jackson 自动序列化。

### 3.3 发射点布局

```
DeviceConnectionMonitor  ─→ emitDevice("device:status", map)
WorkplaceStatusService   ─→ emitWorkplace("workplace:status", map)

ToolHandler.handleCurveData()
  └─ curveDataService.save(data)
     └─ eventPublisher.publishEvent(CurveDataSavedEvent)
        └─ TaskOrchestrator.@EventListener → emitWorkplace("curve:data", dto)

TaskOrchestrator
  └─ engine.onPipelineEvent((type, data) → emitWorkplace(type, data))
     └─ LifecycleEngine.firePipelineEvents → emitWorkplace("tightening:data", TighteningDataDTO)
     └─ LifecycleEngine.faultPipeline     → emitWorkplace("task:control", {stopped, reason})
```

### 3.4 LifecycleEngine 新增回调接口

引擎不直接依赖 `SseService`。新增统一回调：

```java
@FunctionalInterface
public interface PipelineEventListener {
    void onEvent(String type, Object data);
}
```

`LifecycleEngine` 在管道关键节点调用 `pipelineEventListener.onEvent(...)`。`TaskOrchestrator` 设置回调桥接到 `sseService.emitWorkplace(...)`。

### 3.5 改动文件清单

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `constant/SseEvents.java` | 新增 | 5 个事件类型字符串常量 |
| `lifecycle/PipelineEventListener.java` | 新增 | 引擎管道事件回调接口 |
| `device/event/CurveDataSavedEvent.java` | 新增 | 曲线保存完成事件 |
| `SseService.java` | 重写 | 双 emitter + `emitDevice/emitWorkplace` |
| `SseController.java` | 新增端点 | `GET /api/workplace/events` |
| `SseEvent.java` | 删除 | 被 SseEvents 常量替代 |
| `SseEventType.java` | 删除 | 被 SseEvents 常量替代 |
| `WorkplaceStatusPayload.java` | 删除 | 被内联 Map.of 替代 |
| `DeviceConnectionMonitor.java` | 修改 | 适配 `emitDevice` 签名 |
| `WorkplaceStatusService.java` | 修改 | 适配 `emitWorkplace` 签名 |
| `TaskOrchestrator.java` | 修改 | pipelineEvent 桥接 + CurveDataSavedEvent 监听 |
| `LifecycleEngine.java` | 修改 | PipelineEventListener + firePipelineEvents + faultPipeline |
| `ToolHandler.java` | 修改 | ApplicationEventPublisher 注入 + curve:data 发布 |
| `device/handler/impl/{Atlas,Fit,Sudong}*.java` | 修改 | 构造参数适配 |

## 4. 前端对齐

### 4.1 EventSource 连接

```ts
const es = new EventSource('/api/workplace/events')

es.addEventListener('task:control', (e) => {
  const data = JSON.parse(e.data)
  // data = { control: "stopped", reason, ts }
})

es.addEventListener('tightening:data', (e) => {
  const dto = JSON.parse(e.data)
  // dto = TighteningDataDTO 全量
})

es.addEventListener('curve:data', (e) => {
  const dto = JSON.parse(e.data)
  // dto = CurveDataDTO 全量
})

es.addEventListener('workplace:status', (e) => {
  const data = JSON.parse(e.data)
  // data = { status, lockReasons, ts }
})
```

### 4.2 变化点

- `workplace.ts` (API 层)：移除 `onmessage` JSON 解析 + type 分发逻辑
- `workplace.ts` (Store)：`handleEvent` 改为各事件独立注册
- `workplace.ts` (Types)：`WorkplaceEventType` 字符串更新为新事件名
- 解析逻辑从 `evt.data` → `JSON.parse(e.data)`，不再有外层 `type` 字段

## 5. 自审检查

- [x] 无 TBD/TODO
- [x] 事件目录覆盖前端所有 WorkplaceEventType
- [x] 前后端格式一致：`event:` + `data:`
- [x] 单客户端约束明确
- [x] LifecycleEngine 不直接依赖 SseService（通过回调解耦）
- [x] 删除冗余 DTO（SseEvent、SseEventType）
