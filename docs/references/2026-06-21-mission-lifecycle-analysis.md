# Mission 生命周期完整分析

> 分析日期: 2026-06-21  
> 代码版本: v1.6.x  
> 分析范围: Mission 从激活到完成（OK/NG）的完整流程  
> 覆盖版本: 标准版、SCII/SCII_XT（2.0.x+）、GLB、YMT（实际在用版本）
>
> **修订**: 2026-06-22 — 修正拼写错误、行号归属、补充自循环 NG 说明、澄清文件索引

---

## 1. 概述

OperationGuidance 是一个多客户版本的 WinForms 操作引导系统——每个版本面向不同客户独立部署。目前实际在用的版本：标准版、SCII/SCII_XT、GLB、YMT。Mission（任务）是核心业务实体，代表一个包含多个螺栓拧紧点位的产品装配任务。

本文档追踪 Mission 从被用户选择、激活、执行拧紧、到最终完成（OK 或 NG）的完整生命周期，包括所有异步任务、取消令牌管理、以及各版本的差异化行为。已废弃或不使用的版本（YF/WHYC/TZYX）和多设备独立模式不在本文档范围内。

> **阅读指引**: 文档中使用的专用术语（如 `_activeMissionCts`、`_backgroundTaskCts`、`_checkCts`）详见 §10 取消令牌管理；行号引用格式 `:1234` 指 `AWorkplaceContentPanel.cs` 的行号（除非另有标注）。

---

## 2. 架构概览

### 2.1 继承体系

```
AWorkplaceMissionView<T, V>            (抽象泛型基类, 视图外观层)
  ├── WorkplaceMissionView             (标准版)
  ├── WorkplaceMissionView_SCII        (SCII/SCII_XT 客户版, 批头计数器)
  ├── WorkplaceMissionView_GLB         (GLB 客户版, PLC 集成)
  └── WorkplaceMissionView_YMT         (YMT 客户版, 日聚合导出)

AWorkplaceContentPanel                 (抽象基类, 3053 行业务逻辑)
  ├── WorkplaceContentPanel            (标准版)
  ├── WorkplaceContentPanel_SCII       (SCII/SCII_XT 客户版: 批头计数器, SkipScrew)
  ├── WorkplaceContentPanel_GLB        (GLB 客户版: PLC + 外部DB)
  └── WorkplaceContentPanel_YMT        (YMT 客户版: 日聚合导出, 继承标准版)
```

- **AWorkplaceMissionView<T,V>**: 外观层（Facade），负责 Mission 列表展示、用户选择、创建 Workplace 面板。View 级别的 Dispose 管理 `_checkCts`
- **AWorkplaceContentPanel**: 所有业务逻辑的承载者，包含激活/终止/拧紧处理/设备管理/导出/扫码
- **T**: `AWorkplaceContentPanel` 子类，由 `GetWrokplacePanel()` 工厂方法创建
- **V**: `WorkplaceTopBar` 子类

### 2.2 两阶段构造

WorkplaceContentPanel 构造分两阶段：

1. **构造函数**（同步）：初始化所有 UI 组件 → `ActionAfterAllInitialized()`
2. **OnHandleCreated**（异步）：
   - `GetBarCodeMatchingRules()` — 加载扫码匹配规则
   - `LoadDevicesAsync()` — 加载工具/力臂/IO 等设备
   - `InitializeAfterHandelCreated()` — 子类自定义初始化
   - `ActivateMissionAutomatically()` — 自循环模式自动激活

---

## 3. 状态机

### 3.1 WorkplaceProcessStatus（工作台状态）

**文件**: `Constants/WorkplaceProcessStatus.cs`

```
                        ┌─── 排列机/套筒超时 ───┐
                        ▼                      ▼
UNACTIVATED ──► ACTIVATED ──► OPERATION_ENABLE ◄──► OPERATION_DISABLE
                                               │                  │
                                               ▼                  ▼
                                          FINISHED_OK        FINISHED_NG
```

> 注：`ACTIVATED → FINISHED_NG` 的快捷边为排列机/套筒选择器在激活后第一个周期即超时的情况（§9.2/§9.3）。

| 状态 | 含义 | 设置位置 |
|---|---|---|
| `UNACTIVATED` | 未激活（初始状态） | 构造时默认; TerminateMission(UNACTIVATED) |
| `ACTIVATED` | 已激活 | ActionAfterActivatingMission |
| `OPERATION_ENABLE` | 允许操作（无锁定） | StartLockCheckingTask（lockMsgs 为空） |
| `OPERATION_DISABLE` | 禁止操作（工具锁定） | StartLockCheckingTask（lockMsgs 非空） |
| `FINISHED_OK` | 完成 - 合格 | TerminateMission(FINISHED_OK) |
| `FINISHED_NG` | 完成 - 不合格 | TerminateMission(FINISHED_NG) |

### 3.2 BoltStatus（螺栓状态）

**文件**: `Constants/BoltStatus.cs`

```
DEFAULT ──► WORKING ──► DONE (OK)
                  │
                  └──► ERROR (NG) ──► WORKING (反松后重新拧紧)
```

| 状态 | 含义 |
|---|---|
| `DEFAULT` | 初始/未激活时 |
| `WORKING` | 当前正在拧紧（由 ChangeBoltStatusToWorking 设置） |
| `DONE` | 拧紧 OK |
| `ERROR` | 拧紧 NG |

### 3.3 关键内部标志位

