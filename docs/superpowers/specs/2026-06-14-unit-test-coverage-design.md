# Unit Test Coverage — 全项目单元测试完善设计

## 目标

将项目测试覆盖率从 ~10%（8/81 文件）提升到接近全量覆盖，按三层分层推进。以下不移入测试范围：
- **MyBatis-Plus Mapper 接口（3 个）** — 纯接口无实现代码，编译期保证方法签名正确
- **`DeviceHandler` 接口（1 个）** — 纯接口无实现代码，由子类测试间接覆盖
- **`@FieldDescription` 注解（1 个）** — `@Target` / `@Retention` 编译期保证
- **`SerialPortDevice`（1 个）** — 零字段 `@Data` 标记类，无可测内容
- **`TighteningApplication`** — 已有 `contextLoads()` 冒烟测试，保留不动，不纳入 Layer 计数

## 测试基础设施

- **框架：** JUnit 5 + AssertJ + Mockito（`spring-boot-starter-test` 自带）
- **策略：** 纯单元测试，不启动 Spring 上下文（`@SpringBootTest` 仅保留冒烟测试）
- **Mock：** `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`
- **Netty：** `EmbeddedChannel` 测试 Handler（已有模式）
- **结构：** `src/test/java` 完全镜像 `src/main/java` 包结构
- **配置文件：** 沿用 `src/test/resources/application.yaml`
- **Entity/DTO：** 每个类独立手写，不使用反射 helper
- **现有 Codec 测试：** 改写 `AtlasFrameCodecTest` / `FitFrameCodecTest` 去掉 `@SpringBootTest`，改为纯 `@ExtendWith(MockitoExtension.class)`，移除未使用的 `ctx`/`alloc` mock
- **现有 `HeartbeatHandlerTest`：** 去掉 `@SpringBootTest`（无 Spring 依赖）

## 分层策略

### Layer 1 — 验证即通过（约 33 个文件）

浅测试，防止字段名/枚举值改动导致静默失败。

| 类型 | 文件 | 测试策略 |
|---|---|---|
| Entity (5) | TighteningData, CurveData, Device, UserAccountInfo, BaseEntity | Jackson 序列化↔反序列化往返（全字段 + 空对象），手写 |
| DTO (5) | TighteningDataDTO, CurveDataDTO, DeviceDTO, UserAccountInfoDTO, BaseDTO | 同上 |
| Device 类型 (2) | TCPDevice, Arranger | 同上（JsonUtils.parse 反序列化关键路径） |
| Enum 无业务方法 (~8) | AtlasAngleStatus, AtlasTorqueStatus, FitAngleStatus, FitTorqueStatus, TighteningStatus, TighteningResultType, DeviceStatus, DeviceChangeType | `valueOf()` 反查 + 枚举值不重复 |
| Enum 有业务方法 (~11) | DeviceType, AtlasErrorCode, AtlasCommandType, FitCommandType, AtlasExtraDataKeys, ExtraDataKeys, TCPCommand, TCPDeviceConstants, ToolConstants, AtlasConstants, FitConstants | 同上 + 每个业务方法的全分支 |
| Config (4) | DeviceConfig, FitConfig, ToolCommonConfig, DatabaseDirectoryInitializer | 仅 getter/setter/默认值，不测试 `@ConfigurationProperties` 绑定 |

### Layer 2 — 逻辑覆盖（约 20 个文件）

标准深度，补齐已有测试的边界/异常路径 + 新测逻辑工具类。

| 类型 | 文件 | 测试策略 |
|---|---|---|
| 协议编解码器 | AtlasLengthDecoder, AtlasFrame（新）, FitFrame（新）, AtlasFrameCodec（已有-改写）, FitFrameCodec（已有-改写） | 补齐：空帧、截断帧、超长帧、异常路径；Frame 工厂方法 + 字段完整性 |
| 数据解析器 | AtlasTighteningDataParser（已有-补齐）、FitTighteningDataParser（已有-补齐）、FitCurveDataParser（已有-补齐） | Atlas: 补 `parseTorqueStatus`/`parseAngleStatus` 无效 code fallback 路径；FIT tightening: 补 NG 状态、空 barcode、负角度、数据过短；FIT curve: 补 null 输入、数据过短、0 点、单点 |
| 曲线数据 | CurvePoint, CurveDataSamples, FitCurveDataReassembler | 构造、字段、拼装逻辑：正常到达、乱序到达、重复包、超时（4 条路径） |
| 协议工具类 | AtlasDataUtils, FitDataUtils | 所有 public 方法所有分支 |
| 通用工具 | JsonUtils、Converter（已有-补齐） | 所有 public 方法正常/边界/异常路径。Converter 已有 fromList/fromJsonToList，补 entity2Dto/dto2Entity |

### Layer 3 — 深度覆盖（约 22 个文件）

Mock + 全路径覆盖。

| 类型 | 文件 | 测试策略 | Mock 对象 |
|---|---|---|---|
| Controller (3) | DeviceController, LoginController, UserAccountInfoController | 请求验证、业务委托、DeferredResult 手动 `setResult` 回调、异常响应 | Service |
| Service (3) | DeviceService, TighteningDataService, UserAccountInfoService | 所有 public 方法正常/异常路径 | Mapper |
| Handler 抽象 (2) | TCPDeviceHandler, ToolHandler | 匿名子类 stub abstract 方法，测生命周期、状态变更、冷却、超时 | Bootstrap, Channel |
| Handler 实现 (2) | AtlasPFSeriesHandler, FitSeriesHandler | 协议方法实现、命令组装。AtlasPF4000Handler/AtlasPF6000OPHandler 合并入 AtlasPFSeriesHandlerTest；FitFTC6Handler 合并入 FitSeriesHandlerTest | Channel, DeviceHolder |
| Netty Handler (5) | DeviceInitHandler, AtlasPFSeriesInBoundHandler, AtlasPFSeriesInitHandler, FitSeriesInBoundHandler, FitSeriesInitHandler | Channel 事件处理、协议初始化 | EmbeddedChannel |
| Device 管理 (2) | DeviceManager, DeviceHolder | 生命周期、并发、事件、状态转换。真实线程池 + sleep | DeviceHandler mock |
| 工厂/事件/服务 (3) | DeviceHandlerFactory, DeviceHandlerService, DeviceChangeEvent | 工厂匹配、事件属性 | Handler 列表 |

## 编码约定

- 类名：`{TargetClass}Test`，放同包 `src/test/java` 下
- 方法命名：`methodName_scenario_expectedBehavior`（沿用现有 `parse_unsupportedRevision_shouldThrow` 风格）
- AssertJ 优先（`assertThat(...).isEqualTo(...)`），Mockito verify 场景用 JUnit 自带断言
- 每个测试方法一个场景，不合并无关断言
- 不创建测试基类/Helper 继承树——每个测试类独立
- Entity/DTO 往返测试：`ObjectMapper.writeValueAsString` + `readValue`，对比所有字段
- Layer 3 使用 `@ExtendWith(MockitoExtension.class)`，不用 `@SpringBootTest`

## 执行顺序

1. **Layer 1** → ~33 files
2. **Layer 2** → ~20 files
3. **Layer 3** → ~22 files

每批完成后运行 `mvn test` 验证全量通过。
