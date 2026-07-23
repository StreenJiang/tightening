# Atlas MID 0061 拧紧数据解析器设计

## 背景

Atlas 拧紧控制器通过 MID 0061 消息（`AtlasFrame.mid=61`）上报拧紧结果数据。数据区为 ASCII 编码的 TLV 结构（2 字节参数 ID + N 字节值），随 revision 不同字段数量从 23 到 57+ 不等。

当前 `AtlasPFSeriesInBoundHandler` 对 `TIGHTEN_DATA` 仅打日志，未实际解析。FIT 协议已有 `FitDataUtils.parseTighteningData` 解析并入库。需要实现 Atlas 侧解析，统一入库路径。

## 目标

1. 提供静态解析方法，按 revision 解析 MID 0061 data 区
2. 解析结果直接填充 `TighteningData` 实体，可落库
3. 覆盖所有 revision：1-7、998（multistage）、999（light）
4. Atlas 和 FIT 共用同一个 DB 实体

## 设计决策

### 1. 解析策略：硬编码偏移量，按 revision 分派

忽略参数 ID 前缀，直接按文档标明的 byte 偏移量读取。

**关键发现**：rev 1 和 rev 2 的同名字段偏移量不同（如 Torque rev 1 在 byte 141-146，rev 2 在 byte 184-189）。因此不能做链式调用，而是使用 `fillRev2Structured` 助手方法供 rev 2-7/998 共享结构化字段解析。`parseRev1` 独立实现。

**为什么不用注解/配置驱动**：不同 revision 的字段类型混杂（int/double/string/bitfield），rev 998 末尾变长 stage results 和 rev 999 无 ID 格式会破坏统一模型。硬编码最直观、性能最好、易测试。

### 2. 实体：改造现有 `TighteningData`，加 `extraData` JSON + `revision`

不再新建 POJO。所有协议的共有字段保留为结构化列，协议特有字段存入 `extraData`（TEXT/JSON 列）。

新增字段：
- `revision` (int) — Atlas revision 号，非 Atlas 数据为 0
- `extraData` (String, JSON) — 协议特有字段，FIT barcode 等同理

字段重命名（统一用 Status 而非 Result）：
- `tighteningResult` → `tighteningStatus`
- `torqueResult` → `torqueStatus`
- `angleResult` → `angleStatus`
- `rundownAngleResult` → `rundownAngleStatus`

**结构化列**：

| 字段 | 类型 | 来源 |
|---|---|---|
| taskRecordId | long | 业务层填入 |
| workstationName | String | 业务层填入 |
| toolName | String | 业务层填入 |
| toolTypeName | String | Handler 层设 DeviceType 名称 |
| productSideName | String | 业务层填入 |
| boltSerialNum | int | 业务层填入 |
| armLocation | String | 业务层填入 |
| parameterSet | int | Atlas/FIT |
| parameterSetName | String | Atlas rev 3+/FIT |
| tighteningId | long | Atlas/FIT |
| tighteningStatus | int | Atlas (0=NOK/1=OK) / FIT (0=NOK/1=OK) |
| resultType | int | Atlas rev 3+ / FIT |
| torqueStatus | int | Atlas (0=Low/1=OK/2=High) / FIT (0=NOK/1=OK) |
| angleStatus | int | Atlas (0=Low/1=OK/2=High) / FIT (0=NOK/1=OK) |
| rundownAngleStatus | int | Atlas rev 2+ (0=Low/1=OK/2=High) |
| torqueValuesUnit | int | Atlas rev 3+ |
| torqueMinLimit | double | Atlas |
| torqueMaxLimit | double | Atlas |
| torqueFinalTarget | double | Atlas |
| torque | double | Atlas / FIT |
| angleMinLimit | double | Atlas |
| angleMaxLimit | double | Atlas |
| angleFinalTarget | double | Atlas |
| angle | double | Atlas / FIT |
| rundownAngleMinLimit | double | Atlas rev 2+ |
| rundownAngleMaxLimit | double | Atlas rev 2+ |
| rundownAngle | double | Atlas rev 2+ |
| timestamp | String | Atlas / FIT |
| cellId | int | Atlas |
| channelId | int | Atlas |
| controllerName | String | Atlas |
| vin | String | Atlas |
| jobId | int | Atlas |
| batchSize | int | Atlas |
| batchCounter | int | Atlas |
| batchStatus | int | Atlas |
| revision | int | Atlas revision (非 Atlas 为 0) |
| extraData | String (JSON) | 协议特有字段 |

