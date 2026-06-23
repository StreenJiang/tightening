# Mission 生命周期架构设计

> 设计日期: 2026-06-21  
> 修订日期: 2026-06-23 (第三次审查修订)  
> 修订说明: 本次修订根据架构审查决议进行以下调整——(1) 废弃 Profile/装配清单模式，改为 Capability 全局池 + 客户授权；(2) ACTIVATION 子状态从 6 个简化为 3 个；(3) 删除 Suspended/Resume/TCS 挂起恢复机制，Capability.execute() 改为返回 CompletableFuture，阻断确认(AdminConfirm)返回未完成 future，引擎保存恢复点+释放 Actor 线程回到 inbox；(4) CreateMissionRecord 移至 ACTIVATION 管道末尾；(5) VALIDATION/ACTIVATION 失败回归 IDLE 而非 FINALIZATION；(6) 锁检查改为 OPERATION 持久监控 (LockStateMonitor)；(7) OK/NG 判定拆分为 ControllerStatusCheck + TorqueRangeCheck + AngleRangeCheck；(8) 新增 DeviceConnectionMonitor 持久监控；(9) 新增 Outbox Worker 异步 I/O 出站模型；(10) 补充 Inspection/Challenge Task 设计；(11) JudgmentStrategy 注册表（DeviceType → Strategy），设备间解耦；(12) 阻断确认期间 TighteningData 忽略，其他消息照常处理不推进管道。  
> 设计范围: Mission 从激活到完成的完整生命周期架构  
> 适用平台: 架构级设计，语言无关（C# / Java 均可实现）  
> 设计原则: 抛开现有代码，从业务和流程出发做纯粹架构设计

---

## 1. 设计目标

1. **可扩展的流程编排**: 客户随时可能在任何流程的前后增减校验、数据收集等需求
2. **多配置统一**: 通过 Capability 全局池 + 本地配置取代当前的继承重写模式
3. **架构级抽象**: 定义清晰的阶段边界、接口契约、流程编排规则，与具体 UI 框架和语言解耦

---

## 2. Mission 状态机

### 2.1 核心关系：工作台就绪 vs 生命周期

工作台就绪是**一次性的外部准备**，不在生命周期内部。切换 Mission 时需要重新执行。

```
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│  [工作台初始化 — 生命周期外部，一次性 / Mission 切换时重执行]       │
│                                                                │
│  选择 Mission → 加载 MissionData → 加载设备 → 加载扫码规则 → READY │
│      ↑                                                         │
│      │ (切换 Mission: 重新走此流程)                               │
│      │ (自循环: OK 时跳过，Mission 不变)                          │
│      │                                                         │
├──────┼─────────────────────────────────────────────────────────┤
│      │            Mission 生命周期 (4 阶段)                      │
│      │                                                         │
│      │   触发信号 → CheckCanActivate → VALIDATION                │
│      │      │                            │                      │
│      │      │                 ┌──────────┴──────────┐           │
│      │      │             通过(→ACTIVATION)      失败(→IDLE)     │
│      │      │                                              │
│      │      ▼                                              │
│      │   ACTIVATION                                         │
│      │      │                            │                  │
│      │      │                 ┌──────────┴──────────┐       │
│      │      │             通过(→OPERATION)      失败(→IDLE)   │
│      │      │                                              │
│      │      ▼                                              │
│      │   OPERATION                                           │
│      │      │                            │                  │
│      │      │                 结束(→FINALIZATION)            │
│      │      │                                              │
│      │      ▼                                              │
│      │   FINALIZATION ──────(仅 OK 时自循环)──→ 回到触发信号   │
│      │      │                                              │
│      │      ▼                                              │
│      │   生命周期结束（回到 READY，等待切换或下次激活）          │
│      │                                                      │
└──────┴──────────────────────────────────────────────────────┘
```

**工作台就绪输入**（外部注入，生命周期不负责加载）：

| 输入 | 说明 |
|---|---|
| `missionData` | 已加载的 Mission 完整数据（含螺栓列表、条码规则、max_ng_num 等），生命周期内不可变 |
| `workstationConfig` | 站点配置（工具、力臂、排列机等设备映射） |
| `deviceRegistry` | 已连接设备注册表（引用，非所有权。设备生命周期长于单次生命周期） |
| `shouldSelfLoop` | 自循环开关标志 |

**返回 IDLE 时的工具处理**：
- VALIDATION 或 ACTIVATION 失败导致返回 IDLE 时，根据 `autoLockToolEnabled` 配置决定工具行为：
  - `true` → 自动下发锁工具命令
  - `false` → 保持当前工具状态不变

**关键规则**:
- **切换 Mission**：走完整的工作台就绪 + 新生命周期。生命周期进行中禁止切换 Mission（触发按钮禁用/不响应）
- **自循环**：Mission 不变。**仅正常结束(OK)** 触发自循环，跳过工作台就绪，直接回到触发信号。NG/异常结束 → 生命周期结束，**不自循环**。异常结束显示阻断弹窗，操作员确认后可选重试或退出

Mission 生命周期划分为 4 个宏观阶段：

| 阶段 | 职责 | 进入 | 退出 |
|---|---|---|---|
| **VALIDATION** | 设备/工作站配置校验、版本特有检查（批头计数器等）。注：条码校验和挑战任务在触发阶段（§2.3）执行 | 收到触发信号 + CheckCanActivate 通过 | 校验通过 → ACTIVATION；校验失败 → IDLE |
| **ACTIVATION** | 螺栓初始化、排列机/套筒选择器信号、PSet 下发、MissionRecord 创建、后台监控启动 | 校验通过 | 激活完成 → OPERATION；激活失败 → IDLE |
| **OPERATION** | 主循环：螺栓切换(排列机/套筒/PSet)、接收拧紧数据、OK/NG 判定、面切换、管理员确认、排列机/套筒/设备连接监控 | 激活完成 | 全螺栓 DONE / NG 达上限 → FINALIZATION |
| **FINALIZATION** | 统一收尾：取消后台任务、锁定工具、重置状态、数据导出（通过 Outbox Worker 异步）、释放资源、自循环判断 | OPERATION 结束 | 自循环(OK) → 触发信号；否则生命周期结束 |

**失败路径规则**:
- VALIDATION 失败 → **返回 IDLE**（不解锁/锁定工具取决于 `autoLockToolEnabled`，不导出数据，不创建 MissionRecord）
- ACTIVATION 失败 → **返回 IDLE**（同上）
- OPERATION 内部 Capability 返回 `Fail` 或 `Interrupt` → 引擎跳转到 FINALIZATION
- **FINALIZATION 内部特殊处理**：FINALIZATION Capability 返回 `Fail` → 记录日志，**继续执行**后续 Capability（确保收尾操作完整）。返回 `Interrupt` → 跳过当前子状态，继续下一个子状态。FINALIZATION 不可被外部中断（忽略 `interrupt()`）

**工作台就绪失败路径**：
- 加载 MissionData 失败（数据损坏/不存在）、设备连接失败 → 显示错误状态，**阻止进入 READY 状态**
- 生命周期引擎仅在 READY 后才接收触发信号

### 2.2 阶段内部子状态

#### VALIDATION

```
VALIDATING → PASSED → [→ACTIVATION]
      │
      └──→ FAILED → [→IDLE]
```

#### ACTIVATION

```
PREPARING → ACTIVATING → ACTIVATED → [→OPERATION]
     │           │
     └── ACTIVATION_FAILED ──→ [→IDLE]
```

ACTIVATING 子状态仅执行 `CreateMissionRecord`。螺栓初始化在 PREPARING 中完成；排列机/套筒/PSet 信号移至 OPERATION 的 `SWITCH_BOLT` 子状态（每次螺栓切换时执行）。

#### OPERATION

```
SWITCH_BOLT ──► AWAITING_TIGHTENING ◄───────────────────────┐
                     │                                        │
                     ▼                                        │
              TIGHTENING_RECEIVED                              │
                     │                                        │
                     ▼                                        │
              JUDGING                                          │
                     │                                        │
                 ┌───┴───┐                                     │
                OK       NG                                    │
                 │        │                                    │
                 ▼        ▼                                    │
              STORING  STORING                                 │
                 │        │                                    │
                 │        │                                     │
                 ▼        │                                     │
              ADVANCING  │                                     │
                 │        │                                    │
                 │        ├── 重试 → SWITCH_BOLT ──────────────┘
                 │        │
                 │        └── 终止 (NG达上限) → [→FINALIZATION]
                 │
                 ├── 下一螺栓 → SWITCH_BOLT
                 │
                 ├── 下一面   → SWITCH_BOLT
                 │
                 └── 全部完成 → ALL_BOLTS_DONE ──► [→FINALIZATION]
```
```

**自循环规则**：
- OK 完成 → 自循环（若 `shouldSelfLoop=true`），跳过工作台就绪，回到触发信号
- NG 完成 → **不自循环**，生命周期结束
- 异常中断 → 不自循环，显示阻断弹窗，操作员确认后可选重试或结束

#### FINALIZATION

```
CLEANING_TASKS → LOCKING_TOOLS → RESETTING_STATE → EXPORTING → RELEASING
                                                                        │
                                                         ┌──────────────┤
                                                         ▼              ▼
                                           shouldSelfLoop=true    shouldSelfLoop=false
                                               && missionResult=OK         │
                                                         │              ▼
                                                         ▼        生命周期结束
                                                  回到触发信号
                                                    (跳过工作台就绪)
```

FINALIZATION 不再处理 NG/异常的自循环。NG 完成时 lifecycleResult 为 FINISHED_NG，引擎直接结束生命周期，回到 READY。

### 2.3 生命周期触发机制

生命周期触发由三个独立环节组合而成。触发前，工作台必须已处于 READY 状态（Mission 数据、设备、扫码规则全部就绪）。

```
[触发信号] → [条码获取] → [条码校验] → [SkipScrewCheck] → [CheckCanActivate] → 进入 VALIDATION
                                                    │
                                                    └── SkipScrew=true ──► 快速通道 → FINALIZATION(OK)
```

#### 环节 1：触发信号（谁发起）

| 信号来源 | 说明 |
|---|---|
| `OPERATOR_CLICK` | 操作员点击"激活"按钮 |
| `SCANNER_INPUT` | USB 扫码枪扫码，每次扫描后自动触发 |
| `PLC_SIGNAL` | PLC 发送信号（工件到位、工位就绪等） |
| `SELF_LOOP` | 上一个生命周期 OK 结束后自动发出（仅 OK 触发） |
| `HTTP_REQUEST` | Web 版本通过 HTTP API 远程触发 |

#### 环节 2：条码获取（从哪里拿到条码）

| 获取方式 | 说明 |
|---|---|
| `MANUAL_INPUT` | 操作员手输或扫码枪（通过 UI 输入框） |
| `PLC_READ` | PLC 从产线读取条码（RFID、条码阅读器等） |
| `HTTP_BODY` | HTTP 请求体中携带条码 |
| `NONE` | 不需要条码，跳过 |

#### 环节 3：条码校验

每次扫码都会触发校验（校验当前录入的全部条码）。校验策略由 Mission 的条码配置决定：

| 校验策略 | 说明 |
|---|---|
| `FULL_MATCH` | 产品追溯码 + 物料码全部匹配 |
| `PRODUCT_ONLY` | 仅校验产品追溯码 |
| `CUSTOM_RULES` | 客户自定义校验规则 |
| `SKIP` | Mission 未配置条码，跳过校验 |

#### CheckCanActivate（激活条件判定）

扫码校验通过后自动调用，操作员点击"激活"也调用同一个逻辑：

```
CheckCanActivate(context):
    ① mission 配置了产品追溯码？ ─── 未录入 → 拒绝，提示扫码
    ② mission 配置了物料码？   ─── 未录入 → 拒绝，提示扫码
    ③ ①+② 都满足 / 无条码配置 ──► 进入 VALIDATION
```

**关键**：扫码本身不直接触发激活。扫码 → 校验当前已录条码 → 校验通过后自动走 SkipScrewCheck → CheckCanActivate。点击激活也走同一个路径。

#### SkipScrew 快速通道

SkipScrewCheck 在触发阶段执行，与 CheckCanActivate 同级。当 Mission 配置了 `SkipScrew=true` 时，跳过所有检测、所有流程、所有设备交互，直接生成结果。

```
SkipScrewCheck.execute(ctx):
    if ctx.missionData.skipScrew:
        // 快速通道: 创建 MissionRecord → 进入 FINALIZATION → 返回 Completed(OK)
        1. ctx.shouldSelfLoop = false               // SkipScrew 禁止自循环
        2. CreateMissionRecord(mission_result = OK)  // 直接写入 OK
        3. ctx.currentStage = FINALIZATION
        4. ctx.currentSubState = EXPORTING
        5. executeStage(FINALIZATION, ctx)           // ExportData → SelfLoopCheck → Completed(OK)
        return Interrupt("SkipScrew fast track")    // 触发阶段调度器检测 Interrupt → 直接进入快速通道
    return Pass                                     // 正常流程继续到 CheckCanActivate
