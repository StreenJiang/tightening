# Atlas Curve Data Parser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 Atlas 曲线数据解析器，参照 `AtlasTighteningDataParser` 模式，完成从协议帧到持久化的完整链路。

**Architecture:** 创建 `AtlasCurveDataParser`（`netty/protocol/util/atlas/`）解析 MID 0900 的 ASCII header + binary samples；新建 `CurveDataMapper`/`CurveDataService` 提供持久化层；接入 `AtlasPFSeriesInBoundHandler` 的 CURVE_DATA 分支和 `ToolHandler.handleCurveData()`。

**Tech Stack:** Java 21, Spring Boot 3.5.10, MyBatis-Plus 3.5.9, JUnit 5, AssertJ

---

## File Structure

| 操作 | 文件 | 职责 |
|---|---|---|
| Create | `mapper/CurveDataMapper.java` | MyBatis-Plus Mapper |
| Create | `service/CurveDataService.java` | 持久化服务 |
| Create | `netty/protocol/util/atlas/AtlasCurveDataParser.java` | 曲线数据解析 |
| Create | `test/.../atlas/AtlasCurveDataParserTest.java` | 单元测试 |
| Modify | `netty/protocol/handler/atlas/AtlasPFSeriesInBoundHandler.java:63-65` | 接入解析器 |
| Modify | `device/handler/ToolHandler.java:28-35,155-157` | 注入 service + 实现持久化 |
| Modify | `device/handler/impl/AtlasPFSeriesHandler.java:34` | 传递新参数 |
| Modify | `device/handler/impl/FitSeriesHandler.java:44` | 传递新参数 |

---

### Task 1: 创建 CurveDataMapper 和 CurveDataService

**Files:**
- Create: `src/main/java/com/tightening/mapper/CurveDataMapper.java`
- Create: `src/main/java/com/tightening/service/CurveDataService.java`

- [ ] **Step 1: 创建 CurveDataMapper**

```java
package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.CurveData;

@Mapper
public interface CurveDataMapper extends BaseMapper<CurveData> {}
```

- [ ] **Step 2: 创建 CurveDataService**

```java
package com.tightening.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.CurveData;
import com.tightening.mapper.CurveDataMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CurveDataService extends ServiceImpl<CurveDataMapper, CurveData> {

}
```

---

### Task 2: 创建 AtlasCurveDataParser

**Files:**
- Create: `src/main/java/com/tightening/netty/protocol/util/atlas/AtlasCurveDataParser.java`

- [ ] **Step 1: 创建 AtlasCurveDataParser 类框架**

```java
package com.tightening.netty.protocol.util.atlas;

import com.tightening.dto.CurveDataDTO;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * Atlas MID 0900 曲线数据解析器。
 * 参照 AtlasTighteningDataParser 模式：static utility，公开 parse() 入口。
 * headerData = AtlasFrame.data（ASCII header），sampleData = AtlasFrame.attachedData（binary trace samples）
 */
@Slf4j
public final class AtlasCurveDataParser {

    private AtlasCurveDataParser() {}

    public static CurveDataDTO parse(byte[] headerData, byte[] sampleData, int revision) {
        // Step 2-5 will fill this
        return null;
    }
}
```

- [ ] **Step 2: 实现固定头部字段解析 + PID 字段迭代工具方法**

在类内部添加以下辅助方法，与 `AtlasTighteningDataParser` 相同的协议 byte 号约定（protocolByte 从 1 开始，headerData[0] 对应协议 byte 21）：

