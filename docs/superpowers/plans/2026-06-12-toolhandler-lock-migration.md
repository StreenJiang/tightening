# ToolHandler 锁和状态迁移至 DeviceHolder 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 ToolHandler 的实例级锁和状态字段迁移到 DeviceHolder，修复多设备共享同一 ToolHandler 实例时的状态串扰 bug

**Architecture:** 5 个字段（stateLock, pSetLock, isToolEnabled, lastEnableTime, lastDisableTime）从 Spring 单例 ToolHandler 下移到 per-device 的 DeviceHolder。ToolHandler 的 changeToolState 和 sendPSetOp 通过 getHolder(deviceId) 获取 DeviceHolder 后再操作锁和状态

**Tech Stack:** Java 21, Lombok, ReentrantLock

---

## 文件结构

| 文件 | 职责 | 变更 |
|---|---|---|
| `device/DeviceHolder.java` | 设备运行时状态容器，持有锁和 enable/disable 状态 | 新增 5 个字段，`@Data` → `@Getter` + 手动 setter |
| `device/handler/ToolHandler.java` | 工具操作抽象，enable/disable/PSET 命令分发 | 删除 5 个字段，改为从 DeviceHolder 获取 |
| `device/handler/impl/TCPDeviceHandler.java` | TCP 设备处理器基类 | `getHolder()` 增加 warn 日志和带 deviceId 的异常消息 |
| `controller/DeviceController.java` | REST API，`getEnabled` 查询设备 enable 状态 | `isToolEnabled()` → `isToolEnabled(deviceId)` |

---

### Task 1: DeviceHolder 添加锁和状态字段

**Files:**
- Modify: `src/main/java/com/tightening/device/DeviceHolder.java`

- [ ] **Step 1: 重写 DeviceHolder，添加 5 个字段**

```java
package com.tightening.device;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.entity.Device;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.locks.ReentrantLock;

@Getter
public class DeviceHolder {
    private final Device device;
    @Setter private volatile DeviceStatus status;
    @Setter private volatile Channel channel;

    // enable/disable 状态和冷却控制
    private final ReentrantLock stateLock = new ReentrantLock();
    private final ReentrantLock pSetLock = new ReentrantLock();
    @Setter private volatile boolean isToolEnabled = false;
    @Setter private volatile long lastEnableTime = 0;
    @Setter private volatile long lastDisableTime = 0;

    public DeviceHolder(Device device) {
        this.device = device;
        status = DeviceStatus.DISCONNECTED;
    }

    public String resolveToolTypeName() {
        DeviceType deviceType = DeviceType.getType(device.getType());
        return deviceType != null ? deviceType.getName() : null;
    }
}
```

变更点：
- `@Data` → `@Getter`（类级） + `@Setter`（status, channel）
- 新增 5 个字段：`stateLock`, `pSetLock`, `isToolEnabled`, `lastEnableTime`, `lastDisableTime`
- `@Data` 生成的 `device` getter 和构造函数参数由 `@Getter` + 显式构造函数覆盖
- `@Data` 生成的 `equals/hashCode/toString` 丢失，但项目中未见对 DeviceHolder 使用这些方法

- [ ] **Step 2: 检查编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/tightening/device/DeviceHolder.java
git commit -m "refactor: move tool state fields and locks from ToolHandler to DeviceHolder

Add stateLock, pSetLock, isToolEnabled, lastEnableTime, lastDisableTime
to DeviceHolder so each device maintains independent tool state. Replace
@Data with @Getter + manual @Setter annotations."
```

---

### Task 2: ToolHandler 改为从 DeviceHolder 获取锁和状态

**Files:**
- Modify: `src/main/java/com/tightening/device/handler/impl/TCPDeviceHandler.java`
- Modify: `src/main/java/com/tightening/device/handler/ToolHandler.java`

- [ ] **Step 1: 增强 getHolder()，加 warn 日志和异常消息**

在 `TCPDeviceHandler.java` 第 153-159 行，将：

```java
protected DeviceHolder getHolder(long deviceId) {
    DeviceHolder deviceHolder = devices.get(deviceId);
    if (deviceHolder == null) {
        throw new RuntimeException();
    }
    return deviceHolder;
}
```

改为：

```java
protected DeviceHolder getHolder(long deviceId) {
    DeviceHolder deviceHolder = devices.get(deviceId);
    if (deviceHolder == null) {
        log.warn("DeviceHolder not found for deviceId={}", deviceId);
        throw new RuntimeException("DeviceHolder not found for deviceId=" + deviceId);
    }
    return deviceHolder;
}
```

- [ ] **Step 2: 重写 ToolHandler，删除实例级字段，改为从 DeviceHolder 获取**

```java
package com.tightening.device.handler;

