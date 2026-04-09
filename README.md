# 🔩 Tightening

> Industrial tightening process control system

🌍 **Languages**: 
[English](README.md) | [中文](README.zh.md)

[![Java](https://img.shields.io/badge/Java-17-blue)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache--2.0-yellow.svg)](LICENSE)

## 📋 Project Overview

**Tightening** is an industrial tightening process control system built with Java, Spring Boot, and Netty. It focuses on:

- 🔌 **Multi-protocol device integration** (Atlas Copco FIT/PF-series, Modbus TCP, Siemens S7, Serial devices)
- 📡 **Async TCP communication & heartbeat keep-alive** powered by Netty
- 🗃️ **SQLite embedded database** + Flyway for version-controlled schema migrations
- ⚙️ **Device lifecycle management**, command dispatch, real-time data acquisition & event handling

Designed for edge deployment at assembly workstations, supporting lightweight, standalone operation without heavy infrastructure dependencies.

## 🧰 Tech Stack

| Module | Version | Purpose |
|--------|---------|---------|
| **Spring Boot** | 3.5.10 | Application framework |
| **Java** | 17 | Core language |
| **Netty** | 4.1.129.Final | Async device communication |
| **MyBatis-Plus** | 3.5.9 | Data persistence & ORM |
| **SQLite JDBC** | 3.49.1.0 | Embedded database |
| **Flyway** | - | Database migration management |
| **PLC4X** | 0.13.1 | Modbus / S7 protocol drivers |
| **jSerialComm** | 2.10.2 | Serial (RS232/485) communication |
| **Lombok** | - | Boilerplate code reduction |

## 📁 Project Structure

```
tightening/
├── src/main/java/com/tightening/
│   ├── annotation/     # Custom annotations (e.g., device command control)
│   ├── config/         # Spring configuration classes
│   ├── constant/       # Global constants
│   ├── controller/     # Web API controllers
│   ├── device/         # Device management core
│   │   ├── event/      # Device event definitions
│   │   ├── handler/    # Device connection/communication handlers
│   │   ├── type/       # Device type enums
│   │   ├── DeviceHolder.java    # Device connection holder
│   │   └── DeviceManager.java   # Device lifecycle manager
│   ├── dto/            # Data Transfer Objects
│   ├── entity/         # Database entities
│   ├── mapper/         # MyBatis-Plus Mapper interfaces
│   ├── netty/protocol/ # Netty protocol layer
│   │   ├── codec/      # Encoders/Decoders (e.g., FitFrameCodec)
│   │   ├── handler/    # ChannelHandler implementations
│   │   └── util/       # Protocol utilities (FitDataUtils, etc.)
│   ├── service/        # Business logic layer
│   ├── util/           # Common utilities
│   └── TighteningApplication.java
├── src/main/resources/
│   ├── application.yaml           # Main configuration
│   ├── application-dev.yml        # Development environment
│   ├── application-standalone.yml # Standalone deployment
│   └── db/migration/              # Flyway migration scripts
├── pom.xml
├── LICENSE
└── README.md
```

## 🚀 Quick Start

### 1. Prerequisites
- JDK 17+
- Maven 3.8+

### 2. Clone & Build
```bash
git clone https://github.com/StreenJiang/tightening.git
cd tightening
mvn clean package -DskipTests
```

### 3. Configuration
Edit `src/main/resources/application-dev.yml` to match your environment:

```yaml
# Device connection thread pool
device-config:
  connect-thread:
    core-pool-size: 5
    max-pool-size: 10
    keep-alive-time-ms: 30000
    capacity: 50
  scan-thread:
    init-delay-ms: 0
    delay-ms: 5000

# Atlas FIT protocol heartbeat
tool-control:
  common:
    enable_disable_cooldown_ms: 5000
  atlas:
    fit:
      heart-beat-interval-ms: 30000
      heart-beat-retry-max: 3
```

### 4. Run Application
```bash
# Development mode
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Standalone mode
java -jar target/tightening-0.0.1-SNAPSHOT.jar --spring.profiles.active=standalone
```

### 5. Verify
- Console prints `Started TighteningApplication` on success
- First run automatically creates SQLite DB at `${user.home}/tightening_system/tightening.db`

## 🔌 Device Protocol Support

### ✅ Implemented Protocols
| Protocol | Module | Description |
|----------|--------|-------------|
| **Atlas FIT/PF-series** | `netty/protocol/codec/` + `handler/` | Custom Netty Codec with heartbeat, command dispatch & alarm parsing |
| **Modbus TCP** | PLC4X Driver | via `plc4j-driver-modbus` |
| **Siemens S7** | PLC4X Driver | via `plc4j-driver-s7` (enable as needed) |
| **Serial (RS232/485)** | jSerialComm | Legacy serial controller support |

### 🔁 FIT Protocol Features
- Frame encoding/decoding via `ByteToMessageCodec`
- Heartbeat detection using `IdleStateHandler` + `WRITER_IDLE`
- `sendCmdAsync(cmd, needRsp=false)` supports fire-and-forget async commands
- Common alarm parsing logic extracted to `FitDataUtils` for reuse

## 🗃️ Database

- **Type**: SQLite 3.49.1.0 (embedded, zero external dependencies)
- **Location**: `${user.home}/tightening_system/tightening.db`
- **Optimized PRAGMAs** (pre-configured in `application.yaml`):
  ```sql
  PRAGMA journal_mode=WAL;        -- Improves concurrent write performance
  PRAGMA synchronous=NORMAL;      -- Balances performance & data safety
  PRAGMA cache_size=1000;         -- ~4MB memory cache
  PRAGMA temp_store=MEMORY;       -- Temp tables in memory
  PRAGMA busy_timeout=5000;       -- 5s lock wait timeout
  ```
- **Migrations**: Flyway auto-executes scripts under `db/migration/`

## ⚙️ Key Configuration

| Config Prefix | Description | File |
|---------------|-------------|------|
| `spring.datasource` | SQLite connection & HikariCP pool | `application.yaml` |
| `mybatis-plus` | ORM mapping & logical delete settings | `application.yaml` |
| `device-config` | Device connect/scan thread pool params | `application-dev.yml` |
| `tool-control.atlas.fit` | Atlas FIT heartbeat interval & retry | `application-dev.yml` |
| `logging.level` | Log level control (supports Netty/PLC4X debug) | `application.yaml` |

## 🐛 FAQ

| Issue | Possible Cause | Solution |
|-------|----------------|----------|
| `SQLite error` on startup | Directory lacks write permissions | Ensure `${user.home}/tightening_system/` is writable |
| Device connection timeout | Incorrect IP/Port or firewall blocking | Verify addresses in `application-*.yml`, check network reachability |
| Frequent heartbeat drops | `heart-beat-interval-ms` mismatches device settings | Adjust to device-supported interval (default: `30000`) |
| Database lock wait timeout | Concurrent multi-thread writes | Confirm `busy_timeout=5000` and `journal_mode=WAL` are active |

## 🤝 Contributing

1. Fork this repo and create a feature branch (e.g., `feat/atlas-alarm-parse`)
2. Follow the existing code style (Lombok + camelCase conventions)
3. Add unit tests for new protocol parsing (refer to `netty/protocol/codec/` tests)
4. Run `mvn test` to ensure no regressions
5. Update the protocol support list in this document

## 📄 License

This project is licensed under the [Apache License 2.0](LICENSE).

## 📬 Contact

- 👤 **Streen Jiang**
- 🔗 GitHub: [@StreenJiang](https://github.com/StreenJiang)
- 💬 Feel free to open an [Issue](https://github.com/StreenJiang/tightening/issues) for:
  - New device protocol requests
  - Edge deployment troubleshooting
  - Architecture & performance suggestions