### 3. 枚举设计 — 按工具定义独立枚举

`torqueStatus`/`angleStatus` 列在 Atlas 和 FIT 中语义不同：Atlas 三值（Low/OK/High），FIT 二值（OK/NG）。为避免 code 冲突和展示歧义，为每个工具定义独立枚举。

| 枚举 | 值 | 使用方 |
|---|---|---|
| `TighteningStatus` | NG(0), OK(1) | Atlas + FIT 共享（语义一致） |
| `AtlasTorqueStatus` | LOW(0), OK(1), HIGH(2) | AtlasTighteningDataParser |
| `AtlasAngleStatus` | LOW(0), OK(1), HIGH(2) | AtlasTighteningDataParser |
| `FitTorqueStatus` | OK(1), NG(2) | FitDataUtils |
| `FitAngleStatus` | OK(1), NG(2) | FitDataUtils |

Atlas 解析器通过 `AtlasTorqueStatus.fromCode()` / `AtlasAngleStatus.fromCode()` 转换后写入，确保值在协议范围内。FIT 通过 `FitTorqueStatus` / `FitAngleStatus` 取值。

### 4. `AtlasExtraDataKeys` 常量类 + `ExtraDataKeys` 公共常量

`AtlasExtraDataKeys` 定义 Atlas 专属 key，`ExtraDataKeys` 定义跨工具公共 key（如 FIT barcode）。

### 5. 数据库

Flyway 迁移（V1.0.6）：
- RENAME COLUMN: `tightening_result`→`tightening_status`, `torque_result`→`torque_status`, `angle_result`→`angle_status`, `rundown_angle_result`→`rundown_angle_status`
- ADD COLUMN: `cell_id`, `channel_id`, `controller_name`, `vin`, `job_id`, `batch_size`, `batch_counter`, `batch_status`, `extra_data`, `revision`

## 架构

```
AtlasFrame (带 revision)
    │
    ▼
AtlasTighteningDataParser.parse(data, revision)
    │
    ▼
TighteningData (结构化列 + extraData JSON)
    │  ← Handler 补填 toolTypeName
    ▼
TighteningDataService.save()
```

```
FIT: FitFrame.data → FitDataUtils.parseTighteningData() → TighteningData → save()
```

## 文件清单

| 文件 | 操作 | 说明 |
|---|---|---|
| `netty/protocol/util/AtlasTighteningDataParser.java` | 新建 | 解析器，静态 `parse(byte[], int)` |
| `constant/atlas/AtlasExtraDataKeys.java` | 新建 | Atlas extraData key 常量 |
| `constant/ExtraDataKeys.java` | 新建 | 跨工具 extraData key 常量 |
| `constant/AtlasTorqueStatus.java` | 新建（替代 TorqueStatus） | Atlas 扭矩状态枚举 |
| `constant/AtlasAngleStatus.java` | 新建（替代 AngleStatus） | Atlas 角度状态枚举 |
| `constant/FitTorqueStatus.java` | 新建 | FIT 扭矩状态枚举 |
| `constant/FitAngleStatus.java` | 新建 | FIT 角度状态枚举 |
| `constant/TighteningStatus.java` | 新建（替代 TighteningResult） | 共享拧紧状态枚举 |
| `entity/TighteningData.java` | 修改 | 字段重命名 + 新增列 + extraData + revision |
| `dto/TighteningDataDTO.java` | 修改 | 同步实体改动 |
| `db/migration/V1.0.6__rename_and_add_atlas_columns.sql` | 新建 | RENAME + ADD COLUMN |
| `netty/protocol/handler/atlas/AtlasPFSeriesInBoundHandler.java` | 修改 | TIGHTEN_DATA 分支 + 构造器 ToolHandler |
| `netty/protocol/util/FitDataUtils.java` | 修改 | 字段名同步 |

