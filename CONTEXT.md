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

**ProductMission（产品任务）**[规划中]:
一次完整的产品装配任务定义。包含多个 ProductSide，规定所有需要拧紧的螺栓。执行后生成一条 MissionRecord。
_Avoid_: 生产任务、工单

**ProductSide（产品面）**[规划中]:
产品装配任务中一个物理面，包含多个 ProductBolt。一个 ProductMission 的所有 ProductSide 必须全部执行完毕，否则任务判定 NG。
_Avoid_: 面位、装配面

**ProductBolt（产品螺栓）**[规划中]:
ProductSide 中一颗需要拧紧的螺栓，有独立的序列号（boltSerialNum）。
_Avoid_: 螺丝、紧固件

**MissionRecord（任务执行记录）**:
ProductMission 被执行一次后生成的记录。一条 MissionRecord 包含 0 条或多条 TighteningData，记录该任务下所有拧紧操作的结果。是历史快照，不通过外键与 ProductMission 强关联。
_Avoid_: 任务记录、执行日志

**ProductCode（产品码/追溯码）**[规划中]:
产品的唯一标识字符串，由上层系统下发，贯穿整个任务生命周期。作为服务级追溯码，独立于 Atlas 协议层的 VIN 字段。
_Avoid_: 条码、VIN、序列号

**Rework（返工）**[规划中]:
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
一次 MissionRecord 的整体判定。NG(0) 表示不合格，OK(1) 表示合格。编码与 TighteningStatus 对齐。
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
当前支持的设备型号枚举: ATLAS_PF4000(1)、ATLAS_PF6000_OP(2)、FIT_FTC6(3)。每种类型绑定对应的 DeviceHandler 实现类。
_Avoid_: 设备型号

**DeviceHandler（设备处理器）**:
协议处理器的根接口。继承链: DeviceHandler → TCPDeviceHandler → ToolHandler → 具体协议 Handler（AtlasPFSeriesHandler / FitSeriesHandler）→ 具体型号 Handler。
_Avoid_: 驱动、适配器

**DeviceChangeEvent（设备变更事件）**:
设备 CRUD 操作后发布的事件（ADD / UPDATE / DELETE），在事务提交后由 DeviceManager 响应，确保内存状态与数据库一致。
_Avoid_: 设备更新通知

## 工具控制

**enable / disable（启用/禁用）**:
允许或禁止拧紧工具执行拧紧操作。通过异步命令下发到工具端，命令完成后更新状态。
_Avoid_: 启动/停止、激活/停用

**cooldown（冷却机制）**:
连续发送同一类型命令（enable 对 enable、disable 对 disable）的最小间隔。目的是防止短时间内大量重复命令阻塞通道，影响其它命令的反馈接收。可通过 force 方法绕过。
_Avoid_: 限流、节流

**PSET switching（参数集切换）**:
通知工具切换到指定的 PSet 编号。受 pSetLock 保护防止并发切换。切换操作本身不受冷却控制。
_Avoid_: 配方下发、参数切换

## 通信

**Heartbeat（心跳）**:
保持 TCP 连接存活的机制。FIT 协议使用显式心跳（IdleStateHandler WRITER_IDLE 触发，最多重试 3 次后断开）；Atlas 协议依赖服务端被动推送。
_Avoid_: 保活、ping

**Atlas Protocol（Atlas 协议）**:
Atlas Copco Power Focus 系列拧紧控制器的 TCP 通信协议。帧以长度字段开头（LengthFieldBasedFrameDecoder），以空字符（`\0`）结尾。数据字段为定长 ASCII 编码，每个字段由协议字节偏移量（Protocol Byte）定位。支持多种数据 Revision（Rev 1-7、998、999），每个 Revision 定义不同的字段布局和额外数据。
_Avoid_: PF 协议、Open Protocol

**FIT Protocol（FIT 协议）**:
FIT FTC6 拧紧控制器的 TCP 通信协议。帧定界符为 HEAD（0xAA55）和 TAIL（0x55AA），数据载荷为小端序二进制（Little Endian）。支持显式心跳（HeartbeatReq/HeartbeatRsp）、报警数据上报和曲线数据分片传输。曲线数据通过 FitCurveDataReassembler 将分片包拼装为完整 CurvePoint 列表。
_Avoid_: FTC6 协议

**MID（Message Identifier）**:
Atlas 协议的消息类型标识符。每个 MID 对应一种命令或数据类型（如 MID 0061 为拧紧数据）。
_Avoid_: 命令码、消息类型

**Frame（帧）**:
一次完整的协议消息单元。Atlas 帧 = head 长度字段 + MID + Revision + 定长 ASCII 数据 + `\0`。FIT 帧 = HEAD + cmdType + dataLength + data + TAIL。
_Avoid_: 数据包、报文

## 任务生命周期 [规划中]

**Lifecycle Engine（生命周期引擎）**:
驱动 Mission 从激活到完成的 Actor 模型引擎。每次激活创建一个实例，持有 Context（私有状态），通过 inbox 消息队列串行处理设备数据和操作员命令。生命周期结束后返回 LifecycleResult。
_Avoid_: 状态机引擎、流程引擎

**Stage（阶段）**:
生命周期的宏观阶段：VALIDATION（校验）→ ACTIVATION（激活）→ OPERATION（拧紧执行）→ FINALIZATION（收尾）。每个阶段内部包含多个子状态，由 `determineTransition` 按管道结果驱动转换。
_Avoid_: 步骤、节点

**Self-Loop（自循环）**:
上一个 Mission OK 完成后的自动激活机制。此为触发信号的一种（`SELF_LOOP`），不等同于"无条码直接激活"——自循环后仍需走校验和 CheckCanActivate。NG 完成不触发自循环。
_Avoid_: 自动循环、重复任务