```

快速通道中：
- 不做任何设备交互（不连工具、不发信号、不下发 PSet）
- 不启动任何监控器（无 LockStateMonitor、无 DevicePreconditionMonitor、无 DeviceConnectionMonitor）
- 不锁定/解锁工具
- `shouldSelfLoop = false`（SkipScrew 不触发自循环）
- 仅创建一条 `mission_result=OK` 的 MissionRecord + 触发 ExportData 导出

#### Inspection / Challenge Task

ChallengeTaskCheck Capability 在触发阶段的条码校验环节执行，查询"当前操作员/工作站是否有未完成的挑战任务"。

两种挑战任务类型：

| 类型 | 说明 | 数据模型 |
|---|---|---|
| `ALL` | 解锁当日此工作站的全部 Mission | `inspection_mission` 表记录一次检验任务，完成后标记 COMPLETED |
| `MULTIPLE` | 绑定到 N 个特定 Mission | `inspection_mission` + `mission_inspection_binding`（N:N），完成后解除绑定 |

ChallengeTaskCheck 执行逻辑：

```
ChallengeTaskCheck.execute(ctx):
    if !ctx.missionData.inspectionRequired:
        return Pass

    // 三大类检查（具体实现由条码校验模块和岗位管理模块提供，ChallengeTaskCheck 调用对应验证服务获取结果）：
    // 1. 追溯码检查 — 错码/重码检测
    if !barcodeService.validateProductCode(ctx.missionData):
        return Fail("产品追溯码校验失败，存在错码或重码")

    // 2. 物料码检查 — 错码/重码检测
    if !barcodeService.validatePartsCode(ctx.missionData):
        return Fail("物料码校验失败，存在错码或重码")

    // 3. 前置岗位检查 — 前置岗位任务完成状态验证
    if !preShiftChecker.verifyPrecedingStations(ctx.missionData.workstationId):
        return Fail("前置岗位任务未完成，请先完成前置岗位")

    return Pass
```

检验任务使用与常规 Mission 相同的 LifecycleEngine 驱动，不重复设计。

点检链支持：点检任务 B 可配置其前置依赖任务 A（层级校验）。ChallengeTaskCheck 沿依赖链向上查询——所有前置点检任务必须已完成(OK)。**循环依赖在任务配置阶段校验**（非运行时），检测到环则阻止保存。

#### 实际场景组合

```
场景 A — 标准产线（手动扫码）:
  SCANNER_INPUT → MANUAL_INPUT → FULL_MATCH → CheckCanActivate → VALIDATION

场景 B — PLC 全自动产线:
  PLC_SIGNAL → PLC_READ → FULL_MATCH → CheckCanActivate → VALIDATION

场景 C — 无条码工位:
  OPERATOR_CLICK → NONE → SKIP → CheckCanActivate → VALIDATION

场景 D — 自循环 + 扫码（标准自循环）:
  SELF_LOOP → 等待 MANUAL_INPUT → FULL_MATCH → CheckCanActivate → VALIDATION

场景 E — 自循环 + PLC 条码（PLC 自循环）:
  SELF_LOOP → PLC_READ → FULL_MATCH → CheckCanActivate → VALIDATION

场景 F — 自循环 + 无条码:
  SELF_LOOP → NONE → SKIP → CheckCanActivate → VALIDATION

场景 G — PLC 信号 + 手动输入（混合）:
  PLC_SIGNAL → 弹出提示 → MANUAL_INPUT → FULL_MATCH → CheckCanActivate → VALIDATION

场景 H — MES/上位机 HTTP 触发（Web版）:
  HTTP_REQUEST → HTTP_BODY → FULL_MATCH → CheckCanActivate → VALIDATION

场景 I — HTTP 触发 + 无条码（Web版远程激活）:
  HTTP_REQUEST → NONE → SKIP → CheckCanActivate → VALIDATION

场景 J — HTTP 触发 + PLC 条码（Web版混合产线）:
  HTTP_REQUEST → PLC_READ → FULL_MATCH → CheckCanActivate → VALIDATION
```

#### 自循环的本质

自循环（`SELF_LOOP`）只是一种**触发信号**，不等于"无条码直接激活"。FINALIZATION 中 `shouldSelfLoop=true` 且 `missionResult=OK` 时发出该信号，然后：

- 需要条码 → 等待扫码/PLC/HTTP 提供条码 → 校验 → 进入 VALIDATION
- 无条码配置 → 直接进入 VALIDATION

**仅 OK 触发自循环**。NG 完成、Interrupt、异常结束时引擎结束生命周期，不自循环。

#### 触发阶段扩展 Capability

客户特有触发交互封装为独立的触发阶段 Capability，注册到全局池，按配置启用。不影响标准触发流程。

**PlcBarcodeSelfLoop**（GLB 专用）：

```
PlcBarcodeSelfLoop.execute(ctx):
    // 在 SELF_LOOP 触发信号后执行
    // 不阻塞 Actor 线程。Actor 模型下禁止同步 sleep/polling。
    if ctx.triggerSignal != SELF_LOOP or !ctx.hasPLC:
        return Skip("非 PLC 自循环场景")
    
    // 创建未完成的 future，引擎挂起管道
    
    // 注册 pending 确认（操作员确认）
    sendOutbound(Confirmation("PLC 条码自循环模式，是否开始？"))
    return future  // 未完成，管道在此挂起
    
    // 注：PLC 轮询改为定时器驱动，不在 Capability.execute 内同步执行。
    // 操作员确认后，引擎注册一个定时器（如每 200ms 投递 PlcPollTick 到 inbox），
    // onMessage 处理 PlcPollTick 时尝试读取条码：
    //   - 超时 → 发送重试确认或完成 future(Fail)
    // 这么做保持 Actor 线程非阻塞。
```

启用此 Capability 后，场景 E（PLC 自循环）中的 `PLC_READ` 由引擎内部此 Capability 执行，而非依赖外部 PLC 适配器主动推送。

#### 异常阻断

NG/异常结束时，操作界面上显示阻断弹窗（非自循环场景），操作员确认后生命周期结束回到 READY，等待下次激活。不自循环。

### 2.4 状态转换附着点（Attachment Point）

每个子状态转换提供两个附着点，Capability 可插入任意位置。对于状态转换 `A → B`：

```
         A ─────────────────► B
         │                    │
    Before(A→B)          After(A→B)
    (可阻止转换)         (仅通知, 不阻止)
```

- **Before(A→B)**（之前）：A 即将离开、B 即将进入时触发。可阻止转换——返回 `Fail` 或 `Interrupt`
- **After(A→B)**（之后）：B 已经进入后触发。事后通知/日志——无论成功失败都不影响已发生的转换

**`executeEntryAttachments(stage, context)`**：进入一个宏观阶段时，聚合执行该阶段的 entry 附着点。按 §9 定义的 Before(→firstSubState) 附着点顺序执行所有已启用的 Capability。返回聚合结果（Pass / Fail / Interrupt）。

**`executeExitAttachments(stage, context)`**：离开一个宏观阶段时，聚合执行该阶段的 exit 附着点。按 §9 定义的 After(lastSubState→) 附着点顺序执行所有已启用的 Capability。返回聚合结果（仅通知性，不阻止阶段转换）。

**执行规则**（适用于 Before 附着点；After 附着点仅通知，Fail/Interrupt 不阻止转换）：
- 同附着点的多个 Capability 按 `priority` 升序执行，`priority` 相同时按全局池声明顺序
- 任一 Capability 返回 `Fail` 或 `Interrupt`：**立即停止**后续 Capability 执行
- `Fail` → 触发当前阶段的退出（VALIDATION/ACTIVATION → IDLE，OPERATION → FINALIZATION）
- `Interrupt` → 直接跳转到退出（同上）
- 附着点不支持挂起

---

## 3. Pipeline + Capability 管道模型

### 3.1 核心概念

每个宏观阶段内部是一条**可配置的执行管道**。管道由一组按序执行的 **Capability** 组成。Capability 分为两类：

- **管道 Capability**：在阶段管道的特定子状态下执行一次，返回 Pass/Fail/Skip/Interrupt 驱动状态转换
- **持久监控（Persistent Monitor）**：注册在引擎层，由定时器驱动，通过向 inbox 投递定时消息触发执行。不参与管道，不驱动状态转换（除非检测到超时→发 Interrupt 消息）。用于排列机/套筒就位监控、设备连接监控等循环检查场景

```
┌──────────────────────────────────────────────────┐
│  STAGE: VALIDATION                                │
│                                                    │
│  Pipeline:                                         │
│    ┌──────────┐  ┌──────────────┐  ┌───────────┐  │
│    │ 工作站配置校验│→│ 批头计数器检查 │→│ 快速完成检查  │  │
│    └──────────┘  └──────────────┘  └───────────┘  │
│                                                    │
│  每个 Capability 返回:                              │
│    Pass ──► 继续下一个                              │
│    Fail ──► 中止管道，返回失败原因，→ IDLE(无数据导出)  │
│    Skip ──► 跳过（条件不满足时不执行）               │
│    Interrupt ──► 中断整个 Stage                     │
└──────────────────────────────────────────────────┘
```

### 3.2 Capability 接口规范

```
Capability {
    id          : 唯一标识（如 "torque_range_check"）
    name        : 可读名称（如 "扭矩范围检查"）
    stage       : 所属阶段（VALIDATION | ACTIVATION | OPERATION | FINALIZATION）
    attachPoint : 附着位置（如 Before(VALIDATING→PASSED) 或 After(ACTIVATED)）
    priority    : 执行优先级（同附着点多个 Capability 排序用，数字越小越先执行）
    
    // 前置条件：当前上下文是否启用此 Capability
    // 返回 false 时管道自动跳过（Skip），不执行 execute
    // 只检查 Context 状态，不保证外部系统状态不变（乐观检查 + 防御性执行模式）
    precondition(context) → bool
    
    // 执行逻辑
    // 返回值:
    //   - Pass: 继续管道中下一个 Capability
    //   - Fail(reason): 中止管道，失败原因
    //   - Skip(reason): 条件不满足，跳过，继续下一个
    //   - Interrupt(reason): 中断整个 Stage
    execute(context) → CapabilityResult
    
    // 异常处理：execute 抛出异常时的处理策略
    onError(context, error) → ErrorAction
}
```

### 3.3 返回值类型

```
CapabilityResult =
    | Pass                  // 成功，继续管道中下一个 Capability
    | Fail(reason)          // 失败，中止管道
                            //   VALIDATION/ACTIVATION → IDLE
                            //   OPERATION → FINALIZATION
                            //   FINALIZATION → 记录日志，继续执行
    | Skip(reason)          // 条件不满足，跳过此能力，继续下一个
    | Interrupt(reason)     // 中断整个 Stage
                            //   VALIDATION/ACTIVATION → IDLE
                            //   OPERATION → FINALIZATION
                            //   FINALIZATION → 跳过当前子状态

ErrorAction =
    | Abort                 // 终止当前管道，转为 Interrupt
    | Retry(maxTimes)       // 重试当前 Capability（达到最大次数后 Abort）
    | SkipAndContinue       // 记录错误，跳过当前 Capability 继续执行
```

### 3.4 各 Stage 默认 Capability 清单

#### VALIDATION Pipeline

条码校验（ProductBarCodeCheck, PartsBarCodeMatching, ChallengeTaskCheck）在触发阶段执行（§2.3），
不属于 VALIDATION。VALIDATION 只做设备/工作站配置校验和版本特有检查。

```
┌─────────────────────┐  ┌──────────────────┐
│ WorkstationConfig   │→│ ScrewBitCounter  │→ ...
│ Check               │  │ Check            │
└─────────────────────┘  └──────────────────┘
     ... ──► [全部 Pass] → ACTIVATION
     ... ──► [任一 Fail] → IDLE(不导出数据，不创建 MissionRecord)
```

#### ACTIVATION Pipeline

内部顺序执行（对外只呈现 3 个子状态：PREPARING → ACTIVATING → ACTIVATED）：

```
PREPARING 阶段:
  ┌──────────────┐
  │ PrepareBolts │  螺栓数据初始化、NG 计数重置、排序
  └──────────────┘

ACTIVATING 阶段:
  ┌──────────────┐
  │ CreateMission│  MissionRecord 创建（mission_result = NG）
  │ Record       │
  └──────────────┘
     ... ──► ACTIVATED → [→OPERATION]
     ... ──► [任一 Fail/Interrupt] → ACTIVATION_FAILED → IDLE
```

**设计要点**：
- CreateMissionRecord 是 ACTIVATION 管道的**唯一** Capability——螺栓排列机/套筒/PSet 信号下发移至 OPERATION 的 `SWITCH_BOLT` 子状态（每次螺栓切换时执行）
- OPERATION 阶段启动时自动注册所有持久监控器（LockStateMonitor、DevicePreconditionMonitor、DeviceConnectionMonitor），ACTIVATION 管道无需单独处理

#### OPERATION Pipeline

每次拧紧数据到达时触发，按子状态分支执行：

```
AWAITING_TIGHTENING ──► TIGHTENING_RECEIVED ──► JUDGING
                                                      │
                                              ┌───────┴───────┐
                                             OK               NG
                                              │                │
                                              ▼                ▼
                                          STORING           STORING
                                              │                │
                                              │                ├──→ 重试 → SWITCH_BOLT
                                              │                │
                                              ▼                ▼
                                          ADVANCING       终止 → ALL_BOLTS_DONE
                                              │
                                              ├─── 下一螺栓 → SWITCH_BOLT
                                              │
                                              ├─── 下一面   → SWITCH_BOLT
                                              │
                                              └─── 全部完成 → ALL_BOLTS_DONE
