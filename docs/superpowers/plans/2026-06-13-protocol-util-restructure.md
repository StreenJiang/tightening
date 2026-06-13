# Protocol Util 目录按工具重构 & Parser 边界统一 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `protocol/util/` 按 Atlas/FIT 分子目录，提取 FIT Parser 类，统一 Atlas/FIT Parser 返回 DTO

**Architecture:** 3 个源文件移动（+ 包声明变更）+ 2 个新建 Parser 从 FitDataUtils 提取 + 5 个引用方 import 更新 + Atlas parser 返回类型 entity→DTO + Atlas handler 增加 Converter 调用

**Tech Stack:** Java 21, JUnit 5, AssertJ, Maven

---

### Task 1: 创建新目录

**Files:**
- Create: `src/main/java/com/tightening/netty/protocol/util/atlas/` (空目录)
- Create: `src/main/java/com/tightening/netty/protocol/util/fit/` (空目录)
- Create: `src/test/java/com/tightening/netty/protocol/util/atlas/` (空目录)
- Create: `src/test/java/com/tightening/netty/protocol/util/fit/` (空目录)

- [ ] **Step 1: 创建 4 个目录**

```bash
mkdir -p src/main/java/com/tightening/netty/protocol/util/atlas
mkdir -p src/main/java/com/tightening/netty/protocol/util/fit
mkdir -p src/test/java/com/tightening/netty/protocol/util/atlas
mkdir -p src/test/java/com/tightening/netty/protocol/util/fit
```

- [ ] **Step 2: 确认目录已创建**

```bash
ls -d src/main/java/com/tightening/netty/protocol/util/atlas src/main/java/com/tightening/netty/protocol/util/fit src/test/java/com/tightening/netty/protocol/util/atlas src/test/java/com/tightening/netty/protocol/util/fit
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tightening/netty/protocol/util/atlas src/main/java/com/tightening/netty/protocol/util/fit src/test/java/com/tightening/netty/protocol/util/atlas src/test/java/com/tightening/netty/protocol/util/fit
git commit -m "chore: create util/atlas and util/fit directories"
```

---

### Task 2: 移动 AtlasDataUtils（仅改包声明）

**Files:**
- Move: `src/main/java/com/tightening/netty/protocol/util/AtlasDataUtils.java` → `src/main/java/com/tightening/netty/protocol/util/atlas/AtlasDataUtils.java`

- [ ] **Step 1: 移动文件**

```bash
mv src/main/java/com/tightening/netty/protocol/util/AtlasDataUtils.java src/main/java/com/tightening/netty/protocol/util/atlas/AtlasDataUtils.java
```

- [ ] **Step 2: 修改包声明**

编辑 `src/main/java/com/tightening/netty/protocol/util/atlas/AtlasDataUtils.java`:

```
旧: package com.tightening.netty.protocol.util;
新: package com.tightening.netty.protocol.util.atlas;
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tightening/netty/protocol/util/AtlasDataUtils.java src/main/java/com/tightening/netty/protocol/util/atlas/AtlasDataUtils.java
git commit -m "refactor: move AtlasDataUtils to util/atlas"
```

---

### Task 3: 移动 AtlasTighteningDataParser 并改为返回 DTO

**Files:**
- Move: `src/main/java/com/tightening/netty/protocol/util/AtlasTighteningDataParser.java` → `src/main/java/com/tightening/netty/protocol/util/atlas/AtlasTighteningDataParser.java`

- [ ] **Step 1: 移动文件**

```bash
mv src/main/java/com/tightening/netty/protocol/util/AtlasTighteningDataParser.java src/main/java/com/tightening/netty/protocol/util/atlas/AtlasTighteningDataParser.java
```

- [ ] **Step 2: 修改包声明和 import**

包声明:
```
旧: package com.tightening.netty.protocol.util;
新: package com.tightening.netty.protocol.util.atlas;
```

import 变更:
```
删: import com.tightening.entity.TighteningData;
增: import com.tightening.dto.TighteningDataDTO;
```

- [ ] **Step 3: 修改 parse() 方法返回类型**

```java
// 旧
public static TighteningData parse(byte[] data, int revision) {
    TighteningData result = switch (revision) {

// 新
public static TighteningDataDTO parse(byte[] data, int revision) {
    TighteningDataDTO result = switch (revision) {
```

- [ ] **Step 4: 修改 4 处 `parseRev*` 中的 `new TighteningData()`**

`parseRev1`:
```java
// 旧
var d = new TighteningData();
// 新
var d = new TighteningDataDTO();
```

