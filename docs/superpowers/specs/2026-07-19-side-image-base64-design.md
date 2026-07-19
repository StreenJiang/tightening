# Side 图片从 Multipart 改为 Base64 + 接口精简

## 动机

任务创建接口里，side 的图片通过 multipart 文件单独上传，JSON body 和文件分两路走，key 靠字符串拼接（`sides[0].image`），可读性差。改为 Base64 直接嵌入 JSON，一步到位。

同时精简不必要的 side 专用接口，统一由任务接口承担 side 数据的读写。

## DTO 重构

### ProductMissionSaveDTO → ProductMissionDetailDTO

重命名，同时承担保存入参和详情出参。已有 `sides` 字段包含完整嵌套数据。

### ProductMissionDTO

不变。列表专用，8 个平铺字段。

### ProductSideSaveItem — 新增 3 个字段

```java
private String image;           // Base64 编码，空串=删除图片，null=保持原样
private String renderedImage;   // 同上
private String thumbnail;       // 同上
```

### ProductSide 实体

不变。已有 `imageData`、`renderedImageData`、`thumbnailData` 三个 blob 字段。

### ProductSideDTO

不变。轻量引用（被 `ProductBoltDTO` 使用），无需图片字段。

## 接口变更

| 接口 | 动作 |
|------|------|
| `GET /api/sides?missionId=X` | 删除 |
| `GET /api/sides/{id}` | 删除 |
| `GET /api/sides/{sideId}/image` | 删除 |
| `POST /api/missions` | 改为 `@RequestBody` JSON，移除 multipart |
| `PUT /api/missions/{id}` | 改为 `@RequestBody` JSON，移除 multipart |
| `GET /api/missions/{id}` | 返回类型从 `ProductMissionDTO` 改为 `ProductMissionDetailDTO` |

`ProductSideController` 整个类删除。

## Service 层变更

### ProductMissionService

`saveMission` 签名从 `(dto, imageMap)` 简化为 `(dto)`。

`diffSides` 中图片处理逻辑：
- `sideItem.getImage()` 为 null → 跳过，保持原值
- `sideItem.getImage()` 为空串 → blob 设为 null（删除图片）
- `sideItem.getImage()` 有值 → Base64 解码后写入 blob

新增 `getDetail(Long id)` 方法，查询 mission 并组装 nested sides 数据（entity byte[] → Base64 字符串）。

### ProductMissionController

- `create`、`update` 方法：`HttpServletRequest` → `@RequestBody ProductMissionDetailDTO`，移除 `parseDto`、`extractImages`、`asMultipart` 三个私有方法
- `get` 方法：返回 `ProductMissionDetailDTO`，附带 sides 嵌套数据

### ProductSideService

移除以下方法：

| 方法 | 原因 |
|------|------|
| `getImageData` | `/api/sides/{id}/image` 删除 |
| `updateImageData` | 不再单独更新 blob，由 saveOrUpdate 统一处理 |
| `updateRenderedImageData` | 同上 |
| `updateThumbnailData` | 同上 |
| `getByIdWithoutBlobs` | `/api/sides/{id}` 删除 |
| `listByMissionId` | `/api/sides?missionId=X` 删除 |

保留 `listSideIdsByMissionId`（`ProductBoltService` 仍在使用）。

## 受影响的测试

`ProductSideTest.jsonRoundTrip_fullFields_shouldPreserveAllValues` 调用了 `getImageData`，方法删除后需更新为通过 mission 详情接口验证。

## Base64 编解码

- `java.util.Base64.getDecoder().decode(base64Str)` 解码，失败抛 `IllegalArgumentException` 由全局异常处理器捕获
- `java.util.Base64.getEncoder().encodeToString(bytes)` 编码
- 编解码逻辑放在 `util` 层复用（`ImageUtils` 或内联在现有工具类中）

## 兼容性

**不向后兼容**：multipart 上传方式直接移除，前端需同步改为 Base64 方式。