**WorkplaceStatus（工作台状态）**:
工作台的操作锁定状态：UNACTIVATED → ACTIVATED → OPERATION_ENABLE / OPERATION_DISABLE。FINISHED_OK/NG 不由 workplaceStatus 承载，而由 MissionRecord.missionResult 承载。
_Avoid_: 工作台模式

**BoltStatus（螺栓状态）**:
单颗螺栓的运行状态：DEFAULT（初始）→ WORKING（正在拧紧）→ DONE（OK）/ ERROR（NG）。NG 后允许重试——ERROR → WORKING。
_Avoid_: 螺丝状态、点位状态


**Inspection / Challenge Task（点检/挑战任务）**:
正式任务前的强制检验任务。使用与常规 Mission 相同的生命周期引擎。两种类型：ALL（一次性，解锁当日全部 Mission）和 MULTIPLE（绑定 N 个具体 Mission）。
_Avoid_: 检验任务、校准任务

**SkipScrew（快速完成）**:
当 Mission 配置 `SkipScrew=true` 时，触发阶段直接跳过所有检测、所有流程、所有设备交互→ 创建 OK 记录 → 导出 → Completed(OK)。不走 VALIDATION/ACTIVATION/OPERATION。
_Avoid_: 跳点、直通

## 引擎与管道 [规划中]

**Capability（能力单元）**:
可独立启用/禁用的业务判断单元。每个 Capability 有前置条件（precondition）和执行逻辑（execute），返回 Pass/Fail/Skip/Interrupt 驱动管道推进。客户通过授权系统决定启用哪些 Capability。
_Avoid_: 插件、模块、步骤

**Pipeline（管道）**:
一个子状态下 Capability 的有序执行链。按 `priority` 排序，顺序执行。任一 Fail → 管道中止；全部 Pass → 管道完成。Capability 之间通过 Context 字段传递数据。
_Avoid_: 链、序列

**Global Capability Pool（全局 Capability 池）**:
所有已知 Capability 的注册表。客户从池中按授权选择启用哪些 Capability。池按阶段（VALIDATION/ACTIVATION/OPERATION/FINALIZATION）组织。新增功能 = 新增 Capability 并注册入池。
_Avoid_: 功能列表、模块注册表

**Attachment Point（附着点）**:
子状态转换 `A → B` 上的两个插入位置：Before（A 离开前，可阻止转换）和 After（B 进入后，仅通知）。客户额外逻辑通过注册 Capability 到对应附着点实现，无需修改原有 Capability。
_Avoid_: 钩子、事件点

**Persistent Monitor（持久监控）**:
OPERATION 阶段的定时器驱动循环检查器。与管道 Capability 不同——不参与管道，不驱动子状态转换。通过向 inbox 投递定时消息触发执行。当前实例：LockStateMonitor、DevicePreconditionMonitor、DeviceConnectionMonitor。
_Avoid_: 后台任务、守护线程

**LockStateMonitor（锁状态监控器）**:
OPERATION 阶段的持久监控器（50-100ms 周期）。只读消费 `lockMsgs` 集合，合并后统一发送 tool lock/unlock 命令。OPERATION 阶段内**唯一**的锁操作执行者。手动锁定/解锁具有最高优先级（覆盖其他来源）。
_Avoid_: 锁管理、锁定任务

**DevicePreconditionMonitor（设备前置监控器）**:
OPERATION 阶段的持久监控器（100ms 周期）。检查排列机和套筒选择器是否就位，超时后触发重试/终止。
_Avoid_: 前置检查器

**DeviceConnectionMonitor（设备连接监控器）**:
OPERATION 阶段的持久监控器（500ms 周期）。遍历 DeviceRegistry 检查 `isConnected()`，状态变化时发 `DeviceStatusChanged` 出站消息。与 `lockMsgs` 完全独立。
_Avoid_: 连接检测器

**Outbox（出站表）**:
SQLite 中的 `export_task` 表。FINALIZATION 阶段的 ExportData 等 Capability 将异步 I/O 任务（文件导出、外部 DB 写入、PLC 通知）写入 outbox 后立即返回 Pass。后台 ExportWorker 独立线程消费 outbox，支持重试和永久失败告警。
_Avoid_: 导出队列表、异步任务表

**JudgmentStrategy（判定策略）**:
`ControllerStatusCheck` Capability 内部使用的策略接口。按 DeviceType 分发到具体实现（AtlasJudgment / FitJudgment / SudongJudgment），从拧紧数据中提取控制器的原始判定状态。设备新增 = 新增策略实现 + 注册到 StrategyRegistry。
_Avoid_: 判定器、结果解释器

**Trigger Signal（触发信号）**:
生命周期激活的发起者：OPERATOR_CLICK / SCANNER_INPUT / PLC_SIGNAL / SELF_LOOP / HTTP_REQUEST。与条码获取方式、条码校验策略组合形成完整的触发流程。
_Avoid_: 激活信号、启动事件

**CheckCanActivate（激活条件判定）**:
触发阶段的最终门控。检查产品追溯码和物料码是否满足 Mission 配置要求——不满足则拒绝激活并提示操作员扫码。条码校验通过后自动调用，点击激活也走同一逻辑。
_Avoid_: 激活检查、前置校验

**LifecycleResult（生命周期结果）**:
引擎生命周期结束时的返回值：Completed(missionResult) — 走过完整生命周期；Aborted(reason) — 激活前中止（验证/激活失败）；Faulted(error) — 引擎自身异常。
_Avoid_: 结束状态、完成结果
