# Dependency Upgrade (2026-06-21)

## 目标

升级非大版本的依赖到最新稳定版。

## 变更

| 属性 | 当前 | 目标 |
|---|---|---|
| mybatis-plus.version | 3.5.9 | 3.5.16 |
| sqlite.version | 3.49.1.0 | 3.53.2.0 |
| jserialcomm.version | 2.10.2 | 2.11.4 |
| commons-lang3.version | 3.18.0 | 3.20.0 |

Lombok 由 Spring Boot 父 POM 管理版本，通过 `spring-boot-starter-parent` 中升级（实际版本 1.18.42 → 1.18.46 由父 POM 控制，无需在 pom.xml 显式声明）。

## 不升级

- Spring Boot 3.5.10（大版本 4.x 风险高）
- Flyway（由 Spring Boot 父 POM 管理 11.7.2，12.x 需大版本迁移）
- Netty 4.1.129.Final（5.x 是 Alpha）
- AssertJ 3.27.7（4.x 是 M1）
- PLC4X 0.13.1（已是最新）

## 验证

```bash
./mvnw clean test
```

所有 310 个测试通过即视为升级成功。
