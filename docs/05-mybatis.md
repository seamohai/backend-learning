# 05 · 数据访问：MyBatis-Plus 持久化、逻辑删除与乐观锁

> - 模块：[05-mybatis](../05-mybatis)
> - 工程坐标：`top.seamohai:beckend-learning:0.0.1-SNAPSHOT`
> - 环境：JDK **21** ｜ Spring Boot **4.1.1** ｜ MyBatis-Plus **3.5.17** ｜ MySQL **8.x** ｜ HikariCP ｜ 端口 **8005**
> - 本篇是**自包含学习笔记**：所有概念都在文中解释清楚，不需要另查任何资料
> - 和 01 那篇不一样的地方：**本文的 SQL 日志、报错原文、测试结果全部是实测结果**，不是按代码推导的

---

## 目录

1. [本节目标](#1-本节目标)
2. [背景：为什么需要 ORM](#2-背景为什么需要-orm)
3. [三层结构：Controller / Service / Mapper](#3-三层结构controller--service--mapper)
4. [环境准备：依赖、表结构与初始化脚本](#4-环境准备依赖表结构与初始化脚本)
5. [实体类：23 个字段与 MySQL 类型映射](#5-实体类23-个字段与-mysql-类型映射)
6. [Mapper 与 Service：不写 SQL 的增删改查](#6-mapper-与-service不写-sql-的增删改查)
7. [条件构造器：LambdaQueryWrapper 与 LambdaUpdateWrapper](#7-条件构造器lambdaquerywrapper-与-lambdaupdatewrapper)
8. [控制器：19 个接口](#8-控制器19-个接口)
9. [自动填充、逻辑删除、乐观锁、事务](#9-自动填充逻辑删除乐观锁事务)
10. [连接池：HikariCP 参数与排查方向](#10-连接池hikaricp-参数与排查方向)
11. [接口与实测结果（Windows 实操）](#11-接口与实测结果windows-实操)
12. [我踩过的 6 个坑](#12-我踩过的-6-个坑)
13. [报错速查表](#13-报错速查表)
14. [练习](#14-练习)
15. [知识地图与名词速查](#15-知识地图与名词速查)
16. [内容小结与学习小结](#16-内容小结与学习小结)
17. [我代码里的待修正点](#17-我代码里的待修正点)
18. [还没搞懂的问题](#18-还没搞懂的问题)

---

## 1. 本节目标

学完这一节，要能独立做到六件事：

1. 建一张覆盖 MySQL 常用数据类型的表，并把每种类型正确映射到 Java；
2. 用 `BaseMapper` / `IService` 完成单表增删改查，**不写一条 SQL**；
3. 用 `MetaObjectHandler` 做字段自动填充，用 `@TableLogic` 做逻辑删除，用 `@Version` 做乐观锁；
4. 用 `LambdaQueryWrapper` / `LambdaUpdateWrapper` 写条件查询与更新；
5. 说清 `@Transactional` 的回滚规则和失效场景；
6. 配好连接池参数，知道池大小与 MySQL `max_connections` 的关系。

一句话概括这条链路：

> **Controller 接单 → Service 管规矩（事务在这）→ Mapper 跑腿 → MyBatis-Plus 翻译成 SQL → 连接池借车 → MySQL 存取。**

用外卖打个比方：Controller 是**前台接待**（只接单和递结果，不做判断），Service 是**店长**（管规矩、管"整单要么全成要么全退"），Mapper 是**采购员**（只管去哪儿拿，不管该不该拿），MyBatis-Plus 是**翻译官**（把 Java 调用译成 SQL），连接池是**车队**（提前备好车，用完还回去），MySQL 是**仓库**。

---

## 2. 背景：为什么需要 ORM

### 2.1 不用 ORM 有多麻烦

用原生 JDBC 查一条用户，要写这一串：

```java
Connection conn = DriverManager.getConnection(url, user, pwd);
PreparedStatement ps = conn.prepareStatement("SELECT * FROM t_user WHERE id = ?");
ps.setLong(1, id);
ResultSet rs = ps.executeQuery();
User user = null;
if (rs.next()) {
    user = new User();
    user.setId(rs.getLong("id"));                    // 手写列名
    user.setUsername(rs.getString("username"));
    user.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());  // 手写类型转换
    // ... 23 个字段要写 23 遍
}
rs.close(); ps.close(); conn.close();                // 别忘了关
```

**每个字段都要手写一遍"从 ResultSet 取出来、转成 Java 类型、塞进对象"。** 表有 23 列就写 23 遍，加一列就要改一处。

### 2.2 ORM 解决的正是"对象 ↔ 表"的搬运

| 对象世界 | 关系世界 |
| --- | --- |
| 引用 / 指针 | 外键 |
| 继承 | 没有原生继承 |
| 集合、List | 中间表 |
| 身份靠 `equals` | 身份靠主键 |

这个错位叫**阻抗失配（impedance mismatch）**。ORM 就是来填这个坑的。

### 2.3 一个必须澄清的定位问题

**严格说，MyBatis 不算 ORM，它是 SQL Mapping（半自动）。** 这个区别决定了后面所有行为的预期：

| | MyBatis / MyBatis-Plus | JPA / Hibernate |
| --- | --- | --- |
| 类型 | **SQL 映射**（半自动） | **ORM**（全自动） |
| 谁写 SQL | 你（或框架帮你写单表） | 框架生成 |
| 有持久化上下文吗 | **没有** | 有（一级缓存、脏检查） |
| 改字段会自动 UPDATE 吗 | **不会** | 会（dirty checking） |

**最重要的一条**：MyBatis 里**改了对象不调 `update` 是绝对不会同步到数据库的**。这点和 Hibernate 完全不同，别混。

> **MyBatis-Plus 加的不是"ORM 能力"，而是"单表 CRUD 的自动化"。** 它的边界很清楚：**单表自动，多表靠手写。** 复杂查询（多表 JOIN、聚合、窗口函数）你还是得写 SQL。

---

## 3. 三层结构：Controller / Service / Mapper

### 3.1 分工

| 层 | 职责 | 本模块的类 |
| --- | --- | --- |
| Controller | 接收参数、调用 Service、返回结果 | `UserController`（19 个接口） |
| Service | 业务逻辑、事务 | `UserService` / `UserServiceImpl`（21 个方法） |
| Mapper | 数据访问、SQL | `UserMapper`（接口体只有 1 个自定义方法） |

### 3.2 唯一的规则：上层只能调紧邻的下层

```
Controller  →  Service  →  Mapper  →  MySQL
     ✅           ✅          ✅
Controller  ──────────────→  Mapper        ❌ 跨层了
```

**为什么不能跨层**？因为业务规则都在 Service：

```java
// Service 的 createUser 里做了三件事
applyDefaults(user);        // 补默认值（password / gender / memberLevel）
save(user);                 // 写库
if (age > 150) throw ...    // 业务校验
```

**如果 Controller 直接调 Mapper，这三件事全部被绕过** —— 这是我在写测试时**真实撞到**的问题（见坑 4）。

### 3.3 本模块的真实目录

```
05-mybatis
├── pom.xml
└── src
    ├── main
    │   ├── java/top/seamohai/mybatis
    │   │   ├── MybatisApplication.java          # 启动类
    │   │   ├── controller/
    │   │   │   ├── UserController.java          # 用户接口，19 个
    │   │   │   └── CourseController.java        # 课程接口，5 个（练习 3 加的）
    │   │   ├── dto/UserQuery.java               # 查询条件对象，7 个字段
    │   │   ├── entity/
    │   │   │   ├── User.java                    # 用户，23 个字段
    │   │   │   ├── Course.java                  # 课程（练习 3）
    │   │   │   └── Order.java                   # 订单（练习 5）
    │   │   ├── mapper/
    │   │   │   ├── UserMapper.java              # 含 selectRawById / slowQuery 两个手写 SQL
    │   │   │   ├── CourseMapper.java            # 练习 3
    │   │   │   └── OrderMapper.java             # 练习 5
    │   │   ├── service/
    │   │   │   ├── UserService.java             # 21 个方法
    │   │   │   ├── CourseService.java           # 含 deductStock（练习 5 的扣库存）
    │   │   │   ├── OrderService.java            # 含 createOrder（练习 5 的核心）
    │   │   │   └── impl/                        # 三个实现类
    │   │   └── config/
    │   │       ├── MyMetaObjectHandler.java     # 字段自动填充
    │   │       └── MybatisPlusConfig.java       # 分页 + 乐观锁插件
    │   └── resources
    │       ├── application.yml
    │       ├── schema.sql                       # 3 张表，每次启动先 DROP 再 CREATE
    │       ├── data.sql                         # 6 条用户示例数据
    │       └── logback-spring.xml               # 沿用 03 那篇的日志配置
    └── test/java/top/seamohai/mybatis
        ├── mapper/
        │   ├── UserMapperCrudTest.java          # Mapper 层 CRUD（5 个用例）
        │   └── CourseMapperTest.java            # 批量增删 + 对照实验（6 个用例）
        ├── pool/ConnectionPoolTest.java         # 连接池压测（5 个实验）
        └── service/impl/
            ├── UserServiceTest.java             # 随堂 5 个练习（5 个用例）
            └── OrderServiceTest.java            # 事务原子性 + 反证（6 个用例）
```

**包名的一处改动**：`UserServiceImpl` 放在 `service.impl` 子包、`UserQuery` 放在 `dto` 包，比教程原来的平铺结构更贴合分层。

**练习过程中新增的部分**（`Course` / `Order` 两张表、`CourseController`、两个压测探针）都在上面标了出来。**共 5 个测试类、27 个用例。**

---

## 4. 环境准备：依赖、表结构与初始化脚本

### 4.1 依赖（关键：版本必须配套）

```xml
<properties>
    <mybatis-plus.version>3.5.17</mybatis-plus.version>
</properties>

<dependencies>
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
        <version>${mybatis-plus.version}</version>
    </dependency>

    <!-- 分页插件 PaginationInnerInterceptor 依赖 JSqlParser，
         3.5.9 起拆成独立构件，需显式引入（不引会编译不过） -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-jsqlparser</artifactId>
        <version>${mybatis-plus.version}</version>
    </dependency>

    <!-- 版本由 spring-boot-dependencies 管理，不用自己写 -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

**三个要点**：

1. Spring Boot 4 用 `-spring-boot4-starter`；Spring Boot 3 对应 `-spring-boot3-starter`。**名字不同，用错会报找不到类**；
2. `mysql-connector-j` 的 `groupId` 是 **`com.mysql`**，不是 `mysql`（`mysql:mysql-connector-java` 是老坐标，已废弃）；
3. **不要给 MySQL 驱动写版本号** —— 父 POM 的 BOM 已经管了（本模块实测拉到 `9.7.0`）。

`mvn dependency:tree` 实测拉到的关键构件：

```
+- com.baomidou:mybatis-plus-spring-boot4-starter:jar:3.5.17:compile
|  +- com.baomidou:mybatis-plus:jar:3.5.17:compile
|  |  \- org.mybatis:mybatis:jar:3.5.19:compile        ← MP 自带 MyBatis，不用自己加
|  +- org.mybatis:mybatis-spring:jar:4.0.0:compile
+- com.mysql:mysql-connector-j:jar:9.7.0:runtime
```

### 4.2 建表脚本

表的定位是**"一张覆盖 MySQL 常用数据类型的教学表"**，所以字段形态刻意做得杂：

```sql
CREATE TABLE t_user
(
    id              BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    username        VARCHAR(50)  NOT NULL COMMENT '登录名',
    password        CHAR(64)     NOT NULL COMMENT '密码，SHA-256 摘要定长 64 位',
    nickname        VARCHAR(50) COMMENT '昵称',
    gender          TINYINT      NOT NULL DEFAULT 0 COMMENT '性别，0 未设置 / 1 男 / 2 女',
    age             TINYINT UNSIGNED COMMENT '年龄',
    member_level    ENUM ('BRONZE','SILVER','GOLD') NOT NULL DEFAULT 'BRONZE' COMMENT '会员等级',
    points          INT          NOT NULL DEFAULT 0 COMMENT '积分',
    balance         DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '账户余额，金额用定点数',
    score           FLOAT COMMENT '推荐评分，近似小数用浮点',
    phone           VARCHAR(20) COMMENT '手机号',
    email           VARCHAR(100) COMMENT '邮箱',
    avatar          VARCHAR(255) COMMENT '头像地址',
    intro           TEXT COMMENT '个人简介，长文本',
    tags            JSON COMMENT '标签数组，如 ["Java","篮球"]',
    birthday        DATE COMMENT '生日',
    work_time       TIME COMMENT '每日上班时间',
    vip             TINYINT      NOT NULL DEFAULT 0 COMMENT '是否会员，0 否 / 1 是',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态，1 正常 / 0 禁用',
    last_login_time DATETIME COMMENT '最后登录时间',
    create_time     DATETIME     NOT NULL COMMENT '创建时间，MyBatis-Plus 自动填充',
    update_time     DATETIME     NOT NULL COMMENT '更新时间，MyBatis-Plus 自动填充',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除 / 1 已删除',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_status_gender (status, gender)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';
```

**脚本开头必须是 `DROP TABLE IF EXISTS t_user;`** —— 因为配了 `spring.sql.init.mode=always`，脚本每次启动都会执行，不先删就会报 `Table 't_user' already exists`。

### 4.3 启动时自动初始化

```yaml
spring:
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
      data-locations: classpath:data.sql
      encoding: UTF-8
```

**`mode` 的默认值是 `embedded`，只对内嵌库（H2、HSQLDB）生效。** 连 MySQL 这类外部库**必须显式改成 `always`**，否则脚本静默不执行——不报错，你只会发现"表怎么没建"。

> **代价**：每次启动（包括 DevTools 热重启）都会 `DROP` 重建表并重灌示例数据，**数据不持久**。真实项目用 Flyway / Liquibase 做版本化迁移，不会这么干。

### 4.4 示例数据的巧思

`data.sql` 插了 6 条数据，故意覆盖了各种边界：

| id | username | 特殊之处 | 用途 |
| --- | --- | --- | --- |
| 1 | admin | vip=1, age=35, 最高积分 | 主查询对象 |
| 2 | zhangsan | age=20, **vip=0** | 验证条件过滤 |
| 3 | lisi | vip=1, age=26 | 充值测试对象 |
| 4 | wangwu | vip=1, age=31 | 删除测试对象 |
| 5 | zhaoliu | **deleted=1** | **验证逻辑删除查不到** |
| 6 | sunqi | **status=0** | 验证按状态过滤 |

**所以"能查到的用户是 4 个"**（1、2、3、4），5 已被逻辑删除、6 状态为禁用。这个数字在后面测试里反复用到。

---

## 5. 实体类：23 个字段与 MySQL 类型映射

### 5.1 类型映射对照

| 用在什么字段上 | MySQL 类型 | Java 类型 | 本模块字段 |
| --- | --- | --- | --- |
| 自增主键 | `BIGINT` | `Long` | `id` |
| 用户名、标题 | `VARCHAR(n)` | `String` | `username`、`nickname` |
| 手机号、摘要 | `CHAR(n)` | `String` | `password`（SHA-256 定长 64） |
| 状态、布尔 | `TINYINT` | `Integer` / `Boolean` | `gender`、`vip` |
| 年龄、积分 | `INT` / `TINYINT` | `Integer` | `age`、`points` |
| **金额** | **`DECIMAL(m,d)`** | **`BigDecimal`** | `balance` |
| 评分 | `FLOAT` | `Float` | `score` |
| 固定枚举 | `ENUM(...)` | `String` | `memberLevel` |
| 日期 / 时间 | `DATE` / `TIME` / `DATETIME` | `LocalDate` / `LocalTime` / `LocalDateTime` | `birthday`、`workTime`、`createTime` |
| 长文本 | `TEXT` | `String` | `intro` |
| 结构化数据 | `JSON` | `List<String>` + typeHandler | `tags` |

**两个不能踩的**：

- **金额必须用 `DECIMAL`**，不能用 `FLOAT` / `DOUBLE`。浮点是近似值，多次累加后舍入误差会算错账。所以 `balance` 用 `BigDecimal`；
- **`UNSIGNED` 不是"更省空间"，而是"不允许负数"**。本模块 `age` 是 `TINYINT UNSIGNED`，写 `-1` 会被数据库直接拒绝并报 `Out of range value`。所以业务层的校验要提前拦住负数。

### 5.2 实体类完整代码

```java
@Data
@TableName(value = "t_user", autoResultMap = true)   // ← autoResultMap 不能漏！
public class User {

    @TableId(type = IdType.AUTO)                     // 对应 AUTO_INCREMENT
    private Long id;

    private String username;

    /** 只接收不返回，避免接口把密码摘要吐出去 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String nickname;
    private Integer gender;
    private Integer age;
    private String memberLevel;
    private Integer points;
    private BigDecimal balance;
    private Float score;
    private String phone;
    private String email;
    private String avatar;
    private String intro;

    /** JSON 列，autoResultMap = true 才会走 typeHandler 反序列化 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<String> tags;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime workTime;

    private Boolean vip;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
}
```

### 5.3 四个注解逐条说清

**① `@TableName(value = "t_user", autoResultMap = true)`**

`value` 指定表名。类名 `User` 默认推出表名 `user`，推不出 `t_user`，所以必须写。

**`autoResultMap = true` 是 JSON 字段能查出来的前提**。漏了它，`tags` 查出来**永远是 `null`**——不报错、不抛异常，只是数据静默丢失。这是本模块最容易忽略的一条。

`autoResultMap` 的作用是：让 MyBatis-Plus 自动生成一个带 `typeHandler` 的 `resultMap`，而不是用默认的自动映射。

**② `@TableId(type = IdType.AUTO)`**

**必须和 DDL 里的 `AUTO_INCREMENT` 配套。** 如果用了默认的 `ASSIGN_ID`（雪花算法），MyBatis-Plus 会自己塞一个 19 位 ID 进去，数据库的自增列就被架空了——更坑的是**雪花 ID 超过 JS 的 `Number.MAX_SAFE_INTEGER`（2^53），返给前端会精度丢失**，得额外配 `@JsonSerialize(using = ToStringSerializer.class)`。

> **要么 DB 自增、要么应用生成雪花，别两边都想要。**

**③ `@TableField(fill = ...)`**

`FieldFill.INSERT` 只在新增时填充，`FieldFill.INSERT_UPDATE` 新增和更新都填。填充逻辑在 `MyMetaObjectHandler` 里（见第 9 节）。

**④ `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)`**

让 `password` **能随请求体传进来，但不出现在响应里**。实测 `GET /users/1` 的返回中确认没有 `password` 字段。

### 5.4 `Jackson3TypeHandler` 而不是 `JacksonTypeHandler`

这是一个**真实踩到的坑**。MyBatis-Plus 提供两个 JSON typeHandler：

| 类 | 走的 Jackson | 适用 |
| --- | --- | --- |
| `JacksonTypeHandler` | Jackson **2**（`com.fasterxml.jackson`） | Spring Boot 3 及以前 |
| **`Jackson3TypeHandler`** | Jackson **3**（`tools.jackson`） | **Spring Boot 4** |

**Spring Boot 4.1.1 默认集成的是 Jackson 3**（本机实测 `tools.jackson:jackson-databind:3.1.5`），所以必须用 `Jackson3TypeHandler`。

**验证方法**（我用它确认过）：

```powershell
# 看本地仓库里有没有 Jackson 3 的注解类
Test-Path "D:\Maven\maven_jar\tools\jackson\core\jackson-databind\3.1.5\jackson-databind-3.1.5.jar"
```

**⚠️ 一个曾经存疑、现已实测确认的点**：Jackson 3 的 jar 里没有 `com.fasterxml.jackson.annotation.JsonFormat`（只有 `tools.jackson.*`），而本模块的 `@JsonFormat` 用的仍是 Jackson 2 的导入。那它到底生效没有？

**实测结论：生效，而且是双向的。** 依据是反证法——如果注解被忽略，Jackson 3 的 `LocalDateTime` 默认会输出 ISO-8601 格式（**带 `T`**）：

```json
"createTime": "2026-09-01T10:00:00"     ← 注解被忽略时该有的样子
```

**实际拿到的是**：

```json
"createTime": "2026-09-01 10:00:00"     ← 空格分隔，说明 pattern 生效
```

反方向（JSON → Java）也验证过：`POST /users` 传 `"lastLoginTime": "2026-10-11 09:15:30"`、`"birthday": "2000-01-02"`、`"workTime": "08:30:00"`，**全部被正确解析**。

**所以不需要把导入改成 `tools.jackson.annotation.*`** —— Spring Boot 4 的 Jackson 3 与 Jackson 2 的注解是兼容的（类路径上那份 `jackson-annotations:2.21` 是真实生效的依赖，不是摆设）。

**另外要澄清一个常见误解**：`@JsonFormat` 是**日期时间专用**的格式化注解，只对 `LocalDate` / `LocalTime` / `LocalDateTime` 有意义。给 `String`、`Integer`、`Boolean` 加上它，Jackson 会直接忽略，属于无效配置。它对 `BigDecimal` 的唯一有意义用法是 `shape = JsonFormat.Shape.STRING`（把金额序列化成字符串，避免前端浮点精度问题）——那是改接口契约的抉择，不是纯粹的"补注解"。

---

## 6. Mapper 与 Service：不写 SQL 的增删改查

### 6.1 Mapper：接口体几乎是空的

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 绕过逻辑删除，直接查物理行（用于测试和数据维护） */
    @Select("SELECT id, username, nickname, deleted, version FROM t_user WHERE id = #{id}")
    User selectRawById(@Param("id") Long id);
}
```

**除了那一个自定义方法，其余全是继承来的。** 加上 `@Mapper` 注解后也不需要 `@MapperScan`。

`BaseMapper` 提供的方法：

| 方法 | 作用 |
| --- | --- |
| `insert(entity)` | 新增，**主键回填到实体** |
| `deleteById(id)` / `deleteByIds(ids)` | 按主键删除（配 `@TableLogic` 时是逻辑删除） |
| `delete(Wrapper)` | 按条件删除 |
| `updateById(entity)` | 按主键更新，**只更新非 null 字段** |
| `update(entity, Wrapper)` | 按条件更新 |
| `selectById(id)` | 按主键查询（查不到返回 `null`，不抛异常） |
| `selectBatchIds(ids)` | 按主键批量查询 |
| `selectList(Wrapper)` / `selectOne(Wrapper)` | 条件查询 |
| `selectCount(Wrapper)` | 计数 |
| `selectPage(page, Wrapper)` | 分页 |
| `selectObjs(Wrapper)` | 只取第一列的值 |
| `selectMaps(Wrapper)` | 返回 `Map` 列表，做分组统计时用 |

**这个接口为什么能凭空有实现**：MyBatis 在启动时为它**动态生成了代理对象**（JDK 动态代理），方法名 + 注解 + 实体信息组合成 SQL。所以你 `new UserMapper()` 是拿不到的，必须让 Spring 注入。

### 6.2 Service：在 Mapper 之上再包一层

```java
public interface UserService extends IService<User> {
    /* 增 */ boolean createUser(User user);  boolean createUserBatch(List<User> users);
    /* 删 */ boolean removeUser(Long id);    boolean removeUserBatch(List<Long> ids);
             boolean removeUserByStatus(Integer status);
    /* 改 */ boolean modifyUser(User user);  boolean modifyUserStatus(Long id, Integer status);
             boolean recharge(Long id, BigDecimal amount);
    /* 查 */ User getUser(Long id);  List<User> listUsers();  Page<User> pageUsers(long c, long s);
             List<User> listByCondition(UserQuery query);
             Page<User> pageByCondition(UserQuery query, long c, long s);
             List<User> searchByKeyword(String keyword);
             List<User> listByAgeRange(Integer minAge, Integer maxAge);
             List<User> listTop(Integer limit);  List<String> listUsernames();
             List<User> listByTag(String tag);   boolean existsUsername(String username);
             long countByGender(Integer gender); Map<String, Object> statistics();
}
```

**`IService` 和 `ServiceImpl` 在 3.5.9 之后换了包**：

| | 旧包（3.5.9 之前） | **新包（本模块用的）** |
| --- | --- | --- |
| `IService` | `com.baomidou.mybatisplus.extension.service` | **`com.baomidou.mybatisplus.spring.service`** |
| `ServiceImpl` | `com.baomidou.mybatisplus.extension.service.impl` | **`com.baomidou.mybatisplus.spring.service.impl`** |

**IDE 如果自动补全成旧路径，编译会报找不到类。** 我实测过 jar 里的真实路径，确认是 `spring` 而不是 `extension`。

### 6.3 `BaseMapper` 和 `IService` 的分工

| 操作 | `BaseMapper` | `IService`（本模块封装后用这些） |
| --- | --- | --- |
| 新增 | `insert` | `save` → `createUser` |
| 批量新增 | — | `saveBatch(collection, batchSize)` → `createUserBatch` |
| 按主键更新 | `updateById` | `updateById` → `modifyUser` |
| 按条件更新 | `update(entity, Wrapper)` | `update(Wrapper)` → `modifyUserStatus` |
| 按主键删除 | `deleteById` | `removeById` → `removeUser` |
| 批量删除 | `deleteByIds` | `removeByIds` → `removeUserBatch` |
| 按条件删除 | `delete(Wrapper)` | `remove(Wrapper)` → `removeUserByStatus` |
| 按主键查询 | `selectById` | `getById` → `getUser` |
| 查询全部 | `selectList(null)` | `list()` → `listUsers` |
| 条件列表 | `selectList(Wrapper)` | `list(Wrapper)` → `listByCondition` |
| 分页 | `selectPage(page, Wrapper)` | `page(page, Wrapper)` → `pageByCondition` |
| 计数 | `selectCount` | `count(Wrapper)` → `countByGender` |
| 存在判断 | — | `exists(Wrapper)` → `existsUsername` |

**规律**：`BaseMapper` 是"select/insert/update/delete"命名，`IService` 是"get/list/save/remove/page"命名。**Service 层多出来的是批量操作和 `exists`。**

### 6.4 三个有代表性的实现

**① 事务 + 默认值填充（`createUser`）**

```java
@Transactional(rollbackFor = Exception.class)
@Override
public boolean createUser(User user) {
    applyDefaults(user);          // 补 NOT NULL 列的默认值 + 密码转 SHA-256
    save(user);                   // 第一步：真写库
    if (user.getAge() != null && (user.getAge() < 0 || user.getAge() > 150)) {
        throw new RuntimeException("年龄超出合理范围，回滚");   // 第二步：抛异常
    }
    return true;
}
```

**这段代码是刻意这么写的**：先写库、再抛异常，用来**观察第一步有没有被回滚**。`age` 列是 `TINYINT UNSIGNED`（上限 255），所以 `200` 能通过数据库校验、真的插进去，随后才被业务校验拦下。

**② 批量新增（`createUserBatch`）**

```java
@Transactional(rollbackFor = Exception.class)
@Override
public boolean createUserBatch(List<User> users) {
    if (users == null || users.isEmpty()) {
        return false;
    }
    users.forEach(this::applyDefaults);      // 每条都要补默认值，否则 NOT NULL 报错
    return saveBatch(users, BATCH_SIZE);     // BATCH_SIZE = 100
}
```

**为什么用 `saveBatch` 而不是循环 `save`**：前者是 JDBC 批处理，多条 INSERT 攒成一批发给数据库，网络往返次数少，快得多。第二个参数是批大小，攒够 100 条发一批。

**③ 乐观锁更新（`recharge`）**

```java
@Override
public boolean recharge(Long id, BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("充值金额必须大于 0");
    }
    User user = getById(id);                 // ← 必须先查，实体才有 version
    if (user == null) {
        throw new IllegalArgumentException("用户不存在：" + id);
    }
    user.setBalance(user.getBalance().add(amount));
    return updateById(user);                 // 带 version 的 UPDATE
}
```

---

## 7. 条件构造器：LambdaQueryWrapper 与 LambdaUpdateWrapper

### 7.1 为什么要用它

拼 `WHERE` 条件有两种写法：

```java
// ❌ 字符串拼接：字段改名后编译器不管，运行时才炸
new QueryWrapper<User>().eq("usrname", "admin");     // 拼错了，运行时 Unknown column

// ✅ 方法引用：字段改名编译器立刻报错
new LambdaQueryWrapper<User>().eq(User::getUsername, "admin");
```

**`User::getUsername` 这个方法引用的价值在于**：它引用的是**方法**，不是字符串。你把 `username` 字段改名，编译直接失败——**错误从运行期提前到了编译期**。

### 7.2 常用写法速查

| 需求 | 写法 |
| --- | --- |
| 等于 / 不等于 | `.eq(User::getGender, 1)` / `.ne(...)` |
| 模糊 | `.like(User::getUsername, "张")`，还有 `likeLeft` / `likeRight` |
| 大小比较 | `.gt` / `.ge` / `.lt` / `.le` |
| 区间 | `.between(User::getAge, 20, 30)` |
| 集合 | `.in(User::getId, ids)` / `.notIn(...)` |
| 空值判断 | `.isNull` / `.isNotNull` |
| 排序 | `.orderByAsc(User::getId)` / `.orderByDesc(User::getPoints)` |
| 或 | `.or()` |
| 拼原生 SQL 片段 | `.apply("JSON_CONTAINS(tags, JSON_QUOTE({0}))", tag)` |
| 追加到末尾 | `.last("LIMIT 3")` |
| 只查某几列 | `.select(User::getUsername)` |
| 分组 | `QueryWrapper.select("gender", "COUNT(*) AS cnt").groupBy("gender")` |
| **条件可选** | `.eq(条件, 字段, 值)`，条件为 `false` 时该分支不进 SQL |

**最后一条是精髓**，它让"传了才加条件"不需要写 if：

```java
.eq(condition.getGender() != null, User::getGender, condition.getGender())
```

### 7.3 `buildCondition`：动态条件拼装

```java
private LambdaQueryWrapper<User> buildCondition(UserQuery query) {
    UserQuery condition = query == null ? new UserQuery() : query;   // 兜底，避免反复判空
    return new LambdaQueryWrapper<User>()
            .eq(condition.getGender() != null, User::getGender, condition.getGender())
            .eq(condition.getStatus() != null, User::getStatus, condition.getStatus())
            .eq(condition.getVip() != null, User::getVip, condition.getVip())
            .eq(StringUtils.hasText(condition.getMemberLevel()),
                    User::getMemberLevel, condition.getMemberLevel())
            .ge(condition.getMinAge() != null, User::getAge, condition.getMinAge())
            .le(condition.getMaxAge() != null, User::getAge, condition.getMaxAge())
            .and(StringUtils.hasText(condition.getKeyword()),
                    w -> w.like(User::getUsername, condition.getKeyword())
                            .or()
                            .like(User::getNickname, condition.getKeyword()))
            .orderByDesc(User::getId);
}
```

**三个容易忽略的点**：

1. **`query` 本身可能为 `null`**（比如 `GET /users` 一个参数都不带），兜底成空对象后就不用反复判空了；
2. **`keyword` 必须用 `and(w -> ...)` 包一层括号**。不包的话它里面的 `or` 会把前面所有条件一起"放开"，变成 `(gender=1 AND status=1) OR username LIKE ...`，结果就错了；
3. `orderByDesc` 加在最后，SQL 末尾拼上 `ORDER BY id DESC`。

### 7.4 只查一列：`listUsernames`

```java
@Override
public List<String> listUsernames() {
    return listObjs(new LambdaQueryWrapper<User>()
            .select(User::getUsername)
            .orderByAsc(User::getId), Object::toString);
}
```

生成的 SQL 是 `SELECT username FROM t_user WHERE deleted=0 ORDER BY id ASC`，**不把 23 个字段都查出来**。适合下拉框、自动补全这类只要一个字段的场景。

### 7.5 JSON 列查询：`listByTag`

```java
@Override
public List<User> listByTag(String tag) {
    return list(new LambdaQueryWrapper<User>()
            .apply("JSON_CONTAINS(tags, JSON_QUOTE({0}))", tag)
            .orderByAsc(User::getId));
}
```

底层是 MySQL 5.7+ 的 JSON 函数：`WHERE JSON_CONTAINS(tags, JSON_QUOTE(?))`。**`tags` 列存的是 JSON 文本，但可以直接在数据库里查。**

**关于 `{0}` 占位符**：`apply()` 里的 `{0}` 会被替换成预编译占位符 `?`，**拼进去的是参数值而不是 SQL 片段**，所以这里是安全的。

### 7.6 分组统计：`statistics`

```java
@Override
public Map<String, Object> statistics() {
    Map<String, Object> result = new LinkedHashMap<>();

    // 单行聚合：getMap 取一行
    Map<String, Object> aggregate = getMap(new QueryWrapper<User>()
            .select("COUNT(*) AS totalCount",
                    "IFNULL(SUM(balance), 0) AS totalBalance",
                    "IFNULL(MAX(age), 0) AS maxAge"));
    if (aggregate != null) {
        result.putAll(aggregate);
    }

    // 多行分组：listMaps 取多行
    result.put("genderCount", listMaps(new QueryWrapper<User>()
            .select("gender", "COUNT(*) AS cnt")
            .groupBy("gender")
            .orderByAsc("gender")));
    return result;
}
```

**这里刻意改用 `QueryWrapper`（而不是 Lambda 版）**，因为 `COUNT(*)`、`SUM(balance)` 这些聚合表达式没法用方法引用表达，只能写字符串。**返回的也不再是实体，而是原始 `Map`。**

### 7.7 `last()` 的安全性（重要）

```java
@Override
public List<User> listTop(Integer limit) {
    int size = limit == null || limit <= 0 ? 3 : Math.min(limit, 50);   // ← 钳制
    return list(new LambdaQueryWrapper<User>()
            .orderByDesc(User::getPoints)
            .last("LIMIT " + size));                                     // 原样拼字符串
}
```

**`last()` 是原样拼字符串，不走预编译占位符**，所以**绝对不能拼用户传入的原始字符串**，否则就是 SQL 注入。

本模块靠 `Math.min(limit, 50)` 保证拼进去的**一定是算过的数字**，最多 50 条。这个钳制不是为了分页，**是为了安全**。

### 7.8 更新用 `LambdaUpdateWrapper`

```java
@Override
public boolean modifyUserStatus(Long id, Integer status) {
    return update(new LambdaUpdateWrapper<User>()
            .eq(User::getId, id)
            .set(User::getStatus, status));
}
```

`set()` 指定的字段会出现在 `SET` 子句里，生成的 SQL：

```sql
UPDATE t_user SET status=?, update_time=? WHERE id=? AND deleted=0
```

**注意这里没有乐观锁条件** —— 因为它没有先把实体查出来，实体上没有 `version` 值，MyBatis-Plus 就不会拼版本条件。对"改状态"这种操作通常可接受。

---

## 8. 控制器：19 个接口

### 8.1 类头三个注解

```java
@RestController              // = @Controller + @ResponseBody，返回值自动转 JSON
@RequestMapping("/users")    // 类级公共前缀
@RequiredArgsConstructor     // Lombok：为 final 字段生成构造方法
public class UserController {

    private final UserService userService;   // 构造器注入，不用 @Autowired
}
```

**为什么用构造器注入而不是 `@Autowired` 字段注入**：

| | 构造器注入 | 字段注入 |
| --- | --- | --- |
| 字段能否 `final` | ✅ 能 | ❌ 不能 |
| 依赖能否为 null | 不能，启动就失败 | 可能是 null，运行期才炸 |
| 单元测试 | 直接 `new` 传参 | 得靠反射或 Spring 容器 |

### 8.2 接口清单（19 个）

| # | 方法 | 路径 | 入参 | 返回 | 特性 |
| --- | --- | --- | --- | --- | --- |
| 1 | POST | `/users` | `User`（JSON） | 带 id 的 `User` | 默认值填充 + 事务 |
| 2 | POST | `/users/batch` | `List<User>` | 条数 | 批处理 + 事务 |
| 3 | GET | `/users/{id}` | 路径参数 | `User` / `null` | 逻辑删除过滤 |
| 4 | GET | `/users` | `UserQuery` | `List<User>` | 动态条件 |
| 5 | GET | `/users/page` | `UserQuery` + 分页参数 | `Page<User>` | 分页插件 |
| 6 | GET | `/users/keyword` | `keyword` | `List<User>` | 括号包 `or` |
| 7 | GET | `/users/age-range` | `minAge` / `maxAge` | `List<User>` | 区间 + 可选条件 |
| 8 | GET | `/users/top` | `limit` | `List<User>` | `last("LIMIT")` |
| 9 | GET | `/users/usernames` | — | `List<String>` | 只查一列 |
| 10 | GET | `/users/tag` | `tag` | `List<User>` | JSON 列查询 |
| 11 | GET | `/users/exists` | `username` | `boolean` | `exists` |
| 12 | GET | `/users/count` | `gender`（可选） | `long` | 计数 |
| 13 | GET | `/users/statistics` | — | `Map` | 聚合 + 分组 |
| 14 | PUT | `/users/{id}` | 路径 + JSON | 更新后的 `User` | `updateById` |
| 15 | PUT | `/users/{id}/status` | 路径 + `status` | `boolean` | `LambdaUpdateWrapper` |
| 16 | POST | `/users/{id}/recharge` | 路径 + `amount` | 更新后的 `User` | **乐观锁** |
| 17 | DELETE | `/users/{id}` | 路径参数 | `boolean` | **逻辑删除** |
| 18 | DELETE | `/users/batch` | `ids`（逗号分隔） | `boolean` | 批量逻辑删除 |
| 19 | DELETE | `/users/status/{status}` | 路径参数 | `boolean` | 按条件逻辑删除 |

### 8.3 三个值得单独看的写法

**① `update` 里那句 `user.setId(id)`**

```java
@PutMapping("/{id}")
public User update(@PathVariable Long id, @RequestBody User user) {
    user.setId(id);                    // ← 这行必须有
    userService.modifyUser(user);
    return userService.getUser(id);    // ← 返回更新后的，不是原样返回请求体
}
```

**作用**：把路径里的 id 塞进对象，这样即使请求体里没写 id（或写了错的 id），也**以 URL 上的为准**，避免"路径说改 2 号、体里却写了 3 号"这种不一致。

**返回值为什么不直接返回 `user`**：因为要让它带上 `updateTime` 等被自动填充后的**最新值**，所以重新查一遍。

**② `deleteBatch` 为什么用查询参数而不是请求体**

```java
@DeleteMapping("/batch")
public boolean deleteBatch(@RequestParam List<Long> ids) {   // DELETE /users/batch?ids=8,9
    return userService.removeUserBatch(ids);
}
```

**HTTP 规范里 DELETE 带请求体的支持情况不一致**（有些代理和客户端会丢掉 body），所以用查询参数传。Spring 会自动把逗号分隔的字符串转成 `List<Long>`。

**③ `ageRange` 的两个参数都可选**

```java
@GetMapping("/age-range")
public List<User> ageRange(@RequestParam(required = false) Integer minAge,
                           @RequestParam(required = false) Integer maxAge) {
    return userService.listByAgeRange(minAge, maxAge);
}
```

只传 `minAge` 就只有下界，只传 `maxAge` 就只有上界，都不传等于查全部。实现靠的是条件构造器那个"第一个参数是布尔值"的重载。

### 8.4 `UserQuery`：查询条件对象

```java
@Data
public class UserQuery {
    private String keyword;        // 匹配 username 或 nickname
    private Integer gender;
    private String memberLevel;
    private Boolean vip;
    private Integer status;
    private Integer minAge;
    private Integer maxAge;
}
```

**放在 `dto` 包，不在 `controller` 包**。因为 Service 层要用它（`listByCondition(UserQuery)`），DTO 的归属应当是"层间传输"而不是"属于 Controller"。

**放进 `dto` 包后有两个地方要加 import**：`UserService` 和 `UserServiceImpl`。我一开始漏了，编译报错才发现。

---

## 9. 自动填充、逻辑删除、乐观锁、事务

### 9.1 字段自动填充：`MyMetaObjectHandler`

```java
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createTime", LocalDateTime::now, LocalDateTime.class);
        strictInsertFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        strictInsertFill(metaObject, "deleted", () -> 0, Integer.class);
        strictInsertFill(metaObject, "version", () -> 0, Integer.class);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 这里不能用 strictUpdateFill，原因见下
        setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }
}
```

**两个关键点**：

**① 填充只对实体上标了 `@TableField(fill = ...)` 的字段生效。** 没标注解的字段，Handler 里写再多也不管。

**② 更新时不能用 `strictUpdateFill`（本模块最反直觉的一条）**

`strictUpdateFill` 的判断条件是**"字段为 null 才填"**。而更新时实体通常是从库里查出来的，`updateTime` **已经有旧值了**，结果就是**时间永远不变**——看起来代码写了，实际没生效。

**所以更新时必须用 `setFieldValByName` 直接覆盖。**

**实测验证**：Mapper 测试里只设了 `username` 和 `age` 两个字段，但生成的 SQL 是：

```sql
INSERT INTO t_user ( username, age, create_time, update_time, deleted, version )
VALUES ( ?, ?, ?, ?, ?, ? )
```

**自动多出了 4 个字段** —— 证明填充真的在工作。

### 9.2 逻辑删除：`@TableLogic`

配置（放在 `application.yml` 里全局生效，实体上也可以加注解）：

```yaml
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

**效果是 `DELETE` 被改写成 `UPDATE`，所有查询自动追加条件。** 实测生成的三条 SQL：

```sql
-- removeById(7)：删除变成更新
UPDATE t_user SET update_time=?, deleted=1 WHERE id=? AND deleted=0

-- selectById(7)：查不到已删除的行
SELECT ... FROM t_user WHERE id=? AND deleted=0

-- selectList 带条件：用户条件被括号包住
SELECT ... FROM t_user WHERE deleted=0 AND ((username LIKE ? OR nickname LIKE ?))
```

**注意第三条的括号** —— 逻辑删除条件会被 `AND (用户的整个条件)` 包起来，所以用户条件里的 `OR` 不会漏出去。

**优点**：数据可恢复、可追溯。
**缺点**：表会持续变大，真正清理要靠定时任务或归档脚本；而且**唯一索引会和逻辑删除冲突**——比如 `uk_username` 唯一，删掉的 `zhangsan` 仍占着那个用户名，新用户无法再用同名注册。这是实际项目里必须正面处理的问题。

### 9.3 怎么"绕过"逻辑删除

**问题**：逻辑删除后，`BaseMapper` 的所有方法都查不到那一行，怎么验证"物理行还在"？

**我一开始的错误尝试**：

```java
// ❌ 不行！deleted=0 还是被加上了，查到 null
userService.getOne(new QueryWrapper<User>()
        .select("id", "username", "deleted")
        .eq("id", targetId), false);
```

**`.select()` 并不能绕过逻辑删除**，`BaseMapper` 的方法一律会被追加 `deleted = 0`。

**正确做法是手写 SQL**（MyBatis-Plus 的逻辑删除拦截器**不会改写手写的 SQL**）：

```java
@Select("SELECT id, username, nickname, deleted, version FROM t_user WHERE id = #{id}")
User selectRawById(@Param("id") Long id);
```

**这个方法在真实场景也有用**：数据恢复（把误删的改回 `deleted=0`）、归档清理（查出所有已删除的统一处理）。

### 9.4 乐观锁：`@Version`

**它要解决的病叫"丢失更新"**：

```
时间  张三                          李四
t1    读到 User(version=0)
t2                                 读到 User(version=0)
t3    改成"张三改" → 写库成功
t4                                 改成"李四改" → 写库成功
────────────────────────────────────────────────
结果：李四覆盖了张三，张三的改动凭空消失
```

**乐观锁的思路**：写的时候带上"我读到的版本号"，对不上就拒绝。

```
张三写：UPDATE ... WHERE id=2 AND version=0   → 匹配，影响 1 行 ✅
李四写：UPDATE ... SET version=1
        WHERE id=2 AND version=0               → 库里已是 1，影响 0 行 ❌
```

**"乐观"在哪**：假设冲突很少发生，所以**不加锁**，只在写入那一刻检查一下。代价是"可能失败"，收益是"平时没有锁开销"。

**生效需要三个要素**：

| # | 要素 | 本模块 |
| --- | --- | --- |
| 1 | 实体字段标 `@Version` | `User.version` |
| 2 | 注册 `OptimisticLockerInnerInterceptor` | `MybatisPlusConfig` |
| 3 | **实体对象上带着 version 值** | **必须先 `getById` 查出来** |

**第 3 条是唯一容易出错的，而且失败是静默的**：

```java
// ❌ 乐观锁完全不生效，而且不报错
User user = new User();
user.setId(2L);
user.setNickname("改一下");
userService.modifyUser(user);     // version 是 null，MP 不拼版本条件

// ✅ 正确
User user = userService.getById(2L);   // version = 0
user.setNickname("改一下");
userService.modifyUser(user);
```

**所以业务代码必须检查 `updateById` 的返回值**：

```java
if (!userService.modifyUser(user)) {
    throw new RuntimeException("数据已被他人修改，请刷新后重试");
}
```

**不检查返回值 = 乐观锁白配了。**

**它和逻辑删除会叠加**，实测 SQL：

```sql
UPDATE t_user SET ..., version=?
WHERE id=? AND version=? AND deleted=0
```

### 9.5 插件注册：`MybatisPlusConfig`

```java
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 顺序有讲究：分页在前，乐观锁在后
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
```

**三个要点**：

1. **必须指定 `DbType`**。不指定也能跑（靠 JDBC URL 猜），但多数据源或连国产库时会猜错，拼出方言不对的 SQL；
2. **将来要加别的拦截器，分页要放最后**（官方要求：分页改的是最终 SQL，要排在别人后面）；
3. **不注册 `PaginationInnerInterceptor`，分页会静默失效** —— `Page` 退化成普通对象，SQL 里没有 `LIMIT`，把整张表查出来，**而且不报错**。

### 9.6 事务：`@Transactional`

**默认行为（记这几个数）**：

| 项 | 默认值 | 说明 |
| --- | --- | --- |
| propagation | `REQUIRED` | 有事务就加入，没有就新建 |
| isolation | `DEFAULT` | 跟数据库一致，MySQL 默认 `REPEATABLE READ` |
| **rollbackFor** | **`RuntimeException` + `Error`** | **受检异常不回滚** |
| readOnly | false | |

**失效场景清单**：

1. **自调用**：`this.methodB()` 不走代理 → B 上的 `@Transactional` 无效；
2. 方法**不是 `public`**：Spring AOP 默认只代理 public；
3. **异常被自己 catch 了**：容器看不到异常，照常 commit；
4. **抛的是受检异常**：默认不回滚，得写 `rollbackFor = Exception.class`；
5. 类没被 Spring 管：自己 `new` 出来的对象没有代理；
6. 多数据源：事务管理器绑的不是同一个 `DataSource`。

**本模块的选择是显式写 `rollbackFor = Exception.class`**，比依赖默认值更明确。

---

## 10. 连接池：HikariCP 参数与排查方向

### 10.1 为什么需要连接池

`DriverManager.getConnection()` 每次都要走 TCP 握手、身份认证、会话初始化，**本机 MySQL 8 上一次大约 30~50 ms**，跨网络更久。接口 QPS 上来后，这部分开销会**超过 SQL 本身**。

连接池的做法是：**启动时先建好一批连接，用完归还池子而不是真正关闭**，请求来了直接取用。

Spring Boot 默认连接池就是 **HikariCP**，随 `spring-boot-starter-jdbc` 一起引入（本模块的 MP starter 会带进来），**不需要额外依赖**。

### 10.2 本模块的参数配置

```yaml
spring:
  datasource:
    hikari:
      pool-name: backend-learning-pool
      maximum-pool-size: 10     # 池上限
      minimum-idle: 5           # 空闲保底连接数
      connection-timeout: 3000  # 取连接超时，毫秒
      max-lifetime: 1800000     # 连接最长存活，毫秒（30 分钟）
```

### 10.3 参数速查

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `maximum-pool-size` | 10 | 池上限，也是能同时访问数据库的连接数上限 |
| `minimum-idle` | 等于 maximum | 空闲保底连接数。设小可以省连接，代价是流量回升时要现建连接 |
| `connection-timeout` | 30000 ms | 从池里取连接的等待上限，超时抛 `SQLTransientConnectionException` |
| `idle-timeout` | 600000 ms | 空闲连接回收时间，**只在 `minimum-idle < maximum-pool-size` 时生效** |
| `max-lifetime` | 1800000 ms | 连接最长存活，**必须小于 MySQL 的 `wait_timeout`**（默认 28800 秒），否则会拿到已被服务端断开的连接 |
| `keepalive-time` | 0（关闭） | 定期给空闲连接发探活，避免被防火墙/中间件掐断 |
| `pool-name` | `HikariPool-1` | 池名字，多数据源时用来区分日志 |

### 10.4 池大小怎么定（三条约束）

1. **起点取 `CPU 核数 * 2 + 磁盘数`** —— 这是 HikariCP 官方给的经验值，**不是越大越好**。连接数远超 MySQL 的处理能力时，请求只是排进更长的队列，单条 SQL 延迟反而变高；
2. **上限受 MySQL 约束**：所有应用实例的 `maximum-pool-size` 之和要小于 `max_connections`（默认 **151**），还要给运维连接和其他服务留余量，否则报 `Too many connections`；
3. **池被占满时的表现由 `connection-timeout` 决定**：默认 30 秒内取不到连接就抛 `SQLTransientConnectionException: Connection is not available`。

### 10.5 两种"池有问题"的排查方向完全不同

| 现象 | 在哪一侧 | 多半是什么 | 方向 |
| --- | --- | --- | --- |
| `Connection is not available, request timed out` | **应用侧日志** | 慢 SQL 或长事务占着连接不放 | 查 SQL 执行时间与事务边界 |
| `Too many connections` | **MySQL 侧** | 各实例池上限之和超过了 `max_connections` | 减小 `maximum-pool-size` |

**这两个别混**：前者是"连接被占用完了"，后者是"池总量配得太大"。

**查看 MySQL 当前连接数**：

```sql
SHOW STATUS LIKE 'Threads_connected';
SHOW PROCESSLIST;      -- 看连接都在干什么
```

### 10.6 怎么看连接池状态

**启动日志里能直接看到池的建立**：

```
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
```

**看到这两行说明连接池建好了**，这也是启动成功的标志之一。

### 10.7 关于 Druid（本模块没做）

教程提到了 Druid 监控页（`/druid/`），做法是把它放在 Maven profile 里，默认仍用 HikariCP。**本模块没做这部分**，原因：默认 HikariCP 够用，Druid 只是多一个可视化面板。

**如果要换，有个坑必须知道**：**Druid 的自动配置会顶掉 HikariCP**。如果把它写进常驻依赖，`spring.datasource.hikari` 那套参数就**全部失效**（不报错，只是不生效）。所以两者**只能选一个**。

参数对应关系（换的时候要改的地方）：

| 语义 | HikariCP | Druid |
| --- | --- | --- |
| 池上限 | `maximum-pool-size` | `max-active` |
| 最小空闲 | `minimum-idle` | `min-idle` |
| 取连接超时 | `connection-timeout` | `max-wait` |
| 空闲回收 | `idle-timeout` | `min-evictable-idle-time-millis` |

---

## 11. 接口与实测结果（Windows 实操）

### 11.1 启动

```powershell
cd E:\backend-learning\beckend-learning\05-mybatis
mvn spring-boot:run
```

> **不加 `-am`**：加上会把父工程一并放进 reactor，而父工程是 `pom` 打包、没有主类，`spring-boot:run` 会直接失败。

**实测启动成功的标志**：

```
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
Tomcat started on port 8005 (http)
```

### 11.2 实测结果（自动化测试）

本模块用**自动化测试**代替手工点接口，`mvn clean test` 实测：

```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0  -- in UserMapperCrudTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0  -- in UserServiceTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**10 个用例全部通过**，覆盖：

| 测试类 | 用例 | 验证的能力 |
| --- | --- | --- |
| `UserMapperCrudTest` | `testInsert` | **主键回填** + **自动填充** |
| | `testSelectById` | 驼峰映射 + **`tags` JSON 反序列化** |
| | `testSelectNotExist` | 查不到返回 null |
| | `testDeleteIsLogical` | 删除改写成 UPDATE，物理行还在 |
| | `testUpdateByIdOnlyNonNullFields` | 只更新非 null 字段 |
| `UserServiceTest` | `practice1` | 查不存在的 id 返回 null |
| | `practice2` | Lambda 多条件同时生效 |
| | `practice3` | **事务回滚**（count 不变） |
| | `practice4` | 逻辑删除后查不到、物理行 `deleted=1` |
| | `practice5` | **乐观锁**第二次被拦下 |

**两层分开测的好处**：哪层挂了，一眼就知道问题在哪。Mapper 层挂了 → 数据访问有问题；Mapper 过了但 Service 挂了 → 业务逻辑有问题。

### 11.3 实测的 SQL 日志（关键证据）

打开 `log-impl: StdOutImpl` 后，控制台能看到 MyBatis 生成的 SQL 和参数。以下都是**实测抓取**的原文。

**① 插入：自动填充生效**

```sql
==>  Preparing: INSERT INTO t_user ( username, age, create_time, update_time, deleted, version ) VALUES ( ?, ?, ?, ?, ?, ? )
==> Parameters: zhangsan(String), 20(Integer), 2026-10-10T19:02:53.388(LocalDateTime), ...
```

**只设了 2 个字段，SQL 里出现 6 个** —— 另外 4 个是自动填充的。

**② 查询：自动追加逻辑删除条件**

```sql
==>  Preparing: SELECT id,username,password,nickname,gender,age,member_level,points,balance,score,phone,email,avatar,intro,tags,birthday,work_time,vip,status,last_login_time,create_time,update_time,deleted,version FROM t_user WHERE id=? AND deleted=0
```

**注意末尾的 `AND deleted=0`** —— 我没写，是自动加的。

**③ 乐观锁：`SET version=1` 和 `WHERE version=0` 同时出现**

```sql
==>  Preparing: UPDATE t_user SET username=?, password=?, ..., version=? WHERE id=? AND version=? AND deleted=0
==> Parameters: ..., 1(Integer), 2(Long), 0(Integer)
                  ↑ SET version=1   ↑ id   ↑ WHERE version=0
```

**"我要把版本改成 1，前提是它现在还是 0"** —— 这就是乐观锁的全部秘密。

**④ 逻辑删除：DELETE 变成 UPDATE**

```sql
==>  Preparing: UPDATE t_user SET update_time=?, deleted=1 WHERE id=? AND deleted=0
==> Parameters: 2026-10-10T19:02:53.346(LocalDateTime), 11(Long)
```

### 11.4 一个"假绿"测试的教训

我一开始写的测试里有一条：

```java
@Test
void testSearch() {
    User user = userMapper.selectById(10);
    log.info("用户信息: {}", user);      // ← 没有任何断言
}
```

**查到 `null` 也会显示"通过"。** 这种测试是"假绿"，比失败更危险——它给你一种"验证过了"的错觉。

**教训**：**没有断言的测试等于没测。** 至少要 `assertNotNull` 一下。

---

## 12. 我踩过的 6 个坑

### 坑 1：MyBatis-Plus 3.5.14 与 Spring Boot 4.1.1 不兼容（最大最隐蔽的一个）

**现象**：三个测试全部失败，报错是：

```
java.lang.IllegalStateException: Failed to load ApplicationContext
```

**表面看像"容器起不来"，极容易误判成 `application.yml` 配错了。**

**排查过程**：去读 `target/surefire-reports/` 下的报告，在**最底层的 `Caused by`** 里找到真凶：

```
Caused by: java.lang.NoSuchMethodError:
'org.springframework.boot.context.properties.PropertyMapper
 org.springframework.boot.context.properties.PropertyMapper.alwaysApplyingWhenNonNull()'
    at com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties$CoreConfiguration.applyTo(...)
    at com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.applyConfiguration(...)
```

**根因**：去 Maven 中央仓库查那个 starter 的 POM，发现：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>4.0.0-M2</version>    <!-- ← 里程碑版！ -->
    </dependency>
  </dependencies>
</dependencyManagement>
```

**`mybatis-plus-spring-boot4-starter:3.5.14` 内部导入的是 Spring Boot `4.0.0-M2`（里程碑版本）**，而本项目是 **4.1.1（正式版）**。`alwaysApplyingWhenNonNull()` 这个方法**里程碑版里还在，正式版里被删了** —— 所以编译期没事，运行期才抛 `NoSuchMethodError`。

**解决办法**：升级到 **`3.5.17`**（它导入的是 `spring-boot-dependencies:4.0.1` 正式版，并配套 `mybatis-spring:4.0.0`）。升级后容器正常启动，SQL 正常执行。

**排查手法**：先用命令行参数覆盖版本做对照验证，确认有效再改 POM：

```powershell
mvn -Dmybatis-plus.version=3.5.17 test
```

**教训**：

- **starter 的名字不代表兼容性。** `boot4-starter` 听着像"支持 Boot 4"，但要看它**内部导入的 BOM 是里程碑版还是正式版**；
- **`Failed to load ApplicationContext` 时，直接翻到报告最底层的 `Caused by`**，不要停留在表面报错上；
- 引入与 Spring Boot 强相关的第三方 starter 时，**去 Maven 中央仓库看它的 POM 导入了哪个版本的 BOM**。

### 坑 2：`--enable-preview` 配 `release 17` 是非法组合

我在 `pom.xml` 里加了：

```xml
<build>
    <plugins>
        <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <source>21</source>
                <target>21</target>
                <compilerArgs>--enable-preview</compilerArgs>   <!-- ← 问题在这 -->
            </configuration>
        </plugin>
    </plugins>
</build>
```

**实测报错**：

```
源发行版 17 与 --enable-preview 一起使用时无效
（预览特性需要源发行版 21）
```

**根因**：**`spring-boot-starter-parent` 默认设了 `maven.compiler.release=17`，而 `release` 一旦指定，`source` / `target` 就被完全忽略**。所以编译器实际按 **17** 编译，再配上要求 21+ 的 `--enable-preview`，javac 直接拒绝。

**顺带暴露一个隐性事实**：从编译日志能看出项目**一直在用 Java 17 编译**，不是 21：

```
Compiling 9 source files with javac [debug parameters release 17]
```

**我 pom 里写的 `<source>21</source>` 从来没生效过。** 以后想用 Java 21 的语法（记录模式、`switch` 模式匹配）会报错，而且原因很隐晦。

**解决**：`--enable-preview` 直接删掉（本模块没有任何地方需要预览特性）。

**教训**：**`<source>` / `<target>` 和 `release` 不能混用**。用 `release` 统一指定，别写 `source`/`target`；想看当前实际编译级别，**读编译日志里那句 `release N`**。

### 坑 3：把方法剪切出来，只粘了方法体没建类

我从 `UserServiceImpl` 里把两个方法剪切出来，新建了一个 `applyDefaults.java`：

```java
package top.seamohai.mybatis.service;          // 有包声明

private void applyDefaults(User user) {        // ← 方法飘在类外面！
    ...
}
private String sha256(String raw) {            // ← 同样
    ...
}
```

**实测报错**：

```
applyDefaults.java:[1,1] 未命名类不应有包声明
```

**后果**：**这一个文件让整个模块编译失败**，IDE 里满屏飘红，看不到别的问题。

**解决**：这两个方法本来就该是 `UserServiceImpl` 的 `private` 方法，**搬回去**，删掉那个文件。

**教训**：

- **Java 的方法必须在类里面。** IDE 的"提取到新文件"重构偶尔会出这种半成品；
- **编译错误会互相遮挡**：一个文件坏掉，其他文件的问题都看不见。所以**修编译错误要一个个来，修完立刻重新编译**。

### 坑 4：直接调 Mapper 绕过了 Service 的默认值填充

我一开始的测试是这样写的：

```java
@Test
void testInsert() {
    User user = new User();
    user.setUsername("zhangsan");
    user.setAge(20);
    userMapper.insert(user);      // ← 直接调 Mapper
}
```

**实测报错**：

```
Caused by: java.sql.SQLException: Field 'password' doesn't have a default value
```

**根因**：表里 `password`、`gender`、`member_level` 都是 `NOT NULL` 且**没有数据库默认值**，它们靠 Service 的 `applyDefaults()` 补。**而这个测试直接调 Mapper，绕过了 Service。**

**注意**：`create_time` / `update_time` / `deleted` / `version` **没报错** —— 因为它们是 `MyMetaObjectHandler` 填的，那是 MyBatis 层面的，不经过 Service。

**这个坑的本质是分层的价值**：业务规则（补默认值）在 Service，数据访问在 Mapper。**绕过一层，那一层的保障就全没了。**

**解决**：Mapper 层的测试要么自己把字段填全，要么改成走 Service 层测。

**教训**：**知道自己在测哪一层。** 测 Mapper 就要自己准备完整数据，不能指望 Service 的逻辑。

### 坑 5：`.select()` 绕过不了逻辑删除

想验证"逻辑删除后物理行还在"，我一开始这么写：

```java
// ❌ 查到 null
User raw = userService.getOne(new QueryWrapper<User>()
        .select("id", "username", "deleted")
        .eq("id", targetId), false);
```

**我以为指定了 `select` 列、用了 `QueryWrapper` 手写条件，就不会被逻辑删除过滤——错了。** `BaseMapper` 的所有方法**一律**会被追加 `deleted = 0`。

**解决**：手写 SQL（逻辑删除拦截器**不改写手写 SQL**）：

```java
@Select("SELECT id, username, nickname, deleted, version FROM t_user WHERE id = #{id}")
User selectRawById(@Param("id") Long id);
```

**教训**：**"绕过框架能力"要靠更底层的机制。** MyBatis-Plus 的拦截器只改它自己生成的 SQL，你手写的它不碰。遇到"框架自动加的条件去不掉"，思路就是**下沉到能自己控制 SQL 的那一层**。

### 坑 6：断言"数量"而不是断言"性质"（测试脆弱性）

练习 2 我一开始断言：

```java
assertEquals(3, users.size(), "示例数据里应该有 3 条满足 age>18 且 vip=true");
```

**实测失败**：`expected: <3> but was: <2>`。

**根因**：`data.sql` 里符合条件的本来是 admin、lisi、wangwu 三条，**但 wangwu 被上一次失败的测试留成了 `deleted=1`**，所以只查到 2 条。

**这暴露的是断言写法的问题**：**硬编码数量，意味着断言和"当前数据状态"绑死了**。一旦测试之间互相影响、或者以后增删数据，就会误报。

**解决**：改成断言"性质"——**每条结果都满足条件**，并反向验证**不该出现的确实没出现**：

```java
assertTrue(users.stream().allMatch(u -> u.getAge() > 18 && Boolean.TRUE.equals(u.getVip())),
        "结果里出现了不满足条件的记录，说明某个条件没生效");
assertTrue(users.stream().noneMatch(u -> "zhangsan".equals(u.getUsername())),
        "zhangsan 的 vip=false，不该出现在结果里");
```

**教训**：**断言"符合条件的性质"，而不是"符合条件的数量"。** 数量断言只在数据完全受控时才可靠；性质断言在任何数据状态下都成立。

---

## 13. 报错速查表

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| `Failed to load ApplicationContext` + 底层 `NoSuchMethodError: PropertyMapper...` | MP 版本内置的 Spring Boot BOM 是里程碑版，与项目正式版冲突 | 升级 MP（本模块 3.5.14 → **3.5.17**），见坑 1 |
| `No database selected`（1046） | JDBC URL 里没写库名，或客户端没选库 | URL 加 `/backend_learning`；客户端先 `USE backend_learning;` |
| `Unknown database`（1049） | 库名拼错或库不存在 | `SHOW DATABASES;` 核对 |
| `Access denied for user`（1045） | 账号密码错，或该用户无此库权限 | 核对 `spring.datasource.username/password` |
| `Table 'xxx.t_user' doesn't exist`（1146） | 库选对了但表没建 | 确认 `spring.sql.init.mode: always`；或手工执行 `schema.sql` |
| 启动时 `schema.sql` 没执行 | `sql.init.mode` 默认只对内嵌库生效 | **显式设成 `always`** |
| 启动报 `Table 't_user' already exists` | 脚本没有 `DROP TABLE IF EXISTS`，而 `mode=always` 每次启动都执行 | 脚本开头加 `DROP TABLE IF EXISTS t_user;` |
| `Field 'password' doesn't have a default value` | 列是 `NOT NULL` 且无默认值，但没走 Service 的 `applyDefaults` | 自己填全字段，或改成走 Service 层；见坑 4 |
| 字段查询结果为 `null` | 下划线未转驼峰 | 开 `map-underscore-to-camel-case: true` |
| **JSON 字段查出来是 `null`** | **少了 `autoResultMap = true`** | `@TableName(value = "t_user", autoResultMap = true)` |
| **分页返回全量数据** | **没注册 `PaginationInnerInterceptor`** | 加 `MybatisPlusInterceptor` Bean；且不报错，最易漏 |
| 编译找不到分页插件类 | 没引入 JSqlParser 构件 | 加依赖 `mybatis-plus-jsqlparser` |
| 编译找不到 `IService` / `ServiceImpl` | 3.5.9 起类已换包 | 用 `com.baomidou.mybatisplus.spring.service` 下的同名类 |
| `源发行版 17 与 --enable-preview 一起使用时无效` | `release` 覆盖了 `source`/`target` | 删掉 `--enable-preview`；见坑 2 |
| `未命名类不应有包声明` | 方法飘在类外面 | 把方法放回类里；见坑 3 |
| 插入 `age = -1` 报 `Out of range value for column 'age'` | 列是 `TINYINT UNSIGNED`，不接受负数 | 业务层先校验；确实要存负数就改列类型 |
| **更新后 `update_time` 没变** | **`strictUpdateFill` 只在字段为 null 时才填**，查出来的实体已有旧值 | `updateFill` 里改用 `setFieldValByName` |
| `deleteById` 变成 `UPDATE`，物理行还在 | `@TableLogic` 逻辑删除生效 | 符合预期；要物理删除得手写 SQL |
| `selectById` 查不到刚删的数据 | 逻辑删除后查询自动追加 `deleted = 0` | 符合预期；要看已删数据用 `selectRawById` 那种手写 SQL |
| 想绕过逻辑删除但查不到 | `BaseMapper` 所有方法都会被追加条件，`.select()` 没用 | 手写 `@Select` 的 SQL；见坑 5 |
| **乐观锁没生效** | **实体上的 `version` 为 `null`，MP 不拼版本条件** | 先 `getById` 拿到带版本的实体再改；且**必须检查返回值** |
| `updateById` 返回 `false` | id 不存在 / 已被逻辑删除 / 乐观锁冲突 | 三种原因都不抛异常，得自己判 |
| 想把某列更新成 `NULL` 但 `updateById` 做不到 | 默认字段策略 NOT_NULL，null 不参与 SET | 用 `UpdateWrapper.set("列名", null)` |
| 日志报 `Connection is not available, request timed out` | 池被占满 | 查慢 SQL 与长事务；确属高并发再调大 `maximum-pool-size` |
| MySQL 报 `Too many connections` | 各实例池上限之和超过 `max_connections` | 减小 `maximum-pool-size`，或调大 MySQL 的 `max_connections` |
| 用 Druid 时 Hikari 参数不生效 | Druid 自动配置顶掉了 HikariCP | 两者选一个，删掉另一套配置 |
| 事务没回滚 | 异常类型不在回滚范围，或方法是自调用 | 用 `rollbackFor = Exception.class`；别在同类内部自调用 |
| 测试跑完数据被改乱 | 测试没做数据复原 | 加 `@Transactional` 自动回滚，或在用例末尾手动复原 |

---

## 14. 练习

### 14.1 随堂练习（约 30 分钟）

| # | 练习 | 验证点 | 状态 |
| --- | --- | --- | --- |
| 1 | 用 `getById` 查询一个不存在的 id | 返回 `null`，不报错 | ✅ |
| 2 | 用 `LambdaQueryWrapper` 按 `age > 18` 且 `vip = true` 查询 | 两个条件都生效 | ✅ |
| 3 | 新增一条 `age = 200` 的记录 | 抛异常，`count` 不变（事务回滚） | ✅ |
| 4 | 逻辑删除一条记录后 `selectById` | 返回 `null`，直接查库能看到 `deleted = 1` | ✅ |
| 5 | 用同一个实体对象连续调两次 `updateById` | 第二次返回 `false`，被乐观锁拦下 | ✅ |

**五个练习都已写成自动化测试并通过**（见 11.2），测试代码在 `UserServiceTest` 里。

**几个练习的要点补充**：

- **练习 2**：`age` 是 `TINYINT`，所以 `> 18` 和 `>= 19` 结果相同。用接口测可以写 `GET /users?vip=true&minAge=19`，但**要严格验证 `.gt()` 就得用代码**；
- **练习 3**：`age=200` 能通过数据库校验（`TINYINT UNSIGNED` 上限 255），**正因为"插入成功了"，后续回滚才有意义**。如果数据库直接拒绝，测的就变成数据库约束而不是事务了；
- **练习 4**：**光看"查不到"说明不了问题**——物理删除也一样查不到。**真正要证明的是"物理行还在、`deleted=1`"**，所以必须用 `selectRawById` 绕过逻辑删除去查；
- **练习 5**：**不能用 Apifox 接口测**。走 HTTP 时每次请求都新建对象、都重新拿最新 `version`，两次都会成功。乐观锁的冲突场景必须在同一个方法里持有"过期版本"的对象才能复现。

### 14.2 课后练习（五题全部完成）

| # | 难度 | 题目 | 状态 | 验证方式 |
| --- | --- | --- | --- | --- |
| 1 | 基础 | 给 `t_user` 加 `email` 字段，用 `LambdaUpdateWrapper` 按 username 更新邮箱 | ✅ | Apifox 调接口 + 查库 |
| 2 | 基础 | 给 `score` 之外的字段补 `@JsonFormat`，验证接口输入输出格式 | ✅ | 正反两次请求对照 |
| 3 | 进阶 | 新建 `t_course` 表，练习 `saveBatch` 与 `removeByIds` | ✅ | 6 个测试用例 + SQL 条数统计 |
| 4 | 进阶 | `maximum-pool-size` 调成 1，并发复现 `Connection is not available` | ✅ | 5 个压测实验 + Hikari MXBean |
| 5 | 挑战 | 新建 `t_order` 表，演示"创建订单 + 扣库存"的事务原子性 | ✅ | 6 个测试用例（含反证） |

> **本节的小结写法以"遇到什么问题、怎么定位、怎么解决"为准**，而不是罗列用了哪些 API —— 因为这几道题真正花时间的部分是排查，不是编码。

#### 练习 1：按 username 更新邮箱

**用到的技术**：`LambdaUpdateWrapper` 的 `.eq()` 拼 `WHERE`、`.set()` 拼 `SET`，一条 SQL 只改 `email` 一列，不手写 SQL。

**实际生成**：

```sql
UPDATE t_user SET email=?, update_time=? WHERE deleted=0 AND (username = ?)
```

**遇到的问题**：参数明明传了 `username=zhangsan&email=...`，接口却报 `用户名和邮箱都不能为空`（HTTP 500）。

**排查过程**：

一开始怀疑 Apifox 没把查询参数发出去。但报错栈显示**方法体确实执行了**——

```
at UserServiceImpl.modifyEmailByUsername(UserServiceImpl.java:139)
at UserController.updateEmail(UserController.java:405)
```

**如果参数真的缺失**，`@RequestParam` 默认 `required = true`，Spring 会先抛 `MissingServletRequestParameterException` 返回 **400**，**根本不会进方法体**。既然进来了，说明参数有值。

**根因**：判断条件写反了。

```java
// ❌ 原写法：&& 把逻辑反过来了
if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(email)) {
    throw new IllegalArgumentException("用户名和邮箱都不能为空");
}
// 两个参数都非空时：(true) && (true) = true → 抛异常
```

**这是德摩根定律用反了**：要表达"任意一个为空就拦"，应该是 `(!A) || (!B)`，而不是 `(!A) && (!B)`（后者等于"两个都为空"）。

**解决**：

```java
// ✅ 改 && 为 ||，并把 isEmpty 换成 hasText
if (!StringUtils.hasText(username) || !StringUtils.hasText(email)) {
    throw new IllegalArgumentException("用户名和邮箱都不能为空");
}
```

**顺带修正的第二个点**：`StringUtils.isEmpty("   ")` 返回 `false`（纯空白不算空），会被绕过；`hasText` 把纯空白也算空。

**教训**：**看到 `if (!a && !b)` 就该警惕**——它等价于 `if (!(a || b))`，含义是"a 和 b 都没有"。绝大多数场景要的是 `if (!a || !b)`。这类错误不报编译错、只是行为反了，而且表现为"正常输入反而失败"，很容易误判成"参数没传对"。

#### 练习 2：`@JsonFormat` 输入输出验证

**做法**：不是简单地"给字段加注解"，而是**故意把 pattern 改坏，用对照实验反证注解在起作用**。

**步骤与实测**：

| 步骤 | 操作 | 结果 |
| --- | --- | --- |
| 1 | 把 `birthday` 的 pattern 改成 `yyyy-MM dd`（把 `-` 换成空格） | — |
| 2 | `POST /users` 传正常格式 `"birthday": "1999-08-15"` | **400 Bad Request** |
| 3 | 改传 `"birthday": "1999-08 15"`（中间空格） | **200**，返回 `"birthday": "1999-08 15"` |
| 4 | 把 pattern 改回 `"yyyy-MM-dd"` | 正常格式恢复通过 |

**为什么要这么验证**：`@JsonFormat` 属于**静默失效**类注解——不生效也不会报错，只是格式不对。

**反证逻辑**：如果注解没生效，`"1999-08-15"` 是标准 ISO 格式，**本该被接受**；现在被拒绝了，说明确实是注解里写的 pattern 在管事。而且**第 3 步的返回也是空格格式**，证明输入输出用的是**同一个 pattern**。

**额外收获（顺手解掉了一个疑问）**：Spring Boot 4 用的是 Jackson 3（`tools.jackson`），而实体导入的是 Jackson 2 的 `com.fasterxml.jackson.annotation.JsonFormat`。实测**双向都生效**，不需要改成 `tools.jackson.*`。验证方法：`GET /users/1` 返回的 `createTime` 是 `"2026-09-01 10:00:00"`（空格分隔）而不是 ISO 的 `"2026-09-01T10:00:00"`。

**教训**：**验证"配置类注解是否生效"，最好的办法是用一个"本来应该失败"的输入去试探**，而不是看到正常输入返回 200 就认为它对。

#### 练习 3：`t_course` 批量增删

**表设计**：`credit` 用 `DECIMAL(3,1)` 而不是 `FLOAT`——因为学分有 `2.5` 这种，浮点累加会有误差（和金额同理）。另外把逻辑删除、自动填充、乐观锁、唯一索引都复用了上来。

**遇到的问题一：编译失败**

```
CourseServiceImpl 不是抽象的, 并且未覆盖
com.baomidou.mybatisplus.core.mapper.BaseMapper 中的抽象方法 <E>selectObjs(...)
```

**排查**：报错位置指向 `CourseServiceImpl`，第一反应是"实现类写漏了方法"。但实现类只有一个，而 `selectObjs` 根本不是 `IService` 的方法——**它是 `BaseMapper` 的方法**。

**根因**：接口继承错了。

```java
// ❌ 原写法
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
public interface CourseService extends BaseMapper<Course> {

// ✅ 改成
import com.baomidou.mybatisplus.spring.service.IService;
public interface CourseService extends IService<Course> {
```

继承 `BaseMapper` 后，接口里凭空多出一整套 Mapper 方法（`insert`/`selectObjs`/…），实现类当然覆盖不了。

**遇到的问题二：编译通过但启动失败**

改完接口编译过了，启动时报 `NoSuchBeanDefinitionException: No qualifying bean of type 'CourseMapper'`。

**根因**：`CourseMapper` 漏了 `@Mapper`。本项目启动类是 `@SpringBootApplication`，**没有 `@MapperScan`**，每个 Mapper 必须自己标 `@Mapper`。

**教训**：**这两类错误性质完全不同** —— 一个是**编译期**（javac 挡住），一个是**启动期**（Spring 容器挡住）。而且**报错位置不等于病根**：提示在实现类，病根在接口。

**批量操作的核心实测结论**（详见 14.3）：`saveBatch` **不会合并 SQL**。

#### 练习 4：连接池并发压测

**遇到的问题：按题目把 `maximum-pool-size` 调成 1，却没复现出报错。**

| 尝试 | 配置 | 结果 |
| --- | --- | --- |
| 第 1 次 | 池 = 1，**60 个并发读** | **全部成功，0 失败** |
| 第 2 次 | 池 = 1，8 线程各批量插 200 条 | 成功 8 / 失败 0（等待线程峰值 = 7） |
| **第 3 次** | 池 = 1，**加 `SELECT SLEEP(1)`**，20 线程 | **成功 3 / 失败 17** ← 复现！ |

**排查**：读一下自己测出来的基线——本机 MySQL 单次查询只要 **1~3 ms**，连接**借出立刻归还**。所以就算池只有 1 条，60 个线程也只是排队几百毫秒，**远够不到 `connection-timeout` 的 3000 ms**。

**结论**：**能不能压垮连接池，取决于「单次占用连接的时间 × 排队人数」有没有超过 `connection-timeout`，而不只是"池小"。**

**解决**：在 `UserMapper` 加一个压测探针，用 `SLEEP()` 强行把连接占用 1 秒：

```java
@Select("SELECT SLEEP(#{seconds})")
Integer slowQuery(@Param("seconds") int seconds);
```

**数字对得严丝合缝**：`3000ms ÷ 1000ms = 3` —— 正好成功 3 个。

**拿到的原始报错**（绕过 MyBatis 直接抢连接，才看得到 Hikari 原话）：

```
java.sql.SQLTransientConnectionException:
  backend-learning-pool - Connection is not available, request timed out after 3010ms
  (total=1, active=1, idle=0, waiting=19)
```

**这组数据就是判据**：`active=1, idle=0` —— **池里有连接、但都被占用、一条空闲的都没有**。

**对照实验**（证明是池大小的锅）：

| 池上限 | 总连接 | 8 线程抢连接（各持有 6 秒） | 超时失败 |
| --- | --- | --- | --- |
| **1** | 1 | **1 个成功** | **7 个超时** |
| **10** | 5 | **8 个全部成功** | **0** |

**两个必须记住的点**：

- **"`Connection is not available`" ≠ "连不上数据库"**。前者是**池里借不到连接**（`total>0, idle=0`，应用侧问题）；后者是 `Communications link failure`（`total=0`，环境侧问题）。**两者排查方向完全相反**；
- **压测类绝对不能加 `@Transactional`**。Spring 在事务期间会把 Connection **绑定到线程**，同线程多次查询复用同一条连接——那样无论并发多少，真正占用的连接数都很小，**池永远不会满，实验做不出来**。代价是数据不会自动回滚，所以要自己写清理。

**替代 Druid 监控页**：本模块没做 Druid profile，改用 HikariCP 的 MXBean 直接读：

```java
HikariDataSource hikari = (HikariDataSource) dataSource;
var mx = hikari.getHikariPoolMXBean();
mx.getActiveConnections();            // 活跃连接数
mx.getThreadsAwaitingConnection();    // ★ 正在排队等连接的线程数（比活跃数更早暴露问题）
```

#### 练习 5：`t_order` 跨表事务原子性

**场景**：一次下单要改两张表，必须原子。

```
① t_course：stock = stock - 1     扣名额
② t_order ：INSERT 一条订单        记流水
```

**考点一：扣库存不能用"先查再改"**

```java
// ❌ 先查再改：查到 stock=1 到写回 stock=0 之间有窗口期
//    两个线程都查到 1、都认为能扣 → 超卖
Course c = getById(courseId);
if (c.getStock() > 0) { c.setStock(c.getStock() - 1); updateById(c); }

// ✅ 判断进 WHERE，由数据库加锁保证原子性
UPDATE t_course SET stock = stock - 1 WHERE id = ? AND stock > 0
```

**考点二：`deductStock` 返回 `false` 时必须主动抛异常**

它只是"影响 0 行"，**自己不会抛异常**。调用方不抛，事务就会照常提交 —— 结果是**"没扣到名额，却建了订单"**。

```java
boolean deducted = courseService.deductStock(courseId);
if (!deducted) {
    throw new IllegalStateException("名额不足或课程不存在，下单失败");   // ← 必须自己抛
}
```

**验证原子性：用重复订单号做失败注入**

不用往业务代码里塞测试专用的 `throw`，而是**故意用已经存在的订单号**，让第二步撞 `uk_order_no` 唯一索引：

```
第 1 次下单（订单号 xxx-dup）：成功，库存 5 → 4
第 2 次下单（同一个订单号）：
    ① deductStock 成功      → 库存 4 → 3   ← 库已经被改了
    ② INSERT 撞唯一索引      → 抛 DuplicateKeyException
    ③ 事务回滚              → 库存必须回到 4
```

**★ 反证实验：去掉 `@Transactional` 会怎样**

光看上面那条测试通过，只能说明"库存没被多扣"，**不能证明是事务起的作用**（万一本就扣不动呢）。所以又做了一次对照 —— **复刻同样两步，但不带事务**：

| | 有 `@Transactional` | 没有 |
| --- | --- | --- |
| 第 1 步扣库存 | 4 → 3 | 4 → 3 |
| 第 2 步建订单 | 失败 | 失败 |
| **最终库存** | **回到 4** ✅ | **停在 3** ❌ |
| 后果 | 无痕迹 | **名额白扣，订单没有** |

**同样的代码，只差一个注解** —— 这就是原子性的价值。

**这个测试类为什么不加 `@Transactional`**：本练习的关键用例是"让事务中途失败看回滚"。如果测试类自己带事务，`createOrder` 会**加入外层事务**，失败时只是把外层标记为 `rollback-only`，**并不会真的回滚**，后续查询还在同一事务里，看不到最终结果。所以让 `createOrder` 自己开完整事务，然后在**事务之外**查数据库。

**逻辑删除在这里的副作用**：订单被逻辑删除后，`order_no` **依然占着唯一索引**，同一个订单号不能复用。真实项目的订单号一般带全局唯一后缀（时间戳 / 雪花 id）来规避。

### 14.3 关键写法提示

```java
// 练习 2：两个条件（注意 age 用 gt 是严格大于）
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
        .gt(User::getAge, 18)
        .eq(User::getVip, true);
List<User> users = userService.list(wrapper);

// 练习 3：故意造一个业务上不合理的值，观察回滚
User bad = new User();
bad.setUsername("practice3_bad_age");
bad.setAge(200);                       // TINYINT UNSIGNED 能存，但业务不允许
assertThrows(RuntimeException.class, () -> userService.createUser(bad));
assertEquals(countBefore, userService.count());     // 总数没变 = 回滚了

// 练习 4：绕过逻辑删除查物理行（必须手写 SQL）
User raw = userMapper.selectRawById(4L);
assertNotNull(raw);                    // 物理行还在
assertEquals(1, raw.getDeleted());     // deleted 被置为 1

// 练习 5：同一个对象 + 手动退回版本 = 模拟并发
User user = userService.getById(2L);    // 必须查出来，version 才有值
Integer v = user.getVersion();          // 记下原值
user.setNickname("第一次改");
assertTrue(userService.modifyUser(user));       // 成功，version 0→1
user.setVersion(v);                     // 关键：退回旧版本，模拟"别人的旧对象"
user.setNickname("第二次改");
assertFalse(userService.modifyUser(user));      // 被乐观锁拦下
```

### 14.4 自测：能不能回答这几个问题

1. 为什么 `@TableName` 上要写 `autoResultMap = true`？不写会怎样？
2. `IdType.AUTO` 和默认的 `ASSIGN_ID` 有什么区别？什么情况下雪花 ID 会出问题？
3. `updateById` 返回 `false` 有哪三种可能？为什么都不抛异常？
4. 逻辑删除之后，为什么 `selectById` 查不到、但数据库里能查到？
5. 想绕过逻辑删除查已删除的数据，为什么 `.select()` 不行？正确做法是什么？
6. 乐观锁生效需要哪三个条件？最容易漏的是哪个？
7. 为什么更新时的自动填充不能用 `strictUpdateFill`？
8. `IService` 和 `ServiceImpl` 在 3.5.9 之后换到哪个包了？
9. 分页插件没注册会怎样？会报错吗？
10. `last("LIMIT " + size)` 为什么危险？本模块怎么防的？
11. `and(w -> ...)` 那层括号不加会出什么问题？
12. `spring.sql.init.mode` 默认值是什么？连 MySQL 为什么必须改成 `always`？

---

## 15. 知识地图与名词速查

### 15.1 本节要点回顾

| 概念 | 一句话 |
| --- | --- |
| ORM | 对象↔表的映射，解决阻抗失配；**MyBatis 严格说是 SQL Mapping，不是 ORM** |
| 三层结构 | Controller 接单 → Service 管规矩（事务） → Mapper 跑腿，不跨层 |
| 类型映射 | 金额用 `DECIMAL`/`BigDecimal`，时间用 `LocalXxx`，`TINYINT` 可映射 `Boolean` |
| `BaseMapper` | 免写 SQL 的通用 CRUD，接口体可以是空的 |
| `IService` / `ServiceImpl` | 在 Mapper 之上再包一层，批量、分页、计数、`exists` 都有现成方法 |
| `autoResultMap` | **JSON 字段能查出来的前提**，漏了查出来是 null |
| `IdType.AUTO` | 与 `AUTO_INCREMENT` 配套；用 `ASSIGN_ID` 会架空自增列且有前端精度问题 |
| 自动填充 | `MetaObjectHandler` 管审计字段；**更新时不要用 `strictUpdateFill`** |
| 逻辑删除 | `@TableLogic`，DELETE 变 UPDATE，查询自动追加 `deleted=0` |
| 绕过逻辑删除 | `BaseMapper` 做不到，必须**手写 SQL** |
| 乐观锁 | `@Version` + 插件 + **实体带 version**，靠影响行数判断有没有人抢先改 |
| 条件构造器 | `LambdaQueryWrapper` 选条件，`LambdaUpdateWrapper` 改数据，`and(w->...)` 别忘了括号 |
| 条件可选 | `.eq(条件, 字段, 值)`，条件为 false 时该分支不进 SQL |
| `last()` | 原样拼字符串，**不能拼用户输入** |
| 分页插件 | `PaginationInnerInterceptor` + `Page`，**不注册就静默失效** |
| `@Transactional` | 写操作原子性；显式 `rollbackFor`；注意自调用失效 |
| 连接池 | HikariCP 默认；池上限之和要小于 MySQL `max_connections` |

### 15.2 名词速查

| 名词 | 解释 |
| --- | --- |
| **ORM** | 对象关系映射，把对象操作翻译成表操作 |
| **阻抗失配** | 对象模型和关系模型对不上的地方（继承、集合、粒度） |
| **SQL Mapping** | MyBatis 的定位：SQL 与对象的映射，SQL 主要由人写 |
| **`BaseMapper`** | MyBatis-Plus 的通用 Mapper 接口，单表 CRUD 全都有 |
| **`IService`** | 在 Mapper 之上封装的 Service 接口，多出批量与 `exists` |
| **`ServiceImpl`** | `IService` 的默认实现，继承它就有了全部基础能力 |
| **`Wrapper`** | 条件构造器，负责拼 `WHERE` / `SET` |
| **`LambdaQueryWrapper`** | 用方法引用（`User::getName`）指定字段的查询构造器 |
| **`LambdaUpdateWrapper`** | 更新用的构造器，`set()` 指定要改的列 |
| **`Page`** | 分页对象，携带 `current` / `size`，返回时带 `records` / `total` |
| **`PaginationInnerInterceptor`** | 分页拦截器，负责改写 SQL 加 `LIMIT` 和 `COUNT` |
| **`OptimisticLockerInnerInterceptor`** | 乐观锁拦截器，负责拼版本条件 |
| **乐观锁** | 不加锁，写入时校验版本号，冲突就失败 |
| **悲观锁** | 直接锁行（`SELECT ... FOR UPDATE`），别人等待 |
| **丢失更新** | 两个人基于同一份旧数据修改，后写的覆盖先写的 |
| **`@TableLogic`** | 逻辑删除标记，让 DELETE 变成 UPDATE |
| **自动填充** | `MetaObjectHandler` 在插入/更新时自动补字段值 |
| **`IdType.AUTO`** | 主键用数据库自增 |
| **`ASSIGN_ID`** | 主键由 MyBatis-Plus 生成雪花算法 ID |
| **JCBD 批处理** | 多条 SQL 攒成一批发送，减少网络往返（`saveBatch` 用的） |
| **HikariCP** | Spring Boot 默认连接池 |
| **连接池** | 预先建好一批连接复用，避免每次建连的开销 |
| **`max_connections`** | MySQL 允许的最大连接数，默认 151 |
| **SHA-256** | 哈希算法，输出 64 位十六进制，所以密码列用 `CHAR(64)` |
| **`@JsonProperty(WRITE_ONLY)`** | 只接收不序列化，用来防止密码回显 |

---

## 16. 内容小结与学习小结

### 16.1 内容小结

| 能力 | 使用场景 | 本模块的落点 |
| --- | --- | --- |
| 通用 CRUD | 单表增删改查 | `BaseMapper` + `IService`，**21 个 Service 方法零 SQL** |
| 类型映射 | 每列对应一个 Java 类型 | 23 个字段覆盖 12 种 MySQL 类型 |
| 条件构造器 | 动态查询与更新 | `buildCondition` 统一拼装，7 个可选条件 |
| 字段自动填充 | 审计字段不用业务关心 | `MyMetaObjectHandler`，实测 4 个字段自动补 |
| 逻辑删除 | 数据可恢复可追溯 | `@TableLogic`，DELETE 变 UPDATE |
| 乐观锁 | 并发更新防覆盖 | `@Version` + 插件，实测第二次被拦下 |
| 事务 | 多写操作原子性 | `@Transactional(rollbackFor = Exception.class)` + 实测回滚 |
| 分页 | 列表接口带总数 | `PaginationInnerInterceptor` + `Page` |
| 连接池 | 降低建连开销 | HikariCP，4 个参数已配 |
| 自动化测试 | 验证上面每一条 | 2 个测试类、10 个用例、**全部通过** |

### 16.2 学习小结

> 这周的 05-mybatis，是我做这个仓库以来**最容易"看起来对"**的一节。前面 01-04 踩的坑基本都会让应用起不来或者接口报错，我能立刻发现；**这一节的坑有一半是静默的**——不报错、不崩溃，只是数据悄悄不对。
>
> 最大的打击来自那个版本不兼容。测试一跑，三个全挂，报的是 `Failed to load ApplicationContext`。我第一反应是"`application.yml` 写错了"，把数据源配置翻来覆去看了好几遍，账号密码库名一个个核对，全都对。后来去翻 `target/surefire-reports/` 里的报告原文，才在最底层的 `Caused by` 里看到 `NoSuchMethodError`，指向 `PropertyMapper.alwaysApplyingWhenNonNull()`。顺着查下去，发现 `mybatis-plus-spring-boot4-starter:3.5.14` 内部导入的是 Spring Boot `4.0.0-M2`——**一个里程碑版本**。名字里明明写着 boot4，装的却是照着里程碑版编的东西。
>
> 这件事改变了我对"依赖版本"的态度。以前我觉得版本号随便填个新的就行，现在知道**要看它内部导入的 BOM**。而且我学会了一个具体动作：**报错说"容器起不来"时，直接翻报告最底部的 `Caused by`**，别停留在表面那行。
>
> 第二个让我警惕的是**乐观锁的静默失效**。`version` 字段为 `null` 时，MyBatis-Plus 就不拼版本条件了——不报错、不警告，你以为有并发保护，其实一点都没有。练习 5 让我亲手把这件事走了一遍：必须 `getById` 查出来、必须检查 `updateById` 的返回值。**不检查返回值等于白配**，因为它失败的方式就是"安静地返回 false"。
>
> 还有 `strictUpdateFill` 那个：更新时 `updateTime` 永远不变，因为查出来的实体已经有旧值了，而严格填充只在字段为 `null` 时才写。代码看起来完全合理，跑起来什么都没做。
>
> 这些东西让我重新理解了一句话：**不报错的不等于是对的**。而且我发现，**静默失效的坑几乎都集中在"框架帮你做的事"上**——自动填充、逻辑删除、乐观锁、分页插件、JSON 类型转换。这些能力的特点是"配对了就自动生效，配错一点就静默失效"。所以我现在对这些功能的态度是：**配完之后必须用测试验一遍，不能靠"我写了注解"来判断它生效了。**
>
> 也是因为这个，这一节我最后选择了**写自动化测试**而不是手工点接口。10 个用例里，`testSelectById` 那条专门断言 `tags` 不为 `null`——那是在验证 `autoResultMap` 真的生效；`testInsert` 专门断言 `user.getId()` 不为 `null`——那是在验证主键真的回填了。**这些"框架承诺的行为"，只有断言过才算数。**
>
> 最后是测试本身教我的：我一开始断言"符合条件的用户有 3 条"，结果因为上一条测试留下的脏数据变成 2 条。让我明白**断言应该描述"性质"而不是"数量"**。还有一条测试没有任何断言，查到 `null` 也显示绿色——**假绿比失败更危险**。

### 16.3 作业要求完成度自查

| # | 要求 | 状态 | 证据 |
| --- | --- | --- | --- |
| 1 | 建一张覆盖常用类型的表 | ✅ | `schema.sql`，23 列覆盖 12 种类型 |
| 2 | `BaseMapper` / `IService` 完成单表 CRUD | ✅ | 21 个 Service 方法，零 SQL |
| 3 | `MetaObjectHandler` 自动填充 | ✅ | 实测 INSERT 多出 4 个字段（见 11.3） |
| 4 | 逻辑删除 | ✅ | 实测 DELETE 变 UPDATE（见 11.3） |
| 5 | 乐观锁 | ✅ | 实测 `SET version=1 WHERE version=0`（见 11.3）；练习 5 通过 |
| 6 | `@Transactional` 事务 | ✅ | 练习 3 实测 count 不变 |
| 7 | `LambdaQueryWrapper` 条件查询 | ✅ | `buildCondition` + 5 个专用查询 |
| 8 | 连接池参数 | ✅ | HikariCP 4 个参数已配（见 10.2） |
| 9 | Controller 层 | ✅ | 19 个接口，每个方法带注释 |
| 10 | 自动化测试 | ✅ | **5 个测试类、27 个用例**，`mvn clean test` 全绿（见 11.2） |
| 11 | 接口同步 Apifox 并测试 | ⬜ | 19 个用户接口 + 5 个课程接口的清单见 8.2；本地调过，**Apifox 集合还没整理上传** |
| 12 | Druid 监控页 | ⬜ | 未实现（见 10.7），默认 HikariCP 够用 |
| 13 | 内容小结 | ✅ | 16.1 |
| 14 | 学习小结 | ✅ | 16.2 |
| 15 | 课后练习 | ✅ | **五题全部完成**，每题的"遇到什么、怎么定位、怎么解决"见 14.2 |

---

## 17. 我代码里的待修正点

- [x] ~~**`createTime` 的日期格式没实测**~~ **已实测确认：`@JsonFormat` 双向生效**。`GET /users/1` 返回 `"2026-09-01 10:00:00"`（空格分隔，不是 ISO 的 `T`），`POST /users` 传 `"2026-10-11 09:15:30"` 也被正确解析。**Jackson 3 兼容 Jackson 2 的注解，不需要改导入**。详见 5.4
- [ ] **`pom.xml` 里 `<source>21</source>` / `<target>21</target>` 实际没生效**（见坑 2）：编译日志是 `release 17`。要么删掉这两行承认用 17，要么加 `<maven.compiler.release>21</maven.compiler.release>` 真正用上 21
- [ ] **`updateById` 的返回值在业务里没检查**：`modifyUser` 和 `recharge` 都直接把 `boolean` 返回给 Controller，**调用方没有任何"更新失败"的处理**。乐观锁冲突时用户看到的是 `false`，不是明确的提示
- [x] ~~**`uk_username` 唯一索引与逻辑删除冲突**~~ **已确认是真实副作用**：删掉的用户仍占着用户名，新用户无法用同名注册。订单表 `t_order` 也有同样问题（`uk_order_no`）。**策略还没定**，见 14.2 练习 5 的说明
- [ ] **`password` 用 SHA-256 无盐**：教学演示够用，**真实项目必须用 BCrypt / Argon2 这类带盐的慢哈希**，SHA-256 对撞库几乎没有抵抗
- [ ] **`applyDefaults` 不会把传入的明文密码转摘要**：只处理"没传密码"的情况；如果调用方传了明文，会**原样存进库**。要做真正的"传明文就哈希"，得在 Service 里再加一步
- [ ] **`selectRawById` 的手写 SQL 列不全**（见 9.3）：只 `SELECT` 了 5 列，如果以后拿它当通用查询会缺字段。要么补全，要么在注释里写清"仅用于特定场景"
- [ ] **`UserMapper.slowQuery` 是压测探针，不该留在生产代码里**：它用 `SELECT SLEEP(n)` 强行拖慢查询，只为复现连接池超时（见 14.2 练习 4）。已加注释说明，但真实项目应删掉或挪到测试目录
- [ ] **`UserQuery` 缺少 `keyword` 之外的模糊字段**：现在只能按 username / nickname 模糊，`email`、`phone` 不行
- [ ] **`statistics` 里 `genderCount` 的 key 是原始列名**：返回的是 `[{gender=1, cnt=2}, ...]`，前端拿到的是数据库列名而不是驼峰。需要的话要在 Service 里转一道
- [ ] **`deleteByStatus` 太危险**：`DELETE /users/status/1` 会把所有正常用户"删"掉，**没有权限校验、没有二次确认、没有数量上限保护**
- [ ] **`createUserBatch` 的返回值是数组长度而不是真实影响行数**（Controller 里 `return users.size()`）：如果部分失败（虽然事务会回滚），这个数字会骗人
- [ ] **类头注释缺失**：`User`、`UserService`、`UserServiceImpl`、`UserMapper` 都没有 `@author` 和类说明（`UserController` 已补）
- [ ] **测试的 `practice4` / `practice5` 末尾手动复原数据**：更稳的做法是抽成 `@AfterEach`，避免以后新增用例时忘记
- [ ] **`UserMapperTest` 和重复的 `UserServiceTest` 已删除**：删掉的是过时的旧测试，功能已被 `UserMapperCrudTest` 和 `service/impl/UserServiceTest` 覆盖

---

## 18. 还没搞懂的问题

> **已经从"存疑"变成"已解决"的**：
>
> - ~~Jackson 3 认不认 Jackson 2 的 `@JsonFormat`？~~ → **认**。实测 `GET /users/1` 返回 `"2026-09-01 10:00:00"`（空格而非 ISO 的 `T`），反方向 POST 也解析正确。详见 5.4。**这条是练习 2 解决的。**

1. **逻辑删除和唯一索引的冲突怎么解才是对的？** 我知道三种思路（物理删除、组合唯一索引 `(username, deleted)`、删除时改名），但**组合唯一索引在"同一用户被删两次"时还是会冲突**（两行 `deleted=1`）。到底哪种是业界通行做法，**我还没查到确定答案**。
2. **乐观锁的"失败之后"该怎么办？** 现在 `updateById` 返回 `false` 就结束了，调用方拿不到"是版本冲突还是记录不存在"。`OptimisticLockerInnerInterceptor` 内部有没有办法区分这两种情况？**我推测要靠影响行数为 0 时再查一次，但没实测。**
3. **`selectRawById` 这种手写 SQL 会不会也受其他拦截器影响？** 我确认了逻辑删除拦截器不改写手写 SQL，但如果以后加了多租户插件、防全表更新插件，**它们对手写 SQL 是什么态度，我没查**。
4. **`saveBatch` 的真实批量效果怎么验证？** 文档说它是 JDBC 批处理，但 MyBatis 默认的 `ExecutorType` 是 `SIMPLE`，**真正的批量需要 `ExecutorType.BATCH`**。`saveBatch` 内部是通过 `SqlSessionFactory` 重开来实现的，具体一次网络往返发多少条，**我没抓包验证过**。
5. **连接池参数到底该怎么定？** `CPU * 2 + 磁盘数` 这个公式我给的是 10（本机 8 核），但**"磁盘数"在现代 SSD/NVMe 上还有没有意义**？云数据库场景下 Amazon 建议的公式又不一样，**我还没搞清哪个适用**。
6. **`spring.sql.init` 和 Flyway 能不能共存？** 本模块用 `schema.sql` 每次重建，真实项目要用迁移工具。如果哪天要换，`sql.init` 该不该关掉、两者执行顺序如何，**我没试过**。
7. **`@TableLogic` 和物理删除能不能共存？** 现在配了 `@TableLogic` 之后，**所有** `delete` 都变逻辑删除，没法对某一条执行物理删除。要不要留一个"彻底删除"的入口（比如后台管理用），**我还没想好怎么设计**。
8. **乐观锁冲突在高并发下会不会变成"活锁"？** 如果一批请求互相冲突、全部失败重试，会不会大家一起重试又一起失败？**理论上有这个风险，但我没做过压测。**
9. **`Page` 对象的 `count` 查询能不能优化？** 现在复杂查询的 `COUNT(*)` 是包一层子查询算的，数据量大时会很慢。文档提到可以手写 count SQL 或关掉 `searchCount`，**具体在什么数据量级下需要这么做，我没有数据。**

---

**上一篇**：[04 · Web 进阶](/04-web) ｜ **下一篇**：06 · Redis 缓存（待写）