| 字段 | 类型 | 位置 | 含义 |
|---|---|---|---|
| `_activated` | `volatile bool` | AWorkplaceContentPanel:36 | mission 是否处于激活状态 |
| `_isActivating` | `bool` | :164 | 正在执行 ActivateMission 流程中 |
| `_pendingBoltBarCodePopup` | `bool` | :165 | 激活完成后需重新打开扫码弹窗 |
| `_needLoosening` | `bool` | :57 | 是否需要反松（NG 后先反松再重拧） |
| `_missionNGAdminConfirmed` | `volatile bool` | :60 | 管理员是否已确认 NG |
| `_exportTriggered` | `int` | :65 | 原子锁，确保导出只触发一次 |

---

## 4. Mission 选择与 Workplace 初始化

### 4.1 启动路径

**AWorkplaceMissionView.Initialize(bool operatorOpenning)**:

```
operatorOpenning = true  → 直接进入工作台 (OpenWorkplaceViewDirectly)
operatorOpenning = false → 显示 Mission 列表 (OpenMissionListView)
```

> `operatorOpenning` 由 `MainForm` 中 `AfterLogin` 菜单循环根据角色配置传入——管理员通常看到 Mission 列表，产线操作工通常直接进入工作台。

### 4.2 Mission 列表展示

`OpenMissionListView()` 创建 `MissionListPanel`，包含：
- "选择任务" 标题
- "直接进入工作台" 按钮（跳过选择）
- Mission 卡片列表

当 View 变为可见时（`VisibleToTrue`），调用 `CheckAndDisplayAsync()`:
- 取消旧的 `_checkCts`，创建新的
- `FetchDataAsync()` — `Task.Run` 中调用 `QueryProductMissionList` API
- 成功后调用 `_missionListPanel.RefreshMissionBlocks()`
- RefreshMissionBlocks 使用两阶段加载：骨架 UI → 后台异步加载封面图（`SemaphoreSlim(4)` 控制并发）

### 4.3 进入工作台

用户选择 Mission → `OpenWorkplaceView(missionId)`:

```
OpenWorkplaceView(missionId)
  ├── Dispose 旧的 _workplacePanel
  ├── 创建 CustomTabPanel (pagePanel)
  ├── 创建 WorkplaceTopBar (V 实例)
  ├── _workplacePanel = GetWrokplacePanel(missionId, topBar)  ← 各版本工厂方法
  ├── topBar.Workplace = _workplacePanel
  ├── pagePanel 添加 topBar + _workplacePanel
  └── 隐藏主面板，显示工作台面板
```

`GetWrokplacePanel` 内部调用 `new WorkplaceContentPanel_XXX(missionId, resetMissionName)`:
1. 查询 `QueryProductMissionDetail` 获取完整 Mission 数据
2. 初始化 9 个 UI 组件（扫码区、产品图、用户信息、状态面板、拧紧数据、任务信息、数据表格、设备块、时间显示）
3. `ActionAfterAllInitialized()` — 子类扩展点
4. `OnHandleCreated` 时触发异步加载设备 + 自动激活检查

---

## 5. Mission 激活流程

### 5.1 激活触发方式

| 方式 | 触发路径 |
|---|---|
| **手动扫码激活** | 扫码弹窗 "激活任务" 按钮 → `CheckCanActivateMission()` → `ActivateMission()` |
| **自循环模式** | `ActivateMissionAutomatically()` → 延迟 500ms → `ActivateMission()` |
| **USB 扫码器** | `ActivateMissionAutomatically()` → 打开扫码弹窗 → 扫码完成后激活 |
| **PLC 条码自循环** (GLB) | PLC 读取条码 → `ActionAfterRecevingBarCode()` → 自动填充/激活 |

### 5.2 ActivateMission() 完整步骤链

**文件**: `AWorkplaceContentPanel.cs:1314`

```
ActivateMission()  [public virtual async void]
│
├─ [Step 0] 清理旧的取消令牌
│   ├── 遍历 _backgroundTaskCts → Cancel() → Dispose()
│   ├── _backgroundTaskCts.Clear()
│   ├── _activeMissionCts.Cancel() → Dispose()
│   └── _activeMissionCts = new CancellationTokenSource()
│
├─ [Step 1] PrepareBeforeActivatingMission()           [protected virtual]
│   ├── _currentSideIndex = 0 → ChangeSideAndInvalidate()
│   ├── 重新检查 _locating_enabled
│   ├── WorkingProcessPanel: TightenOrLoosen = TIGHTENING
│   ├── 清空 NGReasons
│   ├── _sumBoltDone = 0, _needLoosening = false
│   ├── _exportTriggered = 0
│   └── 排序所有螺栓（按 serial_num）→ 重置到 DEFAULT → NgTimes = 0
│
├─ [Step 2] await ValidationBeforeActivatingMission()  [protected virtual async Task<bool>]
│   ├── 检查: 站点配置存在
│   ├── 检查: 所有螺栓的 workstation 都有 tool
│   ├── 检查: 力臂定位启用时所有螺栓都有 arm
│   ├── 检查: 排列机配置（specification > 0 时 arranger_id 有效）
│   ├── 检查: 套筒选择器配置（bit_specification > 0 时 setter_selector_id 有效）
│   └── 子类额外验证（见 5.3）
│
├─ [Step 3] if (验证通过):
│   ├── _activated = true        ← 必须先设置（后续 CheckBoltBoundPartsBarCode 依赖）
│   ├── _isActivating = true
│   │
│   ├─ [Step 4] InitializeBeforeActivatingMission()    [protected virtual]
│   │   ├── SwitchBolt(0) → 获取第一个螺栓
│   │   ├── _currentWorkingBolt = boltButton           ← 先缓存
│   │   ├── ChangeBoltStatusToWorking(boltButton):
│   │   │   ├── ClearLockMsgs() / ClearInformationMsgs()
│   │   │   ├── SendSignalToArranger(bolt) — 向排列机发信号
│   │   │   ├── SendSignalToSetterSelector(bolt) — 向套筒选择器发信号
│   │   │   ├── CheckBoltBoundPartsBarCode(bolt) — 检查物料码绑定
│   │   │   │   └── 若未扫码 + _isActivating → _pendingBoltBarCodePopup = true
│   │   │   ├── 异步 SendPSet(bolt, toolTask, pset) — 下发程序号到工具
│   │   │   └── bolt.BoltStatus = WORKING
│   │   └── 初始化套筒选择器 PositionsInUse
│   │
│   ├─ [Step 5] await ActionAfterActivatingMission()   [protected virtual async Task]
│   │   ├── 重置扫码计数 (ProductScanCount=0, PartsScanCount=0)
│   │   ├── 创建 _missionRecord（初始 mission_result = NG）
│   │   │   └── _apis.AddOrUpdateMissionRecord() — 写入数据库
│   │   ├── 向 PF 系列工具发送条码 (SendBarcode)
│   │   ├── 力臂定位启用:
│   │   │   ├── 锁定所有工具 ForceSendLock()
│   │   │   ├── 注册坐标监听 ActionAfterCoordinatesReceived
│   │   │   └── WorkplaceProcessStatus = ACTIVATED
│   │   ├── 力臂定位未启用:
│   │   │   └── WorkplaceProcessStatus = ACTIVATED
│   │   ├── await Task.Delay(500) — 等待设备稳定
│   │   ├── StartLockCheckingTask()       ← 50ms 循环
│   │   ├── StartArrangerTask()           ← 100ms 循环（排列机就位检查）
│   │   └── StartSetterSelectorTask()     ← 100ms 循环（套筒选择器检查）
│   │
│   ├── _isActivating = false
│   ├── _barCodePopUpForm?.Dispose()
│   └── if (_pendingBoltBarCodePopup) → OpenBarCodePopUpForm(null)
│
└─ [Step 3 失败分支]
    ├── _currentWorkingBolt = null
    ├── _currentWorkingBoltIndependence.Clear()
    └── TerminateMission(UNACTIVATED)
```

