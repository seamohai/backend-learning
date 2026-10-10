# 05 - 数据访问（MyBatis-Plus）学习总结

本次通过一个 `t_user` 表，完整走通了「配置数据源 → 实体映射 → Mapper → 单元测试」这条链路，
实现了对数据库的**新增、查询、删除**操作。

---

## 一、本次完成的内容

### 1. 数据源配置

在 `application.yml` 中配置 MySQL 连接：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/backend_learning?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: ******
```

要点：

- `driver-class-name` 使用 MySQL 8 的驱动类 `com.mysql.cj.jdbc.Driver`（旧的 `com.mysql.jdbc.Driver` 已废弃）
- URL 中必须**带上库名**（`/backend_learning`），否则会报 `1046 - No database selected`
- `serverTimezone=Asia/Shanghai` 用于和数据库中的 `DATETIME` 字段对齐

### 2. 建表

```sql
CREATE TABLE t_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50) NOT NULL,
    age         INT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 3. 实体类映射

```java
@Data
@TableName("t_user")                 // 类名 User 推不出 t_user，必须显式指定
public class User {

    @TableId(type = IdType.AUTO)     // 对应数据库的 AUTO_INCREMENT
    private Long id;

    private String username;
    private Integer age;
    private LocalDateTime createTime;
}
```

要点：

- **表名与类名不一致时必须写 `@TableName`**，否则 MyBatis-Plus 会去找 `user` 表
- **`IdType.AUTO` 必须和 DDL 中的 `AUTO_INCREMENT` 配套**。若用默认的 `ASSIGN_ID`（雪花 ID），
  应用会自己生成 19 位 ID，数据库的自增列就被架空了
- 字段用包装类型（`Integer`）而非基本类型（`int`），以便区分「未填」和「填了 0」
- 下划线转驼峰由配置项统一处理，普通字段无需写 `@TableField`

### 4. Mapper

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

继承 `BaseMapper<User>` 后，单表的增删改查方法（`insert` / `selectById` / `deleteById` 等）直接可用，
不需要写任何 SQL。加上 `@Mapper` 注解后也无需再配 `@MapperScan`。

### 5. 单元测试

```java
@SpringBootTest
@Slf4j
class UserMapperTest {

    @Resource
    private UserMapper userMapper;

    @Test
    void testInsert() { /* ... */ }

    @Test
    void testSearch() { /* ... */ }

    @Test
    void testDelete() { /* ... */ }
}
```

两个关键注解的作用：

| 注解 | 作用 | 不加的后果 |
|---|---|---|
| `@SpringBootTest` | 启动完整的 Spring 容器，让 MyBatis 完成自动配置 | Mapper 不会被创建，注入失败 |
| `@Resource` / `@Autowired` | 把容器里的 Mapper 代理对象注入进来 | 字段为 `null`，调用时报 NPE |

> 注意：Spring Boot 3 以后 `@Resource` 应导入 `jakarta.annotation.Resource`，
> 而不是旧的 `javax.annotation.Resource`，否则注入会是 `null`。

另外验证了 MyBatis-Plus 的一个便利特性：**`IdType.AUTO` 时，插入后数据库生成的自增主键会回填到实体对象中**，
即 `insert(user)` 之后可以直接 `user.getId()` 拿到 ID。

---

## 二、本次遇到的主要问题

### 问题 1（核心）：依赖版本不兼容导致容器启动失败

**现象**

三个测试全部失败，报错为：

```
java.lang.IllegalStateException: Failed to load ApplicationContext
```

**排查过程**

表面的报错信息是「容器加载失败」，容易误以为是 `application.yml` 配置写错。
实际查看 `target/surefire-reports/` 下的测试报告，在**最底层的 `Caused by`** 中找到了真正原因：

```
Caused by: java.lang.NoSuchMethodError:
'org.springframework.boot.context.properties.PropertyMapper
 org.springframework.boot.context.properties.PropertyMapper.alwaysApplyingWhenNonNull()'
    at com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties$CoreConfiguration.applyTo(...)
    at com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.applyConfiguration(...)
```

**根因分析**

- 项目父 POM 使用 **Spring Boot 4.1.1**（正式版）
- 依赖的 `mybatis-plus-spring-boot4-starter:3.5.14`，其 POM 中导入的是
  **`spring-boot-dependencies:4.0.0-M2`（里程碑版本）**
