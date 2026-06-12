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

**MID（Message Identifier）**:
Atlas 协议的消息类型标识符。每个 MID 对应一种命令或数据类型（如 MID 0061 为拧紧数据）。
_Avoid_: 命令码、消息类型
