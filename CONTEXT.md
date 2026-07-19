# Tightening — 工业拧紧流程控制系统

工业拧紧工具的通信中间件。通过 TCP 协议连接多种品牌的拧紧控制器，接收拧紧结果数据并持久化，提供 REST API 暴露设备控制能力。

## 物理实体

**Device（设备）**:
泛指系统中接入的所有物理硬件实体，包括拧紧工具、PLC、扫码枪、串口设备等。每台 Device 在数据库中有对应的配置行，通过 `type` 字段区分具体类型。
_Avoid_: 工具实例、机器

**Tool（拧紧工具）**:
Device 的子集，特指执行拧紧操作的设备（如 Atlas Copco、FIT FTC6）。Tool 有自己的协议处理器（ToolHandler），支持 enable/disable 控制和参数集切换。
_Avoid_: 拧紧机、扳手

**Workstation（工位）**:
产线上执行拧紧任务的物理位置。一台 Tool 属于一个 Workstation。
_Avoid_: 工站、站台

**Controller（控制器）**:
拧紧工具的硬件控制单元，负责驱动拧紧枪并采集数据。服务端通过 TCP 与 Controller 通信。
_Avoid_: 主机、控制柜

## 业务实体

**ProductMission（产品任务）**:
一次完整的产品装配任务定义。包含多个 ProductSide，规定所有需要拧紧的螺栓。执行后生成一条 MissionRecord。
_Avoid_: 生产任务、工单

**ProductSide（产品面）**:
产品装配任务中一个物理面，包含多个 ProductBolt。一个 ProductMission 的所有 ProductSide 必须全部执行完毕，否则任务判定 NG。
_Avoid_: 面位、装配面

**ProductBolt（产品螺栓）**:
ProductSide 中一颗需要拧紧的螺栓，有独立的序列号（boltSerialNum）。
_Avoid_: 螺丝、紧固件

**MissionRecord（任务执行记录）**:
ProductMission 被执行一次后生成的记录。一条 MissionRecord 包含 0 条或多条 TighteningData，记录该任务下所有拧紧操作的结果。是历史快照，不通过外键与 ProductMission 强关联。
_Avoid_: 任务记录、执行日志

**ProductCode（产品码/追溯码）**:
产品的唯一标识字符串，由上层系统下发，贯穿整个任务生命周期。作为服务级追溯码，独立于 Atlas 协议层的 VIN 字段。
_Avoid_: 条码、VIN、序列号

**MaterialCode（物料码）**:
物料的标识字符串，用于追溯装配过程中使用的零部件批次。区别于 ProductCode 的产品级追溯，MaterialCode 是零部件级。
_Avoid_: 零件码、部件码

**BarCodeMatchingRule（条码匹配规则）**:
用于验证 ProductCode 或 MaterialCode 是否格式合规的规则。按类型分为 PRODUCT_TRACE（产品码规则，每任务最多 1 条）和 MATERIAL_BARCODE（物料码规则，0-n 条）。规则通过 segments 定义码的位置匹配条件。创建物料码规则前必须先有产品码规则。
_Avoid_: 条码校验规则、码规则

**MissionPrerequisite（前置任务）**:
定义任务之间的依赖关系。类型分为 SAME_TRACE（产品码前置，两个任务共用同一产品码）、MATERIAL_TRACE（物料码前置，前置任务的产品码 = 当前任务的物料码值）、INSPECTION_CHAIN（点检链，当前和前置任务都必须是点检任务）。新增前置关系时须通过 BFS 检测循环依赖。MATERIAL_TRACE 必须通过 barcodeRuleId 关联当前任务的一条 MATERIAL_BARCODE 规则——因为一个任务可有多个物料码规则，须明确指定用哪个来匹配前置任务的产品码。SAME_TRACE 和 INSPECTION_CHAIN 不需要 barcodeRuleId（前者每个任务至多一个产品码规则可通过任务定位，后者是纯任务-任务依赖不涉及条码匹配）。
_Avoid_: 前置条件、依赖任务

**Rework（返工）**:
对已完成任务的重新执行。MissionRecord.isRework 标记该记录是否为返工任务（0=正常首次执行，1=返工）。默认创建为 NORMAL(0)。
_Avoid_: 重做、重试

