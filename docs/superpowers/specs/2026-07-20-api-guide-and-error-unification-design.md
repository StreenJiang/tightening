# API 使用指南与错误返回统一 — 设计文档

## 背景

前后端一人负责。AI 辅助前端开发时，只读 Controller/DTO 代码无法获取跨文件的隐式约定和运行时行为，导致生成代码出错率高。

## 目标

1. 写一份 `docs/api-guide.md`，只记录从代码中读不出来的隐式约定
2. 统一所有 Controller 的错误返回格式：HTTP 200 + `ApiResponse.fail()`

## API 指南内容

### 覆盖的隐式约定

1. **`ApiResponse<T>` 响应体包装** — code 200/500，业务失败也是 HTTP 200
2. **分页约定** — page 从 1 开始
3. **SSE 单连接模式** — 只有一个 emitter，新连接替换旧连接
4. **Mission 调用时序** — validate-product → validate-parts → trigger → SSE
5. **BarcodeValidationResult 状态机** — MATCHED/WRONG_MISSION/NOT_MATCHED/PASS/FAIL
6. **check-name 返回值语义** — true=重复（不可用），false=可用
7. **自动填充字段** — createTime/modifyTime 自动填充，deleted 软删除自动过滤
8. **DeferredResult vs ResponseEntity** — 对前端透明，HTTP 层面无差异
9. **非生产级 Controller 标注** — LoginController、DeviceController 不能作为参考
10. **LockReason** — 键值映射，SSE 中 lockReasons 是 Map<String, String>

### 明确不记录

- API 路由表（可直接读 Controller 的 `@RequestMapping`）
- 请求/响应字段列表（可直接读 DTO）
- Java 实现细节

## 错误返回统一

### 现状

| Controller | 模式 |
|---|---|
| MissionLifecycleController | `ResponseEntity.badRequest()` + `ApiResponse.fail()` |
| ProductMissionController | `ResponseEntity.ok()` + `ApiResponse.fail()` |
| DeviceController | `ResponseEntity.status(408/500)` + 裸 `Boolean`，没用 `ApiResponse` |

### 目标

全部统一为：`ResponseEntity.ok(ApiResponse.fail("message"))`

### 改动范围

1. `MissionLifecycleController` — 5 处 `badRequest()` → `ok()`
2. `DeviceController` — 4 处改为 `ApiResponse<Boolean>`，`status(408/500)` → `ok()`

### 不做的事

- 不加全局异常处理器（留到后续需要时再做）

## 文件变更

| 文件 | 操作 |
|---|---|
| `docs/api-guide.md` | 新增 |
| `MissionLifecycleController.java` | 修改 — badRequest → ok |
| `DeviceController.java` | 修改 — ResponseEntity<Boolean> → ApiResponse<Boolean> |