### 5.3 各版本额外验证

| 版本 | 额外验证 | 位置 |
|---|---|---|
| **SCII/SCII_XT** | 批头计数器检查（CountScrewBitUsedTime），超限 → 管理员密码窗 → 重置计数器 → 递归重新验证 | SCII.cs:1132 |
| **SCII/SCII_XT** | SkipScrewPoints == true → 直接跳过所有验证返回 true | SCII.cs:1132 |
| **GLB** | 无覆盖，使用基类 | - |
| **YMT** | 无覆盖，使用基类 | - |

---

## 6. 拧紧执行过程

### 6.1 数据接收入口

工具控制器拧紧完成后，回调 `DoAfterRecevingTighteningDataAsync(TighteningData data, int deviceId)`

**状态守卫**: `if (!_activated) return;` — 未激活时忽略所有数据

### 6.2 数据接收处理流程（基类）

```
DoAfterRecevingTighteningDataAsync(data, deviceId)                [:2316]
│
├── BeginInvoke 回到 UI 线程
│
├── [Guard] if (!_activated) return
├── 获取 ToolTask → ForceSendLock()（力臂定位模式）
├── 确定当前螺栓: _currentWorkingBolt
│
├── Side 自动切换: 如果螺栓所属面 ≠ 当前显示面 → ChangeSideAndInvalidate()
│
├── 数据转换: TighteningData → OperationDataDTO
│   ├── 填充 workstation_id/name, tool_name/ip/type
│   ├── 填充 bolt_serial_num, mission_record_id, vin_number
│   └── 填充 arm_position（力臂坐标）
│
├── if (data.result_type == TIGHTENING):                          [:2413]
│   │
│   ├── 分层 OK 检查（全部通过才算 OK）:
│   │   ├─ [第 1 层] ① data.tightening_status == OK               ← 入口检查
│   │   │   └── 若 ① 失败，进入错误诊断:
│   │   │       ⑥ 速动 X7 错误码检查（滑丝/浮锁/扭矩不良等）
│   │   │       ② data.torque_status == OK (扭矩状态)
│   │   │       ③ data.angle_status == OK (角度状态)
│   │   │       ※ ②③⑥ 仅在 tightening_status != OK 时执行
│   │   ├─ [第 2 层] ④ 扭矩范围: torque_min ≤ torque ≤ torque_max ← 独立检查
│   │   └─ [第 3 层] ⑤ 角度范围: angle_min ≤ angle ≤ angle_max ← 独立检查
│   │       ※ ④⑤ 无论 tightening_status 如何都会执行
│   │
│   ├── [OK 路径]:
│   │   ├── _needLoosening = false (恢复正常拧紧模式)
│   │   ├── 清空 NGReasons 和错误信息
│   │   ├── currentBolt.BoltStatus = DONE
│   │   ├── 计算下一个螺栓（跳过已 DONE 的）
│   │   ├── StoreTighteningData(dataDTO) — 拧紧状态=OK
│   │   ├── 下一螺栓存在 → SwitchBolt + ChangeBoltStatusToWorking
│   │   ├── 下一面存在 → ChangeSideAndInvalidate + 切换到新面第一个螺栓
│   │   └── 全部完成:
│   │       ├── _missionRecord.mission_result = OK
│   │       ├── AddOrUpdateMissionRecord()
│   │       ├── 挑战任务检查: AddChallengeResult(MISSION_OK)
│   │       └── TerminateMission(FINISHED_OK)
│   │
│   └── [NG 路径]:
│       ├── currentBolt.BoltStatus = ERROR
│       ├── currentBolt.NgTimes++
│       ├── 设置 NGReasons 错误描述
│       ├── dataDTO.tightening_status = NG
│       ├── StoreTighteningData(dataDTO)
│       └── ForceSendUnlock()（力臂定位模式，解锁让操作工处理）
│
└── if (data.result_type == LOOSENING):                            [:2609]
    ├── _needLoosening = false
    ├── 扭矩/角度恢复默认颜色
    ├── 清空 NGReasons
    └── 可选存储反松数据
```