**ParameterSet / PSet（参数集/配方）**:
拧紧工具端预设的一组拧紧参数（扭矩上下限、目标值、角度上下限、目标值等）。存储在工具端，`sendPSet` 命令仅切换工具当前使用的 PSet 编号，不下发参数内容。
_Avoid_: 配方参数、工艺参数、配置集

## 拧紧数据

**TighteningData（拧紧数据）**:
一次拧紧操作的完整记录。包含扭矩/角度的实际值、上下限、目标值、状态判定（OK/NG）、批次信息、时间戳等。由工具在拧紧完成后主动推送。TighteningData 中冗余存储 Workstation、ProductSide、Bolt 等字段作为历史快照，不与上游实体强关联。
_Avoid_: 拧紧结果、拧紧记录（易与 MissionRecord 混淆）

**TighteningStatus（拧紧状态）**:
单次拧紧操作是否合格。OK(1) 表示拧紧达标，NG(0) 表示不合格。
_Avoid_: 通过/失败

**MissionResult（任务结果）**:
一次 MissionRecord 的最终判定：OK(1) 或 NG(0)。所有非 OK 的结束（Trigger 不通过、Capability Fail、引擎异常）最终都记为 NG。`faultMessage` 字段承载 NG 的原因供前端展示。编码与 TighteningStatus 对齐。
_Avoid_: 任务状态、完成状态、JobStatus

**TighteningResultType（拧紧结果类型）**:
区分操作是拧紧 TIGHTENING(1) 还是松开 LOOSENING(2)。
_Avoid_: 操作类型、拧紧方向

**TorqueStatus（扭矩状态）**:
单次拧紧的实际扭矩相对于预设范围的判定。Atlas 协议: LOW(0) / OK(1) / HIGH(2)；FIT 协议: OK(1) / NG(2)。
_Avoid_: 扭矩结果

**AngleStatus（角度状态）**:
单次拧紧的实际角度相对于预设范围的判定。Atlas 协议: LOW(0) / OK(1) / HIGH(2)；FIT 协议: OK(1) / NG(2)。
_Avoid_: 角度结果

**CurveData（曲线数据）**:
拧紧过程中的扭矩/角度采样序列。不同协议的传输方式不同：FIT 分片传输由 FitCurveDataReassembler 拼装；Atlas 扭矩和角度分两次发送。每条 CurveData 属于一条 TighteningData。
_Avoid_: 波形数据、采样数据

**Batch（批次）**:
一组拧紧操作的逻辑分组，有预设大小（batchSize）和当前计数（batchCounter）。批次状态: 0=NOK（未完成）、1=OK（已完成）、2=未使用。
_Avoid_: 组、轮次

## 设备生命周期

**DeviceManager（设备管理器）**:
设备生命周期的中心管理器。随用户登录启动（`userLoggedIn`），在最后一个用户登出时停止。内部维护定时扫描线程，自动重连断开设备。
_Avoid_: 连接管理器

**DeviceHolder（设备持有者）**:
设备在内存中的运行时对象，包含 Device 实体引用、Netty Channel、当前 DeviceStatus、工具 enable/disable 状态和冷却时间戳。
_Avoid_: 设备会话、设备连接

**DeviceStatus（设备连接状态）**:
CONNECTING（连接中）、CONNECTED（已连接）、DISCONNECTED（已断开）、NONE（初始无状态）。
_Avoid_: 在线/离线

**DeviceType（设备类型）**:
当前支持的设备型号枚举: ATLAS_PF4000(1)、ATLAS_PF6000_OP(2)、FIT_FTC6(3)、SUDONG_X7(4)。每种类型绑定对应的 DeviceHandler 实现类。
_Avoid_: 设备型号

**DeviceHandler（设备处理器）**:
协议处理器的根接口。继承链: DeviceHandler → TCPDeviceHandler → ToolHandler → 具体协议 Handler（AtlasPFSeriesHandler / FitSeriesHandler）→ 具体型号 Handler。
_Avoid_: 驱动、适配器

**DeviceChangeEvent（设备变更事件）**:
设备 CRUD 操作后发布的事件（ADD / UPDATE / DELETE），在事务提交后由 DeviceManager 响应，确保内存状态与数据库一致。
_Avoid_: 设备更新通知

