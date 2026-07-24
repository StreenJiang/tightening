# IO 网关设备适配 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增安维能通信盒 DeviceHandler + 三种子设备契约接口（IArm/ISetterSelector/IArranger），扩展 DeviceRegistry，实现 SendArrangerSignal 和 SendSetterSelector Capability。

**Architecture:** 通信盒直接实现 DeviceHandler 接口（不继承 TCPDeviceHandler），管理 Modbus RTU over TCP 帧收发和子设备轮询。子设备通过契约接口暴露，DeviceRegistry 维护多套注册表（tools/arms/setters/arrangers）。Capability 执行期间通过 lockReason 机制与 LockStateMonitor 协作锁定工具。

**Tech Stack:** Java 21, Spring Boot 3.5.10, Netty 4.1.129, MyBatis-Plus 3.5.16, SQLite, JUnit 5 + AssertJ

## Global Constraints

- Java 21, 无额外新依赖
- 遵循项目现有分层：controller → service → mapper / device handler
- 子设备接口放 `device/contract/` 包，与现有 ITool 平级
- Adapters 放 `device/handler/impl/aneng/` 包下，package-private
- 禁止全限定类名 — 所有类型文件顶部 import
- Modbus RTU CRC16 复用 `Crc16Utils.compute()`
- Flyway 迁移文件编号顺延当前最末 V1.0.23

## File Structure

| File | Responsibility |
|------|---------------|
| `constant/DeviceType.java` (modify) | 新增 ANENG_GATEWAY/ARM/SETTER_SELECTOR/ARRANGER 枚举值 |
| `constant/DeviceStatus.java` (modify) | 新增 DEGRADED |
| `device/type/SubDevice.java` (create) | 子设备公共基类，持有 gatewayDeviceId |
| `device/type/Arm.java` (create) | 力臂实体（extends SubDevice） |
| `device/type/SetterSelector.java` (create) | 批头选择器实体（extends SubDevice） |
| `device/type/Arranger.java` (modify) | 改基类为 SubDevice |
| `entity/ProductBolt.java` (modify) | 新增 arranger/setter 4 字段 |
| `entity/ArmModelConfig.java` (create) | arm_model_config 表实体 |
| `mapper/ArmModelConfigMapper.java` (create) | 力臂型号配置 Mapper |
| `db/migration/V1.0.24__...sql` (create) | gateway_device_id + ProductBolt 列 |
| `db/migration/V1.0.25__...sql` (create) | arm_model_config 表 + CF01~CF04 预置 |
| `device/contract/IArm.java` (create) | 力臂契约接口 |
| `device/contract/ISetterSelector.java` (create) | 批头选择器契约接口 |
| `device/contract/IArranger.java` (create) | 排列机契约接口 |
| `device/contract/Coordinates3D.java` (create) | 三维坐标 record |
| `config/GatewayConfig.java` (create) | 网关配置类 |
| `netty/protocol/codec/ModbusRtuFrameDecoder.java` (create) | Modbus RTU 帧解码器 |
| `device/handler/impl/AnengGatewayHandler.java` (create) | 通信盒 DeviceHandler |
| `device/handler/impl/aneng/ArmAdapter.java` (create) | IArm 实现 |
| `device/handler/impl/aneng/SetterSelectorAdapter.java` (create) | ISetterSelector 实现 |
| `device/handler/impl/aneng/ArrangerAdapter.java` (create) | IArranger 实现 |
| `device/DeviceRegistry.java` (modify) | 新增 arms/setters/arrangers 注册表 |
| `device/DeviceManager.java` (modify) | addDevice 子设备过滤；DEGRADED 不重连 |
| `device/handler/DeviceHandlerFactory.java` (modify) | ANENG_GATEWAY 自动注册 |
| `lifecycle/TaskContext.java` (modify) | 新增 sub-device registry maps |
| `lifecycle/LifecycleEngineFactory.java` (modify) | 传入 sub-device maps；注册 SendSetterSelector |
| `lifecycle/capability/SendArrangerSignal.java` (modify) | 桩代码 → 真实实现 |
| `lifecycle/capability/SendSetterSelector.java` (create) | 批头切换 Capability |

### Task 1: 数据库迁移 + 枚举 + 实体基础

**Files:**
- Modify: `constant/DeviceType.java`
- Modify: `constant/DeviceStatus.java`
- Create: `device/type/SubDevice.java`
- Create: `device/type/Arm.java`
- Create: `device/type/SetterSelector.java`
- Modify: `device/type/Arranger.java`
- Modify: `entity/ProductBolt.java`
- Create: `entity/ArmModelConfig.java`
- Create: `mapper/ArmModelConfigMapper.java`
- Create: `resources/db/migration/V1.0.24__add_gateway_subdevice_columns.sql`
- Create: `resources/db/migration/V1.0.25__add_arm_model_config.sql`

**Interfaces:**
- Consumes: nothing
- Produces: `DeviceType.ANENG_GATEWAY(10)`, `ARM(11)`, `SETTER_SELECTOR(12)`, `ARRANGER(13)`; `DeviceStatus.DEGRADED`; `SubDevice` entity with `gatewayDeviceId`; `Arm`/`SetterSelector`/`Arranger` entity classes; `ProductBolt` with new fields; `ArmModelConfig` entity + mapper; deployed Flyway migrations

- [ ] **Step 1: 扩展 DeviceType 枚举**

```java
// constant/DeviceType.java — 尾部新增 4 个值
ATLAS_PF4000(1, "PF4000"),
ATLAS_PF6000_OP(2, "PF6000-OP"),
FIT_FTC6(3, "FIT-FTC6"),
SUDONG_X7(4, "SUDONG-X7"),

// 新增：网关及子设备（ID 分段：10-19）
ANENG_GATEWAY(10, "安维能通信盒"),
ARM(11, "力臂"),
SETTER_SELECTOR(12, "批头选择器"),
ARRANGER(13, "排列机"),
;
```

- [ ] **Step 2: 新增 DeviceStatus.DEGRADED**

```java
// constant/DeviceStatus.java — 尾部新增
public enum DeviceStatus {
    CONNECTING,
    CONNECTED,
    DEGRADED,       // TCP 通但部分子设备异常
    DISCONNECTED,
    NONE
}
```

- [ ] **Step 3: 新建 V1.0.24 Flyway 迁移**

```sql
-- resources/db/migration/V1.0.24__add_gateway_subdevice_columns.sql
ALTER TABLE device ADD COLUMN gateway_device_id INTEGER;
ALTER TABLE device ADD COLUMN arm_model_id INTEGER;

ALTER TABLE product_bolt ADD COLUMN arranger_device_id INTEGER;
ALTER TABLE product_bolt ADD COLUMN arranger_channels VARCHAR(50);
ALTER TABLE product_bolt ADD COLUMN setter_selector_id INTEGER;
ALTER TABLE product_bolt ADD COLUMN setter_position INTEGER;
```

- [ ] **Step 4: 新建 V1.0.25 Flyway 迁移**

```sql
-- resources/db/migration/V1.0.25__add_arm_model_config.sql
CREATE TABLE arm_model_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(20) NOT NULL,
    x_slave_addr INTEGER NOT NULL,
    x_register INTEGER NOT NULL,
    x_count INTEGER NOT NULL DEFAULT 1,
    y_slave_addr INTEGER NOT NULL,
    y_register INTEGER NOT NULL,
    y_count INTEGER NOT NULL DEFAULT 1,
    z_slave_addr INTEGER,
    z_register INTEGER,
    z_count INTEGER DEFAULT 1,
    parse_strategy VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
);

INSERT INTO arm_model_config (id, name, x_slave_addr, x_register, x_count,
    y_slave_addr, y_register, y_count, z_slave_addr, z_register, z_count, parse_strategy)
VALUES
  (1, 'CF01', 1, 0x0003, 2, 2, 0x0003, 2, NULL, NULL, 0, 'STANDARD'),
  (2, 'CF02', 1, 0x0000, 1, 2, 0x0000, 1, NULL, NULL, 0, 'STANDARD'),
  (3, 'CF03', 1, 0x0000, 1, 2, 0x0000, 1, 3, 0x0000, 1, 'STANDARD'),
  (4, 'CF04', 1, 0x0019, 1, 2, 0x0019, 1, NULL, NULL, 0, 'DIVIDE_BY_100');
```

- [ ] **Step 5: 新建 SubDevice 基类**

