# 🔩 Tightening - Industrial Tightening Flow Control System

🌍 **Languages**: 
[English](README.md) | [中文](README.zh.md)

---
<!-- 下方继续原英文内容 -->

[![Java](https://img.shields.io/badge/Java-21-blue)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache--2.0-yellow.svg)](LICENSE)

## 📋 项目简介

**Tightening** 是一款基于 Java + Spring Boot + Netty 的工业拧紧流程控制系统，专注于：

- 🔌 多协议设备接入（Atlas Copco FIT/PF-series、Modbus TCP、Siemens S7、串口设备）
- 📡 基于 Netty 的异步 TCP 通信与心跳保活
- 🗃️ SQLite 嵌入式数据库 + Flyway 版本化管理
- ⚙️ 设备连接管理、命令下发、数据采集与事件处理

适用于工位级边缘部署，支持轻量化独立运行。

## 🧰 技术栈

| 模块 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.10 | 应用框架 |
| Java | 21 | 开发语言 |
| Netty | 4.1.129.Final | 异步设备通信 |
| MyBatis-Plus | 3.5.9 | 数据持久化 |
| SQLite JDBC | 3.49.1.0 | 嵌入式数据库 |
| Flyway | - | 数据库迁移管理 |
| PLC4X | 0.13.1 | Modbus/S7 协议驱动 |
| jSerialComm | 2.10.2 | 串口通信支持 |
| Lombok | - | 简化样板代码 |

## 📁 项目结构

```
tightening/
├── src/main/java/com/tightening/
│   ├── annotation/     # 自定义注解（如设备命令控制）
│   ├── config/         # Spring 配置类
│   ├── constant/       # 全局常量定义
│   ├── controller/     # Web 接口控制器
│   ├── device/         # 设备管理核心
│   │   ├── event/      # 设备事件定义
│   │   ├── handler/    # 设备连接/通信处理器
│   │   ├── type/       # 设备类型枚举
│   │   ├── DeviceHolder.java    # 设备连接持有者
│   │   └── DeviceManager.java   # 设备生命周期管理
│   ├── dto/            # 数据传输对象
│   ├── entity/         # 数据库实体
│   ├── mapper/         # MyBatis-Plus Mapper 接口
│   ├── netty/protocol/ # Netty 协议层
│   │   ├── codec/      # 编解码器（如 FitFrameCodec）
│   │   ├── handler/    # ChannelHandler 实现
│   │   └── util/       # 协议工具类（FitDataUtils 等）
│   ├── service/        # 业务逻辑层
│   ├── util/           # 通用工具类
│   └── TighteningApplication.java
├── src/main/resources/
│   ├── application.yaml           # 主配置文件
│   ├── application-dev.yml        # 开发环境配置
│   ├── application-standalone.yml # 单机部署配置
│   └── db/migration/              # Flyway 迁移脚本
├── pom.xml
├── LICENSE
└── README.md
```

## 🚀 快速开始

### 1. 环境要求
- JDK 21+
- Maven 3.8+

### 2. 克隆与构建
```bash
git clone https://github.com/StreenJiang/tightening.git
cd tightening
mvn clean package -DskipTests
```

### 3. 配置说明
编辑 `src/main/resources/application-dev.yml`：

```yaml
# 设备连接线程池配置
device-config:
  connect-thread:
    core-pool-size: 5
    max-pool-size: 10
    keep-alive-time-ms: 30000
    capacity: 50
  scan-thread:
    init-delay-ms: 0
    delay-ms: 5000

# Atlas FIT 协议心跳配置
tool-control:
  common:
    enable_disable_cooldown_ms: 5000
  atlas:
    fit:
      heart-beat-interval-ms: 30000
      heart-beat-retry-max: 3
```

### 4. 启动应用
```bash
# 开发模式
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 单机部署模式
java -jar target/tightening-0.0.1-SNAPSHOT.jar --spring.profiles.active=standalone
```

### 5. 验证运行
- 日志输出 `Started TighteningApplication` 表示启动成功
- 首次运行会自动在 `${user.home}/tightening_system/tightening.db` 创建 SQLite 数据库

## 🔌 设备协议支持

### ✅ 已实现协议
| 协议 | 模块 | 说明 |
|------|------|------|
| **Atlas FIT/PF-series** | `netty/protocol/codec/` + `handler/` | 自定义 Netty Codec，支持心跳保活、命令下发、报警解析 |
| **Modbus TCP** | PLC4X Driver | 通过 `plc4j-driver-modbus` 接入 |
| **Siemens S7** | PLC4X Driver | 通过 `plc4j-driver-s7` 接入（按需启用） |
| **Serial (RS232/485)** | jSerialComm | 串口设备通信支持 |

### 🔁 FIT 协议特性
- 基于 `ByteToMessageCodec` 实现帧编解码
- `IdleStateHandler` + `WRITER_IDLE` 实现写空闲心跳检测
- `sendCmdAsync(cmd, needRsp=false)` 支持 fire-and-forget 异步命令
- 报警数据解析等通用逻辑抽取至 `FitDataUtils` 复用

## 🗃️ 数据库说明

- **类型**：SQLite 3.49.1.0（嵌入式，无需独立服务）
- **位置**：`${user.home}/tightening_system/tightening.db`
- **优化参数**（`application.yaml` 中预置）：
  ```sql
  PRAGMA journal_mode=WAL;        -- 提升并发写入性能
  PRAGMA synchronous=NORMAL;      -- 平衡性能与数据安全
  PRAGMA cache_size=1000;         -- 4MB 内存缓存
  PRAGMA temp_store=MEMORY;       -- 临时表存内存
  PRAGMA busy_timeout=5000;       -- 锁等待超时 5 秒
  ```
- **版本管理**：Flyway 自动执行 `db/migration/` 下的迁移脚本

## ⚙️ 关键配置项

| 配置前缀 | 说明 | 示例文件 |
|----------|------|----------|
| `spring.datasource` | SQLite 连接与连接池 | `application.yaml` |
| `mybatis-plus` | ORM 映射与逻辑删除配置 | `application.yaml` |
| `device-config` | 设备连接/扫描线程池参数 | `application-dev.yml` |
| `tool-control.atlas.fit` | Atlas FIT 协议心跳与重试 | `application-dev.yml` |
| `logging.level` | 日志级别控制（支持调试 Netty/PLC4X） | `application.yaml` |

## 🐛 常见问题

| 现象 | 可能原因 | 建议 |
|------|----------|------|
| 启动报 `SQLite error` | 目录无写权限 | 确保 `${user.home}/tightening_system/` 可写 |
| 设备连接超时 | IP/端口错误或防火墙拦截 | 检查 `application-*.yml` 中设备地址配置 |
| 心跳频繁断开 | `heart-beat-interval-ms` 与设备端不匹配 | 调整为设备支持的间隔（默认 30s） |
| 数据库锁等待 | 多连接并发写入 | 确认已启用 `busy_timeout=5000` 和 WAL 模式 |

## 🤝 贡献指南

1. Fork 本仓库，创建特性分支（如 `feat/atlas-alarm-parse`）
2. 遵循项目现有代码风格（Lombok + 驼峰命名）
3. 新增协议解析请补充单元测试（参考 `netty/protocol/codec/` 下测试类）
4. 提交前执行 `mvn test` 确保无回归
5. 更新本文档中协议支持列表

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 开源协议。

## 📬 联系作者

- 👤 **Streen Jiang**
- 🔗 GitHub: [@StreenJiang](https://github.com/StreenJiang)
- 💬 欢迎通过 [Issues](https://github.com/StreenJiang/tightening/issues) 反馈问题或提出需求