### 6.3 StoreTighteningData — 数据存储

```
StoreTighteningData(operationDataDTO)                              [:2670]
├── await _storeTighteningDataLock.WaitAsync()  ← SemaphoreSlim(1,1) 排队
├── StoreTighteningDataInternal():
│   ├── StoreDataToDatabaseAsync() — API 写入 operation_data 表
│   │   └── currentOperationData = 返回的 DTO（含自增 id）
│   ├── 数据转换: OperationDataDTO → OperationDataVO
│   ├── _tighteningDataVOs.Add(dataFormatted)  ← ConcurrentBag 线程安全
│   └── BeginInvoke → RefreshTighteningDataPanel() — 更新 DataGridView
└── _storeTighteningDataLock.Release()
```

### 6.4 曲线数据接收

`DoAfterRecevingCurveDataAsync` — 处理 PF 系列工具的拧紧曲线数据:
- 等待 `currentOperationData` 可用（最多 10 秒，200ms × 50 次）
- 转换 `CurveDataTemp → CurveDataDTO`
- 关联 `operation_data_id`
- API 写入 `curve_data` 表

---

## 7. OK/NG 判定逻辑

### 7.1 各版本 NG 处理差异矩阵（拧紧数据处理范围）

> 此矩阵限定于 `DoAfterRecevingTighteningDataAsync` 中的拧紧数据 NG 处理。排列机/套筒选择器超时导致的 `FINISHED_NG` 在所有版本均可用（基类 `StartArrangerTask`/`StartSetterSelectorTask`），见 §9.2、§9.3、§11。

| 能力 | 基类 | SCII/SCII_XT | GLB | YMT |
|---|---|---|---|---|
| `max_ng_num` 检查 | ❌ | ✅ | ✅ | ❌ |
| `password_need_time` 检查 | ❌ | ✅ | ✅ | ❌ |
| 反松模式 (`LOOSENING`) | ❌ | ✅ | ✅ | ❌ |
| 管理员密码蜂鸣器 | ❌ | ✅ | ❌ | ❌ |
| NG 终止任务 | ❌ | ✅ | ✅ | ❌ |

### 7.2 SCII/GLB NG 处理（最完整）

> **注意**: `max_ng_num = 0` 时永不触发上限终止（代码条件为 `!= 0`），该值来自 `ProductMissionDTO`，默认值=3。

**SCII NG 处理流程**:

```
螺栓 NG:
├── bolt.BoltStatus = ERROR
├── bolt.NgTimes++
│
├── if (max_ng_num != 0 && NgTimes >= max_ng_num): ← _mission.max_ng_num
│   ├── StoreTighteningData(NG)
│   ├── TerminateMission(FINISHED_NG)
│   └── MissionNGConfirmPopUp("NG次数达上限，任务失败，请输入管理员密码")
│       └── SCII override: 额外激活蜂鸣器 (BuzzerController)
│
└── else (未达上限):
    ├── _needLoosening = true             ← 进入反松模式
    ├── WorkingProcessPanel.TightenOrLoosen = LOOSENING
    ├── StoreTighteningData(NG)
    │
    └── if (password_need_time != 0 && NgTimes >= password_need_time):
        ├── AddLockMsg(AdminConfirmation)  ← 锁定工具
        └── BoltNGConfirmPopUp()           ← 弹出管理员密码窗(不允许取消)
            └── 内部调用 OpenAdminPasswordPopUpForm(msg, allowCancel: false)
```

> **GLB OK 路径**: GLB **完全重写**了 `DoAfterRecevingTighteningDataAsync`（不调用 base），OK 路径逻辑与基类功能等价但实现独立。主要差异：额外维护 `_operationDatasCached` 缓存（供 `StoreTighteningDataToOuterDatabase()` 在 TerminateMission 时批量写入外部数据库）。

**GLB NG 处理** — 逻辑结构与 SCII 一致（max_ng_num → 反松 → password_need_time），但有以下差异：
| 差异点 | SCII | GLB |
|---|---|---|
| `_errorMsg` 赋值 | 无 | ✅ 有（用于外部 DB 上报） |
| `MissionNGConfirmPopUp` | Override：激活蜂鸣器 | 使用基类版本，无蜂鸣器 |
| 冗余 `dataDTO.tightening_status = NG` | ✅ 有 | 无 |

### 7.3 基类/YMT NG 处理（简单）

```
螺栓 NG:
├── bolt.BoltStatus = ERROR
├── bolt.NgTimes++
├── 设置错误信息
├── StoreTighteningData(NG)
└── ForceSendUnlock() — 不终止任务，不限次数
```

**⚠️ 注意**: 标准版和 YMT 使用基类 NG 处理，不会因 NG 次数超限自动终止任务，操作工可无限重试。SCII/GLB 则具备完整的 max_ng_num 终止 + 反松 + 管理员密码机制。

### 7.4 TighteningStatus 枚举

**文件**: `Constants/TighteningStatus.cs`
```
OK, NG
```

**文件**: `Constants/TighteningCommonStatus.cs`
```
OK, NG, HIGH, LOW
```

---

## 8. Mission 完成/终止流程

### 8.1 TerminateMission 完整步骤

**文件**: `AWorkplaceContentPanel.cs:2819`