- `PropertyMapper.alwaysApplyingWhenNonNull()` 这个方法在 Boot 4 的**正式版中已被移除**，
  但里程碑版中仍然存在
- 因此编译期不报错，**运行期才抛 `NoSuchMethodError`**

**结论：starter 的命名（`boot4`）并不代表它一定与当前使用的 Spring Boot 正式版兼容，
关键在于它内部导入的 `spring-boot-dependencies` 是里程碑版还是正式版。**

**解决办法**

将 MyBatis-Plus 升级到 **`3.5.17`** —— 该版本的 POM 已导入 `spring-boot-dependencies:4.0.1`（正式版），
并配套 `mybatis-spring:4.0.0`。升级后容器正常启动，SQL 正常执行，测试全部通过。

排查时先通过命令行参数覆盖版本做了对照验证，确认有效后再修改 POM：

```bash
mvn -Dmybatis-plus.version=3.5.17 test
```

**经验沉淀**

1. 遇到 `Failed to load ApplicationContext` 时，应直接翻到报告最底层的 `Caused by`，
   而不是停留在表面的报错信息上
2. 引入与 Spring Boot 强相关的第三方 starter 时，要确认它适配的 Boot 版本
3. 查一个依赖是否兼容，可以直接去 Maven 中央仓库看它的 POM 导入了哪个版本的 BOM

### 问题 2：数据库未选择（`1046 - No database selected`）

**现象**：在客户端新建连接后直接执行建表语句，报 `1046 - No database selected`。

**原因**：连接建立成功，但没有指定使用哪个数据库，服务端不知道该往哪里建表。

**解决**：先执行 `USE backend_learning;` 切换到目标库，或在建表时直接带上库名前缀
（`CREATE TABLE backend_learning.t_user (...)`）。

**收获**：`1046` 是「连接 OK 但没选库」，与 `1049`（库不存在）、`1045`（认证失败）是不同的错误，
可以据此快速判断问题出在哪一层。

### 问题 3：IDEA 生成的测试类只有空壳

**现象**：通过 IDEA 右键生成的 `UserMapperTest` 只有一个空类，没有任何注解和测试方法。

**原因**：生成时未勾选任何待生成的方法。同时 `UserMapper` 的方法是从 `BaseMapper` **继承**而来的，
IDEA 的方法列表里列不出来，所以无法勾选，最终只生成了空壳。

**解决**：手动补充 `@SpringBootTest`、`@Resource` 注入和 `@Test` 方法。

---

## 三、待改进 / 存疑的地方

`testDelete` 的写法存在**执行顺序依赖**，值得后续优化：

```java
@Test
void testDelete() {
    int result = userMapper.deleteById(1);   // 硬编码 id = 1
    assertEquals(1, result);                 // 要求必须删除 1 条
}
```

- JUnit 5 **不保证测试方法的执行顺序**。实测中出现过 `testDelete` 先于 `testInsert` 执行的情况，
  此时表中还没有 `id = 1` 的记录，`deleteById` 返回 `0`，断言失败
- 断言硬编码了 `id = 1`，一旦表中没有该记录（或自增 ID 已经增长到别的值），测试同样会失败

**更稳妥的写法**：先 `insert` 一条数据并利用主键回填拿到真实 ID，再用这个 ID 去执行删除和断言，
这样测试之间就不存在顺序依赖。

另外，测试类目前**没有加 `@Transactional`**，因此 `testInsert` 产生的数据会**真实写入数据库**，
多跑几次会留下重复数据。如果希望测试不污染数据库，可以加上 `@Transactional` 让测试结束后自动回滚。

---

## 四、小结

| 项目 | 结果 |
|---|---|
| 数据源连接 | ✅ 通过 `spring.datasource` 配置成功连接 MySQL |
| 实体映射 | ✅ `@TableName` + `@TableId(AUTO)` 与表结构对齐 |
| 单表 CRUD | ✅ 通过继承 `BaseMapper` 免写 SQL 实现增、查、删 |
| 单元测试 | ✅ `@SpringBootTest` + `@Resource` 注入 Mapper 验证通过 |
| 核心问题 | ✅ 定位并解决 MyBatis-Plus 与 Spring Boot 的版本不兼容问题 |