```java
    // === 固定字段解析（协议 byte 号 → 数组偏移 = protocolByte - 21）===

    private static int parseInt(byte[] data, int protocolByte, int length) {
        int offset = protocolByte - 21;
        if (offset + length > data.length) return 0;
        String raw = new String(data, offset, length, StandardCharsets.US_ASCII).trim();
        if (raw.isEmpty()) return 0;
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException e) {
            log.warn("Failed to parse int at protocol byte {}: '{}'", protocolByte, raw);
            return 0;
        }
    }

    private static String parseString(byte[] data, int protocolByte, int length) {
        int offset = protocolByte - 21;
        if (offset + length > data.length) return "";
        return new String(data, offset, length, StandardCharsets.US_ASCII).trim();
    }

    // === 可变字段解析（直接传数组偏移，0-indexed from headerData start）===

    private static int parseIntAtOffset(byte[] data, int offset, int length) {
        if (offset + length > data.length) return 0;
        String raw = new String(data, offset, length, StandardCharsets.US_ASCII).trim();
        if (raw.isEmpty()) return 0;
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String parseStringAtOffset(byte[] data, int offset, int length) {
        if (offset + length > data.length) return "";
        return new String(data, offset, length, StandardCharsets.US_ASCII).trim();
    }
```

- [ ] **Step 3: 实现 header 可变字段遍历逻辑（在 parse() 中）**

用一个追踪 offset 遍历 PID data fields、parameter data fields、resolution fields，提取 coefficient、trace type、number of samples：

```java
    public static CurveDataDTO parse(byte[] headerData, byte[] sampleData, int revision) {
        // 固定字段
        int tighteningId = parseInt(headerData, 21, 10);
        String timestamp = parseString(headerData, 31, 19);
        int numPids = parseInt(headerData, 50, 3);

        // 遍历 PID data fields，提取 coefficient
        int offset = 33; // byte 54 - 21 = 33，PID 数据字段从协议 byte 54 开始
        double coefficient = Double.NaN;

        for (int i = 0; i < numPids; i++) {
            if (offset + 17 > headerData.length) break;
            String pid = parseStringAtOffset(headerData, offset, 5);
            int fieldLen = parseIntAtOffset(headerData, offset + 10, 3);
            int valueOffset = offset + 17;

            if ("02214".equals(pid)) {
                coefficient = Double.parseDouble(parseStringAtOffset(headerData, valueOffset, fieldLen));
            } else if ("02213".equals(pid) && Double.isNaN(coefficient)) {
                coefficient = 1.0 / Double.parseDouble(parseStringAtOffset(headerData, valueOffset, fieldLen));
            }

            offset = valueOffset + fieldLen;
        }

        if (Double.isNaN(coefficient)) {
            log.warn("Coefficient PID (02213/02214) not found in header, defaulting to 1.0");
            coefficient = 1.0;
        }

        // Trace Type (2) + Transducer Type (2) + Unit (3)
        int traceType = parseIntAtOffset(headerData, offset, 2);
        offset += 7;

        // Number of parameter data fields → skip
        int numParamFields = parseIntAtOffset(headerData, offset, 3);
        offset += 3;
        for (int i = 0; i < numParamFields; i++) {
            if (offset + 17 > headerData.length) break;
            int fieldLen = parseIntAtOffset(headerData, offset + 10, 3);
            offset += 17 + fieldLen;
        }

        // Number of resolution fields → skip
        int numResFields = parseIntAtOffset(headerData, offset, 3);
        offset += 3;
        for (int i = 0; i < numResFields; i++) {
            if (offset + 18 > headerData.length) break;
            int timeLen = parseIntAtOffset(headerData, offset + 13, 3);
            offset += 18 + timeLen;
        }

        // Number of trace samples
        int numSamples = parseIntAtOffset(headerData, offset, 5);
        offset += 5;
        // offset now points to NUL (0x00), sampleData starts after it

        // 构建 DTO
        CurveDataDTO dto = new CurveDataDTO();
        dto.setTighteningId(tighteningId);
        dto.setTimestamp(timestamp);
        dto.setDataType(traceType);

        if (numSamples > 0 && sampleData != null && sampleData.length >= numSamples * 2) {
            dto.setDataSamples(parseSamples(sampleData, numSamples, coefficient, traceType));
        }

        log.debug("Parsed curve data: tighteningId={}, traceType={}, numSamples={}, coefficient={}",
                  tighteningId, traceType, numSamples, coefficient);
        return dto;
    }
```

