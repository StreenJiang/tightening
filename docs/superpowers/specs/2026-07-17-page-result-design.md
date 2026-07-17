# PageResult — 通用分页响应 DTO

## 动机

`ProductMissionController.list` 是唯一的分页接口。它调用 `missionService.listByPage()` 返回 MyBatis-Plus `Page<ProductMission>`，但当前只取 `getRecords()` 放入 `ApiResponse`，丢弃了 `total`/`size`/`current` 等分页元数据。前端无法知道总页数，分页组件不可用。

## 设计

### 新增 `PageResult<T>`

```
src/main/java/com/tightening/dto/PageResult.java
```

```java
public record PageResult<T>(
    List<T> records,
    long total,
    long size,
    long current
) {
    public static <T> PageResult<T> of(Page<?> page, List<T> records) {
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }
}
```

- 泛型 `T`，可被任何分页接口复用
- 字段命名对齐 MyBatis-Plus `Page` 的 getter（`getRecords()`, `getTotal()`, `getSize()`, `getCurrent()`）
- 静态工厂 `of(Page, records)` 减少调用侧样板代码
- 使用 `record`，与项目中 `ApiResponse` 等 DTO 风格一致

### 修改 `ProductMissionController.list`

- **返回类型**：`ResponseEntity<ApiResponse<List<ProductMissionDTO>>>` → `ResponseEntity<ApiResponse<PageResult<ProductMissionDTO>>>`
- **方法体**：通过 `PageResult.of(resultPage, dtos)` 构造分页响应，替换原来的直接传 `records`

响应 JSON 结构变化：

```diff
- data: ProductMissionDTO[]
+ data: { records: ProductMissionDTO[], total: number, size: number, current: number }
```

### 测试更新

`ProductMissionControllerTest` 中涉及 `list` 方法的两个测试用例需更新：
- 返回类型断言适配 `PageResult<ProductMissionDTO>`
- 加强断言：校验 `data().total()` / `data().size()` / `data().current()` 的值，确保分页元数据正确传递

## 影响范围

| 层级 | 影响 | 是否破坏性 |
|------|------|-----------|
| `PageResult.java` | 新增文件 | 否 |
| `ProductMissionController.list` | 返回类型 + 方法体 | **是**（API 响应格式变更） |
| `ProductMissionControllerTest` | 类型断言更新 | 否 |
| 前端 | 需从 `data.records` 读取列表 | **是**（需同步更新） |
| 其他接口 | 不受影响 | 否 |

## 后续复用

`PageResult` 作为通用 DTO 放在 `dto/` 包下，任何新增分页接口可直接使用，无需重复定义。