## 解析器结构

```java
public final class AtlasTighteningDataParser {

    public static TighteningData parse(byte[] data, int revision) {
        return switch (revision) {
            case 1 -> parseRev1(data);
            case 2 -> parseRev2(data);      // fillRev2Structured + rev2 extraData
            case 3 -> fillRevNBase(data, fillRev2Structured, buildRev2Extra, ...);
            case 4 -> ...;
            case 5 -> ...;
            case 6 -> ...;
            case 7 -> ...;
            case 998 -> parseRev998(data);  // fillRev2Structured + rev3-6 + multistage
            case 999 -> parseRev999(data);  // 独立：无参数ID，纯定长
            default -> throw new IllegalArgumentException("Unsupported revision: " + revision);
        };
    }

    // fillRev2Structured: 共享的 rev 2+ 结构化字段解析（共 ~25 个字段）
    // buildRev2Extra: 共享的 rev 2 extraData 构建（共 19 个字段）
    // fillRev6Base: 共享的 rev 3-6 叠加逻辑（减少重复代码）
    // parseRev998: fillRev6Base + 末尾变长 stage results
    // parseRev999: 完全独立的定长位置解析
}
```

## 字段映射（结构化列）

| TighteningData 字段 | Atlas rev 1 | Atlas rev 2+ | FIT |
|---|---|---|---|
| cellId | byte 23-26 | byte 23-26 | — |
| channelId | byte 29-30 | byte 29-30 | — |
| controllerName | byte 33-57 | byte 33-57 | — |
| vin | byte 60-84 | byte 60-84 | — |
| jobId | byte 87-88 | byte 87-88 | — |
| parameterSet | byte 91-93 | byte 91-93 | data[2] |
| tighteningStatus | byte 108 | byte 121 | status 派生 |
| torqueStatus | byte 111 | byte 127 | status 派生 |
| angleStatus | byte 114 | byte 130 | status 派生 |
| torque | byte 141-146 (÷100) | byte 184-189 (÷100) | 小端 4 字节 |
| angle | byte 170-174 | byte 213-217 | 小端 4 字节 |
| timestamp | byte 177-195 | byte 346-364 | BCD 7 字节 |
| batchStatus | byte 219 | byte 124 | — |
| tighteningId | byte 222-231 | byte 304-313 | 小端 4 字节 |

## extraData 字段（Atlas 专属）

### Rev 2+

strategy, strategyOptions, currentMonitoringStatus, selfTapStatus,
pvMonitorStatus, pvCompensateStatus, tighteningErrorStatus,
currentMonitoringMin, currentMonitoringMax, currentMonitoringValue,
selfTapMin, selfTapMax, selfTapTorque, pvMonitorMin, pvMonitorMax,
prevailTorque, jobSequenceNumber, syncTighteningId, toolSerialNumber

### Rev 4+

identifierResultPart2, identifierResultPart3, identifierResultPart4

### Rev 5+

customerTighteningErrorCode

### Rev 6+

pvCompensateValue, tighteningErrorStatus2

### Rev 7+

compensatedAngle, finalAngleDecimal

### Rev 998

totalStages, completedStages, stageResults (List\<Map\>)

### Rev 999

无额外字段，仅核心结构化列。

## 边界处理

- **data 长度不足**：记录 warn 日志，已读字段保留，未读字段保持默认值
- **ASCII 解析失败**（非数字等）：记录 warn，字段置默认值
- **revision 无效**：抛 `IllegalArgumentException`
- **offset 计算**：方法参数使用文档 byte 编号（1-based），内部减 21 转 data 数组索引

## 测试策略

- 复用 `AtlasFrameCodecTest` 中已有的真实 rev 3 数据验证
- 每个 revision 构造最小有效数据，验证结构化字段和 extraData
- rev 998 验证变长 stage results 正确解析
- rev 999 验证无 ID 定长解析
- 边界测试：data 截断、空 data、无效 revision