- [ ] **Step 4: 实现二进制 sample 解析（参照 C# AnalyseCurveData）**

```java
    /**
     * 解析二进制曲线采样点。
     * 每点 2 字节 int16，计算物理值：rawValue × coefficient
     */
    private static String parseSamples(byte[] sampleData, int numSamples, double coefficient, int traceType) {
        int decimals = (traceType == 2) ? 2 : 0; // 2=扭矩保留2位, 1=角度保留0位
        StringBuilder sb = new StringBuilder();
        int singleDataLength = 2;

        for (int i = 0; i < numSamples * singleDataLength; i += singleDataLength) {
            // 2 字节合成 unsigned int
            int raw = ((sampleData[i] & 0xFF) << 8) | (sampleData[i + 1] & 0xFF);

            // 补码：值 > 2^15-1 表示负数
            if (raw > 32767) {
                raw = raw - 65536;
            }

            // 物理值 = raw × coefficient
            double value = Math.round(raw * coefficient * Math.pow(10, decimals)) / Math.pow(10, decimals);
            if (value == -0.0) {
                value = 0.0;
            }

            if (i > 0) sb.append(',');
            sb.append(formatDouble(value, decimals));
        }

        return sb.toString();
    }

    private static String formatDouble(double value, int decimals) {
        if (decimals == 0) {
            return String.valueOf((long) value);
        }
        return String.format("%." + decimals + "f", value);
    }
```

---

### Task 3: 编写 AtlasCurveDataParser 单元测试

**Files:**
- Create: `src/test/java/com/tightening/netty/protocol/util/atlas/AtlasCurveDataParserTest.java`

- [ ] **Step 1: 创建测试类，测试扭矩 trace 完整解析**

```java
package com.tightening.netty.protocol.util.atlas;

import com.tightening.dto.CurveDataDTO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AtlasCurveDataParserTest {

    // 构造一个最小 header：固定字段 + 1 个 PID (02214=2.5) + TraceType=2 + 1 sample
    // 协议 byte 布局（data 区从协议 byte 21 对应数组[0]）：
    //   [0-9]  = tighteningId  "0000000123"
    //   [10-28]= timestamp      "2024-06-25:10:30:00"
    //   [29-31]= numPids        "001"
    //   [32-36]= PID            "02214"
    //   [37-39]= Length         "009"
    //   [40-41]= DataType       "00"
    //   [42-44]= Unit           "000"
    //   [45-48]= StepNo         "0000"
    //   [49-57]= Value          "000002.50"
    //   [58-59]= TraceType      "02"
    //   [60-61]= TransducerType "01"
    //   [62-64]= Unit           "001"
    //   [65-67]= numParamFields "000"
    //   [68-70]= numResFields   "000"
    //   [71-75]= numSamples     "00001"
    //   [76]   = NUL            '\0'

    private static void writeAt(byte[] data, int protocolByte, String value) {
        if (value == null) return;
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, data, protocolByte - 21, bytes.length);
    }

    @Test
    void parseTorqueTrace_shouldExtractAllFields() {
        byte[] header = new byte[200];
        Arrays.fill(header, (byte) ' ');

        writeAt(header, 21, "0000000123");
        writeAt(header, 31, "2024-06-25:10:30:00");
        writeAt(header, 50, "001");

        int offset = 33; // byte 54 → array offset 33
        writeAt(header, offset + 21, "02214");
        writeAt(header, offset + 26, "009");
        writeAt(header, offset + 34, "000002.50");
        offset += 17 + 9;

        writeAt(header, offset + 21, "02");
        offset += 7;
        writeAt(header, offset + 21, "000");
        offset += 3;
        writeAt(header, offset + 21, "000");
        offset += 3;
        writeAt(header, offset + 21, "00001");
        offset += 5;
        header[offset] = 0; // NUL

        // 2-byte sample: 1000 → physical = 1000 * 2.5 = 2500.00
        byte[] samples = new byte[] { (byte) 0x03, (byte) 0xE8 };

        CurveDataDTO dto = AtlasCurveDataParser.parse(header, samples, 1);

        assertThat(dto.getTighteningId()).isEqualTo(123);
        assertThat(dto.getTimestamp()).isEqualTo("2024-06-25:10:30:00");
        assertThat(dto.getDataType()).isEqualTo(2);
        assertThat(dto.getDataSamples()).isEqualTo("2500.00");
    }
```