```
TerminateMission(status)  [public virtual async Task]
│
├─ [1] 取消所有后台任务（防竞态）
│   ├── _backgroundTaskCts 逐一 Cancel()
│   ├── 等待 100ms（让任务退出）
│   ├── Dispose() 所有 CTS
│   └── _backgroundTaskCts.Clear()
│
├─ [2] ForceLockAllTools() — 锁定 3 次确保
│   （仅 _activated=true + IsAutoLockToolEnabled）
│
├─ [3] ResetIoBox() — 重置所有 IO 状态
│
├─ [4] 重置标志位
│   ├── _arrangerNeeded = false
│   ├── _setterSelectorNeeded = false
│   └── _activated = false
│
├─ [5] await Task.Delay(300) — 防力臂设备错误更新状态
│
├─ [6] ClearAndResetAllCurrentBolts(resetToDefault)
│   ├── UNACTIVATED: 所有螺栓 → DEFAULT
│   ├── 其他状态: WORKING → ERROR, 其余保持
│   └── 停止所有闪烁
│
├─ [7] ResetWorkingProcessPanel(resetToDefault, status)
│   ├── UNACTIVATED: 显示"未激活"，清空描述
│   └── 其他: 设置为 FINISHED_OK / FINISHED_NG
│
├─ [8] StopRetrivingDataFromArmDevice()
│   ├── 取消所有力臂坐标监听
│   └── 取消注册 ActionAfterArmDataReceived 回调
│
├─ [9] 清空条码缓存
│   ├── _barCodeObj.Reset()
│   ├── _ruleIdsCheckedCached = null
│   └── _isRedo = NO
│
├─ [10] await OnMissionCompleted(status)
│   ├── 仅 FINISHED_OK / FINISHED_NG 触发
│   ├── Interlocked.Exchange 防重入
│   ├── 等待 _storeTighteningDataLock 排空（5s 超时）
│   ├── 获取 _tighteningDataVOs 快照
│   ├── 组装 ExportRequest（含 parts_bar_code 回填）
│   ├── ExportDataAsync() → DataExportService
│   └── 清空 _tighteningDataVOs + 刷新 UI
│
└─ [11] 自循环检查
    └── if (非挑战任务 && mission_result == OK && 自循环模式):
        └── ActivateMissionAutomatically()
```

> **注意**: NG 完成（mission_result != OK）不会触发自循环。只有当 mission_result == OK 且自循环模式启用时才会自动重新激活。mission_result 的赋值位置为 §6.2 OK 路径末尾（`_missionRecord.mission_result = OK`）。

### 8.2 各版本 TerminateMission 覆盖

| 版本 | 覆盖行为 | 位置 |
|---|---|---|
| **SCII/SCII_XT** | `ResetMissionDetails()` + 日志 → `await base.TerminateMission(status)` | SCII.cs:1512 |
| **GLB** | 1. `StoreTighteningDataToOuterDatabase()` — 将缓存在 `_operationDatasCached` 中的全量拧紧数据批量写入外部数据库 2. PLC 同步发送 `SendJobFinished(true)` + `SendJobResult(result)`（`Thread.Sleep` 重试，最多阻塞 ~300ms）3. `await base.TerminateMission(status)` | GLB.cs:56 |
| **YMT** | 覆盖 `ExportDataAsync` 路由到 `YmtDataExportService`（日聚合追加模式，即每个任务每天一个文件，后续完成追加到已有文件末尾），其余使用基类 | - |

### 8.3 OnHandleDestroyed — 窗口关闭/退出登录

**文件**: `AWorkplaceContentPanel.cs:2992`

```
OnHandleDestroyed:
├── 取消所有 CTS (_activeMissionCts + _backgroundTaskCts)
├── 补充 NG 导出（如果从未触发过导出）
│   └── OnMissionCompleted(FINISHED_NG).Wait() — 同步等待
├── base.OnHandleDestroyed()
├── 锁定所有工具 + 清空委托（ActionAfterAnalysis = null）
├── ResetIoBox()
├── 清空串口/通信任务委托
└── Dispose 所有 BoltButton
```

### 8.4 AWorkplaceMissionView.Dispose

**文件**: `AWorkplaceMissionView.cs:142`

```csharp
protected override void Dispose(bool disposing) {
    if (disposing) {
        _checkCts?.Cancel();
        _checkCts?.Dispose();
    }
    base.Dispose(disposing);
}
```

---

## 9. 后台任务详情

### 9.1 StartLockCheckingTask（锁检查任务）

**启动**: ActionAfterActivatingMission
**周期**: 50ms（finally 块中的 `await Task.Delay(_lockCheckingTaskDelay)` 控制）
**令牌**: `CreateLinkedTokenSource(_activeMissionCts.Token)`
**线程模型**: `BeginInvoke` → UI 线程排队 → 内部 `Task.Run` 在线程池执行 → 对 `_workingProcessPanel` 的 UI 属性设置存在跨线程风险（代码注释已知 `InvalidOperationException`）
**退出条件**: `!IsDisposed && _activated && !cts.Token.IsCancellationRequested`（三者 AND）

```
每 50ms 循环:
├── 获取当前螺栓和 ToolTask
├── CheckCurrentPSetForLockMsg()     — 程序号未下发 → 锁
├── CheckAdminConfirmationForLockMsg() — 管理员未确认 → 锁
├── if (lockMsgs.Count > 0):
│   ├── WorkplaceProcessStatus = OPERATION_DISABLE
│   └── toolTask.SendLock()
└── else:
    ├── WorkplaceProcessStatus = OPERATION_ENABLE
    │   （_needLoosening ? LOOSENING : TIGHTENING）
    └── toolTask.SendUnlock()
```