**DeviceRegistry（设备注册表）**:
设备接口视图的查询入口。监听 DeviceChangeEvent 维护 Tool 实例注册表，与 DeviceManager 零耦合（事件驱动）。不管理连接，只提供查询。
_Avoid_: 设备仓库、连接池

## 工具控制

**Lock / Unlock（锁定/解锁）**:
锁定（lock）= 禁止拧紧工具执行拧紧操作；解锁（unlock）= 允许操作。ITool 接口提供 `sendLock()` / `sendUnlock()`，ToolHandler 公开方法为 `lock()` / `unlock()`。Lock/Unlock 是发给工具的硬件命令（协议层），区别于 WorkplaceStatus 的 OPERATION_ENABLE/DISABLE（前端展示状态）。
_Avoid_: 启用/禁用、enable/disable

**cooldown（冷却机制）**:
连续发送同一类型命令（lock 对 lock、unlock 对 unlock）的最小间隔。目的是防止短时间内大量重复命令阻塞通道，影响其它命令的反馈接收。可通过 force 方法绕过。
_Avoid_: 限流、节流

**LockMsg（加锁原因）**:
任何需要 tool 保持 locked 状态的条件。所有 Capability 和管理员操作只往 `MissionContext.lockMsgs` 集合中增/删自己的锁消息，不直接操作 tool。LockStateMonitor 是该集合的唯一消费者。生命周期内管理员手动 unlock 通过设置 `boltUnlockOverride` 实现——LockStateMonitor 看到此标志为 true 则跳过 lock，SWITCH_BOLT 时重置。
典型锁消息：`adminConfirm`、`psetSwitching`、`arrangerPositioning`、`socketSelecting`。
_Avoid_: 锁请求、锁标记

**PSET switching（参数集切换）**:
通知工具切换到指定的 PSet 编号。受 pSetLock 保护防止并发切换。切换操作本身不受冷却控制。
_Avoid_: 配方下发、参数切换

## 通信

**Heartbeat（心跳）**:
保持 TCP 连接存活的周期性消息。FIT 和 SudongX7 使用显式心跳，Atlas 依赖服务端被动推送。心跳失败累计超过阈值后断开连接。
_Avoid_: 保活、ping

**Frame（帧）**:
一次完整的协议消息单元。不同协议有不同的帧格式（定界符、长度字段、校验方式等），由对应的 FrameDecoder/FrameCodec 处理。
_Avoid_: 数据包、报文

## 任务生命周期

**Lifecycle Engine（生命周期引擎）**:
驱动 Mission 从激活到完成的 Actor 模型引擎。每次激活创建一个实例，持有 Context（私有状态），通过 inbox 消息队列串行处理设备数据和操作员命令。生命周期结束后通过 onCompleted / onFaulted 回调通知 MissionOrchestrator。
_Avoid_: 状态机引擎、流程引擎

**Stage（阶段）**:
生命周期的宏观阶段：VALIDATION（校验）→ ACTIVATION（激活）→ OPERATION（拧紧执行）→ FINALIZATION（收尾）。每个阶段内部包含多个子状态，由引擎 `advancePipeline()` + `PipelineDefinition.getNext()` 驱动转换。
_Avoid_: 步骤、节点

**Self-Loop（自循环）**:
配置标志（`self-loop-enabled`），通过 getSettings/getConfigs API 返回给前端。前端根据该配置决定上一条 Mission OK 后是否自动调用激活 API。后端不做自循环逻辑——激活就是激活，不区分来源。
_Avoid_: 自动循环、重复任务

**WorkplaceStatus（工作台状态）**:
工作台运行状态，与登录会话绑定，不持久化。4 状态平级：

```
UNACTIVATED ──(登录 + 条码校验通过 + tool 已连接)──→ ACTIVATED
ACTIVATED ──(后端流程自动 enable)──→ OPERATION_ENABLE
OPERATION_ENABLE ←─(LockStateMonitor 下发 lock/unlock)─→ OPERATION_DISABLE
```

- 默认进入 ACTIVATED 后为 OPERATION_DISABLE，后端校验通过后自动切到 ENABLE
- 前端特殊情况下手动发 lock/unlock，需 adminConfirm
- 登出/登录过期 → 当前 Mission 立即 NG → 清理引擎 → 回到 UNACTIVATED
- 通过 SSE 推送给前端，前端仅展示，不持有状态机逻辑
_Avoid_: 工作台模式