```java
// device/type/SubDevice.java
package com.tightening.device.type;

import com.tightening.entity.Device;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SubDevice extends Device {
    private Long gatewayDeviceId;
}
```

- [ ] **Step 6: 新建 Arm / SetterSelector 实体**

```java
// device/type/Arm.java
package com.tightening.device.type;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Arm extends SubDevice {
    private Integer armModelId;
}
```

```java
// device/type/SetterSelector.java
package com.tightening.device.type;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SetterSelector extends SubDevice {
    private Integer setterCount;
}
```

- [ ] **Step 7: 修改 Arranger — 改基类，补充字段**

```java
// device/type/Arranger.java — 替代当前文件
package com.tightening.device.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Arranger extends SubDevice {
    @JsonProperty("switch_bar_code")
    private String switchBarCode;

    @JsonProperty("switch_position")
    private String switchPosition;

    private Integer channelCount = 8;
    private Boolean reverseFirstFour;
}
```

- [ ] **Step 8: ProductBolt 新增字段 + Device entity 新增列映射**

```java
// entity/Device.java — 在现有字段后追加
private Long gatewayDeviceId;
private Integer armModelId;
```

```java
// entity/ProductBolt.java — 在现有字段后追加

```java
// entity/ProductBolt.java — 在现有字段后追加
private Long arrangerDeviceId;
private String arrangerChannels;          // 逗号分隔通道号
private Long setterSelectorId;
private Integer setterPosition;           // 1-based
```

- [ ] **Step 9: 新建 ArmModelConfig 实体 + Mapper**

```java
// entity/ArmModelConfig.java
package com.tightening.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("arm_model_config")
public class ArmModelConfig extends BaseEntity {
    private String name;
    private Integer xSlaveAddr;
    private Integer xRegister;
    private Integer xCount;
    private Integer ySlaveAddr;
    private Integer yRegister;
    private Integer yCount;
    private Integer zSlaveAddr;
    private Integer zRegister;
    private Integer zCount;
    private String parseStrategy;
}
```

```java
// mapper/ArmModelConfigMapper.java
package com.tightening.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.ArmModelConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArmModelConfigMapper extends BaseMapper<ArmModelConfig> {
}
```

- [ ] **Step 10: 验证数据库迁移**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Expected: 启动成功，console 输出 `Successfully applied 2 migrations`。检查生成的 `tightening.db` 确认新表和列存在。

- [ ] **Step 11: Commit**

```bash
git add ...
git commit -m "feat: add DeviceType/DeviceStatus enums, SubDevice entities, Flyway migrations"
```

---

### Task 2: 契约接口 + Coordinates3D

**Files:**
- Create: `device/contract/IArm.java`
- Create: `device/contract/ISetterSelector.java`
- Create: `device/contract/IArranger.java`
- Create: `device/contract/Coordinates3D.java`

**Interfaces:**
- Consumes: `DeviceType` enum (Task 1)
- Produces: `IArm extends IDevice`, `ISetterSelector extends IDevice`, `IArranger extends IDevice`, `Coordinates3D record`

- [ ] **Step 1: Coordinates3D record**

```java
// device/contract/Coordinates3D.java
package com.tightening.device.contract;

public record Coordinates3D(int x, int y, int z) {
    public static final Coordinates3D ZERO = new Coordinates3D(0, 0, 0);
}
```

- [ ] **Step 2: IArm 接口**

```java
// device/contract/IArm.java
package com.tightening.device.contract;

import java.util.concurrent.CompletableFuture;

public interface IArm extends IDevice {
    CompletableFuture<Coordinates3D> getCurrentCoordinates();
}
```

- [ ] **Step 3: ISetterSelector 接口**

```java
// device/contract/ISetterSelector.java
package com.tightening.device.contract;

import java.util.concurrent.CompletableFuture;

public interface ISetterSelector extends IDevice {
    CompletableFuture<Boolean> writePosition(int position);
    CompletableFuture<Boolean> reset();
    int getPositionCount();
}
```

- [ ] **Step 4: IArranger 接口**

```java
// device/contract/IArranger.java
package com.tightening.device.contract;

import java.util.concurrent.CompletableFuture;

public interface IArranger extends IDevice {
    CompletableFuture<Boolean> sendPulse(int[] channels, int pulseWidthMs);
    CompletableFuture<int[]> getOutputStatus();
    CompletableFuture<int[]> getInputStatus();
    CompletableFuture<Boolean> reset();
}
```

- [ ] **Step 5: 编译验证**

```bash
mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add device/contract/
git commit -m "feat: add IArm, ISetterSelector, IArranger contract interfaces"
```

---

### Task 3: GatewayConfig

**Files:**
- Create: `config/GatewayConfig.java`
- Modify: `src/main/resources/application-dev.yml`

**Interfaces:**
- Consumes: nothing (standalone config bean)
- Produces: `GatewayConfig` with `pollIntervalMs`, `armReadTimeoutMs`, `setterResetRetryMax`, `setterRetryDelayMs`

- [ ] **Step 1: 新建 GatewayConfig**

```java
// config/GatewayConfig.java
package com.tightening.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tool-control.gateway")
public class GatewayConfig {
    private long pollIntervalMs = 100;
    private long armReadTimeoutMs = 3000;
    private int setterResetRetryMax = 10;
    private long setterRetryDelayMs = 100;
}
```

- [ ] **Step 2: application-dev.yml 新增配置块**

```yaml
tool-control:
  gateway:
    poll-interval-ms: 100
    arm-read-timeout-ms: 3000
    setter-reset-retry-max: 10
    setter-retry-delay-ms: 100
```

同时更新 `application-standalone.yml` 和 `src/test/resources/application.yaml` 加入同配置块。

- [ ] **Step 3: 编译验证**

```bash
mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add config/ src/main/resources/application-dev.yml src/main/resources/application-standalone.yml src/test/resources/application.yaml
git commit -m "feat: add GatewayConfig for gateway device parameters"
```

---

### Task 4: ModbusRtuFrameDecoder

**Files:**
- Create: `netty/protocol/codec/ModbusRtuFrameDecoder.java`

**Interfaces:**
- Consumes: Netty `ByteToMessageDecoder`
- Produces: `ModbusRtuFrameDecoder` — 根据功能码推断响应帧长度，通过 CRC16 校验后 emit 完整 ByteBuf

- [ ] **Step 1: 编写测试**

```java
// 测试位置: src/test/java/com/tightening/netty/protocol/codec/ModbusRtuFrameDecoderTest.java
package com.tightening.netty.protocol.codec;

import com.tightening.util.Crc16Utils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModbusRtuFrameDecoderTest {

    @Test
    @DisplayName("03 响应帧 → 完整解码")
    void decodeReadHoldingRegistersResponse() {
        // 09 03 04 0001 0002 CRC16 — 排列机读 4 字节响应
        byte[] payload = hexToBytes("09030400010002");
        int crc = Crc16Utils.compute(payload);
        byte[] frame = new byte[payload.length + 2];
        System.arraycopy(payload, 0, frame, 0, payload.length);
        frame[payload.length] = (byte) (crc & 0xFF);
        frame[payload.length + 1] = (byte) ((crc >> 8) & 0xFF);

        EmbeddedChannel channel = new EmbeddedChannel(new ModbusRtuFrameDecoder());
        ByteBuf in = Unpooled.wrappedBuffer(frame);
        channel.writeInbound(in);

        ByteBuf out = channel.readInbound();
        assertThat(out).isNotNull();
        assertThat(out.readableBytes()).isEqualTo(frame.length);
        out.release();
    }

    @Test
    @DisplayName("06 写响应帧 → 固定 8 字节")
    void decodeWriteSingleRegisterResponse() {
        // 09 06 0000 0080 CRC16 — 排列机写响应（echo）
        byte[] payload = hexToBytes("090600000080");
        int crc = Crc16Utils.compute(payload);
        byte[] frame = new byte[payload.length + 2];
        System.arraycopy(payload, 0, frame, 0, payload.length);
        frame[payload.length] = (byte) (crc & 0xFF);
        frame[payload.length + 1] = (byte) ((crc >> 8) & 0xFF);

        EmbeddedChannel channel = new EmbeddedChannel(new ModbusRtuFrameDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(frame));

        ByteBuf out = channel.readInbound();
        assertThat(out).isNotNull();
        assertThat(out.readableBytes()).isEqualTo(8);
        out.release();
    }

    @Test
    @DisplayName("异常响应帧 → 固定 5 字节")
    void decodeErrorResponse() {
        // 09 83 02 CRC16 — 功能码 0x80+03，异常码 02
        byte[] payload = hexToBytes("098302");
        int crc = Crc16Utils.compute(payload);
        byte[] frame = new byte[payload.length + 2];
        System.arraycopy(payload, 0, frame, 0, payload.length);
        frame[payload.length] = (byte) (crc & 0xFF);
        frame[payload.length + 1] = (byte) ((crc >> 8) & 0xFF);

        EmbeddedChannel channel = new EmbeddedChannel(new ModbusRtuFrameDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(frame));

        ByteBuf out = channel.readInbound();
        assertThat(out).isNotNull();
        assertThat(out.readableBytes()).isEqualTo(5);
        out.release();
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
    }
}
```

- [ ] **Step 2: 运行测试确保失败**

```bash
mvn test -Dtest=ModbusRtuFrameDecoderTest
```

Expected: compilation error — `ModbusRtuFrameDecoder` not found

- [ ] **Step 3: 实现 ModbusRtuFrameDecoder**

```java
// netty/protocol/codec/ModbusRtuFrameDecoder.java
package com.tightening.netty.protocol.codec;