```

各子状态的 Capability 分配：

| 子状态 | Capability 管道 |
|---|---|
| `SWITCH_BOLT` | `SendArrangerSignal` → `SendSetterSelector` → `SendPSet` → `BoltBarCodeCheck` |
| `TIGHTENING_RECEIVED` | `ReceiveData` |
| `JUDGING` | `ControllerStatusCheck` → `TorqueRangeCheck` → `AngleRangeCheck`（详见 §5） |
| `STORING` (OK) | `StoreData` |
| `STORING` (NG) | `MaxNGCheck` → `BuzzerAlert` → `AdminConfirm` → `StoreData` |
| `ADVANCING` | `AdvanceBolt` |
JUDGING 子状态中，三个 Capability 必须全部 Pass 才判定为 OK。任一 Fail 判定为 NG。

**MaxNGCheck 的行为规则**（关键）：
- ngTimes 的递增已在 executeStage 的 JUDGING 子状态管道完成后统一执行（见后文 executeStage 第 1.5 步），MaxNGCheck **不再递增**
- MaxNGCheck **必须始终返回 `Pass`**，永远不返回 `Fail`
- 达上限时的终止路由由 `determineTransition` 的 `PipelineAllPassed` + `computeNgDecision` 处理
- 如果 MaxNGCheck 返回 Fail，管道路由将走 `PipelineFailed → nextSubState(ADVANCING)`，错误地推进螺栓索引

```
MaxNGCheck.execute(ctx):
    // ngTimes 已由 executeStage 在 JUDGING 完成后递增
    // MaxNGCheck 仅记录日志或触发自定义扩展逻辑
    // 终止路由由 determineTransition 根据 computeNgDecision 统一判定
    return Pass
```

**AdvanceBolt 的行为规则**：

AdvanceBolt 是 ADVANCING 子状态的唯⼀管道 Capability。螺栓索引的推进、面切换、状态标记均在此完成。

```
AdvanceBolt.execute(ctx):
    // OK 路径：推进螺栓索引，更新螺栓状态
    boltState = ctx.boltStates[ctx.currentBoltIndex]
    boltState.status = DONE
    // 尝试推进到下一螺栓
    nextIndex = nextBoltIndex(ctx.currentBoltIndex, ctx.currentSideIndex, ctx.missionData.bolts)
    if nextIndex != null:
        // 还有螺栓 → 推进索引，复位 ngTimes
        ctx.currentBoltIndex = nextIndex
        ctx.boltStates[nextIndex].status = WORKING
        ctx.boltStates[nextIndex].ngTimes = 0
        // 如果 switchingFaces(nextIndex, ctx.currentSideIndex) → 更新 ctx.currentSideIndex
        return Pass  // executeStage 递归 → ADVANCING → getTransition → AWAITING_TIGHTENING
    else:
        // 全部螺栓完成
        return Pass  // executeStage 递归 → ADVANCING → ALL_BOLTS_DONE
```

**关键规则**：
- **OK 路径**：AdvanceBolt 执行，推进 `currentBoltIndex`，螺栓状态设为 `DONE`。
- **NG 路径**：NG 重试时**不经过 ADVANCING**（STORING(NG) → RETRY → AWAITING_TIGHTENING，螺栓索引不变），NG 重试不推进螺栓。NG 达上限时 STORING(NG) → ALL_BOLTS_DONE → FINALIZATION。
- **面切换**：当面切换需要时（根据螺栓配置的 `sideIndex` 变化判断），AdvanceBolt 内部更新 `ctx.currentSideIndex`。面切换**不改变当前螺栓索引**（同一面内螺栓走完后才跨面）。
- **状态标记**：当前螺栓标记为 `DONE` 后，`determineMissionResult` 根据所有螺栓状态集合判断整体结果。
- **曲线数据清理**：推进到下一螺栓前，遍历 `pendingCurveData` 并用 `previousOperationData` 做最后一次匹配；匹配失败的直接丢弃（后续不会再有匹配窗口）

#### OPERATION 持久监控（Persistent Monitor — 引擎层定时驱动，不属于阶段 Pipeline）

引擎在进入 OPERATION 阶段时启动以下持久监控器。每个监控器由独立定时器驱动，通过向 inbox 投递定时消息触发执行。

```
引擎层定时器 → 向 inbox 投递定时检查消息
                       │
         ┌─────────────┼─────────────┐
         ▼             ▼             ▼
 DevicePrecondition  LockState     DeviceConnection
 Monitor             Monitor       Monitor
```

**1. DevicePreconditionMonitor**

```
检查周期: 100ms (可配置)

职责: 检查前置设备就位状态（排列机、套筒选择器）

行为:
  - 未就位 + 未超时 → 更新锁消息 (lockMsgs)
  - 未就位 + 超时 → 弹窗确认重试（由监控器通过 Outbound 消息通知操作员）
  - 达最大重试次数 → 维持 lockMsg 不解除，不终止任务。操作员须手动处理或等待设备恢复
  - 已就位 → 移除锁定消息

当前支持的 deviceType: arranger, setter_selector（行为相同：超时仅维持 lockMsg，不终止任务）
新增设备类型: 实现 IPreconditionCheckable 接口 + 注册定时消息处理
```

**2. LockStateMonitor**

```
检查周期: 50-100ms (可配置)

职责: 持续检查 lockMsgs 集合，根据锁定消息的合并结果发送工具锁/解锁命令

lockMsgs 的写入者（OPERATION 阶段内活跃，详见《mission-lifecycle-analysis.md》§9.1）：

| 来源 | 说明 | 写入条件 |
|---|---|---|
| 排列机未就位 | DevicePreconditionMonitor(arranger) | 前置条件检查未通过 |
| 套筒选择器未就位 | DevicePreconditionMonitor(setter_selector) | 前置条件检查未通过 |
| PSet 未下发/下发失败 | PSet 为空或 SendPSet 失败 | 螺栓切换时 PSet 不可用 |
| 力臂未到位 | LockStateMonitor 读 arm.getLatestPosition() 与目标比较 | 坐标不在目标范围内 |
| 手动锁定 | 操作员通过 UI 手动锁定工具 | 用户操作 |
| 条码未扫码 | 产品/物料码不匹配或未录入 | 扫码校验未通过 |
| 管理员确认中 | AdminConfirm Capability 等待密码确认（含 NG 重试等待场景） | AdminConfirm 执行中添加，Pass/超时/取消时移除 |

**关键设计规则**：LockStateMonitor **仅在 OPERATION 阶段运行**，且是 OPERATION 阶段内工具锁/解锁的**唯一执行者**。所有需要改变工具锁状态的模块（DevicePreconditionMonitor、AdminConfirm、手动锁等）通过 `lockMsgs` 的 Add/Remove 表达意图，由 LockStateMonitor 统一执行。OPERATION 之前（ACTIVATION）和之后（FINALIZATION 的 LockTools、IDLE 的 autoLockToolEnabled 配置），锁操作由对应的 Capability 或配置直接执行，不经过 LockStateMonitor。

行为:
  - 遍历 lockMsgs
  - **手动覆盖规则**（优先级最高）：
    - "手动锁定" 存在 → 无条件 sendLock()（忽略所有其他消息）
    - "手动解锁" 存在 → 无条件 sendUnlock()（忽略所有其他消息）
    - "手动锁定"和"手动解锁"由 UI 保证互斥
  - **力臂坐标检查**：若 `armLocatingEnabled`，读取 `arm.getLatestPosition()`（volatile 内存读）与目标坐标比较，不在范围内 → 添加 `LockedArmPosition`；在范围内 → 移除
  - **标准合并**（无手动覆盖时）：
    - 有任何活跃锁定消息 → sendLock()
    - 无任何锁定消息 → sendUnlock()
  - UI 始终显示所有活跃 lockMsgs（含被手动覆盖忽略的消息），确保操作员看到完整状态
  - 不修改 lockMsgs 内容（只读消费），LockStateMonitor 与 DevicePreconditionMonitor 职责明确分离

设计要点:
  - 原设计将锁检查作为 ACTIVATION 的一次性 Capability "StartLockCheckTask"，
    本次修订改为 OPERATION 阶段的持久监控器，确保整个拧紧过程中锁状态实时响应
  - 与 DevicePreconditionMonitor 的模式一致——定时器驱动，消息投递到 inbox
```

**3. DeviceConnectionMonitor**

```
检查周期: 500ms (可配置)

职责: 监控所有已注册设备的连接状态

行为:
  - 遍历 DeviceRegistry.getAllDevices()
  - 调用 isConnected()（纯内存读取，无 I/O）
  - 状态变化时发出 DeviceStatusChanged 出站消息（带 dedup——仅状态变化时发送）
  - 断连的设备不会影响 lockMsgs——与 LockStateMonitor 完全独立

设计要点:
  - Netty 的 channelInactive 提供零延迟首次响应
  - 轮询作为兜底（应对 channelInactive 丢失的场景）
  - DeviceConnectionMonitor 只负责状态检测和通知，不修改 lockMsgs
```

#### OPERATION 曲线数据旁路（异步回调，不参与主 Pipeline）

```
┌───────────────────────┐
│ HandleCurveData       │
│ (曲线数据异步旁路)      │
└───────────────────────┘
```

- **HandleCurveData**：接收工具控制器推送的拧紧曲线数据（与拧紧数据异步到达）。不参与 OK/NG 判定，不影响工具锁定。使用曲线匹配策略注册表 `Map<DeviceType, CurveMatchingStrategy>` 按协议分发：

| 策略 | 匹配方式 | 适用协议 |
|---|---|---|
| `SlotBasedCurveMatching` | 双槽位匹配：先查 `previousOperationData` → 再查 `currentOperationData` → 入 `pendingCurveData` | Atlas（拧紧数据与曲线无可关联字段） |
| `FieldBasedCurveMatching` | 字段精确匹配：读曲线中的关联字段直接匹配对应拧紧数据 | FIT（有可匹配字段） |

双槽位机制：Context 维护 `previousOperationData`（上一螺栓）和 `currentOperationData`（当前螺栓）。每次新拧紧数据到达时降级：`previous = current → current = new`。曲线数据优先匹配 `previous`（最常见场景：曲线天然晚于拧紧数据），每螺栓有两个拧紧周期的窗口时间。`FieldBasedCurveMatching` 不依赖双槽位——直接精准命中，但仍以双槽位为兜底。

#### FINALIZATION Pipeline

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐
│ CancelTasks  │→│ LockTools     │→│ ResetState    │→│ ExportData +         │
└──────────────┘  └──────────────┘  └──────────────┘  │  SelfLoopCheck       │
                                                       │  (EXPORTING 子状态)  │
     ... ──► RELEASING ──► Completed(result)           └──────────────────────┘
```

各子状态的 Capability 分配：

| 子状态 | Capability 管道 |
|---|---|
| `CLEANING_TASKS` | `CancelTasks` |
| `LOCKING_TOOLS` | `LockTools` |
| `RESETTING_STATE` | `ResetState` |
| `EXPORTING` | `ExportData` → `SelfLoopCheck` |
| `RELEASING` | 无 Capability（终点子状态，直接返回 `Completed`） |

**CancelTasks** Capability 先调用 `arm.stopListening()` 停止力臂坐标监听，因此无需分析文档中的 300ms 防抖等待。若仍有残留坐标事件到达 inbox，Actor 在 FINALIZATION 阶段检测到当前阶段非 OPERATION 后自动丢弃。

ExportData Capability 将导出任务写入 `export_task` 表（Outbox 模式，详见 §8），不阻塞 Actor 线程。实际导出由后台 ExportWorker 异步执行。

SelfLoopCheck Capability 在 EXPORTING 子状态的 ExportData 之后执行，检查 `missionResult == OK` 且 `shouldSelfLoop == true`：两者均满足时引擎在 Completed 处理中投递 SelfLoopSignal；否则直接结束生命周期，不自循环。

**重要**：RELEASING 是终点子状态，executeStage 的 `case RELEASING` 直接返回 `Completed(result)`，不执行任何管道 Capability。因此 SelfLoopCheck 等自循环判断必须放在 EXPORTING 管道中。之前附着点目录中标记为 RELEASING 的附着点（如 `→ RELEASING` Before/After）在子状态转换时触发，但因 RELEASING 无后续转换，`RELEASING → ...` 的附着点在当前设计中为占位符。

### 3.5 Capability 全局池 + 本地配置

不再定义 Base/SCII/GLB/YMT Profile 和装配清单。改为**全局 Capability 池 + 本地配置文件**模式。