- [ ] **Step 2: 添加负值 sample 测试**

```java
    @Test
    void parseNegativeSamples_shouldHandleTwosComplement() {
        byte[] header = new byte[200];
        Arrays.fill(header, (byte) ' ');

        writeAt(header, 21, "0000000999");
        writeAt(header, 31, "2024-06-25:10:30:00");
        writeAt(header, 50, "001");

        int offset = 33;
        writeAt(header, offset + 21, "02214");
        writeAt(header, offset + 26, "003");
        writeAt(header, offset + 34, "1.0");
        offset += 17 + 3;

        writeAt(header, offset + 21, "01"); // traceType=1 角度
        offset += 7;
        writeAt(header, offset + 21, "000");
        offset += 3;
        writeAt(header, offset + 21, "000");
        offset += 3;
        writeAt(header, offset + 21, "00002");
        offset += 5;
        header[offset] = 0;

        // 0xFFFE = -2 (补码), 0x0005 = 5
        byte[] samples = new byte[] {
            (byte) 0xFF, (byte) 0xFE,
            (byte) 0x00, (byte) 0x05
        };

        CurveDataDTO dto = AtlasCurveDataParser.parse(header, samples, 1);

        assertThat(dto.getDataType()).isEqualTo(1);
        assertThat(dto.getDataSamples()).isEqualTo("-2,5");
    }
```

- [ ] **Step 3: 添加 02213 coefficient 测试 + 空 samples 测试**

```java
    @Test
    void parseWith02213Coefficient_shouldUseDivision() {
        byte[] header = new byte[200];
        Arrays.fill(header, (byte) ' ');

        writeAt(header, 21, "0000000001");
        writeAt(header, 31, "2024-06-25:10:30:00");
        writeAt(header, 50, "001");

        int offset = 33;
        writeAt(header, offset + 21, "02213"); // divide coefficient
        writeAt(header, offset + 26, "003");
        writeAt(header, offset + 34, "2.0");   // raw / 2.0
        offset += 17 + 3;

        writeAt(header, offset + 21, "02");
        offset += 7;
        writeAt(header, offset + 21, "000");
        offset += 3;
        writeAt(header, offset + 21, "000");
        offset += 3;
        writeAt(header, offset + 21, "00001");
        offset += 5;
        header[offset] = 0;

        byte[] samples = new byte[] { (byte) 0x00, (byte) 0x64 }; // 100

        CurveDataDTO dto = AtlasCurveDataParser.parse(header, samples, 1);
        assertThat(dto.getDataSamples()).isEqualTo("50.00"); // 100 / 2 = 50
    }

    @Test
    void parseEmptySamples_shouldReturnNullDataSamples() {
        byte[] header = new byte[200];
        Arrays.fill(header, (byte) ' ');

        writeAt(header, 21, "0000000000");
        writeAt(header, 31, "2024-06-25:10:30:00");
        writeAt(header, 50, "000");

        int offset = 33;
        writeAt(header, offset + 21, "00");
        offset += 7;
        writeAt(header, offset + 21, "000");
        offset += 3;
        writeAt(header, offset + 21, "000");
        offset += 3;
        writeAt(header, offset + 21, "00000");
        offset += 5;
        header[offset] = 0;

        CurveDataDTO dto = AtlasCurveDataParser.parse(header, new byte[0], 1);
        assertThat(dto.getDataSamples()).isNull();
    }
```