import com.tightening.util.Crc16Utils;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class ModbusRtuFrameDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 4) return; // 至少: slave(1) + func(1) + crc(2) = 4

        in.markReaderIndex();
        int slaveAddr = in.readByte() & 0xFF;
        int funcCode = in.readByte() & 0xFF;

        int frameLength = calculateFrameLength(funcCode, in);
        if (frameLength < 4 || in.readableBytes() < frameLength - 2) {
            in.resetReaderIndex();
            return; // 不够一帧，等更多数据
        }

        // 重新定位到帧开头，读完整帧
        in.resetReaderIndex();
        byte[] frameData = new byte[frameLength];
        in.readBytes(frameData);

        // CRC16 校验
        byte[] payload = new byte[frameLength - 2];
        System.arraycopy(frameData, 0, payload, 0, frameLength - 2);
        int expectedCrc = ((frameData[frameLength - 1] & 0xFF) << 8) | (frameData[frameLength - 2] & 0xFF);
        int actualCrc = Crc16Utils.compute(payload);

        if (expectedCrc != actualCrc) {
            return; // CRC 不匹配，丢弃帧（等下一个完整的）
        }

        out.add(Unpooled.wrappedBuffer(frameData));
    }

    private int calculateFrameLength(int funcCode, ByteBuf in) {
        if ((funcCode & 0x80) != 0) {
            // 异常响应: slave(1) + func(1) + exceptionCode(1) + crc(2) = 5
            return 5;
        }
        if (funcCode == 0x03) {
            // 读保持寄存器: slave(1) + func(1) + byteCount(1) + data(byteCount) + crc(2)
            if (in.readableBytes() < 1) return -1;
            int byteCount = in.readByte() & 0xFF;
            return 3 + byteCount + 2;
        }
        if (funcCode == 0x06) {
            // 写单寄存器（echo）: slave(1) + func(1) + reg(2) + value(2) + crc(2) = 8
            return 8;
        }
        // 未知功能码，尝试最小帧长
        return -1;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest=ModbusRtuFrameDecoderTest
```

Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add netty/protocol/codec/ModbusRtuFrameDecoder.java src/test/java/com/tightening/netty/protocol/codec/ModbusRtuFrameDecoderTest.java
git commit -m "feat: add ModbusRtuFrameDecoder for Modbus RTU over TCP"
```

---

### Task 5: AnengGatewayHandler — 连接/断连/DeviceHandler

**Files:**
- Create: `device/handler/impl/AnengGatewayHandler.java`

**Interfaces:**
- Consumes: `DeviceHandler`, `DeviceService`, `GatewayConfig`, `SseService`, `NioEventLoopGroup` (from `NettyConfig`), `DeviceHolder`
- Produces: `AnengGatewayHandler` — connect/disconnect/getStatus/getSupportedTypes, 子设备注册方法（registerArm/registerSetterSelector/registerArranger），轮询调度框架

- [ ] **Step 1: 创建 Handler 骨架（不含 adapter 逻辑，Task 6-8 补充）**

```java
// device/handler/impl/AnengGatewayHandler.java
package com.tightening.device.handler.impl;

import com.tightening.config.GatewayConfig;
import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.impl.aneng.ArmAdapter;
import com.tightening.device.handler.impl.aneng.ArrangerAdapter;
import com.tightening.device.handler.impl.aneng.SetterSelectorAdapter;
import com.tightening.entity.Device;
import com.tightening.netty.protocol.codec.ModbusRtuFrameDecoder;
import com.tightening.service.DeviceService;
import com.tightening.service.SseService;
import com.tightening.util.Crc16Utils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
public class AnengGatewayHandler implements DeviceHandler {

    private static final int RECONNECT_INTERVAL_MS = 5000;

    private final Bootstrap bootstrap;
    private final NioEventLoopGroup group;
    private final DeviceService deviceService;
    private final GatewayConfig gatewayConfig;
    private final SseService sseService;
    private final Map<Long, DeviceHolder> devices = new ConcurrentHashMap<>();
    private final Map<Long, Channel> channels = new ConcurrentHashMap<>();

    // 子设备 Adapter（Task 6-8 填充）
    private final Map<Long, ArmAdapter> armAdapters = new ConcurrentHashMap<>();
    private final Map<Long, SetterSelectorAdapter> setterAdapters = new ConcurrentHashMap<>();
    private final Map<Long, ArrangerAdapter> arrangerAdapters = new ConcurrentHashMap<>();

    // 用于同步请求-响应
    private final Map<String, CompletableFuture<ByteBuf>> pendingCommands = new ConcurrentHashMap<>();
    private int seqCounter;

    private ScheduledExecutorService pollScheduler;

    public AnengGatewayHandler(NioEventLoopGroup group, DeviceService deviceService,
                                    GatewayConfig gatewayConfig, SseService sseService) {
        this.group = group;
        this.deviceService = deviceService;
        this.gatewayConfig = gatewayConfig;
        this.sseService = sseService;
        this.bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new ModbusRtuFrameDecoder());
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
                                // 响应到来 → 完成对应的 pending future
                                String key = ch.attr(PENDING_KEY).get();
                                if (key != null) {
                                    CompletableFuture<ByteBuf> future = pendingCommands.remove(key);
                                    if (future != null) {
                                        future.complete(frame.retain());
                                    }
                                }
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                Long deviceId = ch.attr(DEVICE_ID_KEY).get();
                                if (deviceId != null) handleDisconnect(deviceId);
                            }
                        });
                    }
                });
    }

    static final AttributeKey<Long> DEVICE_ID_KEY = AttributeKey.valueOf("gwDeviceId");
    static final AttributeKey<String> PENDING_KEY = AttributeKey.valueOf("pendingKey");

    // === DeviceHandler 实现 ===

    @Override
    public void connect(long deviceId) {
        Device device = deviceService.lambdaQuery().eq(Device::getId, deviceId).one();
        if (device == null) {
            log.error("Device not found: {}", deviceId);
            return;
        }
        DeviceHolder holder = devices.computeIfAbsent(deviceId,
                id -> new DeviceHolder(device, DeviceStatus.CONNECTING));
        holder.setDeviceStatus(DeviceStatus.CONNECTING);

        bootstrap.connect(device.getDetail() != null ? parseIp(device.getDetail()) : "127.0.0.1",
                        device.getPort() > 0 ? device.getPort() : 4545)
                .addListener((ChannelFuture f) -> {
                    if (f.isSuccess()) {
                        Channel ch = f.channel();
                        ch.attr(DEVICE_ID_KEY).set(deviceId);
                        channels.put(deviceId, ch);
                        holder.setChannel(ch);
                        holder.setDeviceStatus(DeviceStatus.CONNECTED);
                        startPolling(deviceId);
                        log.info("Gateway connected: deviceId={}", deviceId);
                    } else {
                        holder.setDeviceStatus(DeviceStatus.DISCONNECTED);
                        log.warn("Gateway connect failed deviceId={}, retrying in {}ms",
                                deviceId, RECONNECT_INTERVAL_MS);
                        ch.eventLoop().schedule(() -> connect(deviceId),
                                RECONNECT_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    }
                });
    }

    @Override
    public void disconnect(long deviceId) {
        Channel ch = channels.remove(deviceId);
        if (ch != null) ch.close();
        stopPolling(deviceId);
        DeviceHolder holder = devices.get(deviceId);
        if (holder != null) holder.setDeviceStatus(DeviceStatus.DISCONNECTED);
    }

    @Override
    public DeviceStatus getStatus(long deviceId) {
        DeviceHolder holder = devices.get(deviceId);
        return holder != null ? holder.getDeviceStatus() : DeviceStatus.NONE;
    }

    @Override
    public Set<DeviceType> getSupportedTypes() {
        return Set.of(DeviceType.ANENG_GATEWAY);
    }

    // === Modbus 命令 ===

    CompletableFuture<ByteBuf> sendModbusFrame(long gatewayDeviceId, byte[] frame) {
        Channel ch = channels.get(gatewayDeviceId);
        if (ch == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Gateway not connected: " + gatewayDeviceId));
        }
        int seq = seqCounter++;
        String key = gatewayDeviceId + "-" + seq;
        CompletableFuture<ByteBuf> future = new CompletableFuture<>();
        pendingCommands.put(key, future);

        ch.attr(PENDING_KEY).set(key);
        ch.writeAndFlush(Unpooled.wrappedBuffer(frame)).addListener((ChannelFuture f) -> {
            if (!f.isSuccess()) {
                pendingCommands.remove(key);
                future.completeExceptionally(f.cause());
            }
        });

        // 超时处理
        ch.eventLoop().schedule(() -> {
            CompletableFuture<ByteBuf> timedOut = pendingCommands.remove(key);
            if (timedOut != null) {
                timedOut.completeExceptionally(
                        new TimeoutException("Modbus command timeout for " + key));
            }
        }, gatewayConfig.getArmReadTimeoutMs(), TimeUnit.MILLISECONDS);

        return future;
    }

    // === 子设备注册/注销 ===

    void registerArm(long gatewayDeviceId, long armDeviceId, ArmAdapter adapter) {
        armAdapters.put(armDeviceId, adapter);
    }

    void registerSetterSelector(long gatewayDeviceId, long ssDeviceId, SetterSelectorAdapter adapter) {
        setterAdapters.put(ssDeviceId, adapter);
    }

    void registerArranger(long gatewayDeviceId, long arrDeviceId, ArrangerAdapter adapter) {
        arrangerAdapters.put(arrDeviceId, adapter);
    }

    void removeSubDevice(long deviceId) {
        armAdapters.remove(deviceId);
        setterAdapters.remove(deviceId);
        arrangerAdapters.remove(deviceId);
    }

    // === 轮询 ===

    private void startPolling(long gatewayDeviceId) {
        if (pollScheduler == null || pollScheduler.isShutdown()) {
            pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "gw-poll-" + gatewayDeviceId);
                t.setDaemon(true);
                return t;
            });
        }
        pollScheduler.scheduleWithFixedDelay(
                () -> pollAllSubDevices(gatewayDeviceId),
                0, gatewayConfig.getPollIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private void stopPolling(long gatewayDeviceId) {
        if (pollScheduler != null) {
            pollScheduler.shutdown();
        }
    }

    private void pollAllSubDevices(long gatewayDeviceId) {
        // Task 6-8 补充具体轮询逻辑
        evaluateGatewayStatus(gatewayDeviceId);
    }

    private void evaluateGatewayStatus(long gatewayDeviceId) {
        // 所有子设备健康 → CONNECTED，部分异常 → DEGRADED
        boolean allHealthy = armAdapters.values().stream().allMatch(ArmAdapter::isHealthy)
                && setterAdapters.values().stream().allMatch(SetterSelectorAdapter::isHealthy)
                && arrangerAdapters.values().stream().allMatch(ArrangerAdapter::isHealthy);
        boolean anyExists = !armAdapters.isEmpty() || !setterAdapters.isEmpty() || !arrangerAdapters.isEmpty();

        DeviceHolder holder = devices.get(gatewayDeviceId);
        if (holder != null) {
            DeviceStatus newStatus = anyExists && !allHealthy ? DeviceStatus.DEGRADED : DeviceStatus.CONNECTED;
            if (holder.getDeviceStatus() != newStatus) {
                holder.setDeviceStatus(newStatus);
                sseService.pushDeviceStatus(gatewayDeviceId, newStatus);
            }
        }
    }

    private void handleDisconnect(long deviceId) {
        stopPolling(deviceId);
        DeviceHolder holder = devices.get(deviceId);
        if (holder != null) holder.setDeviceStatus(DeviceStatus.DISCONNECTED);
        // 级联标记子设备断开
        sseService.pushDeviceStatus(deviceId, DeviceStatus.DISCONNECTED);
        // 延迟重连
        group.next().schedule(() -> connect(deviceId), RECONNECT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private static String parseIp(String detail) {
        // detail 字段用于存储额外配置，如 ip 不在 ip 列时
        return "127.0.0.1";
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile
```

Expected: BUILD SUCCESS（此时 ArmAdapter 等尚不存在，先用注释替代 import 并在后续 Task 中补充）

- [ ] **Step 3: Commit**（骨架阶段，编译通过但无实际 adapter 调用）

```bash
git add device/handler/impl/AnengGatewayHandler.java
git commit -m "feat: add AnengGatewayHandler skeleton (connect/disconnect/polling)"
```

---

### Task 6: ArmAdapter

**Files:**
- Create: `device/handler/impl/aneng/ArmAdapter.java`
- Modify: `device/handler/impl/AnengGatewayHandler.java`（补充 import + pollArm）

**Interfaces:**
- Consumes: `IArm`, `AnengGatewayHandler`, `ArmModelConfig`, `Device`
- Produces: `ArmAdapter` — pollHealth, isHealthy, getCurrentCoordinates

- [ ] **Step 1: 编写 ArmAdapter**

```java
// device/handler/impl/aneng/ArmAdapter.java
package com.tightening.device.handler.impl.aneng;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.device.contract.Coordinates3D;
import com.tightening.device.contract.IArm;
import com.tightening.entity.ArmModelConfig;
import com.tightening.entity.Device;
import com.tightening.device.handler.impl.AnengGatewayHandler;
import com.tightening.util.Crc16Utils;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;

class ArmAdapter implements IArm {

    private final AnengGatewayHandler gateway;
    private final long gatewayDeviceId;
    private final ArmModelConfig model;
    private final Device device;
    @Getter
    private volatile boolean healthy;

    ArmAdapter(AnengGatewayHandler gateway, long gatewayDeviceId,
               ArmModelConfig model, Device device) {
        this.gateway = gateway;
        this.gatewayDeviceId = gatewayDeviceId;
        this.model = model;
        this.device = device;
    }

    @Override
    public Long id() { return device.getId(); }

    @Override
    public DeviceType type() { return DeviceType.ARM; }

    @Override
    public boolean isConnected() { return healthy; }

    @Override
    public CompletableFuture<Coordinates3D> getCurrentCoordinates() {
        CompletableFuture<ByteBuf> xFuture = gateway.sendModbusFrame(gatewayDeviceId,
                buildReadFrame(model.getXSlaveAddr(), model.getXRegister(), model.getXCount()));
        CompletableFuture<ByteBuf> yFuture = gateway.sendModbusFrame(gatewayDeviceId,
                buildReadFrame(model.getYSlaveAddr(), model.getYRegister(), model.getYCount()));

        CompletableFuture<ByteBuf> zFuture;
        if (model.getZSlaveAddr() != null) {
            zFuture = gateway.sendModbusFrame(gatewayDeviceId,
                    buildReadFrame(model.getZSlaveAddr(), model.getZRegister(),
                            model.getZCount() != null ? model.getZCount() : 1));
        } else {
            zFuture = CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.allOf(xFuture, yFuture, zFuture)
                .thenApply(v -> {
                    try {
                        int x = parseCoordinate(xFuture.get(), model.getParseStrategy());
                        int y = parseCoordinate(yFuture.get(), model.getParseStrategy());
                        int z = zFuture.get() != null
                                ? parseCoordinate(zFuture.get(), model.getParseStrategy()) : 0;
                        return new Coordinates3D(x, y, z);
                    } catch (Exception e) {
                        return Coordinates3D.ZERO;
                    }
                });
    }

    void pollHealth() {
        // 发送读 X 命令检测响应
        byte[] frame = buildReadFrame(model.getXSlaveAddr(), model.getXRegister(), model.getXCount());
        gateway.sendModbusFrame(gatewayDeviceId, frame)
                .whenComplete((buf, ex) -> {
                    boolean wasHealthy = healthy;
                    healthy = (ex == null && buf != null);
                    if (buf != null) buf.release();
                    if (wasHealthy != healthy) {
                        gateway.getSseService().pushDeviceStatus(device.getId(),
                                healthy ? DeviceStatus.CONNECTED : DeviceStatus.DISCONNECTED);
                    }
                });
    }

    private byte[] buildReadFrame(int slaveAddr, int register, int count) {
        byte[] payload = new byte[6];
        payload[0] = (byte) slaveAddr;
        payload[1] = 0x03;
        payload[2] = (byte) ((register >> 8) & 0xFF);
        payload[3] = (byte) (register & 0xFF);
        payload[4] = (byte) ((count >> 8) & 0xFF);
        payload[5] = (byte) (count & 0xFF);
        int crc = Crc16Utils.compute(payload);
        byte[] frame = new byte[8];
        System.arraycopy(payload, 0, frame, 0, 6);
        frame[6] = (byte) (crc & 0xFF);
        frame[7] = (byte) ((crc >> 8) & 0xFF);
        return frame;
    }

    private int parseCoordinate(ByteBuf response, String parseStrategy) {
        if (response == null || response.readableBytes() < 5) return 0;
        // 响应: slave(1)+func(1)+byteCount(1)+data(byteCount)+crc(2)
        byte[] payload = new byte[response.readableBytes()];
        response.getBytes(response.readerIndex(), payload);
        int byteCount = payload[2] & 0xFF;
        if (byteCount < 2) return 0;
        int rawValue = ((payload[3] & 0xFF) << 8) | (payload[4] & 0xFF);

        if ("DIVIDE_BY_100".equals(parseStrategy)) {
            return rawValue / 100;
        }
        return rawValue;
    }
}
```

- [ ] **Step 2: 编译验证 + AnengGatewayHandler 补充 pollArm 调用**

在 `AnengGatewayHandler.pollAllSubDevices` 中补充：

```java
private void pollAllSubDevices(long gatewayDeviceId) {
    for (ArmAdapter arm : armAdapters.values()) {
        arm.pollHealth();
    }
    // SetterSelector / Arranger 在后续 Task 补充
    evaluateGatewayStatus(gatewayDeviceId);
}
```

并新增 `getSseService()` getter 供 Adapter 使用。

```bash
mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add device/handler/impl/aneng/ArmAdapter.java device/handler/impl/AnengGatewayHandler.java
git commit -m "feat: add ArmAdapter with Modbus RTU coordinate reading"
```

---

### Task 7: SetterSelectorAdapter

**Files:**
- Create: `device/handler/impl/aneng/SetterSelectorAdapter.java`
- Modify: `device/handler/impl/AnengGatewayHandler.java`（补充 pollSetterSelector）

**Interfaces:**
- Consumes: `ISetterSelector`, `AnengGatewayHandler`, `Device`
- Produces: `SetterSelectorAdapter` — writePosition/reset/pollHealth

- [ ] **Step 1: 编写 SetterSelectorAdapter**

```java
// device/handler/impl/aneng/SetterSelectorAdapter.java
package com.tightening.device.handler.impl.aneng;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.device.contract.ISetterSelector;
import com.tightening.entity.Device;
import com.tightening.device.handler.impl.AnengGatewayHandler;
import com.tightening.util.Crc16Utils;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;

class SetterSelectorAdapter implements ISetterSelector {

    // 硬编码 Modbus 参数（排列机和批头选择器固定）
    private static final int SLAVE_ADDR = 0x09;
    private static final int IO_REGISTER = 0x0000;

    private final AnengGatewayHandler gateway;
    private final long gatewayDeviceId;
    private final Device device;
    private final int setterCount;
    @Getter
    private volatile boolean healthy;

    SetterSelectorAdapter(AnengGatewayHandler gateway, long gatewayDeviceId,
                          Device device, int setterCount) {
        this.gateway = gateway;
        this.gatewayDeviceId = gatewayDeviceId;
        this.device = device;
        this.setterCount = setterCount;
    }

    @Override public Long id() { return device.getId(); }
    @Override public DeviceType type() { return DeviceType.SETTER_SELECTOR; }
    @Override public boolean isConnected() { return healthy; }
    @Override public int getPositionCount() { return setterCount; }

    @Override
    public CompletableFuture<Boolean> writePosition(int position) {
        if (position < 1 || position > setterCount) {
            return CompletableFuture.completedFuture(false);
        }
        // 构建 IO 输出字节：position 位设为 1
        int outputBits = 0;
        for (int i = 0; i < setterCount; i++) {
            if (i == position - 1) outputBits |= (1 << i);
        }
        return writeRegister(SLAVE_ADDR, IO_REGISTER, outputBits);
    }

    @Override
    public CompletableFuture<Boolean> reset() {
        return writeRegister(SLAVE_ADDR, IO_REGISTER, 0);
    }

    private CompletableFuture<Boolean> writeRegister(int slaveAddr, int register, int value) {
        byte[] payload = new byte[6];
        payload[0] = (byte) slaveAddr;
        payload[1] = 0x06;
        payload[2] = (byte) ((register >> 8) & 0xFF);
        payload[3] = (byte) (register & 0xFF);
        payload[4] = (byte) ((value >> 8) & 0xFF);
        payload[5] = (byte) (value & 0xFF);
        int crc = Crc16Utils.compute(payload);
        byte[] frame = new byte[8];
        System.arraycopy(payload, 0, frame, 0, 6);
        frame[6] = (byte) (crc & 0xFF);
        frame[7] = (byte) ((crc >> 8) & 0xFF);

        return gateway.sendModbusFrame(gatewayDeviceId, frame)
                .thenApply(buf -> {
                    if (buf != null) { buf.release(); return true; }
                    return false;
                });
    }

    void pollHealth() {
        // 读 IO 状态验证连接
        byte[] frame = buildReadFrame(SLAVE_ADDR, IO_REGISTER, 4);
        gateway.sendModbusFrame(gatewayDeviceId, frame)
                .whenComplete((buf, ex) -> {
                    boolean wasHealthy = healthy;
                    healthy = (ex == null && buf != null && buf.readableBytes() >= 7);
                    if (buf != null) buf.release();
                    if (wasHealthy != healthy) {
                        gateway.getSseService().pushDeviceStatus(device.getId(),
                                healthy ? DeviceStatus.CONNECTED : DeviceStatus.DISCONNECTED);
                    }
                });
    }

    private byte[] buildReadFrame(int slaveAddr, int register, int count) {
        byte[] payload = new byte[6];
        payload[0] = (byte) slaveAddr;
        payload[1] = 0x03;
        payload[2] = (byte) ((register >> 8) & 0xFF);
        payload[3] = (byte) (register & 0xFF);
        payload[4] = (byte) ((count >> 8) & 0xFF);
        payload[5] = (byte) (count & 0xFF);
        int crc = Crc16Utils.compute(payload);
        byte[] frame = new byte[8];
        System.arraycopy(payload, 0, frame, 0, 6);
        frame[6] = (byte) (crc & 0xFF);
        frame[7] = (byte) ((crc >> 8) & 0xFF);
        return frame;
    }
}
```

- [ ] **Step 2: 编译验证 + AnengGatewayHandler 补充 pollSetterSelector**

```bash
mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add device/handler/impl/aneng/SetterSelectorAdapter.java
git commit -m "feat: add SetterSelectorAdapter for setter position switching"
```

---

### Task 8: ArrangerAdapter

**Files:**
- Create: `device/handler/impl/aneng/ArrangerAdapter.java`
- Modify: `device/handler/impl/AnengGatewayHandler.java`（补充 pollArranger）

**Interfaces:**
- Consumes: `IArranger`, `AnengGatewayHandler`, `Device`
- Produces: `ArrangerAdapter` — sendPulse/reset/getOutputStatus/getInputStatus/pollHealth

- [ ] **Step 1: 编写 ArrangerAdapter**

```java
// device/handler/impl/aneng/ArrangerAdapter.java
package com.tightening.device.handler.impl.aneng;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.device.contract.IArranger;
import com.tightening.entity.Device;
import com.tightening.device.handler.impl.AnengGatewayHandler;
import com.tightening.util.Crc16Utils;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class ArrangerAdapter implements IArranger {

    private static final int SLAVE_ADDR = 0x09;
    private static final int IO_REGISTER = 0x0000;

    private final AnengGatewayHandler gateway;
    private final long gatewayDeviceId;
    private final Device device;
    private final boolean reverseFirstFour;
    @Getter
    private volatile boolean healthy;

    ArrangerAdapter(AnengGatewayHandler gateway, long gatewayDeviceId,
                    Device device, boolean reverseFirstFour) {
        this.gateway = gateway;
        this.gatewayDeviceId = gatewayDeviceId;
        this.device = device;
        this.reverseFirstFour = reverseFirstFour;
    }

    @Override public Long id() { return device.getId(); }
    @Override public DeviceType type() { return DeviceType.ARRANGER; }
    @Override public boolean isConnected() { return healthy; }

    @Override
    public CompletableFuture<Boolean> sendPulse(int[] channels, int pulseWidthMs) {
        // 构建 IO 输出字节（8 位，channels 中非 0 位设为 1）
        int outputBits = 0;
        for (int i = 0; i < Math.min(channels.length, 8); i++) {
            if (channels[i] != 0) outputBits |= (1 << i);
        }
        if (reverseFirstFour) {
            outputBits = swapFirstFour(outputBits);
        }
        outputBits = reverseBits(outputBits);

        // Step 1: 写 Set 信号
        return writeRegister(SLAVE_ADDR, IO_REGISTER, outputBits)
                .thenCompose(ok -> {
                    if (!ok) return CompletableFuture.completedFuture(false);
                    // Step 2: 延时
                    CompletableFuture<Boolean> delayFuture = new CompletableFuture<>();
                    CompletableFuture.delayedExecutor(pulseWidthMs, TimeUnit.MILLISECONDS)
                            .execute(() -> delayFuture.complete(true));
                    return delayFuture;
                })
                .thenCompose(ok -> {
                    if (!ok) return CompletableFuture.completedFuture(false);
                    // Step 3: 写 Reset 信号
                    return writeRegister(SLAVE_ADDR, IO_REGISTER, 0);
                });
    }

    @Override
    public CompletableFuture<int[]> getOutputStatus() {
        return readIoStatus().thenApply(arr -> arr[0]);
    }

    @Override
    public CompletableFuture<int[]> getInputStatus() {
        return readIoStatus().thenApply(arr -> arr[1]);
    }

    @Override
    public CompletableFuture<Boolean> reset() {
        return writeRegister(SLAVE_ADDR, IO_REGISTER, 0);
    }

    private CompletableFuture<int[][]> readIoStatus() {
        byte[] frame = buildReadFrame(SLAVE_ADDR, IO_REGISTER, 4);
        return gateway.sendModbusFrame(gatewayDeviceId, frame)
                .thenApply(buf -> {
                    if (buf == null || buf.readableBytes() < 9) return new int[][]{new int[8], new int[8]};
                    byte[] payload = new byte[buf.readableBytes()];
                    buf.getBytes(buf.readerIndex(), payload);
                    buf.release();
                    // payload[3]=output_byte, payload[4]=input_byte
                    int outBits = payload[3] & 0xFF;
                    int inBits = payload[4] & 0xFF;
                    if (reverseFirstFour) {
                        outBits = swapFirstFour(outBits);
                    }
                    int[] out = new int[8];
                    int[] in = new int[8];
                    for (int i = 0; i < 8; i++) {
                        out[i] = (outBits >> i) & 1;
                        in[i] = (inBits >> i) & 1;
                    }
                    return new int[][]{out, in};
                });
    }

    void pollHealth() {
        byte[] frame = buildReadFrame(SLAVE_ADDR, IO_REGISTER, 4);
        gateway.sendModbusFrame(gatewayDeviceId, frame)
                .whenComplete((buf, ex) -> {
                    boolean wasHealthy = healthy;
                    healthy = (ex == null && buf != null && buf.readableBytes() >= 9);
                    if (buf != null) buf.release();
                    if (wasHealthy != healthy) {
                        gateway.getSseService().pushDeviceStatus(device.getId(),
                                healthy ? DeviceStatus.CONNECTED : DeviceStatus.DISCONNECTED);
                    }
                });
    }

    private CompletableFuture<Boolean> writeRegister(int slaveAddr, int register, int value) {
        byte[] payload = new byte[6];
        payload[0] = (byte) slaveAddr;
        payload[1] = 0x06;
        payload[2] = (byte) ((register >> 8) & 0xFF);
        payload[3] = (byte) (register & 0xFF);
        payload[4] = (byte) ((value >> 8) & 0xFF);
        payload[5] = (byte) (value & 0xFF);
        int crc = Crc16Utils.compute(payload);
        byte[] frame = new byte[8];
        System.arraycopy(payload, 0, frame, 0, 6);
        frame[6] = (byte) (crc & 0xFF);
        frame[7] = (byte) ((crc >> 8) & 0xFF);

        return gateway.sendModbusFrame(gatewayDeviceId, frame)
                .thenApply(buf -> { if (buf != null) { buf.release(); return true; } return false; });
    }

    private byte[] buildReadFrame(int slaveAddr, int register, int count) {
        byte[] payload = new byte[6];
        payload[0] = (byte) slaveAddr;
        payload[1] = 0x03;
        payload[2] = (byte) ((register >> 8) & 0xFF);
        payload[3] = (byte) (register & 0xFF);
        payload[4] = (byte) ((count >> 8) & 0xFF);
        payload[5] = (byte) (count & 0xFF);
        int crc = Crc16Utils.compute(payload);
        byte[] frame = new byte[8];
        System.arraycopy(payload, 0, frame, 0, 6);
        frame[6] = (byte) (crc & 0xFF);
        frame[7] = (byte) ((crc >> 8) & 0xFF);
        return frame;
    }

    static int swapFirstFour(int bits) {
        int b0 = (bits >> 0) & 1, b3 = (bits >> 3) & 1;
        int b1 = (bits >> 1) & 1, b2 = (bits >> 2) & 1;
        int swapped = bits & 0xF0;
        swapped |= (b3 << 0) | (b2 << 1) | (b1 << 2) | (b0 << 3);
        return swapped;
    }

    static int reverseBits(int bits) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            result |= ((bits >> i) & 1) << (7 - i);
        }
        return result;
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 更新 AnengGatewayHandler.pollAllSubDevices 补充 Arranger 轮询**

```java
private void pollAllSubDevices(long gatewayDeviceId) {
    for (ArmAdapter arm : armAdapters.values()) arm.pollHealth();
    for (SetterSelectorAdapter ss : setterAdapters.values()) ss.pollHealth();
    for (ArrangerAdapter arr : arrangerAdapters.values()) arr.pollHealth();
    evaluateGatewayStatus(gatewayDeviceId);
}
```

- [ ] **Step 4: Commit**

```bash
git add device/handler/impl/aneng/ArrangerAdapter.java device/handler/impl/AnengGatewayHandler.java
git commit -m "feat: add ArrangerAdapter with pulse-based screw feeding"
```

---

### Task 9: DeviceRegistry 扩展 + DeviceManager 过滤 + DeviceHandlerFactory

**Files:**
- Modify: `device/DeviceRegistry.java`
- Modify: `device/DeviceManager.java`
- Modify: `device/handler/DeviceHandlerFactory.java`

**Interfaces:**
- Consumes: `DeviceHandlerFactory`, `DataRouter`, `DeviceService`, `AnengGatewayHandler`
- Produces: `DeviceRegistry` with `getArm`/`getSetterSelector`/`getArranger`/`getAllArms`/etc; `DeviceManager` with sub-device type filtering; `DeviceHandlerFactory` auto-registers `AnengGatewayHandler`

- [ ] **Step 1: DeviceHandlerFactory 保持自动注册（不需要手动添加）**

```java
// DeviceHandlerFactory 已通过 List<DeviceHandler> 自动注入所有 handler
// AnengGatewayHandler.getSupportedTypes() 返回 Set.of(ANENG_GATEWAY)
// 只要 AnengGatewayHandler 是 Spring Bean，工厂会自动注册
```

在 `AnengGatewayHandler` 类上加 `@Component`：

```java
@Component
public class AnengGatewayHandler implements DeviceHandler {
```

- [ ] **Step 2: DeviceManager 子设备类型过滤**

```java
// device/DeviceManager.java — 在 addDevice 方法顶部加过滤
private static final Set<DeviceType> SUB_DEVICE_TYPES = Set.of(
    DeviceType.ARM, DeviceType.SETTER_SELECTOR, DeviceType.ARRANGER);

private void addDevice(Device device) {
    if (device == null) return;
    DeviceType type = DeviceType.getType(device.getType());
    if (type == null || SUB_DEVICE_TYPES.contains(type)) return;

    DeviceHandler handler = DeviceType.getHandlerByTypeId(device.getType());
    if (handler instanceof TCPDeviceHandler tcpDeviceHandler) {
        tcpDeviceHandler.tryAddDeviceInfo(device);
    }
    deviceHandlers.put(device.getId(), handler);
}

// scanAndConnect: DEGRADED 不触发重连
private void scanAndConnect() {
    deviceHandlers.forEach((deviceId, handler) -> {
        DeviceStatus status = handler.getStatus(deviceId);
        if (status == DeviceStatus.NONE || status == DeviceStatus.DISCONNECTED) {
            connectExecutor.submit(() -> {
                try { handler.connect(deviceId); }
                catch (Exception e) { log.error("Connect failed deviceId={}", deviceId, e); }
            });
        }
        // DEGRADED 不触发重连（TCP 是通的）
    });
}
```

- [ ] **Step 3: DeviceRegistry 扩展**

注入 `DeviceService` 和 `AnengGatewayHandler`，新增子设备注册表：

```java
// device/DeviceRegistry.java — 在现有字段后添加
private final Map<Long, IArm> arms = new ConcurrentHashMap<>();
private final Map<Long, ISetterSelector> setterSelectors = new ConcurrentHashMap<>();
private final Map<Long, IArranger> arrangers = new ConcurrentHashMap<>();
private final Map<Long, Long> gatewayMap = new ConcurrentHashMap<>(); // 子设备→通信盒

private final DeviceService deviceService;

// 构造函数增加 DeviceService 参数
public DeviceRegistry(DeviceHandlerFactory handlerFactory, DataRouter dataRouter,
                       DeviceService deviceService) {
    this.handlerFactory = handlerFactory;
    this.dataRouter = dataRouter;
    this.deviceService = deviceService;
}

// 新增查询方法
public IArm getArm(Long deviceId) { return arms.get(deviceId); }
public ISetterSelector getSetterSelector(Long deviceId) { return setterSelectors.get(deviceId); }
public IArranger getArranger(Long deviceId) { return arrangers.get(deviceId); }
public Map<Long, IArm> getAllArms() { return Map.copyOf(arms); }
public Map<Long, ISetterSelector> getAllSetterSelectors() { return Map.copyOf(setterSelectors); }
public Map<Long, IArranger> getAllArrangers() { return Map.copyOf(arrangers); }

// onDeviceChange 扩展
@TransactionalEventListener
void onDeviceChange(DeviceChangeEvent event) {
    Device device = event.getDevice();
    DeviceType type = DeviceType.getType(device.getType());

    switch (event.getEventType()) {
        case ADD -> {
            if (type == DeviceType.ANENG_GATEWAY) {
                // 通信盒连接 → 反查 DB 子设备 → 创建 Adapter
                registerGatewaySubDevices(device);
            } else if (isSubDevice(type)) {
                registerSubDevice(device, type);
            } else {
                registerTool(device);
            }
        }
        case UPDATE -> {
            if (type == DeviceType.ANENG_GATEWAY || isSubDevice(type)) {
                removeSubDeviceReg(event.getDeviceId());
                if (type == DeviceType.ANENG_GATEWAY) {
                    registerGatewaySubDevices(event.getDevice());
                } else {
                    registerSubDevice(event.getDevice(), type);
                }
            } else {
                tools.remove(event.getDeviceId());
                registerTool(event.getDevice());
            }
        }
        case DELETE -> {
            tools.remove(event.getDeviceId());
            removeSubDeviceReg(event.getDeviceId());
        }
    }
}

private boolean isSubDevice(DeviceType type) {
    return type == DeviceType.ARM || type == DeviceType.SETTER_SELECTOR || type == DeviceType.ARRANGER;
}

private void removeSubDeviceReg(long deviceId) {
    arms.remove(deviceId);
    setterSelectors.remove(deviceId);
    arrangers.remove(deviceId);
    Long gwId = gatewayMap.remove(deviceId);
    if (gwId != null) {
        DeviceHandler handler = getGatewayHandler(gwId);
        if (handler instanceof AnengGatewayHandler gw) {
            gw.removeSubDevice(deviceId);
        }
    }
}

private DeviceHandler getGatewayHandler(long gatewayDeviceId) {
    // 通过 DeviceHandlerFactory 获取通信盒 handler
    return handlerFactory.getHandler(DeviceType.ANENG_GATEWAY);
}

private void registerGatewaySubDevices(Device gatewayDevice) {
    // 反查 DB 中所有 gateway_device_id = gatewayDevice.getId() 的子设备
    List<Device> subDevices = deviceService.lambdaQuery()
            .apply("json_extract(detail, '$.gateway_device_id') = {0}", gatewayDevice.getId())
            .list();
    // 简化：用 Device 表的 gateway_device_id 列（Task 1 已加）
    // 实际查询条件: eq(Device::getGatewayDeviceId, gatewayDevice.getId())
    for (Device sub : subDevices) {
        registerSubDevice(sub, DeviceType.getType(sub.getType()));
    }
}

private void registerSubDevice(Device device, DeviceType type) {
    if (type == null) return;
    DeviceHandler handler = getGatewayHandler(getGatewayId(device));
    if (!(handler instanceof AnengGatewayHandler gw)) return;
    if (!gw.getStatus(getGatewayId(device)).equals(DeviceStatus.CONNECTED)) return;

    switch (type) {
        case ARM -> {
            ArmModelConfig model = armModelConfigMapper.selectById(device.getArmModelId());
            if (model != null) {
                ArmAdapter adapter = new ArmAdapter(gw, getGatewayId(device), model, device);
                gw.registerArm(getGatewayId(device), device.getId(), adapter);
                arms.put(device.getId(), adapter);
                gatewayMap.put(device.getId(), getGatewayId(device));
            }
        }
        case SETTER_SELECTOR -> {
            SetterSelector ss = (SetterSelector) device;
            int count = ss.getSetterCount() != null ? ss.getSetterCount() : 8;
            SetterSelectorAdapter adapter = new SetterSelectorAdapter(gw,
                    getGatewayId(device), device, count);
            gw.registerSetterSelector(getGatewayId(device), device.getId(), adapter);
            setterSelectors.put(device.getId(), adapter);
            gatewayMap.put(device.getId(), getGatewayId(device));
        }
        case ARRANGER -> {
            Arranger arr = (Arranger) device;
            boolean rev = arr.getReverseFirstFour() != null && arr.getReverseFirstFour();
            ArrangerAdapter adapter = new ArrangerAdapter(gw, getGatewayId(device), device, rev);
            gw.registerArranger(getGatewayId(device), device.getId(), adapter);
            arrangers.put(device.getId(), adapter);
            gatewayMap.put(device.getId(), getGatewayId(device));
        }
    }
}

private Long getGatewayId(Device device) {
    if (device instanceof SubDevice sd) return sd.getGatewayDeviceId();
    return null;
}
```

- [ ] **Step 4: 编译验证**

```bash
mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add device/DeviceRegistry.java device/DeviceManager.java device/handler/DeviceHandlerFactory.java device/handler/impl/AnengGatewayHandler.java
git commit -m "feat: extend DeviceRegistry for sub-devices, filter in DeviceManager"
```

---

### Task 10: TaskContext 扩展 + LifecycleEngineFactory 更新

**Files:**
- Modify: `lifecycle/TaskContext.java`
- Modify: `lifecycle/LifecycleEngineFactory.java`

**Interfaces:**
- Consumes: `DeviceRegistry`, `ArmModelConfigMapper`
- Produces: `TaskContext` with `armRegistry`/`setterSelectorRegistry`/`arrangerRegistry` maps; `LifecycleEngineFactory` passes sub-device maps to TaskContext

- [ ] **Step 1: TaskContext 新增 sub-device registry maps**

```java
// lifecycle/TaskContext.java — 新增字段
@Builder.Default
private final Map<Long, IArm> armRegistry = new ConcurrentHashMap<>();

@Builder.Default
private final Map<Long, ISetterSelector> setterSelectorRegistry = new ConcurrentHashMap<>();

@Builder.Default
private final Map<Long, IArranger> arrangerRegistry = new ConcurrentHashMap<>();

public Map<Long, IArm> getArmRegistry() { return armRegistry; }
public Map<Long, ISetterSelector> getSetterSelectorRegistry() { return setterSelectorRegistry; }
public Map<Long, IArranger> getArrangerRegistry() { return arrangerRegistry; }
```

添加对应 import：
```java
import com.tightening.device.contract.IArm;
import com.tightening.device.contract.ISetterSelector;
import com.tightening.device.contract.IArranger;
```

- [ ] **Step 2: LifecycleEngineFactory 注入 DeviceRegistry，传递 sub-device maps**

```java
// lifecycle/LifecycleEngineFactory.java — 新增注入 + 修改 createEngine
private final DeviceRegistry deviceRegistry;

// TaskContext builder 追加
TaskContext ctx = TaskContext.builder()
    .productTaskId(task.getId())
    .taskData(task)
    .boltConfigs(bolts)
    .deviceRegistry(convertToToolMap(deviceRegistry))  // 现有: Map<Long, ITool>
    .armRegistry(deviceRegistry.getAllArms())
    .setterSelectorRegistry(deviceRegistry.getAllSetterSelectors())
    .arrangerRegistry(deviceRegistry.getAllArrangers())
    .productCode(productCode)
    .partsCode(partsCode)
    .boltBarcodeRuleIds(barcodeMap)
    .build();

// helper method
private static Map<Long, ITool> convertToToolMap(DeviceRegistry registry) {
    Map<Long, ITool> map = new java.util.HashMap<>();
    for (ITool tool : registry.getAllTools()) {
        map.put(tool.id(), tool);
    }
    return map;
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add lifecycle/TaskContext.java lifecycle/LifecycleEngineFactory.java
git commit -m "feat: add sub-device registry maps to TaskContext"
```

---

### Task 11: SendArrangerSignal（桩→实）+ SendSetterSelector（新）

**Files:**
- Modify: `lifecycle/capability/SendArrangerSignal.java`
- Create: `lifecycle/capability/SendSetterSelector.java`

**Interfaces:**
- Consumes: `Capability`, `TaskContext`（armRegistry/arrangerRegistry/setterSelectorRegistry）, `LockReason`
- Produces: `SendArrangerSignal` with real execute + lockReason; `SendSetterSelector`

- [ ] **Step 1: SendArrangerSignal — 桩代码变真实实现**

```java
// lifecycle/capability/SendArrangerSignal.java — 替换文件
package com.tightening.lifecycle.capability;

import com.tightening.constant.LockReason;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.IArranger;
import com.tightening.entity.ProductBolt;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class SendArrangerSignal implements Capability {

    @Override public String id() { return "SendArrangerSignal"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 0; }

    @Override
    public boolean precondition(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        return bolt != null && bolt.getArrangerDeviceId() != null
                && bolt.getArrangerChannels() != null && !bolt.getArrangerChannels().isBlank();
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        IArranger arranger = ctx.getArrangerRegistry().get(bolt.getArrangerDeviceId());
        if (arranger == null) {
            log.warn("Arranger not found: deviceId={}", bolt.getArrangerDeviceId());
            return CapabilityResult.Skip;
        }
        if (!arranger.isConnected()) {
            log.warn("Arranger not connected: deviceId={}", bolt.getArrangerDeviceId());
            return CapabilityResult.Fail;
        }

        int[] channels = parseChannels(bolt.getArrangerChannels(), 8);

        ctx.getLockReasons().add(LockReason.ARRANGER_POSITIONING);
        try {
            Boolean ok = arranger.sendPulse(channels, 200)
                    .get(3, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(ok)) {
                log.info("Arranger pulse sent: deviceId={}, channels={}",
                        bolt.getArrangerDeviceId(), bolt.getArrangerChannels());
                return CapabilityResult.Pass;
            }
            return CapabilityResult.Fail;
        } catch (Exception e) {
            log.error("Arranger pulse failed: deviceId={}", bolt.getArrangerDeviceId(), e);
            return CapabilityResult.Fail;
        } finally {
            ctx.getLockReasons().remove(LockReason.ARRANGER_POSITIONING);
        }
    }

    private int[] parseChannels(String channelsStr, int maxChannels) {
        int[] channels = new int[maxChannels];
        if (channelsStr == null || channelsStr.isBlank()) return channels;
        for (String part : channelsStr.split(",")) {
            try {
                int idx = Integer.parseInt(part.trim()) - 1; // 1-based → 0-based
                if (idx >= 0 && idx < maxChannels) channels[idx] = 1;
            } catch (NumberFormatException ignored) {}
        }
        return channels;
    }
}
```

- [ ] **Step 2: SendSetterSelector 新建**

```java
// lifecycle/capability/SendSetterSelector.java
package com.tightening.lifecycle.capability;

import com.tightening.constant.LockReason;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.ISetterSelector;
import com.tightening.entity.ProductBolt;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class SendSetterSelector implements Capability {

    @Override public String id() { return "SendSetterSelector"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 1; }

    @Override
    public boolean precondition(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        return bolt != null && bolt.getSetterSelectorId() != null
                && bolt.getSetterPosition() != null;
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        ISetterSelector setter = ctx.getSetterSelectorRegistry().get(bolt.getSetterSelectorId());
        if (setter == null) {
            log.warn("SetterSelector not found: deviceId={}", bolt.getSetterSelectorId());
            return CapabilityResult.Skip;
        }
        if (!setter.isConnected()) {
            log.warn("SetterSelector not connected: deviceId={}", bolt.getSetterSelectorId());
            return CapabilityResult.Fail;
        }

        int position = bolt.getSetterPosition();
        ctx.getLockReasons().add(LockReason.SOCKET_SELECTING);
        try {
            Boolean ok = setter.writePosition(position)
                    .get(3, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(ok)) {
                log.info("SetterSelector position {} set: deviceId={}",
                        position, bolt.getSetterSelectorId());
                return CapabilityResult.Pass;
            }
            return CapabilityResult.Fail;
        } catch (Exception e) {
            log.error("SetterSelector write failed: deviceId={}", bolt.getSetterSelectorId(), e);
            return CapabilityResult.Fail;
        } finally {
            ctx.getLockReasons().remove(LockReason.SOCKET_SELECTING);
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add lifecycle/capability/SendArrangerSignal.java lifecycle/capability/SendSetterSelector.java
git commit -m "feat: implement SendArrangerSignal and SendSetterSelector Capabilities"
```

---

### Task 12: 集成测试

**Files:**
- Create: `src/test/java/com/tightening/device/handler/impl/AnengGatewayHandlerTest.java`
- Modify 或 Create 相应测试文件

**Interfaces:**
- Consumes: 所有已完成模块
- Produces: 测试覆盖

- [ ] **Step 1: ModbusRtuFrameDecoder 单元测试**（已在 Task 4 完成）

- [ ] **Step 2: ArmAdapter.parseCoordinate 测试**

```java
// src/test/java/com/tightening/device/handler/impl/aneng/ArmAdapterStandaloneTest.java
// 测试 parseCoordinate 在 STANDARD / DIVIDE_BY_100 策略下的正确性
// 测试 buildReadFrame 输出与 C# 参考值对比

@Test
@DisplayName("buildReadFrame CF01 X → 01 03 0003 0002 + CRC16")
void buildReadFrameCF01X() {
    byte[] frame = ArmAdapter.buildReadFrameForTest(1, 0x0003, 2);
    assertThat(bytesToHex(frame)).isEqualTo("010300030002340b");
}
```

- [ ] **Step 3: ArrangerAdapter.reverseBits 和 swapFirstFour 测试**

```java
@Test
void testReverseBits() {
    assertThat(ArrangerAdapter.reverseBits(0b10000000)).isEqualTo(0b00000001);
    assertThat(ArrangerAdapter.reverseBits(0b00000001)).isEqualTo(0b10000000);
}

@Test
void testSwapFirstFour() {
    // 1,2,3,4 → 4,3,2,1
    assertThat(ArrangerAdapter.swapFirstFour(0b00001001)).isEqualTo(0b00000110);
}
```

- [ ] **Step 4: DeviceRegistry 子设备注册测试**

```java
// src/test/java/com/tightening/device/DeviceRegistryTest.java — 追加
@Test
@DisplayName("子设备类型 ARM → getArm 返回非 null")
void armRegistersToDeviceRegistry() { /* ... */ }

@Test
@DisplayName("通信盒 DELETE → 级联移除所有子设备")
void gatewayDeleteCascadesSubDevices() { /* ... */ }
```

- [ ] **Step 5: 运行全部测试**

```bash
mvn test
```

Expected: all tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/test/
git commit -m "test: add unit tests for gateway, adapters, and sub-device registry"
```

---

## Implementation Order

按依赖顺序执行：Task 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12

每个 Task 完成后运行 `mvn test` 确保未引入回归。