```
┌──────────────────────────────────────────────────────┐
│                   全局 Capability 池                     │
│                                                        │
│  VALIDATION:  │  ACTIVATION:  │  OPERATION:   │ FINALIZATION:│
│  Workstation  │  PrepareBolts │  SendArranger │ CancelTasks  │
│  ConfigCheck  │  CreateMission│  Signal       │ LockTools    │
│  ScrewBit     │  Record       │  SendSetter   │ ResetState   │
│  CounterCheck │               │  Selector     │ ExportData   │
│               │               │  SendPSet     │ SelfLoopCheck│
│               │               │  ReceiveData  │ StoreToOuter │
│               │               │  Controller   │ DB           │
│               │               │  StatusCheck  │ SendPlcJob   │
│               │               │  TorqueRange  │ Result       │
│               │               │  Check        │ ...          │
│               │               │  AngleRange   │              │
│               │               │  Check        │              │
│               │               │  StoreData    │              │
│               │               │  AdvanceBolt  │              │
│               │               │  MaxNGCheck   │              │
│               │               │  BuzzerAlert  │ ExportService│
│               │               │  BoltBarCode  │ ...          │
│               │               │  Check        │              │
│               │               │  AdminConfirm │              │
│               │               │  DataCache    │              │
│               │               │  ForOuterDB   │              │
│               │               │  ...          │              │
└───────────────────────┬──────────────────────────────┘
                        │
注: SkipScrewCheck、PlcBarcodeSelfLoop 属于**触发阶段**（与 CheckCanActivate 同级），不在上述全局池的四阶段列中。OPERATION 列中 ReceiveData、ControllerStatusCheck、TorqueRangeCheck、AngleRangeCheck、StoreData、AdvanceBolt 为**默认管道**（默认启用）；MaxNGCheck、BoltBarCodeCheck、BuzzerAlert、AdminConfirm、DataCacheForOuterDB 等为**扩展**（按配置选择启用）。FINALIZATION 列中 CancelTasks、LockTools、ResetState、ExportData、SelfLoopCheck 为**默认管道**；StoreToOuterDB、SendPlcJobResult 等为**扩展**。

            ┌───────────┴───────────┐
            │   本地配置（.conf 等）   │
            │                       │
            │   示例：               │
            │    WorkstationConfigCheck, ReceiveData, ...│
            │   (具体启用项由配置     │
            │    文件决定)           │
            └───────────────────────┘
```

**核心机制**：

1. **全局池**：所有 Capability 注册在一个全局池中。每个 Capability 是独立的功能单元，不绑定到任何"版本"概念。新增 Capability 只需实现接口并注册到池。

2. **本地配置**：本机持有一份配置文件（格式待定，如 `.properties`/`.conf`/`.ini`），声明启用的 Capability 列表。配置是"正向选择"——只列举要启用的 Capability，不继承任何默认集合。配置文件可复制到同产线其它机器，实现批量部署。

3. **引擎初始化**：
   ```
   engine.init():
       // 读取本地配置，获取启用的 Capability ID 列表
       enabledIds = loadCapabilityConfig()
       
       // 从全局池过滤
       pool = GlobalCapabilityRegistry.getAll()
       enabled = pool.filter(cap -> enabledIds.contains(cap.id))
       
       // 展开为运行时管道（按 stage/subState/attachPoint 分组排序）
       this.runtimePipeline = expandPipeline(enabled)
   ```

4. **新增功能**：如果已有 Capability 满足需求 → 配置中启用对应 Capability。如果需要新功能 → 新增 Capability 注册到全局池 + 配置文件启用。**无需修改任何现有 Capability 代码**。

5. **运行时不可变**：引擎初始化后，运行时管道固定。配置变更需要重启引擎会话。

7. **Capability 加载进度**：引擎初始化时，每条 Capability 的启用/加载状态通过 OutboundMessage 推送到表现层。UI 展示"XXXX 功能加载中..." → "XXXX 功能加载成功 ✅"动画，已完成的条目向上堆积，1-2 秒后自动消失。

**与旧 Profile 模式的对比**：

| 维度 | 旧模式 (Profile) | 新模式 (全局池 + 授权) |
|---|---|---|
| 扩展方式 | 新建 Profile 继承、覆盖 | 新增 Capability + 本地配置启用 |
| 多版本关系 | 继承链（脆弱，父变更影响子） | 无继承，配置独立 |
| 部署流程 | 选择或创建 Profile | 配置已启用的 Capability，配置可复制到同产线其它机器 |
| 代码修改 | 可能需改现有 Capability | 不改现有代码 |
| 配置存储 | 隐含在 Profile 中 | 本地配置文件（.conf/.properties/.ini 等） |
| 运行时结构 | Profile 展开列表 | 全局池过滤列表 |

### 3.6 Capability 粒度原则

一个 Capability 应该是一个**可独立决策的业务判断单元**——内聚相关的检查逻辑，对外暴露出一个明确的判定结果（Pass/Fail/Interrupt）。

- **合**：判断逻辑紧密相关且不会被单独替换的，保留在一个 Capability 中
- **拆**：当某个内部检查需要独立启用/禁用/替换时，拆分为独立 Capability。典型例子：OK/NG 判定从单个 `OKNGJudge` 拆分为 `ControllerStatusCheck`、`TorqueRangeCheck`、`AngleRangeCheck`——不同客户可能只需要其中一部分检查
- **复用优先**：`AdminConfirm` 覆盖螺栓级和任务级两种确认场景——只做一个动作（弹窗让管理员输密码+记录日志），确认后的后续行为由触发它的 Capability 自行决定。未来新场景也可以复用同一个 `AdminConfirm`

### 3.7 Capability 间数据传递

- **显式数据流**：Pipeline 中相邻 Capability 之间的明确数据通过 Context 的可变字段定义（如 `judgeResult`、`currentOperationData`）
- **隐式数据流**：`context.extras{}` 键值存储用于 Capability 间的临时/自定义数据传递，引擎不感知其内容。仅用于客户特定需求的临时扩展

---

## 4. 生命周期引擎与上下文

### 4.1 生命周期引擎（Lifecycle Engine）

引擎负责驱动状态机流转、调度管道、管理并发和取消。引擎**不持有业务数据**，数据在 Context 中。引擎采用 **Actor 模型**——每个实例是一个 Actor，一次处理一条消息，内部串行。

```
LifecycleEngine {
    capabilities    // 运行时管道（已在初始化时从全局池展开）
    shouldSelfLoop  // 自循环开关
    
    // 接收 Context（所有权转移），驱动完整生命周期。
    // 调用者传入 Context 后不应再持有引用——Context 是引擎的私有状态。
    // 生命周期结束后通过返回值获取结果。
    start(context) → LifecycleResult
    
    // 外部中断信号（窗口关闭、用户取消等）
    // FINALIZATION 阶段忽略此信号（关键清理不可中断）
    interrupt(reason)
    
    // 当前是否在生命周期进行中（用于切换 Mission 的门控判断）
    isActive() → bool
    
    // 通用消息入口：表现层适配器和设备层通过此方法向引擎投递消息
    // 内部将消息入队到 Actor inbox，由 onMessage 分发处理
    postMessage(msg: InboundMessage)
}

// 生命周期执行结果
LifecycleResult =
    | Completed(missionResult)  // 走过完整生命周期（FINISHED_OK | FINISHED_NG）
                                //   自循环(OK): 引擎向 inbox 投递 SELF_LOOP 信号后返回 Completed
    | Aborted(reason, details)  // 激活前中止（验证失败/激活失败/外部 interrupt(非 OPERATION 阶段)）
                                //   无 MissionRecord，无导出，任务未启动
    | Faulted(error)            // 不可恢复的错误（如 FINALIZATION 自身失败）
```

### 4.2 上下文对象（Context）

Context 是引擎在各阶段之间传递的共享数据载体。分为**不可变部分**（外部注入，生命周期内只读）和**可变部分**（运行时状态）。

```
MissionContext {
    // === 不可变部分（生命周期外部注入） ===
    missionData       // 已加载的 Mission 完整数据（含螺栓配置列表、产品信息、max_ng_num 等）
                      //   missionData 业务参数: max_ng_num(NG次数上限), password_need_time(第几次NG起需管理员密码)
                      //   missionData.bolts[i] 包含: boltId, torque_min/max, angle_min/max,
                      //   pset_id, arranger_id, setter_selector_id, bit_specification, ...
    workstationConfig // 站点设备配置
    enabledCapabilityIds // 本机启用的 Capability ID 集合（引擎初始化时注入）
    deviceRegistry    // 设备注册表引用（非所有权，设备生命周期长于单次生命周期）

    // === 可变部分（运行时状态） ===
    currentStage      // 当前所处宏观阶段
    currentSubState   // 当前子状态
    workplaceStatus   // UNACTIVATED | ACTIVATED | OPERATION_ENABLE | OPERATION_DISABLE
                      //   注: FINISHED_OK/NG 由 missionRecord.missionResult 承载，
                      //   workplaceStatus 仅表示工作台的操作锁定状态
    
    boltStates[]      // 螺栓运行时状态（按 boltId 关联 missionData.bolts）:
                      //   boltId, status(DEFAULT|WORKING|DONE|ERROR), ngTimes, index
                      //   ngTimes 为单螺栓独立计数：每颗螺栓的 NG 次数独立累计，不与其它螺栓混淆
                      //   配置属性（torque_min/max 等）一律从 missionData.bolts 读取
    
    currentBoltIndex  // 当前螺栓索引
    currentSideIndex  // 当前面索引
    
    missionRecord     // 本次激活的 MissionRecord（OK/NG 结果）
    tighteningData[]  // 本次生命周期的拧紧数据集合（线程安全，由 Actor 串行化自然保证）
    
    // === 管道执行中间数据 ===
    tighteningStatus  // 控制器原始判定状态（ControllerStatusCheck 写入，供诊断/UI 展示）
    judgeResult       // 合并后的 OK/NG 最终判定（所有启用检查全部 Pass → OK）
    previousOperationData // 上一螺栓的 OperationData（供曲线数据双槽位匹配）
    currentOperationData  // 当前螺栓的 OperationData（供曲线数据关联）
    pendingCurveData      // 待匹配曲线数据队列（双槽位均未命中时暂存）
    barcodeObj           // 条码缓存对象（已录入的产品追溯码和物料码，供 Capability 校验）
    
    // === 回滚信息 ===
    activationCheckpoint // ACTIVATION 阶段的步骤完成情况（每个子步骤完成后记录）
                         // 用于失败时精确回滚（补偿排列机信号、重置已修改的螺栓状态）
    
    // === 信号 ===
    cancellationToken  // 主取消令牌
    interruptRequested // 外部中断标志 — interrupt() 设置，executePipeline 检查
    interruptReason    // 中断原因字符串
    lockMessages{}      // 当前锁定消息集合
    errorInfo{}         // 当前错误信息
    
    // === 设备开关（运行时可变，用户/配置控制） ===
    armLocatingEnabled    : Boolean   // 力臂定位开关
    arrangerEnabled       : Boolean   // 排列机开关  
    setterSelectorEnabled : Boolean   // 套筒选择器开关
    autoLockToolEnabled   : Boolean   // 自动锁工具开关（返回 IDLE 时生效）

    // === 扩展存储 ===
    extras{}           // Capability 间自由传递数据的键值存储，引擎不感知内容
}
```

### 4.3 取消令牌管理

```
工作台就绪后创建主令牌 _lifecycleToken

Stage 切换规则:
  VALIDATION → ACTIVATION:      保留令牌
  ACTIVATION → OPERATION:       保留令牌（后台任务绑定此令牌）
  OPERATION → FINALIZATION:     取消令牌 → 等待后台任务排空 → 创建新令牌用于收尾
                                排空策略: 先发停止新请求信号 → 等待当前 StoreData 完成(超时 5s)

VALIDATION/ACTIVATION → IDLE:   取消令牌 → 令牌重置（生命周期结束）

FINALIZATION 阶段不可中断: 忽略 interrupt() 命令，使用独立的不可取消令牌执行关键清理
```

### 4.4 引擎驱动流程

引擎采用 **Actor 模型**（详见 §6.5），所有外部消息（设备回调、操作员命令）通过 inbox 入队后串行处理。以下伪代码展示 Actor 内部的消息处理逻辑。