**BoltState（螺栓状态）**:
单颗螺栓的运行状态（`BoltState` 枚举）：PENDING(0) → TIGHTENING(1) → JUDGED_OK(2) / JUDGED_NG(3)。每个螺栓独立追踪，OPERATION 阶段循环推进。
_Avoid_: 螺丝状态、点位状态、BoltStatus


**Inspection / Challenge Task（点检/挑战任务）**:
正式任务前的强制检验任务，复用 Mission 生命周期引擎。设计要点：

- **策略**：`InspectionPolicy` 枚举（当前 DAILY，可扩展 SHIFT / HOURLY / PER_BATCH）
- **存储**：`inspection_record` 表（workstation_id, policy, inspection_date, mission_record_id）
- **触发**：`CheckInspection` TriggerCapability 在触发管道中排在条码校验之后，今日无记录 → Interrupt（告知前端先做点检）
- **执行**：点检任务即普通 ProductMission，走完完整生命周期后写 inspection_record
- 点检结果判定标准与正常拧紧一致
_Avoid_: 检验任务、校准任务

**SkipScrew（快速完成）**:
当 Mission 配置 `SkipScrew=true` 时，触发阶段直接跳过所有检测、所有流程、所有设备交互→ 创建 OK 记录 → 导出 → Completed(OK)。不走 VALIDATION/ACTIVATION/OPERATION。
_Avoid_: 跳点、直通

## 引擎与管道

**Capability（能力单元）**:
可独立启用/禁用的业务判断单元。每个 Capability 有前置条件（precondition）和执行逻辑（execute），返回 Pass/Fail/Skip/Interrupt 驱动管道推进。开发者通过 settings.yml 配置每个客户启用哪些 Capability，PipelineDefinition 只加载已启用的。
_Avoid_: 插件、模块、步骤

**TriggerCapability（触发能力单元）**:
Capability 的子类型（`TriggerCapability extends Capability`），属于 pre-VALIDATION 触发管道，不注册到 4 阶段 `PipelineDefinition` 中。由 `LifecycleEngineFactory` 作为独立列表注入引擎，在 `TriggerRequest` 到达时由 `executeTriggerPipeline()` 执行。当前实现：ProductBarCodeCheck、PartsBarCodeMatching、SkipScrewCheck。
_Avoid_: 触发检查、前置校验 Capability

**Pipeline（管道）**:
一个子状态下 Capability 的有序执行链。按 `priority` 排序，顺序执行。任一 Fail → 管道中止；全部 Pass → 管道完成。Capability 之间通过 Context 字段传递数据。
_Avoid_: 链、序列

**Global Capability Pool（全局 Capability 池）**:
所有已知 Capability 的注册视图。开发时：Spring 自动收集带 `@Component` 的 Capability Bean；交付时：由开发者在 server 管理端按客户配置 `settings.yml` 中启用哪些 Capability，PipelineDefinition 只加载已启用的。新增功能 = 新增 Capability 实现 + 在 settings 里注册。
_Avoid_: 功能列表、模块注册表

**SubState（子状态）**:
生命周期 4 阶段内部的细分状态点（VALIDATING / PREPARING / ACTIVATING / SWITCH_BOLT / TIGHTENING_RECEIVED / JUDGING / STORING / ADVANCING / CLEANING_TASKS / LOCKING_TOOLS / RESETTING_STATE / EXPORTING / FAULTED）。每个 SubState 是 Capability 的挂载点——Capability 声明自己挂载到哪个 SubState，引擎执行到该 SubState 时按 priority 顺序驱动所属 Capability。
_Avoid_: 步骤、节点、附着点

**Persistent Monitor（持久监控）**:
定时器驱动的循环检查器，分两类：

- **引擎级**（OPERATION 阶段内）：挂载在 LifecycleEngine 的 tickScheduler 上，通过向 inbox 投递定时消息触发。与管道 Capability 不同——不参与管道，不驱动子状态转换。当前实例：LockStateMonitor。
- **系统级**（登录期间持续运行）：独立于引擎，用户登录后启动、登出时停止。当前实例：DeviceConnectionMonitor。

