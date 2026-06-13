# Protocol Util 目录按工具重构 & Parser 边界统一

## 动机

1. `src/main/java/com/tightening/netty/protocol/util/` 下 3 个文件混合了 Atlas 和 FIT 两个工具的代码，而 `codec/` 和 `handler/` 已按 `atlas/` / `fit/` 分子目录
2. Atlas 和 FIT 的 Parser 返回类型不一致：Atlas 返回 entity，FIT 返回 DTO。统一为 DTO 让 parser 职责边界清晰（bytes → DTO，entity 转换由调用方负责）

## 目标结构

```
protocol/util/
├── atlas/
│   ├── AtlasDataUtils.java              # 低层 ASCII 编解码工具（内部不变）
│   └── AtlasTighteningDataParser.java   # MID 0061 拧紧数据解析（返回类型: entity → DTO）
└── fit/
    ├── FitDataUtils.java                # 精简为低层 BCD/时间戳/字节工具 + parseAlarmData
    ├── FitTighteningDataParser.java     # 新建：拧紧数据解析 → TighteningDataDTO
    └── FitCurveDataParser.java          # 新建：曲线数据解析 → CurveDataDTO
```

`FitCurveDataReassembler`（Netty `MessageToMessageDecoder`）保持在 `codec/fit/`。

## FitDataUtils 拆分

### 保留在 FitDataUtils（10 个方法）

`parseAlarmData` 返回展示用 `String` 而非域对象，留在工具类。

| 方法 | 旧可见性 | 新可见性 |
|------|---------|---------|
| `bcdToInt(byte)` | public | public |
| `getCurrentTimestampBytes()` | public | public |
| `getTimestampBytes(long)` | public | public |
| `bytesToTimestamp(byte[])` | public | public |
| `getDateStr(byte[])` | public | public |
| `parseAlarmData(byte[])` | public | public |
| `longToLittleEndianBytes(long, int)` | private | private |
| `getTimestampStr(byte[], int)` | private | package-private |
| `parseBcdTimestamp(byte[], int)` | private | package-private |
| `getCurrentTimestampStr()` | private | package-private |

### 提取到 FitTighteningDataParser（新建）

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `parse(byte[])` | `TighteningDataDTO` | 去掉原 `throws Exception`（无实际 checked exception） |

### 提取到 FitCurveDataParser（新建）

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `parse(byte[])` | `CurveDataDTO` | 去掉原 `throws Exception` |

## AtlasTighteningDataParser 变更

### 返回类型

`parse(byte[], int)` 返回 `TighteningDataDTO` 替代 `TighteningData`。

所有内部 `parseRev*` 方法同步改为构造 `TighteningDataDTO`。入口方法 `parse()` 中 `result.setRevision(revision)` 调用保持不变（DTO 上存在同名字段）。

### 移除的依赖

不再 import `com.tightening.entity.TighteningData`，新增 import `com.tightening.dto.TighteningDataDTO`。

## AtlasPFSeriesInBoundHandler 变更

解析后增加 DTO → entity 转换，与 FIT 侧 `FitSeriesInBoundHandler` 模式一致：

```java
// 旧
TighteningData tighteningData = AtlasTighteningDataParser.parse(msg.getData(), msg.getRevision());
TCPDeviceHandler.applyToolTypeName(ctx.channel(), tighteningData);
deviceHandler.getTighteningDataService().save(tighteningData);

// 新
TighteningDataDTO dto = AtlasTighteningDataParser.parse(msg.getData(), msg.getRevision());
TighteningData tighteningData = Converter.dto2Entity(dto, TighteningData::new);
TCPDeviceHandler.applyToolTypeName(ctx.channel(), tighteningData);
deviceHandler.getTighteningDataService().save(tighteningData);
```

新增 import `com.tightening.dto.TighteningDataDTO` 和 `com.tightening.util.Converter`（如尚未引入）。

## 风格规范

所有 Parser 类统一：

- `public final class` + `private` 构造器
- `@Slf4j` 日志
- 公开入口方法 `parse()` 放在最前
- 私有辅助方法用 `// ==================== Utility methods ====================` 分隔
- 参数校验抛 `IllegalArgumentException`，解析异常记日志回退、返回字段默认值

## 受影响文件总览

### 文件移动（4 个）

| 文件 | 旧路径 | 新路径 |
|------|--------|--------|
| `AtlasDataUtils.java` | `.../util/` | `.../util/atlas/` |
| `AtlasTighteningDataParser.java` | `.../util/` | `.../util/atlas/` |
| `FitDataUtils.java` | `.../util/` | `.../util/fit/` |
| `AtlasTighteningDataParserTest.java` | `test/.../util/` | `test/.../util/atlas/` |

### 新建文件（4 个）

- `.../util/fit/FitTighteningDataParser.java`
- `.../util/fit/FitCurveDataParser.java`
- `test/.../util/fit/FitTighteningDataParserTest.java`
- `test/.../util/fit/FitCurveDataParserTest.java`

### 引用方 import 更新

| 文件 | 变更 |
|------|------|
| `codec/atlas/AtlasFrame.java` | `util.AtlasDataUtils` → `util.atlas.AtlasDataUtils` |
| `codec/atlas/AtlasFrameCodec.java` | 4 条 static import `util.AtlasDataUtils.*` → `util.atlas.AtlasDataUtils.*` |
| `handler/atlas/AtlasPFSeriesInBoundHandler.java` | import 更新 + 增加 `Converter.dto2Entity()` 调用 |
| `codec/fit/FitFrame.java` | `util.FitDataUtils` → `util.fit.FitDataUtils` |
| `handler/fit/FitSeriesInBoundHandler.java` | `FitDataUtils.parseTighteningData` → `FitTighteningDataParser.parse`，`FitDataUtils.parseCurveData` → `FitCurveDataParser.parse` |

### 测试更新

`AtlasTighteningDataParserTest.java`：所有断言从 entity 改为 DTO，测试逻辑不变。随源文件移到 `test/.../util/atlas/`。

## FIT 侧 Smoke Test

新增 `FitTighteningDataParserTest` + `FitCurveDataParserTest`，各 1 个用例，使用现有 FIT hex 数据验证 `parse()` 返回正确 DTO：

- `FitTighteningDataParserTest`: 按协议格式构造 TIGHTEN_FINAL 数据（tighteningId 4B + status 1B + programNumber 1B + barcodeLength 1B + barcode NB + torque 4B + angle 4B + timestamp 7B），验证 `parse()` → `TighteningDataDTO` 关键字段（tighteningId, torque, angle, timestamp）
- `FitCurveDataParserTest`: 按协议格式构造 CURVE 数据（tighteningId 4B + 若干 12B 点），验证 `parse()` → `CurveDataDTO` 关键字段（tighteningId, 点数）

目的：确保代码搬运不破坏解析逻辑，不为全覆盖。

## 不在范围

- 不修改 `FitCurveDataReassembler`
- Atlas `AtlasDataUtils` 内部代码不变
