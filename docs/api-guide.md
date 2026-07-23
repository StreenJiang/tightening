# Tightening API 使用指南

本文档记录前端调用后端 API 时需要知道的**隐式约定和领域知识**。这些内容从代码中难以直接推断，且容易导致 AI 辅助开发时出错。

API 路由表、请求/响应字段结构直接从 Controller 和 DTO 文件中读取，本文档不重复记录。

---

## 1. 统一响应包装 — `ApiResponse<T>`

所有 API 响应体（除 SSE）都经过 `ApiResponse<T>` 包装，结构如下：

```json
{
  "code": 200,
  "message": "ok",
  "data": { ... }
}
```

| 场景 | code | message | data |
|---|---|---|---|
| 成功 | 200 | "ok" | 业务数据 |
| 失败 | 500 | 错误描述 | null |

**关键约定：**

- **业务失败也返回 HTTP 200**。前端必须读取 `code` 字段判断成败，不能依赖 HTTP 状态码
- 成功时 `data` 为业务数据（可能为 null）
- 失败时 `data` 为 null，`message` 为错误信息

---

## 2. 分页约定

列表接口（`GET /api/tasks`）使用分页参数：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `page` | 1 | **从 1 开始**，不是 0 |
| `size` | 100 | 每页条数 |
| `name` | 无 | 可选，按名称模糊搜索 |

响应中 `PageResult<T>` 结构：

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "records": [ ... ],
    "total": 50,
    "size": 10,
    "current": 1
  }
}
```

| 字段 | 说明 |
|---|---|
| `records` | 当前页数据列表 |
| `total` | 总记录数 |
| `size` | 每页条数 |
| `current` | **当前页码（从 1 开始）** |

---

## 3. SSE 事件流 — 单连接模式

`GET /api/events` 是 Server-Sent Events 端点，用于接收实时推送。

**关键约定：**

- **单连接模式**：后端同一时间只维护一个 SSE 连接。新连接建立时，旧连接会被静默关闭。前端不需要也不能开多个 SSE 连接
- **不需要传 taskId 参数**：事件流是全局的，不是按 task 过滤
- **30 秒心跳**：后端每 30 秒发送一次 keepalive comment（`:` 开头），前端可忽略或用做连接存活检测

**事件类型：**

| Event Name | 用途 | Payload 类型 |
|---|---|---|
| `WORKPLACE_STATUS` | 工位状态变化（锁定/解锁、锁原因） | `SseEvent<WorkplaceStatusPayload>` |
| `TIGHTENING_DATA` | 拧紧数据到达 | `SseEvent<TighteningDataDTO>` |
| `DEVICE_STATUS` | 设备状态变化 | `SseEvent<...>` |

**SSE 事件数据格式：**

```json
{
  "type": "WORKPLACE_STATUS",
  "payload": { ... },
  "timestamp": "2026-07-20T10:30:00"
}
```

`WorkplaceStatusPayload` 结构：

```json
{
  "status": "LOCKED",
  "lockReasons": {
    "barcodeRequired": "请录入物料码",
    "pSetSending": "程序号下发中"
  }
}
```

`lockReasons` 是一个 `Map<String, String>`，key 为 `LockReason.key`，value 为中文显示名。

**LockReason 完整映射：**

| key | 中文显示名 | 含义 |
|---|---|---|
| `pSetSending` | 程序号下发中 | 正在向工具发送程序号 |
| `arrangerPositioning` | 送钉中 | 自动送钉装置定位中 |
| `socketSelecting` | 套筒选择中 | 自动套筒选择中 |
| `barcodeRequired` | 请录入物料码 | 需要扫码验证物料 |
| `adminConfirm` | 需管理员确认 | 需要管理员手动确认 |

---

## 4. Task 调用时序

任务执行流程必须按以下顺序调用：

```
1. POST /api/tasks/{id}/validate-product-barcode   → 验证产品追溯码
2. POST /api/tasks/{id}/validate-parts-barcode      → 验证物料码
3. POST /api/tasks/{id}/trigger                      → 触发任务开始执行
4. GET  /api/events                                     → 通过 SSE 监控执行状态
```

**注意事项：**

- 步骤 1 和 2 可以跳过（如果不需要扫码验证），但 trigger 前必须先 create 任务
- `trigger` 有**幂等保护**：已 active 的 task 再次 trigger 会返回失败
- trigger 成功后通过 SSE `WORKPLACE_STATUS` 事件跟踪工位状态变化

---

## 5. BarcodeValidationResult 状态机

### 产品追溯码验证（validate-product-barcode）

| result | 含义 | 有用字段 |
|---|---|---|
| `MATCHED` | 匹配成功 | - |
| `WRONG_MISSION` | 条码属于另一个任务 | `suggestedTaskId` — 建议跳转的任务 ID |
| `NOT_MATCHED` | 条码不匹配任何任务 | `reason` — 失败原因 |

### 物料码验证（validate-parts-barcode）

| result | 含义 | 有用字段 |
|---|---|---|
| `PASS` | 验证通过 | - |
| `FAIL` | 验证失败 | `reason` — 失败原因 |

---

## 6. `check-name` 返回值语义

`GET /api/tasks/check-name?name=xxx` 检查任务名称是否重复。

**容易误解：**

- `data: true` → 名称**已存在**，不可使用
- `data: false` → 名称**可用**

---

## 7. 自动填充字段

以下字段由后端自动处理，**前端不需要传，也不应该依赖前端传**：

| 字段 | 行为 |
|---|---|
| `id` | 新增任务时不传，后端自动生成；更新时必须传 |
| `createTime` | 创建时自动填充当前时间 |
| `modifyTime` | 创建和更新时自动填充当前时间 |
| `deleted` | 软删除标记，MyBatis-Plus 自动过滤已删除记录，前端不需要感知 |

---

## 8. DeferredResult vs ResponseEntity

设备控制 API（enable/disable/parameter-set）使用 `DeferredResult` 异步返回，task CRUD 使用同步 `ResponseEntity`。

**对前端透明**：HTTP 层面无差异，前端不需要区别对待。等待时间较长时（设备硬件响应），接口会更晚返回，但响应格式一致。

---

## 9. 非生产级 API — 不要参考

以下 Controller 是测试用/未完成品，其 API 模式**不能**作为参考：

| Controller | 路径 | 状态 |
|---|---|---|
| `LoginController` | `POST /api/login` | 硬编码测试数据，无实际认证逻辑 |
| `DeviceController` | `/api/devices/*` | 测试用未完成品，响应格式不同于其他 API |