```
// === Actor 消息循环入口 ===

onMessage(msg, context):
    switch msg:
        case ActivateMission:
            start(context)
        case Terminate(reason):
        case TighteningData(data):
            if context.currentStage == OPERATION:
                context.previousOperationData = context.currentOperationData  // 降级到上一螺栓槽位
                context.currentOperationData = data
                context.currentSubState = TIGHTENING_RECEIVED  // onMessage 直接设置子状态
                result = executeStage(OPERATION, context)
                handleStageResult(result, context)
            matchAndStoreCurveData(data, context)
        case ToggleSwitch(switch, value):
            context.setDeviceSwitch(switch, value)
        case AdminPassword(password):
            if verifyPassword(password):
                context.lockMessages.remove(AdminConfirmation)
            else:
                sendOutbound(ShowAdminPasswordDialog)
        case BarcodeScanned(boltId, value):
            if validateBarcode(boltId, value):
                context.lockMessages.remove(LockedBoltBarCode)
                context.barcodeObj.recordPartsBarcode(boltId, value)
        case DeviceDisconnected(deviceId):
            handleDeviceDisconnect(deviceId, context)
        case DeviceConnected(deviceId):
            handleDeviceConnect(deviceId, context)
        
        // === 引擎内部消息（Actor 自投递） ===
        case MonitorTick:
            for monitor in persistentMonitors:
                if monitor.shouldRun(now()):
                    monitor.execute(context)
        case SelfLoopSignal:
            if shouldSelfLoop && context.missionRecord != null && context.missionRecord.missionResult == OK:
                start(context)  // 重新从 VALIDATION 开始（跳过工作台就绪）

// === onMessage 内部辅助函数 ===

matchAndStoreCurveData(data, context):
    // 曲线数据到达时，Actor 线程内同步执行双槽位匹配
    // 策略注册表按设备类型分发：SlotBasedCurveMatching / FieldBasedCurveMatching
    strategy = curveMatchingRegistry.get(context.currentOperationData.deviceType)
    matched = strategy.tryMatch(data, context)
    if matched != null:
        storeCurveData(matched)                 // 直接存储
    else:
        context.pendingCurveData.add(data)      // 暂存，等 StoreData 完成后重试
    // StoreData Capability 完成时调用 flushPendingCurveData(context) 遍历 pendingCurveData 再次匹配

handleDeviceDisconnect(deviceId, context):
    // 设备断连处理：更新设备注册表状态
    // 不修改 lockMsgs——DeviceConnectionMonitor 在下一次 tick 时感知变化
    context.deviceRegistry.markDisconnected(deviceId)
    log("设备断连: {deviceId}")
    sendOutbound(DeviceStatusChanged(deviceId, connected: false))

handleDeviceConnect(deviceId, context):
    // 设备重连处理（网络恢复）
    context.deviceRegistry.markConnected(deviceId)
    log("设备重连: {deviceId}")
    sendOutbound(DeviceStatusChanged(deviceId, connected: true))

// 处理 executeStage 的返回值（跨阶段推进/中断/完成/挂起）
handleStageResult(result, context):
    switch result:
        case Advance(nextStage):
            if nextStage == FINALIZATION:
                context.cancellationToken.Cancel()
                await drainBackgroundTasks(context, timeoutMs: 5000)
                context.cancellationToken = new CancellationToken()
                executeExitAttachments(context.currentStage, context)
                context.currentStage = FINALIZATION
                context.currentSubState = CLEANING_TASKS
                context.interruptRequested = false
                // 根据螺栓完成状态确定整体任务结果（正常结束 → OK，NG达上限/中断 → NG）
                if context.missionRecord != null:
                    context.missionRecord.missionResult = determineMissionResult(context)
                executeEntryAttachments(FINALIZATION, context)
                finalResult = executeStage(FINALIZATION, context)
                handleStageResult(finalResult, context)
            else:
                executeExitAttachments(context.currentStage, context)
                context.currentStage = nextStage
                context.currentSubState = firstSubStateOf(nextStage)
                // firstSubStateOf 阶段首子状态映射:
                //   VALIDATION→VALIDATING, ACTIVATION→PREPARING,
                //   OPERATION→SWITCH_BOLT, FINALIZATION→CLEANING_TASKS
                executeEntryAttachments(nextStage, context)
                subResult = executeStage(nextStage, context)
                handleStageResult(subResult, context)
        case Interrupted(reason):
            context.cancellationToken.Cancel()
            await drainBackgroundTasks(context, timeoutMs: 5000)
            context.cancellationToken = new CancellationToken()
            context.currentStage = FINALIZATION
            context.currentSubState = CLEANING_TASKS
            context.interruptRequested = false
            context.errorInfo = reason
            // 中断/异常完成：任务结果为 NG，不自循环
            if context.missionRecord != null:
                context.missionRecord.missionResult = FINISHED_NG
            finalResult = executeStage(FINALIZATION, context)
            handleStageResult(finalResult, context)
        case Completed(lifecycleResult):
            if shouldSelfLoop && lifecycleResult.missionResult == OK:
                inbox.enqueue(SelfLoopSignal)
            // 生命周期结束，Actor 等待下一次 ActivateMission
            // NG/Interrupt: 不自循环，直接结束
        case Aborted(reason, details):
            // 验证/激活失败，任务未启动——无 MissionRecord，无导出
            // 由 cleanupAndNotify 负责清理 + Outbound 通知
            log("生命周期中止: {reason} — {details}")
            // 生命周期结束，Actor 回到 READY
        case Waiting:
            return  // 控制权交还 Actor 循环，等待下一条消息（如 AWAITING_TIGHTENING）

// === 生命周期入口 ===

start(context):
    // 被 ActivateMission 消息触发。启动后由 handleStageResult 驱动各阶段推进。
    → context.cancellationToken = new CancellationToken()
    → context.currentStage = VALIDATION
    → context.currentSubState = VALIDATING
    executeEntryAttachments(VALIDATION, context)
    result = executeStage(VALIDATION, context)
    handleStageResult(result, context)   // 处理返回值，递归推进

// === executeStage: 执行当前阶段的完整管道 ===

executeStage(stage, context):
    subState = context.currentSubState
    
    // 1. 执行当前子状态的 Capability 管道
    pipelineResult = executePipeline(stage, subState, context)
    
    // 1.5 如果是 JUDGING 子状态，根据 pipeline 结果设置 judgeResult（供后续阶段使用）
    if subState == JUDGING:
        if pipelineResult == PipelineAllPassed:
            context.judgeResult = OK
        else:
            context.judgeResult = NG
        // NG 路径（仅管道 Fail，不含 Interrupt）：递增当前螺栓的 ngTimes
        if pipelineResult == PipelineFailed:
            context.boltStates[context.currentBoltIndex].ngTimes++
    
    // 2. 根据管道结果和阶段规则，决定子状态转换
    transition = determineTransition(stage, subState, pipelineResult, context)
    
    // 3. 等待子状态（无转换）：挂起，控制权交还 Actor
    if transition.target == subState && transition.target != RELEASING:
        return Waiting  // 等待下一条消息驱动（如 AWAITING_TIGHTENING 等拧紧数据）
    
    // 4. 执行转换的 Before 附着点（可阻止转换）
    beforeResults = executeAttachmentPoint(Before(subState → transition.target), context)
    if anyInterrupted(beforeResults):
        if stage == FINALIZATION:
            log("FINALIZATION 阶段 Before-attachment Interrupt 被忽略，继续执行: {collectReasons(beforeResults)}")
            // FINALIZATION 不可中断：继续执行后续 Capability，确保收尾操作完整
        else:
            return Interrupted(collectReasons(beforeResults))
    if anyFailed(beforeResults):
        transition = transitionWithFailed(stage)
    
    // 5. 执行子状态转换
    oldSubState = context.currentSubState
    context.currentSubState = transition.target
    
    // 6. 执行转换的 After 附着点（通知性，不阻止）
    afterResults = executeAttachmentPoint(After(oldSubState → transition.target), context)
    if anyFailed(afterResults):
        log("After-attachment failure (non-blocking): {collectReasons(afterResults)}")
    
    // 7. 根据转换目标决定返回值
    switch transition.target:
        // 终端子状态 → 跨阶段推进
        case PASSED:           return Advance(ACTIVATION)
        case ACTIVATED:        return Advance(OPERATION)
        case ALL_BOLTS_DONE:
            // 全部螺栓完成 → 进入 FINALIZATION
            // missionResult 由 handleStageResult(Advance(FINALIZATION)) 中
            // 调用的 determineMissionResult 根据全部螺栓状态统一判定
            return Advance(FINALIZATION)
        case RELEASING:
            // 终点子状态——不执行管道 Capability，直接返回 Completed
            // SelfLoopCheck 等收尾检查已在 EXPORTING 子状态管道中执行
            // missionRecord 为空防御（极端中断路径下可能未创建）
            result = context.missionRecord != null ? context.missionRecord.missionResult : FINISHED_NG
            return Completed(result)
        
        // 失败 → 生命周期中止（VALIDATION/ACTIVATION 阶段）
        case FAILED:
            cleanupAndNotify(context, "验证失败", context.errorInfo)
            return Aborted("VALIDATION_FAILED", context.errorInfo)
        case ACTIVATION_FAILED:
            cleanupAndNotify(context, "激活失败", context.errorInfo)
            return Aborted("ACTIVATION_FAILED", context.errorInfo)
        
        // 阶段内非终端子状态 → 同一消息处理中继续推进（递归）
        case VALIDATING:  // VALIDATION: VALIDATING → PASSED 或 FAILED（见 determineTransition）
            return executeStage(stage, context)
        case PREPARING, ACTIVATING:  // ACTIVATION: 非终端子状态均需继续推进
            return executeStage(stage, context)
        
        // OPERATION 子状态 → 同步推进
        case SWITCH_BOLT, TIGHTENING_RECEIVED, JUDGING, STORING, ADVANCING:
            return executeStage(stage, context)
        
        // OPERATION 等待子状态 → 不执行 pipeline，不调 determineTransition
        case AWAITING_TIGHTENING:
            return Waiting  // 直接交还 Actor，等待 TighteningData 消息
        
        // FINALIZATION 线性推进
        case CLEANING_TASKS, LOCKING_TOOLS, RESETTING_STATE, EXPORTING:
            return executeStage(FINALIZATION, context)

// === executePipeline: 执行一个子状态下的 Capability 管道 ===

executePipeline(stage, subState, context):
    capabilities = getCapabilitiesForSubState(stage, subState, context.enabledCapabilityIds)
    sort(capabilities, by: priority)
    
    if context.interruptRequested:
        return PipelineInterrupted(context.interruptReason)
    
    for cap in capabilities:
        if context.interruptRequested:
            return PipelineInterrupted(context.interruptReason)
        
        if !cap.precondition(context):
            log("Skipped: {cap.id} — precondition not met")
            continue
        
        try:
            result = cap.execute(context)
        catch Exception e:
            result = handleCapabilityError(cap, e, context)
        
        switch result:
            case Pass:     continue
            case Skip(r):  log("Skipped: {cap.id} — {r}"); continue
            case Fail(r):
                if stage == FINALIZATION:
                    log("FINALIZATION Capability Fail (ignored): {cap.id} — {r}")
                    continue
                return PipelineFailed(r)
            case Interrupt(r):
                if stage == FINALIZATION:
                    log("FINALIZATION Capability Interrupt (skip sub-state): {cap.id} — {r}")
                    return PipelineSkipSubState
                return PipelineInterrupted(r)
    
    return PipelineAllPassed

// === Capability 异常处理 ===

handleCapabilityError(cap, exception, context):
    strategy = cap.onError(context, exception)
    switch strategy:
        case Abort:
            log("Capability error (abort): {cap.id} — {exception.message}")
            context.errorInfo = exception.message
            return Interrupt(exception.message)
        case Retry(maxTimes):
            log("Capability error (retry): {cap.id} — {exception.message}")
            // 当前 executePipeline 循环中不处理重试逻辑，
            // 由引擎层或调用方决定是否重试，此处转为 Fail
            return Fail(exception.message)
        case SkipAndContinue:
            log("Capability error (skip): {cap.id} — {exception.message}")
            return Skip(exception.message)

// === 恢复点继续执行 ===


// === 错误清理和后台任务排空 ===

cleanupAndNotify(context, reason, details):
    // VALIDATION/ACTIVATION 失败时的统一清理
    // 1. 根据 autoLockToolEnabled 决定是否锁定工具
    if context.autoLockToolEnabled:
        context.deviceRegistry.lockAllTools()
    // 2. 取消主令牌
    context.cancellationToken.Cancel()
    // 4. 重置螺栓状态和索引（回到初始状态）
    context.boltStates.forEach(bs -> bs.status = DEFAULT)
    context.currentBoltIndex = 0
    // 5. 通知表现层（由 Outbound 消息携带）
    sendOutbound(Notification("生命周期中止: {reason} — {details}", ERROR))

drainBackgroundTasks(context, timeoutMs):
    // 等待后台任务排空（持久监控器的定时消息等）
    // 实现策略：设置完成回调 + 注册到 pending confirmations，
    // 后台任务检测到 cancellationToken 取消后标记完成
    log("开始排空后台任务（超时 {timeoutMs}ms）")
    // 具体实现依赖引擎层对后台任务的管理方式，
    // 当前架构阶段定义为接口行为，留待实现时细化
    await allBackgroundTasksComplete(timeoutMs)
    log("后台任务排空完成（或超时）")

// === NG 路径决策辅助 ===

computeNgDecision(context):
    // 判断当前 NG 螺栓是否已达重试上限
    // 返回 RETRY（可继续重试）或 TERMINATE（NG 达上限，终止任务）
    boltState = context.boltStates[context.currentBoltIndex]
    if boltState.ngTimes >= context.missionData.maxNgNum:
        return TERMINATE
    return RETRY

// === 整体任务结果判定（进入 FINALIZATION 前调用） ===

determineMissionResult(context):
    // 根据所有螺栓的最终状态计算整体任务结果
    // 在 Advance(FINALIZATION) 路径中被调用（Interrupted 路径直接设为 FINISHED_NG），
    // 结果写入 context.missionRecord.missionResult，供 SelfLoopCheck 和 Completed 使用
    // 规则:
    //   OPERATION 正常结束 (ALL_BOLTS_DONE): 所有螺栓状态为 DONE → FINISHED_OK
    //   NG 达上限终止: 存在螺栓状态为 ERROR → FINISHED_NG
    //   异常中断 (非 SWITCH_BOLT 子状态): 存在螺栓未完成或 ERROR → FINISHED_NG
    if context.boltStates.all(bs -> bs.status == DONE):
        return FINISHED_OK
    return FINISHED_NG

// === Before 附着点阻止转换时的回退 ===

transitionWithFailed(stage):
    // 当 Before 附着点返回 Fail 时，根据当前阶段返回失败子状态
    // 用于 executeStage 中替代原转换
    if stage == VALIDATION:
        return Transition(target: FAILED)
    if stage == ACTIVATION:
        return Transition(target: ACTIVATION_FAILED)
    // OPERATION/FINALIZATION: Before-attachment Fail 不会阻止阶段退出，
    // 此处不应被调用（由 executeStage 的 FINALIZATION 分支捕获）
    return Transition(target: FAILED)

// === 子状态转换规则（各阶段不同） ===

determineTransition(stage, subState, pipelineResult, context):
    // VALIDATION:
    //   VALIDATING + AllPassed → PASSED      (→ ACTIVATION)
    //   VALIDATING + Failed/Interrupted → FAILED  (→ IDLE)
    //
    // ACTIVATION:
    //   PREPARING + AllPassed → ACTIVATING
    //   ACTIVATING + AllPassed → ACTIVATED   (→ OPERATION)
    //   任何子状态 + Failed/Interrupted → ACTIVATION_FAILED (→ IDLE)
    //
    // OPERATION:
    //   进入 OPERATION 时首个子状态 = SWITCH_BOLT（螺栓 0 切换 + 信号发送）
    //   SWITCH_BOLT + AllPassed → AWAITING_TIGHTENING
    //   SWITCH_BOLT + Fail → AWAITING_TIGHTENING（失败仅加 lockMsg，不终止任务）
    //   TighteningData 消息到达后 onMessage 设 subState=TIGHTENING_RECEIVED
    //   TIGHTENING_RECEIVED + AllPassed → JUDGING
    //   JUDGING: 所有启用的检查全部 Pass → OK→STORING
    //            任一 Fail → NG→STORING
    //   STORING(OK) + Pass → ADVANCING
    //   STORING(NG) + Pass → 调用 computeNgDecision 判定:
    //     判定逻辑: boltStates[currentBoltIndex].ngTimes >= missionData.maxNgNum
    //       → TERMINATE → ALL_BOLTS_DONE（→ FINALIZATION）
    //       否则 → RETRY → SWITCH_BOLT（不经过 ADVANCING，螺栓索引不变）
    //   NG ngTimes 的递增由 executeStage 在 JUDGING 管道失败后统一递增（step 1.5），不由 MaxNGCheck 负责
    //   ADVANCING + 还有螺栓/需切面 → AWAITING_TIGHTENING
    //   ADVANCING + 全部完成 → ALL_BOLTS_DONE
    //
    // FINALIZATION:
    //   每个子状态完成后线性前进: CLEANING_TASKS → LOCKING_TOOLS → RESETTING_STATE → EXPORTING → RELEASING
    //   PipelineAllPassed → 前进到下一子状态
    //   PipelineSkipSubState (Interrupt in FINALIZATION) → 跳过当前子状态
    //   Capability Fail → 记录日志，继续执行（等同于 Pass）
    switch pipelineResult:
        case PipelineAllPassed:
            // 特殊处理 OPERATION + STORING：NG 路径完成后需根据 ngDecision 判定路由
            if stage == OPERATION && subState == STORING:
                if context.judgeResult == NG:
                    ngDecision = computeNgDecision(context)
                    if ngDecision == TERMINATE:
                        return Transition(target: ALL_BOLTS_DONE)
                    else:
                        return Transition(target: AWAITING_TIGHTENING)
            return Transition(target: nextSubState(stage, subState))
        case PipelineFailed(reason):
            if stage == FINALIZATION:
                log("Capability Fail in FINALIZATION (ignored, continue): {reason}")
                return Transition(target: nextSubState(stage, subState))
            if stage == VALIDATION:
                return Transition(target: FAILED)
            if stage == ACTIVATION:
                return Transition(target: ACTIVATION_FAILED)
            // OPERATION: 管道 Fail 经由 executeStage 递归推进到下一子状态
            //   - JUDGING 任一 Fail → 进入 STORING（NG 路径，executePipeline 的 judgeResult 已设 NG）
            //   - STORING Fail（StoreData 失败）→ 推进到 ADVANCING，后续由 executeStage 继续
            //     注意：StoreData 失败的兜底保护需在实现时考虑——当前设计推进到下一子状态，
            //     可能丢失失败数据。实现时可根据业务需求在此处改为 PipelineInterrupted 跳转 FINALIZATION
            //   - 其余子状态 Fail → 推进到下一子状态，由 executeStage 递归
            return Transition(target: nextSubState(stage, subState))
        case PipelineSkipSubState:
            // FINALIZATION 中 Interrupt → 跳过当前子状态，继续下一子状态
            if stage == FINALIZATION:
                return Transition(target: nextSubState(stage, subState))
            // 非 FINALIZATION 阶段不应产生 PipelineSkipSubState（只有 FINALIZATION
            // 的 executePipeline 会忽略 Interrupt 并返回此结果）
            return Transition(target: nextSubState(stage, subState))
        case PipelineInterrupted(reason):
            if stage == FINALIZATION:
                // FINALIZATION 中 Interrupt 同 SkipSubState：跳过当前子状态
                log("Interrupt in FINALIZATION (skip sub-state): {reason}")
                return Transition(target: nextSubState(stage, subState))
            if stage == VALIDATION:
                return Transition(target: FAILED)
            if stage == ACTIVATION:
                return Transition(target: ACTIVATION_FAILED)
            // OPERATION: Interrupt → 直接进入 ALL_BOLTS_DONE（终端子状态），
            // executeStage 的 case ALL_BOLTS_DONE 返回 Advance(FINALIZATION)
            // SWITCH_BOLT 无例外——设备故障应返回 Fail（不终止），非 Interrupt
            return Transition(target: ALL_BOLTS_DONE)

// === 外部中断 ===

interrupt(reason, context):
    if context.currentStage == FINALIZATION:
        log("FINALIZATION 阶段忽略 interrupt: {reason}")
        return
    context.interruptRequested = true
    context.interruptReason = reason
    context.cancellationToken.Cancel()
```