import com.tightening.config.ToolCommonConfig;
import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class ToolHandler extends TCPDeviceHandler {

    @Getter
    private final TighteningDataService tighteningDataService;
    private final ToolCommonConfig toolCommonConfig;

    public ToolHandler(DeviceService deviceService,
                       TighteningDataService tighteningDataService,
                       ToolCommonConfig toolCommonConfig) {
        super(deviceService);
        this.tighteningDataService = tighteningDataService;
        this.toolCommonConfig = toolCommonConfig;
    }

    public CompletableFuture<Boolean> enableToolOp(long deviceId) {
        return changeToolState(true, deviceId, false);
    }

    public CompletableFuture<Boolean> disableToolOp(long deviceId) {
        return changeToolState(false, deviceId, false);
    }

    public CompletableFuture<Boolean> forceEnableToolOp(long deviceId) {
        return changeToolState(true, deviceId, true);
    }

    public CompletableFuture<Boolean> forceDisableToolOp(long deviceId) {
        return changeToolState(false, deviceId, true);
    }

    public CompletableFuture<Boolean> sendPSetOp(long deviceId, int pSet) {
        DeviceHolder holder = getHolder(deviceId);
        holder.getPSetLock().lock();
        try {
            log.debug("sendPSetOp: deviceId={}, pSet={}", deviceId, pSet);
            return sendPSetCmd(deviceId, pSet);
        } finally {
            holder.getPSetLock().unlock();
        }
    }

    /**
     * 查询指定设备的 enable/disable 状态
     */
    public boolean isToolEnabled(long deviceId) {
        return getHolder(deviceId).isToolEnabled();
    }

    public CompletableFuture<Boolean> sendHeartbeat(long deviceId) {
        return CompletableFuture.completedFuture(false);
    }

    /**
     * 通用状态变更（带冷却控制，支持强制绕过）
     *
     * @param targetEnabled  true=启用，false=禁用
     * @param deviceId       设备ID
     * @param bypassCooldown true=绕过冷却检查，false=遵守冷却
     * @return CompletableFuture，操作结果
     */
    private CompletableFuture<Boolean> changeToolState(boolean targetEnabled, long deviceId,
                                                       boolean bypassCooldown) {
        String action = targetEnabled ? "enable" : "disable";
        long now = System.currentTimeMillis();
        DeviceHolder holder = getHolder(deviceId);

        // 1. 冷却检查与意图记录（同步）
        holder.getStateLock().lock();
        try {
            if (!bypassCooldown) {
                boolean oppositeState = targetEnabled != holder.isToolEnabled();
                long lastTime = targetEnabled ? holder.getLastEnableTime() : holder.getLastDisableTime();
                log.debug("{}ToolOp: deviceId={}, current isEnabled={}, oppositeState={}",
                          action, deviceId, holder.isToolEnabled(), oppositeState);

                if (!oppositeState && (now - lastTime) < toolCommonConfig.getEnableDisableCooldownMs()) {
                    log.debug("{}ToolOp rejected by cooldown: deviceId={}, elapsed={}ms",
                              action, deviceId, now - lastTime);
                    return CompletableFuture.completedFuture(false);
                }
            } else {
                log.debug("force{}Tool: deviceId={}, bypass cooldown", action, deviceId);
            }
        } finally {
            holder.getStateLock().unlock();
        }

        // 2. 调用底层异步操作
        CompletableFuture<Boolean> operation = targetEnabled ? enableTool(deviceId) : disableTool(deviceId);

        // 3. 操作完成后更新状态（如果成功）
        return operation.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("{}Tool exception: deviceId={}", action, deviceId, ex);
                return;
            }
            if (result != null && result) {
                holder.getStateLock().lock();
                try {
                    if (targetEnabled) {
                        holder.setToolEnabled(true);
                        holder.setLastEnableTime(System.currentTimeMillis());
                        log.info("{}Tool enable succeeded: deviceId={}", action, deviceId);
                    } else {
                        holder.setToolEnabled(false);
                        holder.setLastDisableTime(System.currentTimeMillis());
                        log.info("{}Tool disable succeeded: deviceId={}", action, deviceId);
                    }
                } finally {
                    holder.getStateLock().unlock();
                }
            } else {
                log.debug("{}Tool failed: deviceId={}", action, deviceId);
            }
        });
    }

    // ============== 抽象方法（子类必须实现，返回 CompletableFuture） ==============
    protected abstract CompletableFuture<Boolean> enableTool(long deviceId);
    protected abstract CompletableFuture<Boolean> disableTool(long deviceId);
    protected abstract CompletableFuture<Boolean> sendPSetCmd(long deviceId, int pSet);
}
```

与旧代码的 diff 要点：
- **删除**：`stateLock`, `pSetLock`, `lastEnableTime`, `lastDisableTime`, `isToolEnabled` 字段声明
- **删除**：`import java.util.concurrent.locks.ReentrantLock;`
- **新增**：`import com.tightening.device.DeviceHolder;`
- `changeToolState()`：开头新增 `DeviceHolder holder = getHolder(deviceId);`，所有 `stateLock` → `holder.getStateLock()`，所有 `isToolEnabled`/`lastEnableTime`/`lastDisableTime` → `holder.getXxx()`/`holder.setXxx()`
- `sendPSetOp()`：新增 `DeviceHolder holder = getHolder(deviceId);`，`pSetLock` → `holder.getPSetLock()`
- 新增 `isToolEnabled(long deviceId)` 公共方法

- [ ] **Step 3: 检查编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/device/handler/impl/TCPDeviceHandler.java src/main/java/com/tightening/device/handler/ToolHandler.java
git commit -m "refactor: delegate tool state and locking to DeviceHolder

Enhance getHolder() with warn logging and descriptive exception message.
Remove instance-level stateLock, pSetLock, isToolEnabled, lastEnableTime,
lastDisableTime from ToolHandler. changeToolState() and sendPSetOp() now
acquire locks and state from the per-device DeviceHolder, fixing state
cross-talk when one handler manages multiple devices."
```