`parseRev2`:
```java
// 旧
var d = new TighteningData();
// 新
var d = new TighteningDataDTO();
```

`parseRev3`:
```java
// 旧
var d = new TighteningData();
// 新
var d = new TighteningDataDTO();
```

`parseRev999`:
```java
// 旧
var d = new TighteningData();
// 新
var d = new TighteningDataDTO();
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tightening/netty/protocol/util/AtlasTighteningDataParser.java src/main/java/com/tightening/netty/protocol/util/atlas/AtlasTighteningDataParser.java
git commit -m "refactor: move AtlasTighteningDataParser to util/atlas, return DTO"
```

---

### Task 4: 移动 FitDataUtils 并精简（提取 Parser 方法，调整可见性）

**Files:**
- Move: `src/main/java/com/tightening/netty/protocol/util/FitDataUtils.java` → `src/main/java/com/tightening/netty/protocol/util/fit/FitDataUtils.java`

- [ ] **Step 1: 移动文件**

```bash
mv src/main/java/com/tightening/netty/protocol/util/FitDataUtils.java src/main/java/com/tightening/netty/protocol/util/fit/FitDataUtils.java
```

- [ ] **Step 2: 修改包声明**

```
旧: package com.tightening.netty.protocol.util;
新: package com.tightening.netty.protocol.util.fit;
```

- [ ] **Step 3: 删除 `parseTighteningData` 和 `parseCurveData` 方法**

删除 `parseTighteningData(byte[] data)` 方法（约 70 行，从 `public static TighteningDataDTO parseTighteningData` 到该方法结束的 `}`）。

删除 `parseCurveData(byte[] data)` 方法（约 60 行，从 `public static CurveDataDTO parseCurveData` 到该方法结束的 `}`）。

保留的 import 不变（这些 import 也被保留的方法使用）。

- [ ] **Step 4: 调整 3 个 private 方法可见性为 package-private**

```java
// 旧
private static String getTimestampStr(byte[] data, int offset) {
// 新
static String getTimestampStr(byte[] data, int offset) {

// 旧
private static String parseBcdTimestamp(byte[] data, int offset) {
// 新
static String parseBcdTimestamp(byte[] data, int offset) {

// 旧
private static String getCurrentTimestampStr() {
// 新
static String getCurrentTimestampStr() {
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tightening/netty/protocol/util/FitDataUtils.java src/main/java/com/tightening/netty/protocol/util/fit/FitDataUtils.java
git commit -m "refactor: move FitDataUtils to util/fit, extract parser methods"
```

---

### Task 5: 创建 FitTighteningDataParser

**Files:**
- Create: `src/main/java/com/tightening/netty/protocol/util/fit/FitTighteningDataParser.java`

- [ ] **Step 1: 创建文件**

