# 任务名称重复检测 & 列表搜索

## 背景

前端正在开发任务编辑功能，需要两个补充接口。

## 接口设计

### 1. 名称重复检测

`GET /api/missions/check-name?name=xxx&excludeId=yyy`

| 参数 | 必填 | 说明 |
|------|------|------|
| `name` | 是 | 要检测的任务名称 |
| `excludeId` | 否 | 编辑模式下排除自身 ID |

返回 `ApiResponse<Boolean>`：`true` 表示已存在（重复），`false` 表示名称可用。

### 2. 列表名称搜索

在现有 `GET /api/missions` 加可选参数 `name`：

```
GET /api/missions?page=1&size=100&name=xxx
```

`name` 为空时不筛选。模糊匹配（`LIKE '%xxx%'`）。

## 数据库

新增 Flyway 迁移，为 `product_mission.name` 加部分唯一索引（只对未删除记录）：

```sql
CREATE UNIQUE INDEX idx_product_mission_name ON product_mission(name) WHERE deleted = 0;
```

软删除的任务名可复用。

## 异常处理

`create` / `update` 在 `saveOrUpdate` 时区分两类异常：
- `DuplicateKeyException` → `ApiResponse.fail("任务名称已存在")`
- 通用 `Exception` → `ApiResponse.fail("创建失败"/"更新失败")` + 日志记录

两者异常处理保持一致，不抛 500。

## 实现

| 层 | 文件 | 改动 |
|---|---|---|
| Migration | 新增 `V1.0.16__add_mission_name_unique.sql` | 部分唯一索引 |
| Controller | `ProductMissionController.java` | 新增 `checkName`；`list` 加 `name` 参数；`create`/`update` 加异常捕获 |
| Service | `ProductMissionService.java` | 新增 `isNameDuplicate`；`listByPage` 加 `name` 参数 |

## 测试

- `checkName`: 名称已存在、名称不存在、编辑模式排除自身
- `list`: 无 name 参数（回归）、包含匹配、无结果
- `create`/`update`: 唯一约束冲突时返回友好错误而非 500