**锁定消息来源**:
| 消息 | 触发条件 | 备注 |
|---|---|---|
| `LockedPsetNull` | 螺栓未配置程序号 | |
| `LockedPsetSending` | (已定义但从未 Add，仅 Remove) | ⚠️ 代码中无 AddLockMsg 调用，锁表永远不会显示此项 |
| `LockedPsetFailed` | 程序号下发失败 | |
| `LockedPsetNotMatched` | (已定义但未使用) | ⚠️ 预留常量，代码中无引用 |
| `LockedArmPosition` | 力臂坐标不匹配 | |
| `LockedArmDisconnected` | 力臂断连 | |
| `LockedManually` | 手动锁止（ToolOperationPopUpForm） | |
| `LockedArrangerNotDone` | 排列机未就位 | |
| `LockedArrangerTimedOut` | 排列机超时 | |
| `LockedSetterSelectorNotMatched` | 套筒选择器未匹配 | |
| `LockedSetterSelectorTimedOut` | 套筒选择器超时 | |
| `LockedBoltBarCode` | 螺栓绑定了物料码但未扫码 | |
| `AdminConfirmation` | 管理员未确认 NG | |

### 9.2 StartArrangerTask（排列机检查任务）

**启动**: ActionAfterActivatingMission（仅 `_arrangerNeeded = true` 时）
**周期**: 100ms

```
每 100ms 循环:
├── if (_arrangerPositionOk != null):
│   ├── 有未就位:
│   │   ├── 未超时: AddLockMsg(LockedArrangerNotDone)
│   │   └── 已超时:
│   │       ├── 重试次数 < max: 弹窗确认 → 重新发信号
│   │       └── 重试次数 >= max: TerminateMission(FINISHED_NG)
│   └── 全部就位: 移除锁定, _arrangerPositionOk = null
```

### 9.3 StartSetterSelectorTask（套筒选择器检查任务）

**启动**: ActionAfterActivatingMission（仅 `_setterSelectorNeeded = true` 时）
**周期**: 100ms

逻辑与排列机检查类似，检查 `_bitPositionOk` 状态。

### 9.4 CheckDeviceConnections（设备连接检查任务）

**启动**: LoadDevicesAsync 之后
**周期**: 2000ms
**令牌**: 无（持续到 Dispose）

遍历所有设备类别，检查连接状态，更新 DeviceBlock 图标和状态。

---

## 10. 取消令牌管理

### 10.1 Token 层级

```
_activeMissionCts (主令牌)
  ├── StartLockCheckingTask → LinkedTokenSource
  ├── StartArrangerTask → LinkedTokenSource
  └── StartSetterSelectorTask → LinkedTokenSource

_checkCts (View 级别，用于数据拉取取消)
```

### 10.2 生命周期

```
字段初始化(AWC:71): _activeMissionCts = new CancellationTokenSource()
ActivateMission:  取消旧的 → Dispose → 创建新的
Start*Task:       LinkedTokenSource 加入 _backgroundTaskCts
TerminateMission: 取消所有 _backgroundTaskCts → 等待 100ms → Dispose
OnHandleDestroyed: 取消 _activeMissionCts + 所有 _backgroundTaskCts
Dispose (View):   取消 _checkCts
```

---

## 11. 中止场景汇总

| 场景 | 触发位置 | 终止状态 |
|---|---|---|
| 验证失败 | ActivateMission → ValidationBeforeActivatingMission = false | UNACTIVATED |
| 排列机超时耗尽 | StartArrangerTask → 重试次数 >= max | FINISHED_NG |
| 套筒选择器超时耗尽 | StartSetterSelectorTask → 重试次数 >= max | FINISHED_NG |
| NG 次数达上限 | DoAfterRecevingTighteningDataAsync (SCII/GLB) | FINISHED_NG |
| SCII SkipScrewPoints | ActivateMission 快捷路径 | FINISHED_OK |
| 窗口关闭/退出登录 | OnHandleDestroyed | FINISHED_NG（导出） |

---

## 12. 完整时序图

```
用户操作          UI层                 业务层                    设备/API层
   │               │                     │                          │
   ├─选择Mission───►│                     │                          │
   │               ├─OpenWorkplaceView───►│                          │
   │               │                     ├─new WorkplaceContentPanel │
   │               │                     ├─查询Mission详情──────────►│
   │               │                     │◄──────────────────────────┤
   │               │                     ├─OnHandleCreated           │
   │               │                     ├─加载设备──────────────────►│
   │               │                     ├─自动激活检查               │
   │               │                     │                          │
   ├─扫码─────────►│                     │                          │
   │               ├─弹窗"激活任务"──────►│                          │
   │               │                     ├─ActivateMission()        │
   │               │                     ├─PrepareBeforeActivating   │
   │               │                     ├─ValidationBeforeActivating│
   │               │                     ├─_activated = true         │
   │               │                     ├─InitializeBeforeActivating│
   │               │                     │  ├─SwitchBolt(0)          │
   │               │                     │  ├─SendPSet──────────────►│
   │               │                     │  └─BoltStatus=WORKING     │
   │               │                     ├─ActionAfterActivating     │
   │               │                     │  ├─创建MissionRecord─────►│
   │               │                     │  ├─StartLockCheckingTask  │
   │               │                     │  └─Status=ACTIVATED       │
   │               │                     │                          │
   ├─拧紧螺栓──────►│                     │◄──TighteningData─────────┤
   │               │                     ├─DoAfterReceving...()     │
   │               │                     ├─OK: BoltStatus=DONE       │
   │               │                     │  ├─StoreTighteningData───►│
   │               │                     │  └─下一螺栓/面            │
   │               │                     │                          │
   │               │                     │  (循环直到全部完成)       │
   │               │                     │                          │
   │               │                     ├─全部DONE → FINISHED_OK   │
   │               │                     ├─TerminateMission(OK)      │
   │               │                     │  ├─取消后台任务            │
   │               │                     │  ├─ForceLockAllTools      │
   │               │                     │  ├─_activated=false       │
   │               │                     │  ├─ClearAndResetBolts     │
   │               │                     │  ├─OnMissionCompleted────►│ (导出)
   │               │                     │  └─自循环检查              │
   │               │                     │                          │
   │◄──完成显示────┤                     │                          │
```