```java
package com.tightening.netty.protocol.util.fit;

import com.tightening.constant.FitAngleStatus;
import com.tightening.constant.FitTorqueStatus;
import com.tightening.constant.TighteningResultType;
import com.tightening.constant.TighteningStatus;
import com.tightening.dto.TighteningDataDTO;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * FIT 协议拧紧数据解析器。
 * 数据区格式：拧紧ID(4B) + 状态(1B) + 程序号(1B) + 条码长度(1B) + 条码(NB) + 扭矩(4B) + 角度(4B) + 时间戳(7B)
 */
@Slf4j
public final class FitTighteningDataParser {

    private FitTighteningDataParser() {}

    public static TighteningDataDTO parse(byte[] data) {
        int offset = 0;

        TighteningDataDTO tighteningData = new TighteningDataDTO();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // 1. 拧紧ID（4字节，小端）
        int tighteningId = buffer.getInt(offset);
        log.debug("tighteningId=" + tighteningId);
        offset += 4;

        // 2. 状态（1字节）
        if (data[offset] == 1) {
            tighteningData.setTighteningStatus(TighteningStatus.OK.getCode());
            tighteningData.setTorqueStatus(FitTorqueStatus.OK.getCode());
            tighteningData.setAngleStatus(FitAngleStatus.OK.getCode());
        } else {
            tighteningData.setTighteningStatus(TighteningStatus.NG.getCode());
            tighteningData.setTorqueStatus(FitTorqueStatus.NG.getCode());
            tighteningData.setAngleStatus(FitAngleStatus.NG.getCode());
        }
        log.debug("tightening_status=" + tighteningData.getTighteningStatus());
        offset += 1;

        // 3. 程序号（1字节）
        tighteningData.setParameterSet(data[offset] & 0xFF);
        log.debug("parameter_set=" + tighteningData.getParameterSet());
        offset += 1;

        // 4. 条码长度（1字节）
        int barcodeLength = data[offset] & 0xFF;
        log.debug("barcode length=" + barcodeLength);
        offset += 1;

        // 5. 条码内容（N字节，GBK编码）
        if (barcodeLength == 0) {
            offset += 1;
        } else {
            byte[] barcodeBytes = new byte[barcodeLength];
            System.arraycopy(data, offset, barcodeBytes, 0, barcodeLength);
            try {
                String barcode = new String(barcodeBytes, "GBK");
                log.debug("barcode=" + barcode);
            } catch (Exception e) {
                log.warn("Failed to decode barcode", e);
            }
            offset += barcodeLength;
        }

        // 6. 扭矩（4字节，IEEE 754 Float，小端）
        float torque = buffer.getFloat(offset);
        tighteningData.setTorque(torque);
        log.debug("torque=" + torque);
        offset += 4;

        // 7. 角度（4字节，IEEE 754 Float，小端）
        float angleFloat = buffer.getFloat(offset);
        int angle = (int) angleFloat;
        tighteningData.setAngle(angle);
        log.debug("angle=" + angle);
        offset += 4;

        if (angle >= 0) {
            tighteningData.setResultType(TighteningResultType.TIGHTENING.getCode());
        } else {
            tighteningData.setResultType(TighteningResultType.LOOSENING.getCode());
        }

        // 8. 时间戳（7字节，BCD码）
        String timestamp = FitDataUtils.parseBcdTimestamp(data, offset);
        tighteningData.setTimestamp(timestamp);
        log.debug("timestamp=" + timestamp);

        return tighteningData;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/tightening/netty/protocol/util/fit/FitTighteningDataParser.java
git commit -m "feat: add FitTighteningDataParser extracted from FitDataUtils"
```

---

### Task 6: 创建 FitCurveDataParser

**Files:**
- Create: `src/main/java/com/tightening/netty/protocol/util/fit/FitCurveDataParser.java`

- [ ] **Step 1: 创建文件**

```java
package com.tightening.netty.protocol.util.fit;

import com.tightening.constant.DeviceType;
import com.tightening.dto.CurveDataDTO;
import com.tightening.netty.protocol.codec.fit.CurveDataSamples;
import com.tightening.util.Converter;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * FIT 协议曲线数据解析器。
 * 数据格式：[tighteningId(4B)][allCurvePointsData]，每点 12B：时间(4B) + 扭矩(4B) + 角度(4B)
 */
@Slf4j
public final class FitCurveDataParser {

    private FitCurveDataParser() {}

    public static CurveDataDTO parse(byte[] data) {
        if (data == null || data.length < 4) {
            throw new IllegalArgumentException("数据区长度不足，最小需要4字节（拧紧ID）");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int offset = 0;

        // 1. 拧紧ID（4字节，小端）
        int tighteningId = buffer.getInt(offset);
        offset += 4;

        // 2. 解析曲线点数据（每个点12字节：时间4+扭矩4+角度4）
        int remainingBytes = data.length - offset;
        int totalPoints = remainingBytes / 12;

        if (totalPoints == 0) {
            throw new IllegalArgumentException("没有曲线点数据");
        }

        // 3. 创建曲线数据样本
        CurveDataSamples samples = new CurveDataSamples(tighteningId);

        for (int i = 0; i < totalPoints; i++) {
            float time = buffer.getFloat(offset);
            offset += 4;

            float torque = buffer.getFloat(offset);
            offset += 4;

            float angle = buffer.getFloat(offset);
            offset += 4;

            samples.addPoint(time, torque, angle);

            if (i < 3 || i >= totalPoints - 1) {
                log.debug("Point {}: time={}s, torque={}Nm, angle={}°",
                          i + 1,
                          String.format("%.4f", time),
                          String.format("%.2f", torque),
                          String.format("%.2f", angle));
            }
        }

        // 4. 创建 DTO
        CurveDataDTO curveData = new CurveDataDTO();
        curveData.setTighteningId(tighteningId);
        curveData.setDataType(DeviceType.FIT_FTC6.getId());
        curveData.setDataSamples(Converter.fromList(samples.getPoints()));
        curveData.setTimestamp(FitDataUtils.getCurrentTimestampStr());

        log.info("解析完成：tighteningId={}, 总点数={}", tighteningId, totalPoints);

        return curveData;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/tightening/netty/protocol/util/fit/FitCurveDataParser.java
git commit -m "feat: add FitCurveDataParser extracted from FitDataUtils"
```