---

## 5. OK/NG 判定与 JudgmentStrategy

### 5.1 三层判定分离

OK/NG 判定不再由单个 `OKNGJudge` Capability 处理，而是拆分为三个独立 Capability，在 JUDGING 子状态中按序执行：

```
JUDGING 子状态管道:

┌──────────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ ControllerStatusCheck│→│ TorqueRangeCheck  │→│ AngleRangeCheck   │→ 判定结果
│ (协议相关)            │  │ (协议无关)        │  │ (协议无关)        │
└──────────────────────┘  └──────────────────┘  └──────────────────┘
         │                        │                        │
         ▼                        ▼                        ▼
 从 DTO 读取拧紧状态      扭矩值 vs 螺栓配置         角度值 vs 螺栓配置
 (protocol-specific)    (仅需数值范围比较)        (仅需数值范围比较)
```

| Capability | 职责 | 协议相关性 | 判定条件 |
|---|---|---|---|
| `ControllerStatusCheck` | 读取拧紧数据的原始状态字段，判断控制器层面的结果 | 协议相关（不同控制器状态字段不同） | Atlas: tightening_status + torque_status + angle_status；FIT: tightening_status；速动: tightening_status + X7 错误码 |
| `TorqueRangeCheck` | 扭矩值是否在螺栓配置的 min/max 范围内 | 协议无关（只读数值） | `torque_min <= measured_torque <= torque_max` |
| `AngleRangeCheck` | 角度值是否在螺栓配置的 min/max 范围内 | 协议无关（只读数值） | `angle_min <= measured_angle <= angle_max` |

**判定合并规则**：所有启用的检查全部 Pass → `judgeResult = OK`。任一 Fail → 管道中止，`judgeResult = NG`。不需要单独的合并 Capability——AND 是管道顺序执行的自然结论。

**客户配置灵活性**：可按需启用/禁用各检查。例如只需要控制器判定 → 仅启用 `ControllerStatusCheck`；需要全量 → 三项全开。AND 逻辑不变。

### 5.2 JudgmentStrategy 注册表

`ControllerStatusCheck` Capability 内部持有 `Map<DeviceType, JudgmentStrategy>`，根据拧紧数据来源的设备类型分发到对应的判定策略。

```
ControllerStatusCheck.execute(ctx):
    deviceType = ctx.currentOperationData.deviceType
    strategy = strategyRegistry.get(deviceType)
    if strategy == null:
        return Fail("不支持的设备类型: " + deviceType)
    result = strategy.judge(ctx.currentOperationData)
    ctx.tighteningStatus = result.status  // 供诊断/UI 展示
    return result.isOk ? Pass : Fail(result.reason)

// JudgmentStrategy 接口
interface JudgmentStrategy {
    // 读取拧紧数据中的控制器状态字段
    // 返回: status(OK/NG) + reason(诊断信息)
    judge(tighteningData) → JudgmentResult
}

// 内置策略实现

AtlasJudgment implements JudgmentStrategy:
    // 读取 tightening_status + torque_status + angle_status 三个字段
    // 全部 OK → OK；任一 NG → NG (携带具体失败字段)
    
FitJudgment implements JudgmentStrategy:
    // 读取 tightening_status 字段
    // OK → OK；NG → NG
    
SudongJudgment implements JudgmentStrategy:
    // 读取 tightening_status + X7 错误码字段
    // OK + 无错误码 → OK；NG/错误码非零 → NG
```

**注册方式**：

```
strategyRegistry = new HashMap<DeviceType, JudgmentStrategy>()
strategyRegistry.put(DeviceType.ATLAS_PF4000, new AtlasJudgment())
strategyRegistry.put(DeviceType.ATLAS_PF6000_OP, new AtlasJudgment())
strategyRegistry.put(DeviceType.FIT_FTC6, new FitJudgment())
strategyRegistry.put(DeviceType.SUDONG_SERIES, new SudongJudgment())
```

**扩展规则**：新增设备类型 = 实现 `JudgmentStrategy` 接口 + 注册到 `strategyRegistry`（通过 Spring 自动注入或手动注册）。`ControllerStatusCheck` Capability 和整个 OPERATION 管道无需修改。

**DeviceRegistry 不承载判定逻辑**。`ControllerStatusCheck` 直接从拧紧数据 DTO 的 `deviceType` 字段分发，不依赖 DeviceRegistry。

### 5.3 设备无关的扭矩/角度检查

`TorqueRangeCheck` 和 `AngleRangeCheck` 是纯数值比较，不关心数据来源的设备类型：

```
TorqueRangeCheck.execute(ctx):
    bolt = ctx.missionData.bolts[ctx.currentBoltIndex]
    if bolt.torque_min == null || bolt.torque_max == null:
        return Skip("螺栓未配置扭矩范围")
    data = ctx.currentOperationData
    ok = bolt.torque_min <= data.torque_value <= bolt.torque_max
    return ok ? Pass : Fail("扭矩值 {data.torque_value} 超出范围 [{bolt.torque_min}, {bolt.torque_max}]")

AngleRangeCheck.execute(ctx):
    bolt = ctx.missionData.bolts[ctx.currentBoltIndex]
    if bolt.angle_min == null || bolt.angle_max == null:
        return Skip("螺栓未配置角度范围")
    data = ctx.currentOperationData
    ok = bolt.angle_min <= data.angle_value <= bolt.angle_max
    return ok ? Pass : Fail("角度值 {data.angle_value} 超出范围 [{bolt.angle_min}, {bolt.angle_max}]")
```

这两个 Capability 完全协议无关，适用于所有设备类型。它们的判定结果与 `ControllerStatusCheck` 合并后形成最终的 `judgeResult`。

---

## 6. 设备与外部集成

### 6.1 设计原则

- 设备是**可发现的服务**，不是引擎的一部分
- 引擎和 Capability 通过统一接口访问设备，不直接依赖具体设备实现
- **设备可用性由两层决定**：配置文件（有什么）+ 运行时开关（是否启用）
- Capability 选择不做设备选择——它只定义业务流程，执行时才判断设备状态
- **乐观初始化 + 防御性执行**：工作台就绪时尽可能加载设备，成功后进入 READY。设备就绪后可能断连（时序窗），Capability 执行时必须再次检查设备状态
- **DeviceRegistry 不承载 OK/NG 判定逻辑**——判定由 `ControllerStatusCheck` + `JudgmentStrategy` 处理（详见 §5）

### 6.2 设备注册表

生命周期外部初始化，注入 Context（引用，非所有权。设备连接在用户登录期间持续存在）。

```
DeviceRegistry {
    // 按设备类别注册
    registerTool(toolId, ITool)
    registerArm(armId, IArm)
    registerIOBox(ioBoxId, IIOBox)
    registerArranger(arrangerId, IArranger)
    registerBuzzer(buzzerId, IBuzzer)
    registerSerialPort(portId, ISerialPort)
    registerPLC(plcId, IPLC)
    
    // 查询
    getTool(toolId) → ITool
    getArm(armId) → IArm
    // ...
    
    // 批量操作
    getAllTools() → List<ITool>
    getAllDevices() → List<IDevice>  // 供 DeviceConnectionMonitor 轮询
    lockAllTools()
    resetAllIO()
}
```