---

## 13. 关键文件索引

| 文件 | 行数 | 核心内容 |
|---|---|---|
| `Views/AbstractViews/AWorkplaceMissionView.cs` | ~150 | View 外观层：Mission 列表、OpenWorkplaceView、Dispose |
| `Views/AbstractViews/AWorkplaceContentPanel.cs` | 3053 | **核心基类**：全部业务逻辑 |
| `Constants/WorkplaceProcessStatus.cs` | 11 | 工作台状态枚举 |
| `Constants/BoltStatus.cs` | ~8 | 螺栓状态枚举 |
| `Constants/TighteningStatus.cs` + `TighteningCommonStatus.cs` | ~10 | 拧紧状态枚举 |
| `Views/WorkplaceMissionView_SCII.cs` | ~1700 | SCII/SCII_XT：批头计数器、max_ng_num、SkipScrewPoints |
| `Views/WorkplaceMissionView_GLB.cs` | ~200 | GLB：PLC 信号 + 外部数据库 |
| `Views/WorkplaceMissionView_YMT.cs` | ~30 | YMT：日聚合导出（覆盖 ExportDataAsync） |
| `Views/ReusableWidgets/BoltButton.cs` | - | Bolt 按钮控件（BoltStatus 属性管理） |
| `Views/SubViews/WorkingProcessPanel.cs` | - | 工作状态面板（WorkplaceProcessStatus 显示） |
| `Service/Models/DTOs/ProductMissionDTO.cs` | - | Mission DTO（max_ng_num 默认=3） |

> **说明**: 文件索引中 `WorkplaceMissionView_SCII.cs`、`WorkplaceMissionView_GLB.cs`、`WorkplaceMissionView_YMT.cs` 属于 View 外观层（Facade），负责 Mission 列表和视图生命周期。各版本的业务逻辑覆盖（批头计数器、PLC 信号、日聚合导出等）实际位于对应的 `WorkplaceContentPanel_SCII`、`WorkplaceContentPanel_GLB`、`WorkplaceContentPanel_YMT` 类中。本文档引用时以源文件中类名标识版本差异，文件命名与类名不完全对应。

## 14. 架构观察与潜在问题

1. **基类过重**: `AWorkplaceContentPanel` 约 3000 行，承载了激活、设备管理、拧紧处理、UI 渲染、导出、扫码匹配等过多职责

2. **NG 判定实现不统一**: 基类/YMT 不检查 `max_ng_num`，但 SCII/GLB 各自实现了 NG 次数检查。使用基类的版本（标准版、YMT）会无限重试 NG

3. **跨线程复杂性**: 大量 `BeginInvoke` + `Task.Run` 嵌套，异常处理和取消传播路径复杂

4. **Token 管理良好**: 所有异步循环都有取消令牌保护，Dispose 路径正确处理。但 `async void` 方法（如 `ActivateMission`）的异常不会传播给调用者

---

## 15. 审查补充发现（第二轮审查追加）

以下内容在初始分析中未覆盖或未充分展开，由多轮深度审查发现。仅覆盖实际在用版本（标准版、SCII/SCII_XT、GLB、YMT）。

### 15.1 `async void ActivateMission()` 异常风险

`ActivateMission()` 没有整体 try-catch。如果 `ActionAfterActivatingMission()`（`:1341`）中抛出异常：

- `_isActivating` 保持 `true`（`:1343` 不会执行）
- `_activated` 保持 `true`（`:1334` 已设置）
- 后台任务（LockChecking/Arranger/SetterSelector）未启动
- Mission 进入**永久卡死**状态：后续拧紧数据会被接收，但 `_missionRecord` 可能为 null → 触发 `NullReferenceException`

### 15.2 挑战任务（Challenge Mission）前置验证

文档 §7 仅在 OK 路径提到 `AddChallengeResult`，但激活前的完整挑战任务验证路径未覆盖：

- `CheckChallengeMissionConfirmation()`（`:1168`）在激活前验证挑战任务约束（首档岗位检测、前置任务检查、挑战校验项检查）
- `ChallengeChecks()`（`:1219`）通过 8 项检查验证挑战任务是否已完成（追溯码错码/重码、物料码错码/重码、前置岗位完成情况）
- 验证失败弹出具体错误提示阻止激活

### 15.3 各版本对基类方法的重写程度

文档原暗示各版本在基类方法上做小范围覆盖，**实际情况是大多数版本完全重写了核心方法**：

| 方法 | 基类 | SCII/SCII_XT | GLB | YMT |
|---|---|---|---|---|
| `ActivateMission` | ✅定义 | 重写(含SkipScrew快捷路径) | 继承 | 继承 |
| `DoAfterRecevingTighteningDataAsync` | ✅定义 | **完全重写** | **完全重写** | 继承 |
| `TerminateMission` | ✅定义 | 前后置+base | 前后置+base | 继承 |
| `StartLockCheckingTask` | ✅定义 | 继承 | 继承 | 继承 |

注：YMT 覆盖了 `ExportDataAsync`（路由到 `YmtDataExportService`）而非上述核心方法。

### 15.4 SCII SkipScrewPoints 的自循环阻塞

当 `SkipScrewPoints = true` 时，SCII 覆盖了 `ActivateMissionAutomatically()`（SCII.cs:167）直接 `return`。这意味着：
- 自循环模式（`IsMissionSelfLoopingModeEnabled`）被完全禁用
- USB 扫码器模式也被跳过
- 每次 mission 完成后需要人工手动重新激活

