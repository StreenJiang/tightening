# Device / Protocol 包优化实施计划

**日期**: 2026-06-15  
**前置文档**: `2026-06-15-device-protocol-review-design.md`

---

## 已完成（审查过程中修复，无需再动）

| # | 文件 | 修复内容 |
|---|------|---------|
| F1 | `ToolHandler.java` | TOCTOU 竞态：时间戳锁内提前更新，失败回滚；提取 `setLastTime()` |
| F2 | `TCPDeviceHandler.java` | `disconnect` 移除无效 `addListener`，加空检查 |
| F3 | `FitFrameCodec.java` | `dataLength==0` 时读取 tail |
| F4 | `AtlasFrame.java` | `toString()` data 判空 |
| F5 | `CurveDataSamples.java` | `points` 字段初始化 `new ArrayList<>()` |
| F6 | `DeviceManager.java` | 添加 `@Slf4j` + catch 中 `log.error` |
| F7 | `FitCurveDataReassembler.java` | 超时在 `computeIfAbsent` 首次创建时启动 |

---

## 批次 1: 低风险单文件改动（立即执行）

### 1.1 日志级别修正

**文件**: `AtlasFrameCodec.java`、`FitFrameCodec.java`

**步骤**:
1. `AtlasFrameCodec.encode()` L25: `log.info` → `log.debug`
2. `AtlasFrameCodec.decode()` L49: `log.info` → `log.debug`
3. `FitFrameCodec.encode()` L17: `log.info` → `log.debug`
4. `FitFrameCodec.decode()` L31: `log.info` → `log.debug`

**验证**: `mvn compile` 通过

---

### 1.2 CONNECT case fall-through 显式化

**文件**: `AtlasPFSeriesInBoundHandler.java`

**步骤**:
1. 在 CONNECT case 的 key 重映射后，添加 `deviceHandler.addResultFuture(key, result); break;`

```java
// 改前
case CONNECT:
    key = deviceHandler.generateKey(AtlasCommandType.CONNECT_ACK, deviceId);
case DISABLE:

// 改后
case CONNECT:
    key = deviceHandler.generateKey(AtlasCommandType.CONNECT_ACK, deviceId);
    deviceHandler.addResultFuture(key, result);
    break;
case DISABLE:
```

**验证**: `mvn test -pl .` 全量测试通过

---

### 1.3 FitTighteningDataParser 补 tighteningId

**文件**: `FitTighteningDataParser.java`

**步骤**:
1. 在 L28 解析 `tighteningId` 后，添加 `tighteningData.setTighteningId(Integer.toUnsignedLong(tighteningId));`

**注意**: `barcode` 暂不处理——它应存入 `mission_record` 而非 `tightening_data`。

**验证**: `mvn test -pl .` 全量测试通过

---

## 批次 2: InBoundHandler 解耦（需架构改动）

### 涉及文件

- `ToolHandler.java` — 新增 3 个回调方法
- `AtlasPFSeriesInBoundHandler.java` — 改为委托调用
- `FitSeriesInBoundHandler.java` — 改为委托调用

### 步骤

**2.1** 在 `ToolHandler` 新增回调方法和 imports：

```java
// 新增 imports
import com.tightening.dto.CurveDataDTO;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.CurveData;
import com.tightening.entity.TighteningData;
import com.tightening.util.Converter;
```

```java
// 新增方法
public void handleTighteningData(TighteningDataDTO dto, Channel channel) {
    TighteningData data = Converter.dto2Entity(dto, TighteningData::new);
    TCPDeviceHandler.applyToolTypeName(channel, data);
    tighteningDataService.save(data);
}

public void handleCurveData(CurveDataDTO dto, Channel channel) {
    // TODO: 补充持久化和 SSE 推送
}

public void handleAlarm(String alarmMsg, long deviceId) {
    log.warn("Alarm from device {}: {}", deviceId, alarmMsg);
}
```

**2.2** `AtlasPFSeriesInBoundHandler` 修改 case 为委托调用：

```java
case TIGHTEN_DATA:
    TighteningDataDTO dto = AtlasTighteningDataParser.parse(msg.getData(), msg.getRevision());
    deviceHandler.handleTighteningData(dto, ctx.channel());
    break;
case CURVE_DATA:
    // TODO: 补充持久化和 SSE 推送
    break;
```

**2.3** `FitSeriesInBoundHandler` 修改 case 为委托调用：

```java
case TIGHTEN_FINAL:
    TighteningDataDTO tighteningDataDTO = FitTighteningDataParser.parse(data);
    deviceHandler.handleTighteningData(tighteningDataDTO, ctx.channel());
    break;
case CURVE:
    CurveDataDTO curveDataDTO = FitCurveDataParser.parse(data);
    deviceHandler.handleCurveData(curveDataDTO, ctx.channel());
    break;
case ALARM:
    deviceHandler.handleAlarm(FitDataUtils.parseAlarmData(data), deviceId);
    break;
```

**2.4** 清理 InBoundHandler 中不再需要的 import：

`AtlasPFSeriesInBoundHandler.java` 移除：
- `import com.tightening.device.handler.impl.TCPDeviceHandler` — applyToolTypeName 已移至 ToolHandler
- `import com.tightening.entity.TighteningData`
- `import com.tightening.util.Converter`

保留（仍在使用）：
- `import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_ID` — 读取 channel 属性

`FitSeriesInBoundHandler.java` 移除：
- `import com.tightening.device.handler.impl.TCPDeviceHandler`
- `import com.tightening.entity.TighteningData`
- `import com.tightening.service.TighteningDataService`
- `import com.tightening.util.Converter`

**2.5** 测试适配：

`AtlasPFSeriesInBoundHandlerTest.java`:
- 移除 `tighteningDataService` mock 和 `getTighteningDataService()` stub
- 将 `verify(tighteningDataService).save()` 改为 `verify(deviceHandler).handleTighteningData(any(), any())`

`FitSeriesInBoundHandlerTest.java`:
- 同上

`AtlasFrameCodecTest.java`:
- 全 pipeline 测试，同样将 `verify(tighteningDataService).save()` 改为 verify `handleTighteningData()`

**验证**: `mvn test -pl .` 全量测试通过

---

## 批次 3: 延后（等触发条件）

| 项目 | 触发条件 |
|------|---------|
| #13 空壳类 → `getSupportedTypes()` 注册 | PF6000-OP 需要与 PF4000 不同的逻辑时 |
| #16 共享 EventLoopGroup | 与 #13 一起做 |

---

## 不改

| 项目 | 原因 |
|------|------|
| rev7→rev2 链式解析 | 结构清晰，性能影响可忽略 |
| self=this 构造器逃逸 | 不影响正确性 |
| enable/disable 共用 key | 协议规定 |
| SUBSCRIBE_DATA ACK | 用户未完成实现 |
| barcode 存 tightening_data | 应存 mission_record |
