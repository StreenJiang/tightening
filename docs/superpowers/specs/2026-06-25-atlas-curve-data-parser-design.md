# Atlas Curve Data Parser Design

## 背景

Atlas MID 0900 协议消息包含拧紧曲线数据（扭矩/角度 trace），分两次独立发送。当前 `AtlasFrameCodec` 已完成 header（ASCII）与 binary samples 的拆解，但 `AtlasPFSeriesInBoundHandler` 中 CURVE_DATA 分支为空，`ToolHandler.handleCurveData()` 也未实现持久化。

## 目标

参照 `AtlasTighteningDataParser` 的模式，新增 `AtlasCurveDataParser` 解析 Atlas 曲线数据，并完成接入链路。

## 设计

### 新类 `AtlasCurveDataParser`

- 位置：`netty/protocol/util/atlas/AtlasCurveDataParser.java`
- 模式：`final` class + `private` constructor + static methods（与 `AtlasTighteningDataParser` 一致）
- 公开入口：`parse(byte[] headerData, byte[] sampleData, int revision)` → `CurveDataDTO`
- 内部常量：`PID_COEFFICIENT_MULTIPLY`("02214")、`PID_COEFFICIENT_DIVIDE`("02213")、`PID_HEADER_BYTES`(17)、`RES_FIELD_HEADER_BYTES`(18)、`TRACE_FIXED_BYTES`(7)、`INT16_MAX`(32767)、`INT16_RANGE`(65536)

### Header 解析（ASCII 部分，`AtlasFrame.data`）

固定字段按协议 byte 号直接提取：

| 字段 | 协议 byte | → DTO 字段 |
|---|---|---|
| Result Data Identifier | 21-30 | tighteningId |
| Timestamp | 31-49 | timestamp |
| Number of PID's | 50-52 | 决定迭代次数 |

可变 PID 数据字段：从 byte 53 开始，按 `PID(5)+Length(3)+DataType(2)+Unit(3)+StepNo(4)+Value(Length)` 迭代。重点关注：

| PID | 用途 |
|---|---|
| 02213 | coefficient（除法） |
| 02214 | coefficient（乘法，优先使用，与 C# 代码一致） |
| 02001 | Trace Type（1=角度, 2=扭矩）→ dataType |
| 02200 | Number of samples |

### Sample 解析（Binary 部分，`AtlasFrame.attachedData`）

与 C# `AnalyseCurveData` 逻辑一致：
- 每 2 字节为 int16
- 值 > 2¹⁵-1 为负数（补码）
- `physicalValue = rawValue × coefficient`
- 扭矩保留 2 位小数，角度保留 0 位
- 拼成逗号分隔字符串 → `dataSamples`

### 接入点

**`AtlasPFSeriesInBoundHandler`** — CURVE_DATA case 替换 TODO：
```
case CURVE_DATA:
    CurveDataDTO dto = AtlasCurveDataParser.parse(msg.getData(), msg.getAttachedData(), msg.getRevision());
    deviceHandler.handleCurveData(dto, ctx.channel());
    break;
```

**`ToolHandler.handleCurveData()`** — 实现持久化，参照 `handleTighteningData`：
```
CurveData entity = Converter.dto2Entity(dto, CurveData::new);
curveDataService.save(entity);
```

### 解析流程

```
AtlasFrame.data (ASCII header)
    │
    ├── AtlasDataUtils.parseIntAtProtocolByte(data, 21, 10) → tighteningId
    ├── AtlasDataUtils.parseStringAtProtocolByte(data, 31, 19) → timestamp
    ├── AtlasDataUtils.parseIntAtProtocolByte(data, 50, 3) → numPids
    ├── iterate PID fields → coefficient (02214 > 02213), traceType, numSamples
    │
AtlasFrame.attachedData (binary)
    │
    └── parseSamples(bytes, coefficient, traceType) → "v1,v2,v3,..."
```

解析辅助方法（`parseIntAtProtocolByte`、`parseStringAtProtocolByte`、`parseIntAtOffset`、`parseStringAtOffset`）统一提取到 `AtlasDataUtils`，供 `AtlasCurveDataParser` 和 `AtlasTighteningDataParser` 共享。

### 新增文件

| 文件 | 职责 |
|---|---|
| `mapper/CurveDataMapper.java` | MyBatis-Plus Mapper |
| `service/CurveDataService.java` | 持久化服务（`ServiceImpl<CurveDataMapper, CurveData>`） |

### 修改文件

| 文件 | 改动 |
|---|---|
| `AtlasDataUtils.java` | 新增 `parseIntAtProtocolByte`、`parseStringAtProtocolByte`、`parseIntAtOffset`、`parseStringAtOffset`、`parseLongAtProtocolByte` |
| `AtlasPFSeriesInBoundHandler.java` | CURVE_DATA case 调用 parser + handleCurveData |
| `ToolHandler.java` | 注入 `CurveDataService`，实现 `handleCurveData()` 持久化 |
| `AtlasPFSeriesHandler.java` | 构造函数增加 `CurveDataService` 参数 |
| `FitSeriesHandler.java` | 同上 |

## 测试

参照 `AtlasTighteningDataParserTest` 写单元测试，覆盖：
- 扭矩 trace 完整解析
- 角度 trace 完整解析
- 负值 sample 处理
- PID 02213 vs 02214 coefficient 选择