_Avoid_: 后台任务、守护线程

**LockStateMonitor（锁状态监控器）**:
OPERATION 阶段的引擎级持久监控器（50ms 周期，由 tickScheduler 驱动）。只读消费 `MissionContext.lockMsgs` 集合——非空则 lock，空则 unlock。OPERATION 阶段内**唯一**的 lock/unlock 执行者。生命周期外（无活跃引擎）管理员手动 lock/unlock 直接调用 ToolHandler，不经过 lockMsg 集合。
_Avoid_: 锁管理、锁定任务

**DevicePreconditionMonitor（设备前置监控器）**:
OPERATION 阶段的持久监控器（100ms 周期）。可选设备——仅在配置了排列机或套筒选择器且启用时才激活。检查辅助设备是否就位，超时后触发重试/终止。
_Avoid_: 前置检查器

**DeviceConnectionMonitor（设备连接监控器）**:
系统级持久监控器（1000ms 周期），用户登录后启动、登出时停止。遍历 DeviceRegistry 检查 `isConnected()`，断开时更新状态推 SSE。纯状态展示用，不直接影响生命周期——设备断连后，需要交互的 Capability 自会失败。
_Avoid_: 连接检测器

**Outbox（出站表）**:
SQLite 中的 `export_task` 表。FINALIZATION 阶段的 ExportData 等 Capability 将异步 I/O 任务（文件导出、外部 DB 写入、PLC 通知）写入 outbox 后立即返回 Pass。后台 ExportWorker 独立线程消费 outbox，支持重试和永久失败告警。
_Avoid_: 导出队列表、异步任务表

**JudgmentStrategy（判定策略）**:
按 DeviceType 分发到具体实现（AtlasJudgment / FitJudgment / SudongJudgment）的策略接口，`ExecuteJudgment` Capability 通过该接口获取拧紧数据的控制器原始判定。位于独立包 `com.tightening.judgment`，不依赖 Capability 基础设施。设备新增 = 新增策略实现 + 注册到 `JudgmentConfig`。
_Avoid_: 判定器、结果解释器

**Mission Activation（任务激活）**:
统一入口 `POST /api/mission/activate`，接收 productCode 和 partsCode（均可空）。激活流程内部根据 Mission 配置判定：配置了需要但未提供 → Interrupt。前端根据返回决定下一步。条码来源（扫码枪/手动输入/PLC）是前端的事，后端不区分。激活触发者始终是前端（即使 self-loop 场景也是前端根据配置自动调用），后端不做自动重启。
_Avoid_: 激活信号、启动事件

**Trigger Pipeline（触发管道）**:
引擎 pre-VALIDATION 的独立管道，由 `executeTriggerPipeline()` 驱动 TriggerCapability 链（ProductBarCodeCheck → PartsBarCodeMatching → CheckInspection → SkipScrewCheck）。条码校验由 TriggerCapability 直接查询规则后判定。
_Avoid_: 激活检查、前置校验、CheckCanActivate

**MissionOrchestrator（任务编排器）**:
引擎生命周期的单一入口。负责创建引擎实例（通过 LifecycleEngineFactory）、持有活跃引擎引用、将设备拧紧数据路由到正确引擎的 inbox。与 DeviceRegistry 协作，在设备注册时挂载数据路由回调。引擎完成后通过回调通知 MissionOrchestrator 清理引擎引用。
_Avoid_: 任务管理器、流程控制器

**LocalSettings / settings.yml（本地化配置）**:
部署环境的本地化配置文件（`~/tightening_system/settings.yml`），独立于 application.yaml。客户部署时按需复制修改。当前包含：自循环开关（`self-loop-enabled`）、导出类型列表（`export-types`）。后续扩展 PLC 超时、心跳间隔、Capability 开关等。
_Avoid_: 应用配置、环境变量

**DataRouter（数据路由器）**:
将拧紧数据从设备回调路由到正确引擎 inbox 的接口。`DeviceRegistry` 依赖此接口（而非 `MissionOrchestrator`），消除循环依赖。`MissionOrchestrator` 实现此接口，在设备注册时通过 `ToolAdapter.onTighteningData()` 挂载回调。
_Avoid_: 消息路由器、事件分发器