---

### Task 7: 更新所有引用方 import

**Files:**
- Modify: `src/main/java/com/tightening/netty/protocol/codec/atlas/AtlasFrame.java`
- Modify: `src/main/java/com/tightening/netty/protocol/codec/atlas/AtlasFrameCodec.java`
- Modify: `src/main/java/com/tightening/netty/protocol/codec/fit/FitFrame.java`

- [ ] **Step 1: 更新 AtlasFrame.java import**

```java
// 旧
import com.tightening.netty.protocol.util.AtlasDataUtils;
// 新
import com.tightening.netty.protocol.util.atlas.AtlasDataUtils;
```

- [ ] **Step 2: 更新 AtlasFrameCodec.java 4 条 static import**

```java
// 旧
import static com.tightening.netty.protocol.util.AtlasDataUtils.decodeCurveData;
import static com.tightening.netty.protocol.util.AtlasDataUtils.decodeData;
import static com.tightening.netty.protocol.util.AtlasDataUtils.formatAscii;
import static com.tightening.netty.protocol.util.AtlasDataUtils.parseAsciiInt;
// 新
import static com.tightening.netty.protocol.util.atlas.AtlasDataUtils.decodeCurveData;
import static com.tightening.netty.protocol.util.atlas.AtlasDataUtils.decodeData;
import static com.tightening.netty.protocol.util.atlas.AtlasDataUtils.formatAscii;
import static com.tightening.netty.protocol.util.atlas.AtlasDataUtils.parseAsciiInt;
```

- [ ] **Step 3: 更新 FitFrame.java import**

```java
// 旧
import com.tightening.netty.protocol.util.FitDataUtils;
// 新
import com.tightening.netty.protocol.util.fit.FitDataUtils;
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tightening/netty/protocol/codec/atlas/AtlasFrame.java src/main/java/com/tightening/netty/protocol/codec/atlas/AtlasFrameCodec.java src/main/java/com/tightening/netty/protocol/codec/fit/FitFrame.java
git commit -m "refactor: update imports for util package restructure"
```

---

### Task 8: 更新 AtlasPFSeriesInBoundHandler（import + Converter 调用）

**Files:**
- Modify: `src/main/java/com/tightening/netty/protocol/handler/atlas/AtlasPFSeriesInBoundHandler.java`

- [ ] **Step 1: 更新 import**

```java
// 旧
import com.tightening.netty.protocol.util.AtlasDataUtils;
import com.tightening.netty.protocol.util.AtlasTighteningDataParser;
// 新
import com.tightening.netty.protocol.util.atlas.AtlasDataUtils;
import com.tightening.netty.protocol.util.atlas.AtlasTighteningDataParser;
```

新增:
```java
import com.tightening.dto.TighteningDataDTO;
import com.tightening.util.Converter;
```

- [ ] **Step 2: 修改 TIGHTEN_DATA case（约第 59-67 行）**

```java
// 旧
case TIGHTEN_DATA:
    TighteningData tighteningData = AtlasTighteningDataParser.parse(
            msg.getData(), msg.getRevision());
    TCPDeviceHandler.applyToolTypeName(ctx.channel(), tighteningData);
    log.debug("Parsed tightening data: tighteningId={}, torque={}, revision={}",
            tighteningData.getTighteningId(), tighteningData.getTorque(),
            tighteningData.getRevision());
    deviceHandler.getTighteningDataService().save(tighteningData);
    break;

// 新
case TIGHTEN_DATA:
    TighteningDataDTO dto = AtlasTighteningDataParser.parse(
            msg.getData(), msg.getRevision());
    TighteningData tighteningData = Converter.dto2Entity(dto, TighteningData::new);
    TCPDeviceHandler.applyToolTypeName(ctx.channel(), tighteningData);
    log.debug("Parsed tightening data: tighteningId={}, torque={}, revision={}",
            tighteningData.getTighteningId(), tighteningData.getTorque(),
            tighteningData.getRevision());
    deviceHandler.getTighteningDataService().save(tighteningData);
    break;
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tightening/netty/protocol/handler/atlas/AtlasPFSeriesInBoundHandler.java
git commit -m "refactor: update AtlasPFSeriesInBoundHandler imports and add DTO conversion"
```

---