### 15.5 GLB PLC 条码自循环 (ActivateMissionAutomatically)

GLB 覆盖了 `ActivateMissionAutomatically()`（`GLB.cs:77-174`），实现完整的 PLC 条码自循环：弹出用户确认 → `StartReadFromPLC()` 异步循环读取 Siemens PLC → 超时 5 秒 → 收到条码后 `ActionAfterRecevingBarCode(barCode)` 校验并自动激活 → 失败可重试。

### 15.6 `OnHandleDestroyed` 中 `Wait()` 的潜在死锁

`:3006` 行 `OnMissionCompleted(FINISHED_NG).Wait()` 在 UI 线程同步阻塞。如果 `_storeTighteningDataLock` 被另一个操作持有，且该操作的下一步需要 UI 线程（通过 `BeginInvoke`），可能导致死锁。风险较低但存在。

### 15.7 `SendPSet` 快速路径无取消令牌

`:2117` 行的 `task.SendPSetAsync(pset.Value)` 不传入 `_activeMissionCts.Token`。如果工具连接阻塞，mission 终止时此调用无法被取消。后续自动重试路径正确传入了令牌。

### 15.8 工具锁策略：两种模式的区别

| 模式 | 配置键 | 策略 | 触发时机 |
|---|---|---|---|
| `IsAutoLockToolEnabled` | 安全锁 | Mission **不活跃**时锁工具 | 工具加载后、Mission 终止时、Handle 销毁时 |
| `IsArmLocatingEnabled` | 流程锁 | 拧紧**过程中**按力臂坐标锁/解锁 | 激活时锁、数据到达时锁、NG 时解锁 |

### 15.9 流程图省略的次要细节（第三轮逐行验证）

以下步骤在文档 ASCII 流程图中为简洁而省略，但对完整理解有参考价值：

| 位置 | 省略内容 | 代码行 |
|---|---|---|
| §8.1 TerminateMission | Step [7] 与 [8] 之间的扭矩/角度颜色恢复（`_torque.ForeColor = NORMAL; _angle.ForeColor = NORMAL`） | `:2876-2879` |
| §8.1 TerminateMission | Step [1] 的 `if (_backgroundTaskCts.Count > 0)` 守卫条件 | `:2822` |
| §8.1 TerminateMission → Step [10] OnMissionCompleted 内部 | Step [10] 的 `if (!IsExcelExportEnabled && !IsTxtExportEnabled) return` 守卫 | `:2743` |
| §6.2 DoAfterReceving | 扭矩/角度面板 UI 即时更新（`_torquePanel.Data = ...; _anglePanel.Data = ...`） | `:2355-2357` |
| §6.2 DoAfterReceving | `_rundownTime = data.rundown_time` | `:2410` |
| §9.1 LockChecking | `_currentWorkingBolt == null` 跳过守卫 | `:1673-1676` |
| §9.1 LockChecking | `lastIterationWasLock` 状态跟踪（仅状态变化时打日志，避免 50ms 日志洪水） | `:1687-1690` |

### 15.10 代码中未被文档覆盖的逻辑路径（第四轮发现）

以下方法和流程存在于源代码中，直接影响 mission 生命周期，但未在文档主体中描述。

#### `CheckCanActivateMission()`（`ABarCodeInputPopUpForm.cs:679`）

激活前的关键门控方法。在扫码弹窗的"激活任务"点击路径中被调用（AWC:1134），检查：
1. `_mission.id > 0` — 必须选择了任务
2. `ProductBarCode` 非空 — 必须录入产品追溯码
3. 物料码数量匹配 — 如有物料码规则，录入数量必须等于预期

失败时高亮空字段并弹出警告。文档 §5.1 的"扫码弹窗 → 激活任务按钮 → ActivateMission()"跳过了这个中间门控。

#### `PopUpMissionListForm()` + `ActionAfterSwitchMission()`（AWC:419, AWC:459）

运行时任务切换的完整流程（不退出工作台）：
1. `PopUpMissionListForm` 弹出"选择任务"弹窗（含 MissionListPanel）
2. 用户选择任务 → `SwitchToMission()` 替换 `_mission` 数据、重置条码缓存、刷新 UI
3. `ActionAfterSwitchMission()` → `ActivateMissionAutomatically()`（自循环模式自动激活）
4. SCII 覆盖额外调用 `ResetMissionDetails()` 重置批头计数器

此路径是**运行时动态切换**，不同于文档 §4.3 描述的首次进入工作台路径。

#### `ActionAfterRecevingBarCode()`（GLB:217）

PLC 接收条码后的回调路径（非 UI 弹窗输入）：校验条码格式 → `BarCodeTextBox.Text = barCode` → 如果弹窗已打开则验证，否则打开弹窗。文档 §5.1 的"PLC 条码自循环 (GLB)"未展开此回调的具体行为。

#### `BoltNGConfirmPopUp()` vs `MissionNGConfirmPopUp()` 的区别

文档 §7.2 在流程图中使用了这两个名称但未区分其语义：

| 方法 | 位置 | 用途 | 是否允许取消 |
|---|---|---|---|
| `BoltNGConfirmPopUp()` | AWC:2306 | 单螺栓 NG 达 `password_need_time` 阈值时，锁工具并弹出管理员密码窗 | `allowCancel: false` |
| `MissionNGConfirmPopUp()` | AWC:2309 | 任务级 NG（达 `max_ng_num` 上限），弹出管理员密码窗 | `allowCancel: false` |
| `MissionNGConfirmPopUp()` (SCII override) | SCII:1255 | 同上 + 激活蜂鸣器（`BuzzerController`） | `allowCancel: false` |