---

### Task 3: DeviceController 适配新的 isToolEnabled 签名

**Files:**
- Modify: `src/main/java/com/tightening/controller/DeviceController.java`

- [ ] **Step 1: 修改 getEnabled 方法，传入 deviceId**

将第 54 行：

```java
result = toolHandler.isToolEnabled();
```

改为：

```java
result = toolHandler.isToolEnabled(deviceId);
```

- [ ] **Step 2: 检查编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/tightening/controller/DeviceController.java
git commit -m "fix: pass deviceId to isToolEnabled for per-device state

getEnabled() was reading the old instance-level isToolEnabled field,
returning incorrect state when the handler manages multiple devices."
```

---

### Task 4: 验证

- [ ] **Step 1: 运行全部测试**

Run: `mvn test`
Expected: All tests pass

- [ ] **Step 2: 确认旧字段已完全迁移**

Run: `grep -rn "stateLock\|pSetLock\|lastEnableTime\|lastDisableTime\|isToolEnabled" src/main/java/com/tightening/device/handler/ToolHandler.java`
Expected: 只有方法名 `sendPSetOp` 和 `isToolEnabled(long)` 匹配，无字段声明

Run: `grep -rn "stateLock\|pSetLock\|lastEnableTime\|lastDisableTime\|isToolEnabled" src/`
Expected: 只有 DeviceHolder.java 中的字段定义和 getter/setter，ToolHandler.java 中的 `holder.getXxx()` 调用，DeviceController.java 中的 `isToolEnabled(deviceId)` 调用

- [ ] **Step 3: 提交（如有遗留变更）**

```bash
git status
```