### Task 9: 更新 FitSeriesInBoundHandler（拆分为三个 import）

**Files:**
- Modify: `src/main/java/com/tightening/netty/protocol/handler/fit/FitSeriesInBoundHandler.java`

- [ ] **Step 1: 更新 import**

```java
// 旧
import com.tightening.netty.protocol.util.FitDataUtils;
// 新
import com.tightening.netty.protocol.util.fit.FitDataUtils;
import com.tightening.netty.protocol.util.fit.FitTighteningDataParser;
import com.tightening.netty.protocol.util.fit.FitCurveDataParser;
```

- [ ] **Step 2: 修改方法调用**

`HEARTBEAT_ACK` case（`FitDataUtils.getDateStr` — 仍在 FitDataUtils，不变）:
```java
log.info("Heart beating from server at {}", FitDataUtils.getDateStr(timestampBytes));
```

`TIGHTEN_FINAL` case:
```java
// 旧
TighteningDataDTO tighteningDataDTO = FitDataUtils.parseTighteningData(data);
// 新
TighteningDataDTO tighteningDataDTO = FitTighteningDataParser.parse(data);
```

`CURVE` case:
```java
// 旧
CurveDataDTO curveDataDTO = FitDataUtils.parseCurveData(data);
// 新
CurveDataDTO curveDataDTO = FitCurveDataParser.parse(data);
```

`ALARM` case（`FitDataUtils.parseAlarmData` — 仍在 FitDataUtils，不变）:
```java
String alarmMsg = FitDataUtils.parseAlarmData(data);
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tightening/netty/protocol/handler/fit/FitSeriesInBoundHandler.java
git commit -m "refactor: update FitSeriesInBoundHandler to use new parser classes"
```

---

### Task 10: 移动并更新 AtlasTighteningDataParserTest

**Files:**
- Move: `src/test/java/com/tightening/netty/protocol/util/AtlasTighteningDataParserTest.java` → `src/test/java/com/tightening/netty/protocol/util/atlas/AtlasTighteningDataParserTest.java`

- [ ] **Step 1: 移动文件**

```bash
mv src/test/java/com/tightening/netty/protocol/util/AtlasTighteningDataParserTest.java src/test/java/com/tightening/netty/protocol/util/atlas/AtlasTighteningDataParserTest.java
```

- [ ] **Step 2: 修改包声明**

```
旧: package com.tightening.netty.protocol.util;
新: package com.tightening.netty.protocol.util.atlas;
```

- [ ] **Step 3: 更新 import — `TighteningData` entity 改为 `TighteningDataDTO`**

```java
// 旧
import com.tightening.entity.TighteningData;
// 新
import com.tightening.dto.TighteningDataDTO;
```

- [ ] **Step 4: 更新 7 处局部变量类型 `TighteningData` → `TighteningDataDTO`**

```java
// 旧（共 7 处）
TighteningData d = AtlasTighteningDataParser.parse(data, ...);
// 新
TighteningDataDTO d = AtlasTighteningDataParser.parse(data, ...);
```

- [ ] **Step 5: 运行测试确认通过**

```bash
mvn test -pl . -Dtest="com.tightening.netty.protocol.util.atlas.AtlasTighteningDataParserTest" -DfailIfNoTests=false
```

- [ ] **Step 6: Commit**

```bash
git add src/test/java/com/tightening/netty/protocol/util/AtlasTighteningDataParserTest.java src/test/java/com/tightening/netty/protocol/util/atlas/AtlasTighteningDataParserTest.java
git commit -m "test: move AtlasTighteningDataParserTest to util/atlas, assert on DTO"
```

---

### Task 11: 创建 FitTighteningDataParserTest（smoke test）

**Files:**
- Create: `src/test/java/com/tightening/netty/protocol/util/fit/FitTighteningDataParserTest.java`

- [ ] **Step 1: 创建测试文件**

