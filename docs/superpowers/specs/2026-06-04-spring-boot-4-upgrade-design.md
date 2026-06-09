# Spring Boot 3.5.10 → 4.0.6 升级设计

## 目标

将项目从 Spring Boot 3.5.10 升级到 4.0.6，利用虚拟线程默认开启、模块化自动配置等新特性，
适配目标硬件 Intel Celeron N2840 + Mini-ITX + 4GB DDR3 + 64GB SSD。

## 背景

- Spring Boot 4.0.0 于 2025-11-20 GA 发布，最新稳定版 4.0.6（2026-04-23）
- 项目当前 SB 3.5.10，OSS 支持将于 2026-06-30 结束
- 目标硬件为低功耗嵌入式工控机，4GB 内存是核心约束
- 部署方式：传统 fat JAR，预期连接 5-20 台拧紧控制器

## 依赖变更

### pom.xml

| 项 | 当前 | 目标 |
|---|---|---|
| spring-boot-starter-parent | 3.5.10 | **4.0.6** |
| MyBatis-Plus starter | `mybatis-plus-spring-boot3-starter:3.5.9` | **`mybatis-plus-spring-boot4-starter:3.5.16`** |
| Flyway | `flyway-core`（直接依赖） | **`spring-boot-starter-flyway`**（SB 4 模块化要求） |

### 无需变更的依赖

以下依赖不受 SB 版本管理，保持当前版本：
- Netty 4.1.129.Final（TCP 设备通信）
- SQLite JDBC 3.49.1.0（数据库驱动）
- PLC4X 0.13.1（PLC 协议）
- jSerialComm 2.10.2（串口通信）
- commons-lang3 3.18.0（工具类）

## 代码变更

### Jackson 3 迁移

Spring Boot 4.0 强制升级到 Jackson 3。关键变化：
- Java 包名从 `com.fasterxml.jackson` → `tools.jackson`（core、databind）
- 注解包名不变：`com.fasterxml.jackson.annotation`（Jackson 2/3 共享）
- `ObjectMapper` → `JsonMapper`（不可变 builder 模式）
- `JsonProcessingException` → `JacksonException`（变为 unchecked）

#### JsonUtils.java

```java
// 当前
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static <T> T parse(String detail, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(detail, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}

// 目标
import tools.jackson.databind.JsonMapper;

public class JsonUtils {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    public static <T> T parse(String detail, Class<T> clazz) {
        return JSON_MAPPER.readValue(detail, clazz);
    }
}
```

#### Converter.java

同 JsonUtils：`ObjectMapper` → `JsonMapper.builder().build()`，去掉 try-catch。

#### Arranger.java

`@JsonProperty` 注解包名保持 `com.fasterxml.jackson.annotation`，**无需修改**。

## JVM 参数调优

针对 N2840 双核 + 4GB DDR3：

```
-Xms128m -Xmx384m -XX:+UseG1GC -XX:MaxGCPauseMillis=100
```

- 堆上限 384MB，为 OS 和 Netty 堆外内存留有余量
- G1GC 适合低延迟 TCP 通信场景
- SB 4.0 默认启用虚拟线程，Netty 多连接 I/O 直接受益

## 风险与验证

| 风险 | 缓解措施 |
|---|---|
| MyBatis-Plus 3.5.16 API 兼容 | 运行现有测试，`mvn clean test` |
| Jackson 3 序列化行为变化 | 默认值变更（日期格式、枚举、未知属性），检查 JSON 序列化/反序列化 |
| Flyway starter 迁移 | 启动应用确认迁移正常执行 |
| 4GB 内存 OOM | 压测 20 路设备连接，观察堆使用 |

## 不涉及的部分

- Spring Security — 项目未使用
- JPA/Hibernate — 项目使用 MyBatis-Plus，无 Hibernate 依赖
- Undertow — 项目使用 Tomcat（SB 4 默认），不受 Undertow 移除影响
- Kotlin — 纯 Java 项目

## 实施步骤

1. 修改 pom.xml（parent 版本、MyBatis-Plus starter、Flyway starter）
2. 修改 JsonUtils.java（Jackson 3 API 迁移）
3. 修改 Converter.java（同上）
4. 添加 JVM 参数到启动脚本
5. `mvn clean test` 验证
6. 启动应用确认 Flyway 迁移正常