### 6.3 通用设备接口契约

```
interface IDevice {
    id()        → DeviceId
    type()      → DeviceType
    connect()   → ConnectionResult
    disconnect()
    isConnected() → Boolean
    onDisconnect(callback)    // 断连回调注册
}

interface ITool extends IDevice {
    sendPSet(psetId, token)     // 下发程序号（可取消）
    sendLock()                   // 锁定
    sendUnlock()                 // 解锁
    sendBarcode(barcode)
    onTighteningData(callback)  // 拧紧数据回调注册
    onCurveData(callback)       // 曲线数据回调注册
}

interface IArranger extends IDevice, IPreconditionCheckable {
    sendSignal(bolt)
}

interface ISetterSelector extends IDevice, IPreconditionCheckable {
    sendSignal(bolt)
}

interface IArm extends IDevice {
    startListening()
    stopListening()
    getLatestPosition() → Position   // 返回最新坐标（volatile 内存读，无 I/O，供 LockStateMonitor 轮询）
}

// 前置条件检查接口（供 DevicePreconditionMonitor 使用）
// IArranger 和 ISetterSelector 均扩展此接口
interface IPreconditionCheckable {
    checkPrecondition() → PreconditionResult
}
```

### 6.4 设备开关

设备开关属于运行时状态，由用户在工作台中切换，存储在 Context 可变部分（详见 §4.2 的 Context 定义中 `=== 设备开关 ===` 部分）。

### 6.5 Capability 执行时的设备判断

```
SendArrangerSignal.execute(ctx):
    arranger = ctx.deviceRegistry.getArranger(ctx.missionData.bolts[ctx.currentBoltIndex].arrangerId)
    
    if arranger == null:
        return Skip("当前螺栓未配置排列机")
    
    if !ctx.arrangerEnabled:
        return Skip("排列机功能未启用")
    
    // 发送信号 ...
    return Pass
```

### 6.6 外部系统出口

数据导出和外部系统通信通过 **Outbox 模式**（详见 §8）接入 FINALIZATION 阶段。Capability 将导出任务写入 `export_task` 表后立即返回 Pass，由后台 ExportWorker 异步执行。

```
// 数据导出（Capability 写入 Outbox）
ExportData Capability:
    execute(ctx):
        task = ExportTask(
            type = "standard_excel",
            missionRecordId = ctx.missionRecord.id,
            payload = serialize(ctx.missionRecord, ctx.tighteningData),
            status = PENDING
        )
        outboxRepository.insert(task)   // 写入 Outbox 表，同步 SQLite 操作
        return Pass                      // 立即返回，不等待导出完成

// 外部数据库/PLC 通知（同样通过 Outbox）
StoreToOuterDB Capability:
    execute(ctx):
        task = ExportTask(
            type = "outer_db_store",
            payload = serialize(ctx.missionRecord, ...),
            status = PENDING
        )
        outboxRepository.insert(task)
        return Pass
```

---

## 7. 引擎通信模型（平台无关）

### 7.1 核心思路

引擎是独立运行的逻辑核心，通过统一的消息通道与外界交互。外界在 WinForms 中是桌面 UI，在 Web 中是浏览器。

```
                          ┌─────────────────────┐
                          │   Lifecycle Engine    │
                          │   (平台无关)           │
                          │                       │
  ┌──────────┐            │   - 状态机流转         │
  │ 设备层    │◄──回调───►│   - Capability 管道    │
  │(工具/PLC) │            │   - 设备管理           │
  └──────────┘            │                       │
                          │   ◄── 消息通道 ──►    │
                          └──────────┬──────────┘
                                     │
                          ┌──────────┴──────────┐
                          │  表现层适配器         │ ← 平台相关
                          │                      │
                          │  WinForms: 消息→UI线程 │
                          │  Web:     消息→SSE    │
                          │            命令←HTTP   │
                          └──────────────────────┘
```

### 7.2 消息通道

引擎只定义消息语义，不定义传输方式。

**引擎 → 外界（Outbound）：**

```
OutboundMessage =
    | StateChanged(stage, subState, workplaceStatus, tighteningStatus?)   // 状态变更（含控制器原始判定状态）
    | BoltStatusChanged(boltIndex, status, tighteningStatus?)             // 螺栓状态（含控制器原始判定状态）
    | LockMessagesUpdated(messages)                     // 锁定消息
    | TighteningDataUpdated(dataTable, tighteningStatus?) // 拧紧数据（含控制器原始判定状态）
    
    | Notification(message, level)                      // 非阻断通知（预留通用通知类型，供 Capability 自定义通知使用）
    
    | ExportCompleted(result)                           // 导出完成
    
    | CapabilityLoadingProgress(capId, status)          // Capability 装载进度
                                                          // status: LOADING | LOADED | FAILED
    | DeviceStatusChanged(deviceId, connected)           // 设备连接状态变更
    
    | OutboxTaskFailed(taskId, error)                   // 异步导出任务失败通知
```

**外界 → 引擎（Inbound）：**

```
InboundMessage =
    | InboundCommand(                                     // 操作员/外部系统命令
    |     ActivateMission                                 // 激活
    |     Terminate(reason)                               // 终止
    |     ToggleSwitch(switch: SwitchName, value: Boolean) // 开关切换
    |     AdminPassword(password)                         // 管理员密码确认
    |     BarcodeScanned(boltId, value)                   // 物料码扫码
    |   )
    | EngineInternal(                                     // 引擎内部消息（Actor 自投递）
    |     SelfLoopSignal                                  // 自循环触发信号（OK 完成时投递）
    |     MonitorTick                                     // 持久监控器定时 tick（由引擎定时器周期投递）
    |   )
    | DeviceEvent(                                        // 设备回调（表现层适配器入队）
    |     TighteningData(data)                            // 拧紧数据到达
    |     CurveData(data)                                 // 曲线数据到达
    |     DeviceDisconnected(deviceId)                    // 设备断连
    |     DeviceConnected(deviceId)                       // 设备重连（网络恢复）
    |   )
```

- `SwitchName` 取值与 Context 的设备开关字段一一对应：`armLocating`、`arranger`、`setterSelector`、`autoLockTool`
- 设备回调来自设备线程，由表现层适配器封装为 `DeviceEvent` 后投递到 inbox

### 7.3 阻断式确认

`lockMsg` 是唯一的阻塞原语。Capability 需要阻止操作时，向 `lockMessages` 添加消息；LockStateMonitor 下一 tick（50ms）自动锁定工具。操作员完成所需操作后，对应的 Inbound 消息到达 → 引擎移除 lockMsg → LockStateMonitor 解锁。

**AdminConfirm**（NG 达到 password_need_time 阈值时）：

```
execute(ctx):
    ctx.lockMessages.add(AdminConfirmation)    // LockStateMonitor 下一 tick 锁工具
    sendOutbound(ShowAdminPasswordDialog)      // 表现层弹出密码框
    return Pass                                // 管道继续 → AWAITING_TIGHTENING

// 外部消息：管理员输入密码
case AdminPassword(password):
    if verifyPassword(password):
        ctx.lockMessages.remove(AdminConfirmation)  // 密码正确，解锁
    else:
        sendOutbound(ShowAdminPasswordDialog)        // 密码错误，重新弹窗
```

**BoltBarCodeCheck**（螺栓有物料码绑定时）：

```
execute(ctx):
    if ctx.barcodeObj.hasPartsBarcode(bolt.id):
        return Pass                              // 已扫码，直接过
    ctx.lockMessages.add(LockedBoltBarCode)       // 未扫码 → 加锁
    sendOutbound(PromptBarcodeScan(bolt.id))       // 提示扫码
    return Pass                                   // 管道继续

// 外部消息：扫码事件
case BarcodeScanned(boltId, value):
    if validateBarcode(boltId, value):
        ctx.lockMessages.remove(LockedBoltBarCode)
        ctx.barcodeObj.recordPartsBarcode(boltId, value)
```

**等待期间的行为**：工具被 LockStateMonitor 锁定，操作工无法拧紧。TighteningData 不会到达（工具锁着不能拧）。持久监控照常运行。操作员可手动终止 Mission。

### 7.4 平台映射

| 维度 | WinForms | Web |
|---|---|---|
| **Outbound** | BeginInvoke → UI 线程 | SSE 推送 → 浏览器 |
| **Inbound** | 按钮事件 → 直接调用引擎 | HTTP POST → Controller → 引擎 |
| **阻断确认** | 模态弹窗 → 管理员输入密码，引擎 remove lockMsg | SSE → 对话框 → HTTP POST 密码 → 引擎 remove lockMsg |
| **设备回调** | 设备线程 → 消息入队 | 同左（设备层不变） |

### 7.5 引擎并发模型

引擎采用 **Actor 模型**。每个 Engine 实例是一个 Actor：

- **私有状态**：`start(context)` 调用后 Context 所有权转移给引擎，外部不应再持有引用。引擎通过返回值（`LifecycleResult.Completed`）暴露结果
- **消息驱动**：设备数据、操作员命令都以消息形式到达，Actor 一次处理一条，内部串行

```
Engine Actor 循环:
  ┌──────────────────────────────────┐
  │ while (alive):                   │
  │   msg = await inbox.take(token)  │ ← 异步阻塞，等待消息
  │   process(msg, context)          │ ← 串行处理
  │                                  │
  │   process 内部:                   │
  │     - 设备数据 → 管道执行          │
  │     - 操作员命令 → 状态机流转       │
  │     - 同步推进子状态链             │
  └──────────────────────────────────┘
```
---

## 8. 异步 I/O 出站（Outbox Worker）

### 8.1 设计动机

FINALIZATION 阶段的数据导出涉及文件写入、外部数据库存储、PLC 信号发送等 I/O 操作。这些操作具有以下特点：

- **延迟高**：毫秒到秒级，不适合阻塞 Actor 线程
- **可重试**：临时失败（网络抖动、磁盘繁忙）可重试
- **非关键路径**：导出失败不影响拧紧数据的完整性（数据已在 SQLite）

### 8.2 Outbox 表结构

发动机旁路 I/O 通过 SQLite `export_task` 表实现：

**PRAGMA 要求**：SQLite 连接必须设置 `PRAGMA journal_mode=WAL;`，确保 Outbox 并发读写（Actor 线程写入 + ExportWorker 线程查询/更新）不产生锁冲突。

```
CREATE TABLE export_task (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    type             TEXT NOT NULL,          // 任务类型: "standard_excel", "outer_db_store", 
                                             //           "send_plc_result", "txt_export", ...
    mission_record_id INTEGER NOT NULL,      // 关联的 MissionRecord ID
    payload          TEXT NOT NULL,           // JSON 序列化的任务参数（含导出所需数据的引用）
    status           TEXT NOT NULL DEFAULT 'PENDING',  // PENDING | RUNNING | COMPLETED | FAILED
    retry_count      INTEGER NOT NULL DEFAULT 0,       // 当前已重试次数
    max_retries      INTEGER NOT NULL DEFAULT 3,        // 最大重试次数
    error_message    TEXT,                             // 最后一次失败的错误信息
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at     TIMESTAMP,
    FOREIGN KEY (mission_record_id) REFERENCES mission_record(id)
);
```

### 8.3 ExportWorker

```
ExportWorker（单线程，非 Actor，由 Spring @Scheduled 管理线程和事务）:

  loop:
    sleep(5s)                                    // 可配置轮询间隔
    
    tasks = outboxRepository.findPending(limit: 10)
    
    for task in tasks:
        outboxRepository.updateStatus(task.id, RUNNING)
        
        try:
            exporter = ExporterRegistry.get(task.type)
            result = exporter.execute(task.payload)  // 实际 I/O 操作
            
            if result.success:
                outboxRepository.updateStatus(task.id, COMPLETED)
            else:
                handleFailure(task, result.error)
                
        catch Exception e:
            handleFailure(task, e.message)

handleFailure(task, error):
    task.retry_count += 1
    if task.retry_count < task.max_retries:
        outboxRepository.updateRetry(task.id, task.retry_count, error.message)
        // 下一次轮询自动重试
    else:
        outboxRepository.markFailed(task.id, error.message)
        notifyAdmin(task.id, error.message)      // 通知管理层：异步导出任务永久失败
        sendOutbound(OutboxTaskFailed(task.id, error.message))
```

### 8.4 Capability 交互模式

```
ExportData.execute(ctx):
    // missionRecord 空值防御（极端路径下可能未创建）
    if ctx.missionRecord == null:
        log("ExportData: missionRecord 为空，跳过导出")
        return Skip("missionRecord 为空")
    task = ExportTask(
        type = "standard_excel",
        missionRecordId = ctx.missionRecord.id,
        payload = jsonSerialize({
            missionId: ctx.missionData.id,
            missionRecordId: ctx.missionRecord.id,
            tighteningDataIds: ctx.tighteningData.map(d => d.id),
            exportFormat: ctx.missionData.exportConfig.format
        }),
        status = PENDING
    )
    outboxRepository.insert(task)   // 同步 SQLite 插入（微秒级）
    return Pass                      // 立即返回，Actor 继续
```