```java
package com.tightening.netty.protocol.util.fit;

import com.tightening.dto.TighteningDataDTO;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FitTighteningDataParserTest {

    @Test
    void parse_shouldExtractKeyFields() {
        // 按协议格式构造数据：tighteningId(4B) + status(1B) + programNumber(1B)
        // + barcodeLength(1B) + barcode(NB) + torque(4B) + angle(4B) + timestamp(7B)
        String barcode = "VIN123";
        byte[] barcodeBytes = barcode.getBytes(java.nio.charset.Charset.forName("GBK"));
        int dataLength = 4 + 1 + 1 + 1 + barcodeBytes.length + 4 + 4 + 7;
        ByteBuffer buf = ByteBuffer.allocate(dataLength).order(ByteOrder.LITTLE_ENDIAN);

        // tighteningId = 42
        buf.putInt(42);
        // status = 1 (OK)
        buf.put((byte) 1);
        // programNumber = 3
        buf.put((byte) 3);
        // barcodeLength
        buf.put((byte) barcodeBytes.length);
        // barcode
        buf.put(barcodeBytes);
        // torque = 12.5f
        buf.putFloat(12.5f);
        // angle = 180.0f
        buf.putFloat(180.0f);
        // timestamp BCD: 2025-06-15 10:30:00
        buf.put((byte) 0x20);
        buf.put((byte) 0x25);
        buf.put((byte) 0x06);
        buf.put((byte) 0x15);
        buf.put((byte) 0x10);
        buf.put((byte) 0x30);
        buf.put((byte) 0x00);

        TighteningDataDTO dto = FitTighteningDataParser.parse(buf.array());

        assertThat(dto.getTighteningStatus()).isEqualTo(1);
        assertThat(dto.getParameterSet()).isEqualTo(3);
        assertThat(dto.getTorque()).isCloseTo(12.5, within(0.01));
        assertThat(dto.getAngle()).isEqualTo(180);
        assertThat(dto.getTimestamp()).isEqualTo("2025-06-15 10:30:00");
    }
}
```

- [ ] **Step 2: 运行测试确认通过**

```bash
mvn test -pl . -Dtest="com.tightening.netty.protocol.util.fit.FitTighteningDataParserTest" -DfailIfNoTests=false
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/tightening/netty/protocol/util/fit/FitTighteningDataParserTest.java
git commit -m "test: add FitTighteningDataParser smoke test"
```

---

### Task 12: 创建 FitCurveDataParserTest（smoke test）

**Files:**
- Create: `src/test/java/com/tightening/netty/protocol/util/fit/FitCurveDataParserTest.java`

- [ ] **Step 1: 创建测试文件**

```java
package com.tightening.netty.protocol.util.fit;

import com.tightening.dto.CurveDataDTO;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;

class FitCurveDataParserTest {

    @Test
    void parse_shouldExtractKeyFields() {
        // 按协议格式构造数据：tighteningId(4B) + 2个曲线点(各12B) = 28B
        ByteBuffer buf = ByteBuffer.allocate(4 + 2 * 12).order(ByteOrder.LITTLE_ENDIAN);

        // tighteningId = 100
        buf.putInt(100);
        // Point 1: time=0.1s, torque=5.0Nm, angle=10.0deg
        buf.putFloat(0.1f);
        buf.putFloat(5.0f);
        buf.putFloat(10.0f);
        // Point 2: time=0.2s, torque=15.0Nm, angle=90.0deg
        buf.putFloat(0.2f);
        buf.putFloat(15.0f);
        buf.putFloat(90.0f);

        CurveDataDTO dto = FitCurveDataParser.parse(buf.array());

        assertThat(dto.getTighteningId()).isEqualTo(100);
        assertThat(dto.getDataSamples()).isNotNull();
        // 2 个点的 JSON 字符串包含 2 个对象
        assertThat(dto.getDataSamples()).contains("\"torque\":5.0");
        assertThat(dto.getDataSamples()).contains("\"torque\":15.0");
    }
}
```

- [ ] **Step 2: 运行测试确认通过**

```bash
mvn test -pl . -Dtest="com.tightening.netty.protocol.util.fit.FitCurveDataParserTest" -DfailIfNoTests=false
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/tightening/netty/protocol/util/fit/FitCurveDataParserTest.java
git commit -m "test: add FitCurveDataParser smoke test"
```

---

### Task 13: 整体验证

- [ ] **Step 1: 编译检查**

```bash
mvn compile -q
```

预期: BUILD SUCCESS，无编译错误。

- [ ] **Step 2: 运行所有相关测试**

```bash
mvn test -Dtest="com.tightening.netty.protocol.**" -DfailIfNoTests=false
```

预期: 所有测试通过，包括 `AtlasTighteningDataParserTest`、`AtlasFrameCodecTest`、`FitFrameCodecTest`、`FitTighteningDataParserTest`、`FitCurveDataParserTest`。

- [ ] **Step 3: 运行全量测试**

```bash
mvn test
```

预期: BUILD SUCCESS，无回归。

- [ ] **Step 4: Commit（如有未提交的修正）**

```bash
git status
# 如有修正，commit
```