- [ ] **Step 4: 运行测试验证通过**

```
mvn test -pl . -Dtest="AtlasCurveDataParserTest" -DfailIfNoTests=false
```
Expected: All tests PASS

---

### Task 4: 接入 AtlasPFSeriesInBoundHandler

**Files:**
- Modify: `src/main/java/com/tightening/netty/protocol/handler/atlas/AtlasPFSeriesInBoundHandler.java:63-65`

- [ ] **Step 1: 替换 CURVE_DATA case 的 TODO**

将：

```java
                case CURVE_DATA:
                    // TODO: 补充持久化和 SSE 推送
                    break;
```

替换为：

```java
                case CURVE_DATA:
                    CurveDataDTO curveDto = AtlasCurveDataParser.parse(
                            msg.getData(), msg.getAttachedData(), msg.getRevision());
                    deviceHandler.handleCurveData(curveDto, ctx.channel());
                    break;
```

添加 import：

```java
import com.tightening.dto.CurveDataDTO;
import com.tightening.netty.protocol.util.atlas.AtlasCurveDataParser;
```

- [ ] **Step 2: 编译验证**

```
mvn compile -pl .
```
Expected: BUILD SUCCESS

---

### Task 5: 实现 ToolHandler.handleCurveData

**Files:**
- Modify: `src/main/java/com/tightening/device/handler/ToolHandler.java`
- Modify: `src/main/java/com/tightening/device/handler/impl/AtlasPFSeriesHandler.java`
- Modify: `src/main/java/com/tightening/device/handler/impl/FitSeriesHandler.java`

- [ ] **Step 1: ToolHandler 注入 CurveDataService**

在字段声明区添加：

```java
    @Getter
    private final CurveDataService curveDataService;
```

修改构造函数，增加参数：

```java
    public ToolHandler(NioEventLoopGroup group,
                       DeviceService deviceService,
                       TighteningDataService tighteningDataService,
                       CurveDataService curveDataService,
                       ToolCommonConfig toolCommonConfig) {
        super(group, deviceService);
        this.tighteningDataService = tighteningDataService;
        this.curveDataService = curveDataService;
        this.toolCommonConfig = toolCommonConfig;
    }
```

添加 import：

```java
import com.tightening.service.CurveDataService;
```

- [ ] **Step 2: 实现 handleCurveData 方法体**

替换：

```java
    public void handleCurveData(CurveDataDTO dto, Channel channel) {
        // TODO: 补充持久化和 SSE 推送
    }
```

为：

```java
    public void handleCurveData(CurveDataDTO dto, Channel channel) {
        CurveData data = Converter.dto2Entity(dto, CurveData::new);
        curveDataService.save(data);
    }
```

- [ ] **Step 3: 更新 AtlasPFSeriesHandler 构造函数**

修改 `super()` 调用，增加 `curveDataService` 参数：

```java
public class AtlasPFSeriesHandler extends ToolHandler {
    public AtlasPFSeriesHandler(NioEventLoopGroup group,
                                DeviceService deviceService,
                                TighteningDataService tighteningDataService,
                                CurveDataService curveDataService,
                                ToolCommonConfig toolCommonConfig) {
        super(group, deviceService, tighteningDataService, curveDataService, toolCommonConfig);
    }
    ...
}
```

- [ ] **Step 4: 更新 FitSeriesHandler 构造函数**

同样修改 `super()` 调用：

```java
public class FitSeriesHandler extends ToolHandler {
    public FitSeriesHandler(NioEventLoopGroup group,
                            DeviceService deviceService,
                            TighteningDataService tighteningDataService,
                            CurveDataService curveDataService,
                            ToolCommonConfig toolCommonConfig) {
        super(group, deviceService, tighteningDataService, curveDataService, toolCommonConfig);
    }
    ...
}
```

- [ ] **Step 5: 全量编译 + 运行测试验证**

```
mvn clean test -pl .
```
Expected: BUILD SUCCESS, all tests pass