**关键设计决策**：
- `export_task` 和 `mission_record` 在同一 SQLite 数据库中，保证事务一致性
- Capability 只做 SQLite 写入（微秒级），不做 I/O 阻塞
- ExportWorker 独立于 Actor 线程，不影响状态机流转
- 失败任务自动重试（至多 N 次），永久失败通知管理员
- 轮询间隔可配置（默认 5s），适用于异步导出场景的时间容忍度

### 8.5 支持的任务类型

| type | 执行器 | 说明 |
|---|---|---|
| `standard_excel` | `StandardExcelExporter` | 标准 Excel 导出 |
| `outer_db_store` | `OuterDatabaseStorer` | 写入外部数据库（GLB） |
| `send_plc_result` | `PlcResultSender` | PLC 作业结果通知（GLB） |
| `txt_export` | `TxtExporter` | TXT 格式数据导出（含日聚合追加模式配置） |

新增任务类型 = 实现 `Exporter` 接口 + 注册到 `ExporterRegistry`。Capability 只需指定 `type` 名称，无需感知具体实现。

---

## 9. 生命周期附着点目录

### VALIDATION

| 附着点 (A→B) | 时机 | 说明 | 可插入的 Capability 示例 |
|---|---|---|---|
| `→ VALIDATING` | Before | 验证开始前 | 验证前置准备 |
| `VALIDATING → PASSED` | Before | 判定通过前 | `ScrewBitCounterCheck`（批头计数器） |
| `VALIDATING → PASSED` | After | 判定通过后 | 校验通过日志 |
| `VALIDATING → FAILED` | Before | 判定失败时 | 失败原因记录、通知推送 |
| `VALIDATING → FAILED` | After | 失败后 | 失败原因展示给操作员 |

验证失败→IDLE，不进入 FINALIZATION，不导出数据，不创建 MissionRecord。工具锁定行为由 §2.1 的 `autoLockToolEnabled` 配置决定。

### ACTIVATION

| 附着点 (A→B) | 时机 | 说明 | 可插入的 Capability 示例 |
|---|---|---|---|
| `→ PREPARING` | Before | 准备阶段前 | NG 计数重置、额外初始化 |
| `→ PREPARING` | After | 准备完成 | 准备日志记录 |
| `PREPARING → ACTIVATING` | After | 已进入激活阶段 | 激活启动日志 |
| `→ ACTIVATING` | Before | 激活执行前 | 激活前置检查 |
| `→ ACTIVATING` | After | 激活执行后（进入 ACTIVE 前不通知） | 激活进度日志 |
| `→ ACTIVATED` | Before | 进入已激活状态前 | 激活完成前置校验、自定义激活逻辑 |
| `→ ACTIVATED` | After | 激活完成 | 通知 MES 系统、自定义上报 |
| `→ ACTIVATION_FAILED` | Before | 激活失败时 | 失败原因详情 |
| `→ ACTIVATION_FAILED` | After | 激活失败后 | 重置部分状态（读取 `activationCheckpoint` 做精确回滚） |

激活失败→IDLE，不进入 FINALIZATION。CreateMissionRecord 是 ACTIVATING 管道的最后一个 Capability，因此激活失败不会产生 MissionRecord。

### OPERATION

| 附着点 (A→B) | 时机 | 说明 | 可插入的 Capability 示例 |
|---|---|---|---|
| `→ SWITCH_BOLT` | After | 螺栓切换后（信号/PSet 已发送、物料码已校验） | 切换通知 |
| `SWITCH_BOLT → AWAITING_TIGHTENING` | Before | 进入等待拧紧前 | 等待前置准备 |
| `→ AWAITING_TIGHTENING` | After | 开始等待数据时 | 工具连接状态快照 |
| `→ TIGHTENING_RECEIVED` | Before | 数据接收时 | 数据格式校验/转换 |
| `TIGHTENING_RECEIVED → JUDGING` | After | 已进入判定 | 判定开始日志 |
| `→ JUDGING` (OK 路径) | Before | OK 判定前 | 自定义 OK 条件 |
| `→ JUDGING` (NG 路径) | Before | NG 判定前 | 自定义 NG 诊断规则 |
| `JUDGING → STORING` (OK) | Before | 存储 OK 数据前 | 数据预览 |
| `JUDGING → STORING` (OK) | After | 已进入 OK 存储 | OK 存储日志 |
| `JUDGING → STORING` (NG) | Before | 存储 NG 数据前 | `AdminConfirm`（管理员确认，螺栓级和任务级复用同一个 Capability，反松由操作员自行决定） |
| `JUDGING → STORING` (NG) | After | 已进入 NG 存储 | NG 存储日志 |
| `STORING → ADVANCING` (OK) | Before | 进入推进子状态前 | 推进前置校验 |
| `STORING → ADVANCING` (OK) | After | 进入推进子状态后 | 自定义推进规则（跳螺栓、指定顺序） |
| `ADVANCING → SWITCH_BOLT` (下一螺栓/下一面) | Before | 螺栓切换前 | 螺栓/面切换通知 |
| `ADVANCING → ALL_BOLTS_DONE` | Before | 全部完成前 | 最终遗漏检查 |
| `STORING(NG) → SWITCH_BOLT` (重试) | Before | 重试前 | 重试次数记录 |
| `STORING(NG) → SWITCH_BOLT` (重试) | After | 已进入螺栓切换 | 重试起始日志 |
| `STORING(NG) → ALL_BOLTS_DONE` (终止) | Before | NG达上限终止前 | 管理员确认日志 |
| `STORING(NG) → ALL_BOLTS_DONE` (终止) | After | 已决终止 | 终止日志 |
| `→ ALL_BOLTS_DONE` | Before | 全部完成判定 | 最后校验（遗漏检查） |
| `ALL_BOLTS_DONE → FINALIZATION` | Before | 进入收尾阶段前 | 操作完成确认 |

### FINALIZATION

| 附着点 (A→B) | 时机 | 说明 | 可插入的 Capability 示例 |
|---|---|---|---|
| `→ CLEANING_TASKS` | Before | 清理后台任务前 | 等待剩余数据落库 |
| `→ CLEANING_TASKS` | After | 清理完成 | 清理确认日志 |
| `CLEANING_TASKS → LOCKING_TOOLS` | Before | 工具锁定前 | 工具状态快照 |
| `→ LOCKING_TOOLS` | After | 工具锁定后 | 工具状态确认 |
| `LOCKING_TOOLS → RESETTING_STATE` | Before | 状态重置前 | 状态备份 |
| `LOCKING_TOOLS → RESETTING_STATE` | After | 已进入状态重置 | 日志记录 |
| `→ RESETTING_STATE` | Before | 状态重置时 | 自定义状态清理、回滚补偿（读取 `activationCheckpoint`） |
| `→ RESETTING_STATE` | After | 状态重置后 | 重置结果通知 |
| `→ EXPORTING` | Before | 数据导出前（写入 Outbox） | 数据完整性检查、外部数据库写入（同为 Outbox 任务） |
| `→ EXPORTING` | After | 数据导出后 | 导出成功通知、文件上传 |
| `EXPORTING → RELEASING` | Before | 资源释放前 | 最终确认检查 |
| `→ RELEASING` | After | 资源释放后 | 资源释放确认 |
| `RELEASING → SelfLoop=true` (OK 结束) | Before | 自循环跳转前 | 日志记录 |
| `RELEASING → 生命周期结束` | Before | NG 或异常结束时 | 最终状态记录、通知推送 |

**FINALIZATION 有三个入口**：(1) OPERATION 正常结束（ALL_BOLTS_DONE）；(2) OPERATION NG 达上限终止；(3) 触发阶段 SkipScrew=true 快速通道。VALIDATION 和 ACTIVATION 的失败不进入 FINALIZATION，直接返回 IDLE。设备临时故障（排列机/套筒/PSet 信号失败）仅加 lockMsg 锁工具，不触发 FINALIZATION。

---

## 附录 A: 多设备并行支持（未来方向）

当前设计假设单工具串行拧紧（`currentBoltIndex` 单值，`boltStates[]` 扁平数组）。如需支持多工作站/多工具并行操作同一任务的不同螺栓，需将 Context 扩展为：

- `workstationStates[workstationId]`：每工作站独立的 `currentBoltIndex`、锁消息、错误信息
- `boltStates[boltId].workstationId`：每螺栓归属的工作站
- `DeviceEvent` 消息携带 `workstationId`，引擎按工作站路由到对应的子状态通道

各工作站的 OPERATION 子状态独立推进，互不阻塞。任务级决策（NG 达上限、全部螺栓完成）汇总所有工作站状态后判定。

此方向待产品需求明确后纳入 Capability 全局池和本地配置设计。

---

## 附录 B: NG 概念的层次

文档和代码中 "NG" 出现在三个上下文中，需区分：

| 层次 | 含义 | Context 承载字段 | 说明 |
|---|---|---|---|
| **拧紧数据 NG** | 工具控制器的原始判定 — `TighteningStatus.NG` | `judgeResult`（由 ControllerStatusCheck + TorqueRangeCheck + AngleRangeCheck 合并得出） | 拧紧数据 NG **导致**螺栓状态变为 ERROR（因果关系，非等同） |
| **螺栓 NG** | 单螺栓拧紧不合格 — `BoltStatus.ERROR` | `boltStates[i].status` | 拧紧数据 NG 触发，一次 NG 触发管理员确认后可重试（反松由操作员自行决定） |
| **任务 NG** | 任务整体不合格 — `FINISHED_NG` | `missionRecord.missionResult` | NG 达上限等导致 |

---

## 附录 C: Web 安全待考虑事项

以下事项当前设计未展开，供后续 Web 版本设计时参考：

| 层次 | 待考虑事项 | 说明 |
|---|---|---|
| 传输安全 | HTTPS | 明文 HTTP 下 Mission 激活/终止可被中间人篡改 |
| 认证 | 请求必须携带身份凭据 | JWT / API Key / mTLS — 需根据部署环境选择 |
| 授权 | 不同命令需要不同角色 | 操作员角色可以激活，管理员角色才能终止/确认 |
| 审计 | 所有 HTTP 触发的操作记录操作者身份 | 与 `missionRecord` 关联 |
| CSRF | 防止跨站请求伪造 | 对浏览器场景必要；MES 系统间 API 可通过 IP 白名单缓解 |
| 消息完整性 | 激活/终止/确认命令防篡改 | 签名/校验和 |
| 重放攻击 | 同一命令不能被重放多次 | 时间戳 + nonce / 幂等键 |
| 超时 | HTTP 请求和 Engine 响应的超时语义 | SSE 连接保活、请求超时重试策略 |
| 身份传递 | InboundCommand 应携带身份上下文 | `ActivateMission { identity: IdentityContext }` 等 |

---

## 附录 D: 术语对照表

| 分析文档名称 | 设计文档名称 | 说明 |
|---|---|---|
| `WorkplaceProcessStatus` | `workplaceStatus` | 工作台操作锁定状态（不含 FINISHED_OK/NG） |
| `IsMissionSelfLoopingModeEnabled` | `shouldSelfLoop` | 自循环开关（仅 OK 触发自循环） |
| `ActivateMissionAutomatically()` | `SELF_LOOP` 触发信号 | 实现方式 vs 抽象概念 |
| `_activeMissionCts` | `context.cancellationToken` | 具体实现 vs 抽象概念 |
| `CheckCanActivateMission()` | `CheckCanActivate` | 激活前置条件门控 |
| `TerminateMission()` | FINALIZATION 阶段 + `interrupt()` | 过程化调用 vs 声明式阶段 |
| `StartLockCheckingTask` | `LockStateMonitor`（持久监控） | 一次性任务改为 OPERATION 阶段持久监控器 |
| `StartArrangerTask` | `DevicePreconditionMonitor(arranger)` 持久监控 | 排列机监控任务 |
| `StartSetterSelectorTask` | `DevicePreconditionMonitor(setter_selector)` 持久监控 | 套筒选择器监控任务 |
| `BoltNGConfirmPopUp` / `MissionNGConfirmPopUp` | `AdminConfirm` Capability（复用同一个） | 管理员密码确认（lockMsg 阻塞） |
| `SkipScrewPoints` | `SkipScrewCheck`（触发阶段，快速通道门控） | 快速完成快捷路径（跳过所有流程，直接 Completed(OK)） |
| 客户 Profile / 装配清单 | Capability 全局池 + 本地配置 | Profile 继承链改为全局池过滤 |
| OKNGJudge | `ControllerStatusCheck` + `TorqueRangeCheck` + `AngleRangeCheck` | 单 Capability 拆分为三个独立判断 |
| TCS / ResumeToken / Suspended | lockMsg + 密码验证消息 ｜ 挂起恢复机制改为 lockMsg 阻塞 + Inbound 消息驱动
| FAILED → FINALIZATION | FAILED → IDLE | 验证/激活失败不再进入 FINALIZATION |
| ACTIVATION_FAILED → FINALIZATION | ACTIVATION_FAILED → IDLE | 激活失败不再进入 FINALIZATION |
