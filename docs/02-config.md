# 02 · 配置管理：@Value、@ConfigurationProperties、多环境与配置校验

> - 模块：[02-config](../02-config)
> - 工程坐标：`top.seamohai:beckend-learning:0.0.1-SNAPSHOT`
> - 环境：JDK **21** ｜ Spring Boot **4.1.1** ｜ Tomcat **11.0.24** ｜ Hibernate Validator **9.1.3** ｜ 端口 **9090**
> - 本篇是**自包含学习笔记**：所有概念都在文中解释清楚，不需要另查任何资料
> - 和 01 那篇不一样的地方：**本文的接口返回、启动日志、报错信息全部是实测结果**，不是按代码推导的

---

## 目录

1. [本节目标](#1-本节目标)
2. [背景：配置为什么值得单独一节](#2-背景配置为什么值得单独一节)
3. [四种读取配置的方式总览](#3-四种读取配置的方式总览)
4. [@Value 的四种形态](#4-value-的四种形态)
5. [@ConfigurationProperties：一组配置绑成对象](#5-configurationproperties一组配置绑成对象)
6. [多环境：@Profile 与多环境配置文件](#6-多环境profile-与多环境配置文件)
7. [配置校验：启动就拦住写错的配置](#7-配置校验启动就拦住写错的配置)
8. [作业要求 3：给原有属性类加邮箱和日期](#8-作业要求-3给原有属性类加邮箱和日期)
9. [接口与实测结果（Windows 实操）](#9-接口与实测结果windows-实操)
10. [同步到 Apifox](#10-同步到-apifox)
11. [我踩过的 5 个坑](#11-我踩过的-5-个坑)
12. [报错速查表](#12-报错速查表)
13. [练习](#13-练习)
14. [知识地图与名词速查](#14-知识地图与名词速查)
15. [内容小结与学习小结](#15-内容小结与学习小结)
16. [还没搞懂的问题](#16-还没搞懂的问题)

---

## 1. 本节目标

学完这一节，要能独立做到四件事：

1. 用 `@Value` 的四种形态取值：**占位符引用 / 默认值 / 随机值 / SpEL 表达式**；
2. 用 `@ConfigurationProperties` 把**一组同前缀、结构复杂的配置**绑定成一个 Java 对象；
3. 用 `@Profile` + `application-{profile}.yml` 区分**开发 / 生产**环境；
4. 用 `@Validated` + JSR-303 注解做**配置校验**，让写错的配置在**启动时**就失败。

一句话概括这四件事的分工：

> **`@Value` 取零散的值 → `@ConfigurationProperties` 把成组的配置绑成对象 → `@Profile` 决定用哪套值 → 校验在启动时兜底，不让错误配置流进运行期。**

四者不是互相替代的关系，是**配合**的关系：本模块里 `app` 那组用 `@Value` 取（因为字段零散），`student` 那组用 `@ConfigurationProperties` 绑（因为是嵌套结构），`env` 那组跟着 profile 走，最后 `app` 和 `student` 两组都加了校验。

---

## 2. 背景：配置为什么值得单独一节

### 2.1 硬编码有多难受

```java
// 反面教材
int port = 9090;
String dbUrl = "jdbc:mysql://localhost:3306/test";
```

问题是：

| 场景 | 后果 |
| --- | --- |
| 换个端口 | 改代码 → 重新编译 → 重新打包 → 重新发布 |
| 开发/生产用不同数据库 | 只能靠"打包前记得改回来"，迟早出事 |
| 别人接手 | 不知道哪些值是"可改的"，哪些是业务逻辑 |

配置抽出来的本质是：**把"会变的东西"从"不会变的东西"里分离出去**。

### 2.2 配置从哪来，谁说了算

这是 01 里提过的优先级，02 的每一个坑都跟它有关，值得再看一眼（简化版，从高到低）：

```
命令行参数（--app.port=70000）
  > 操作系统环境变量 / SPRING_APPLICATION_JSON
  > 外置的 application.yml
  > jar 包内的 application.yml（src/main/resources）
  > application-{profile}.yml（profile 专属文件，覆盖主文件同名项）
  > 代码里的默认值（@Value 冒号后面的）
```

> 我在验证校验功能时就是靠 `--app.port=70000` 这个**命令行参数**覆盖 yml 的值，不动任何文件就复现了启动失败——这就是"优先级最高"的实用价值。

### 2.3 配置一多，新的麻烦来了

| 麻烦 | 表现 | 本节怎么解决 |
| --- | --- | --- |
| 取值代码满天飞 | 每个类里一堆 `@Value`，改个 key 要全局搜索 | `@ConfigurationProperties` 集中绑定 |
| 环境切换靠手改 | 上线前手动改 yml，忘了就事故 | `@Profile` + 多文件 |
| 写错了没人知道 | 端口写成 70000，跑到某一个接口才报错 | `@Validated` + JSR-303，**启动即失败** |

第三条是本节最"工程化"的一点：**能在启动阶段发现的问题，就不要留到运行期。**

---

## 3. 四种读取配置的方式总览

作业给的对照表，我把本模块的落点补上了：

| 案例 | 使用场景 | 本模块的落点（真实文件） |
| --- | --- | --- |
| `@ConfigurationProperties` | 一组同前缀、结构复杂的配置 | `student`：List + Map + 嵌套对象 + 对象列表 |
| 多环境 + `@Profile` | 开发 / 测试 / 生产环境差异 | `application-dev.yml`、`application-prod.yml`、`DevEnvService` / `ProdEnvService` |
| 进阶 `@Value` | 零散字段、默认值、随机值、动态表达式 | `app.author`、`app.remark:暂无备注`、`random.uuid`、SpEL 三元表达式 |
| 配置校验 | 启动时兜底，防止非法配置 | `@Validated` + `@NotBlank` / `@Min` / `@Max` / `@Email` / `@Past` |

### 3.1 `@Value` 和 `@ConfigurationProperties` 到底怎么选

这是本节最该记住的一张表：

| 对比项 | `@Value` | `@ConfigurationProperties` |
| --- | --- | --- |
| 适合的量 | 单个、零散的几个值 | 一组、同前缀的值 |
| 配置层级 | 只能取到某一层的**一个标量** | 能绑**嵌套对象、List、Map** |
| 松散绑定 | ❌ 不支持，key 必须一字不差 | ✅ 支持，`max-count`/`maxCount` 都能绑 |
| SpEL 表达式 | ✅ 支持 `#{...}` | ❌ 不支持 |
| 随机值 | ✅ 支持 `${random.uuid}` | ❌ 一般不用这种方式 |
| 配置校验 | ❌ 不支持 | ✅ 类上加 `@Validated` 即可 |
| 默认值 | ✅ `${key:默认值}` | 直接在 Java 字段上给初始值 |
| 写错了会怎样 | 缺 key 直接启动失败（除非写了默认值） | 缺 key 是 `null`，可能一路带到运行期 |

一句话选型：**零散的、要算的、要随机的用 `@Value`；成组的、有层级的、要校验的用 `@ConfigurationProperties`。**

---

## 4. @Value 的四种形态

`@Value` 的完整写法有两种外壳：

| 外壳 | 名称 | 谁来解析 | 例子 |
| --- | --- | --- | --- |
| `${...}` | 占位符（Placeholder） | 从 Environment 里**读配置** | `${server.port}` |
| `#{...}` | SpEL 表达式 | Spring 的表达式引擎**做计算** | `#{2 > 1}` |
| `#{}` 里套 `${}` | 两者结合 | 先读配置，再计算 | `#{${student.age} >= 18 ? '成年' : '未成年'}` |

下面是四种形态，全部来自 `ConfigController`。

### 4.1 形态一：占位符引用（配置引用配置）

`application.yml` 里配置之间可以互相引用：

```yaml
seamohai:
  name: 申陌海
  job: 学生

app:
  author: ${seamohai.name}     # ← 这里引用了上面的值
```

控制器里再取出来：

```java
@Value("${seamohai.name}")
private String myName;

@Value("${app.author}")          // 取到的是"申陌海"，因为 yml 里 author 引用了它
private String author;
```

**实测输出**：

```
GET /config/my    → 我的姓名是：申陌海，职业是：学生
GET /config/value → 占位符引用 author=申陌海；……
```

两个关键点：

1. 占位符可以**级联**：`app.author` → `seamohai.name` → `申陌海`。解析是递归的，但**不要写循环引用**，会直接报错；
2. `@Value("${app.author}")` 拿到的是**最终值**，不是 `${seamohai.name}` 这个字符串。

> **这条踩过坑**：`@Value` 里的名字必须和 yml 里的 **key 一字不差**。我一开始抄示例用了 `${mqxu.name}`，而 yml 里写的是 `seamohai.name`，应用直接起不来（见第 11 节坑 1）。

### 4.2 形态二：默认值 `${key:默认值}`

语法就是**冒号**：冒号前是 key，冒号后是默认值。

```java
@Value("${app.remark:暂无备注}")
private String remark;
```

而 `app` 那组配置里**故意不写 remark**：

```yaml
app:
  name: 配置管理模块
  author: ${seamohai.name}
  port: 8002
  max-count: 100
  # remark 故意不配，用来验证默认值生效
```

**实测输出**：

```
GET /config/value → ……默认值 remark=暂无备注；……
```

| 写法 | 配置存在 | 配置不存在 |
| --- | --- | --- |
| `${app.remark:暂无备注}` | 用配置里的值 | 用 `暂无备注` |
| `${app.remark:}` | 用配置里的值 | 用**空字符串** |
| `${app.remark}` | 用配置里的值 | ❌ **启动直接失败**：`Could not resolve placeholder 'app.remark'` |

**结论：没有默认值又不存在 = 启动失败。** 这也是为什么"配置写错"有时候表现为"应用根本起不来"。

### 4.3 形态三：随机值

```java
@Value("${random.uuid}")
private String randomUuid;

@Value("${random.int(1,100)}")
private Integer randomInt;
```

| 写法 | 结果 |
| --- | --- |
| `${random.value}` | 32 位随机字符串 |
| `${random.uuid}` | 随机 UUID |
| `${random.int}` | 随机 int（Integer 范围内） |
| `${random.int(1,100)}` | 1~100 之间的随机整数 |
| `${random.long}` | 随机 long |

**实测输出**：

```
GET /config/value → ……随机 UUID=89bf0eb7-ab47-4d32-8d02-cccfe166de8d；随机整数=88；……
```

### ⚠️ 4.3.1 这里有一个最容易误解的点（我实测确认过）

**随机值是"启动时随机一次"，不是"每次请求都随机"。**

我连续请求了两次 `/config/value`，UUID 和随机整数**完全一样**：

```
第 1 次：随机 UUID=89bf0eb7-ab47-4d32-8d02-cccfe166de8d；随机整数=88
第 2 次：随机 UUID=89bf0eb7-ab47-4d32-8d02-cccfe166de8d；随机整数=88   ← 一模一样
```

原因：`@Value` 是在**Bean 创建时解析一次**，把结果**注入到字段**里。控制器是单例 Bean，之后每次请求读的都是同一个字段值。

所以要看到随机值变化，必须**重启应用**，不是刷新页面。

> 录制效果视频时这一点特别重要：如果按"刷新页面"来演示随机值，两次结果相同，看起来就像功能坏了。

### 4.4 形态四：SpEL 表达式

```java
@Value("#{${student.age} >= 18 ? '成年' : '未成年'}")
private String adult;
```

拆开看这一行：

| 片段 | 含义 |
| --- | --- |
| `#{ ... }` | 外层是 SpEL，交给表达式引擎**计算** |
| `${student.age}` | 里层是占位符，先从配置里**读出** `10` |
| `>= 18 ? '成年' : '未成年'` | 用读出来的值做三元判断 |

执行顺序是：**先解析 `${}` 得到 `10`，拼成 `# {10 >= 18 ? '成年' : '未成年'}`，再交给 SpEL 求值**。

**实测输出**（`student.age: 10`）：

```
SpEL adult=未成年
```

> 想让视频里出现"成年"，把 `student.age` 改成 `19` 再重启即可——同一段表达式因为配置不同输出不同结果，正好证明它是**动态计算**的，不是写死的字符串。

### 4.5 `@Value` 的边界（为什么还需要 `@ConfigurationProperties`）

`@Value` 有四个明确的短板：

1. **不支持松散绑定**：yml 里写 `max-count`，你就必须写 `@Value("${app.max-count}")`，写成 `maxCount` 取不到；
2. **取不到结构化数据**：`student.hobbies` 在 YAML 里是一个列表，`@Value` 没法把它直接变成一个 `List<String>` 字段（`@ConfigurationProperties` 可以）；
3. **不能加校验注解生效**：`@Value` 注入的字段上写 `@Min` 是没用的，校验只在 `@ConfigurationProperties` 上生效；
4. **字段一多就啰嗦**：20 个配置就 20 个 `@Value`，还要在 20 个地方写 key。

第 5 节就是来解决这些的。

---

## 5. @ConfigurationProperties：一组配置绑成对象

### 5.1 配置长什么样

```yaml
student:
  name: 张三
  age: 10
  email: 181@163.com
  birthday: 2006-02-18
  hobbies:
    - 篮球
    - 编程
    - 阅读
  scores:
    chinese: 90
    math: 95
    english: 88
  address:
    province: 江苏省
    city: 南京市
  courses:
    - name: 高等数学
      credit: 4
    - name: 大学英语
      credit: 3
```

这一段故意写得很"复杂"，因为它要覆盖四种结构：

| yml 结构 | Java 类型 | 说明 |
| --- | --- | --- |
| `name: 张三` | `String` | 标量 |
| `hobbies:` 下面一串 `-` | `List<String>` | 列表 |
| `scores:` 下面 `键: 值` | `Map<String, Integer>` | 映射 |
| `address:` 下面还有层级 | 嵌套对象 `Address` | 对象 |
| `courses:` 下面一串 `- name/credit` | `List<Course>` | **对象列表** |

### 5.2 属性类怎么写

```java
package top.seamohai.config.properties;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "student")
public class StudentProperties {
    @NotBlank(message = "student.name 不能为空")
    private String name;

    @Min(value = 1, message = "student.age 必须大于等于 1")
    @Max(value = 150, message = "student.age 必须小于等于 150")
    private Integer age;

    private List<String> hobbies;
    private Map<String, Integer> scores;
    private Address address;
    private List<Course> courses;

    @NotNull(message = "student.birthday 不能为空")
    @Past(message = "student.birthday 必须是过去的日期")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    @NotBlank(message = "student.email 不能为空")
    @Email(message = "student.email 邮箱格式不正确")
    private String email;

    @Data
    public static class Address {
        private String province;
        private String city;
    }

    @Data
    public static class Course {
        private String name;
        private Integer credit;
    }
}
```

四个注解各自的职责，一个都不能少：

| 注解 | 作用 | 少了会怎样 |
| --- | --- | --- |
| `@ConfigurationProperties(prefix = "student")` | 说明"去 `student` 前缀下找值" | 不知道绑哪一段配置 |
| `@Component` | 把这个类注册成 Bean，让 Spring 去绑 | 类根本不会被创建，注入时报找不到 Bean |
| `@Data`（Lombok） | 生成 setter，绑定要靠 setter 写值 | 绑不上，字段全是 `null` |
| `@Validated` | 开启 JSR-303 校验（见第 7 节） | 校验注解全部失效 |

> **字段名 → key 的对应关系**：`private Integer age;` 对应 `student.age`；`private Integer credit;` 在 `Course` 里对应 `student.courses[0].credit`。名字对不上就是 `null`，**不会报错**——这是 `@ConfigurationProperties` 比 `@Value` 更"沉默"的地方，也是它更需要校验的原因。

### 5.3 松散绑定（relaxed binding）

`AppProperties` 里的字段是 `maxCount`（驼峰），而 yml 里写的是 `max-count`（短横线）：

```java
@Min(value = 1, message = "app.max-count 必须大于等于 1")
@Max(value = 1000, message = "app.max-count 不能超过 1000")
private Integer maxCount;
```

```yaml
app:
  max-count: 100
```

**这是能绑上的**，因为 `@ConfigurationProperties` 支持松散绑定：

| yml 里可以写 | 绑定到 Java 字段 |
| --- | --- |
| `max-count`（短横线，**官方推荐**） | `maxCount` |
| `maxCount`（驼峰） | `maxCount` |
| `max_count`（下划线） | `maxCount` |
| `MAX_COUNT`（环境变量风格） | `maxCount` |

> 官方推荐配置文件里统一用**短横线**小写（`kebab-case`），因为环境变量风格 `MAX_COUNT` 在 Linux 上最自然，驼峰在 yml 里容易看错。

### 5.4 实测：绑出来的是什么

```
GET /config/app →
{"author":"申陌海","maxCount":100,"name":"配置管理模块","port":8002}

GET /student/info →
{"address":{"city":"南京市","province":"江苏省"},
 "age":10,
 "birthday":"2006-02-18",
 "courses":[{"credit":4,"name":"高等数学"},{"credit":3,"name":"大学英语"}],
 "email":"181@163.com",
 "hobbies":["篮球","编程","阅读"],
 "name":"张三",
 "scores":{"chinese":90,"math":95,"english":88}}
```

两个观察：

1. **嵌套结构和对象列表都被正确绑定了**（`address` 是对象，`courses` 是对象数组），这是 `@Value` 做不到的；
2. JSON 字段顺序和类里的声明顺序不一样（是字母序）。**顺序由 Jackson 序列化决定，值都是对的**，想固定顺序可以加 `@JsonPropertyOrder`，一般不用管。

### 5.5 绑定失败时会发生什么

| 情况 | 结果 |
| --- | --- |
| yml 里少了某个 key | 字段是 `null` / 用 Java 里的初始值，**不报错** |
| key 拼写错了（比如 `maxcount`） | 同上，字段为 `null`，**不报错** |
| 值的类型不对（`age: 十岁`） | ❌ 绑定时报错，启动失败 |
| 值违反校验注解（`age: 0`） | ❌ 启动失败（见第 7 节） |

前两行是最隐蔽的：**配置少写了不会报错**。所以这两条防线要自己建立：

- 加 `@Validated` + `@NotNull` / `@NotBlank`，把"少写"变成"启动失败"；
- 加 `@ConfigurationProperties` 的 `ignoreUnknownFields`（默认 `true`，也就是多写的 key 被忽略）——多写的 key 不会报错，别指望它帮你发现拼写错误。

---

## 6. 多环境：@Profile 与多环境配置文件

环境差异要在**两个层面**处理，很多教程只讲第一个，其实两个都得会。

### 6.1 文件层面：`application-{profile}.yml`

```
src/main/resources/
├── application.yml          # 公共配置：端口、应用名、各业务配置
├── application-dev.yml      # 只有 dev 环境的值
└── application-prod.yml     # 只有 prod 环境的值
```

```yaml
# application-dev.yml
env:
  name: dev
  description: 开发环境
```

```yaml
# application-prod.yml
env:
  name: prod
  description: 生产环境
```

规则：

- `application.yml` **一定会加载**，放公共项；
- `application-dev.yml` 只在 `dev` 被激活时加载，**同名 key 覆盖主文件**；
- 千万不要把 `env.name: dev` 写进主文件，否则切环境就靠手改，等于没做多环境。

### 6.2 怎么激活 profile

| 方式 | 写法 | 适用 |
| --- | --- | --- |
| 写在主配置里（我用的） | `spring.profiles.active: dev` | 本地学习最省事 |
| 命令行参数 | `--spring.profiles.active=prod` | 部署时切换，优先级最高 |
| IDEA 运行配置 | Run Configuration → Active profiles 填 `dev` | 图形化，不改文件 |
| 环境变量 | `SPRING_PROFILES_ACTIVE=prod` | 容器 / 服务器 |

主配置里加这一段就够本地开发用了：

```yaml
spring:
  application:
    name: 配置管理
  profiles:
    active: dev
```

**实测启动日志**：

```
The following 1 profile is active: "dev"
```

### 6.3 Bean 层面：`@Profile` 按环境注册不同的实现

多环境不只是"值不同"，有时候是"**代码不同**"：dev 要连本地 mock，prod 要连真实服务。做法是接口 + 两个实现。

```java
public interface EnvService {
    String envInfo();
}
```

```java
@Service
@Profile("dev")
public class DevEnvService implements EnvService {
    @Override
    public String envInfo() {
        return "我是 dev 环境专属的 Bean";
    }
}
```

```java
@Service
@Profile("prod")          // ⚠️ 这里必须写 prod，抄错了就是大坑
public class ProdEnvService implements EnvService {
    @Override
    public String envInfo() {
        return "我是 prod 环境专属的 Bean";
    }
}
```

控制器直接按**接口类型**注入，不关心具体是哪个实现：

```java
private final EnvService envService;
```

**实测输出**（当前 profile = dev）：

```
GET /config/env → 当前环境：dev，开发环境；Profile Bean：我是 dev 环境专属的 Bean
```

**规则**：`@Profile("dev")` 的意思是"激活的 profile 列表里包含 `dev` 时，这个 Bean 才注册"。所以：

| 激活的 profile | 容器里的 `EnvService` 实现 | 结果 |
| --- | --- | --- |
| `dev` | 只有 `DevEnvService` | ✅ 正常 |
| `prod` | 只有 `ProdEnvService` | ✅ 正常 |
| `dev` + 两个类都写 `@Profile("dev")` | **两个** | ❌ 注入时冲突（见坑 3） |
| `prod` + 两个类都写 `@Profile("dev")` | **零个** | ❌ 找不到 Bean（见坑 3） |

最后两行是我真实踩到的，报错原文在第 11 节。

---

## 7. 配置校验：启动就拦住写错的配置

### 7.1 为什么需要它

配置写错的三种"死法"：

| 死法 | 例子 | 什么时候发现 |
| --- | --- | --- |
| 类型错 | `port: 八十` | 绑定时报错（启动就挂，还算好） |
| **值不合法** | `port: 70000`（端口范围是 0~65535） | 通常要等到**访问这个服务**才炸 |
| 值缺失 | 忘了配 `app.name` | 可能一路 `null` 传到某个接口，报一堆 `NullPointerException` |

`@Validated` + JSR-303 就是把第二、三种**提前到启动阶段**：启动失败总比线上某个接口 500 好排查。

### 7.2 三个必要条件

| 条件 | 具体做法 | 少了会怎样 |
| --- | --- | --- |
| 1. classpath 有校验实现 | 引入 `spring-boot-starter-validation` | 校验注解**直接无效**，不报错、不校验 |
| 2. 类上加 `@Validated` | `org.springframework.validation.annotation.Validated` | 注解无效（同上，最坑的是**静默失效**） |
| 3. 字段上加 JSR-303 注解 | `jakarta.validation.constraints.*` | 没有什么可校验的 |

依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

它拉进来两个关键东西：`jakarta.validation:jakarta.validation-api`（注解定义）和 `org.hibernate.validator:hibernate-validator`（真正干活的实现，我本地是 **9.1.3.Final**）。

> ⚠️ **命名空间是 `jakarta.*` 不是 `javax.*`**。Boot 3 开始从 Java EE 迁到 Jakarta EE，网上大量老教程还是 `javax.validation.constraints.NotBlank`，**照抄会报"找不到符号"**。

### 7.3 常用注解速查

| 注解 | 含义 | 本模块用在哪 |
| --- | --- | --- |
| `@NotNull` | 不能为 `null` | `student.birthday` |
| `@NotBlank` | 不能为 `null`，且去掉空白后长度 > 0（**只能用在字符串**） | `app.name`、`student.name`、`student.email` |
| `@NotEmpty` | 不能为 `null`，且集合 / 字符串长度 > 0 | （可加在 `hobbies` 上） |
| `@Min(1)` / `@Max(65535)` | 数值区间 | `app.port`、`app.maxCount`、`student.age` |
| `@Email` | 邮箱格式 | `student.email` |
| `@Past` / `@Future` | 必须是过去 / 未来的时间 | `student.birthday` |
| `@Size(min, max)` | 字符串长度或集合大小 | （暂时没用） |
| `@Pattern(regexp)` | 正则匹配 | （暂时没用） |
| `@Valid` | **级联校验**：让嵌套对象里的注解也生效 | （还没加在 `Address` 上，见第 16 节） |

`@NotBlank` 和 `@NotEmpty` 的区别值得记：

| 注解 | `null` | `""` | `"   "` |
| --- | --- | --- | --- |
| `@NotNull` | ❌ | ✅ | ✅ |
| `@NotEmpty` | ❌ | ❌ | ✅ |
| `@NotBlank` | ❌ | ❌ | ❌ |

### 7.4 `AppProperties` 是怎么写的

```java
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    @NotBlank(message = "应用名称 app.name 不能为空")
    private String name;

    private String author;

    @Min(value = 1, message = "app.port 必须大于等于 1")
    @Max(value = 65535, message = "app.port 必须小于等于 65535")
    private Integer port;

    @Min(value = 1, message = "app.max-count 必须大于等于 1")
    @Max(value = 1000, message = "app.max-count 不能超过 1000")
    private Integer maxCount;
}
```

`message` 是自己写的提示语，不写会输出英文默认提示（如 `must be less than or equal to 65535`）。**中文提示对排查特别有用**，因为报错信息里会直接把 `app.port` 和规则一起打出来。

### 7.5 实测：一条不合法配置是怎么让启动失败的

我把端口临时改成 `70000`（用命令行参数覆盖，不改文件）：

```powershell
mvn -pl 02-config spring-boot:run "-Dspring-boot.run.arguments=--app.port=70000"
```

**实测完整报错**：

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Binding to target top.seamohai.config.properties.AppProperties failed:

    Property: app.port
    Value: "70000"
    Origin: "app.port" from property source "commandLineArgs"
    Reason: app.port 必须小于等于 65535


Action:

Update your application's configuration
```

逐行读一遍，这段输出其实信息量很大：

| 行 | 含义 |
| --- | --- |
| `APPLICATION FAILED TO START` | 应用**没起来**，不是启动后出错 |
| `Binding to target ... AppProperties failed` | 失败发生在**绑定**阶段，目标类是 `AppProperties` |
| `Property: app.port` | 是哪个配置项 |
| `Value: "70000"` | 实际读到的值 |
| `Origin: ... property source "commandLineArgs"` | 这个值是从**哪来的**（命令行参数）。换成 yml 就是 `application.yml`，排查"我明明改了怎么没用"时特别有用 |
| `Reason: app.port 必须小于等于 65535` | 我写的 `message`，一眼看出违反了哪条规则 |
| `Action:` | Spring 给的下一步建议 |

启动日志里还能看到"先占端口、再回滚"的过程：

```
Tomcat initialized with port 9090 (http)
Exception encountered during context initialization - cancelling refresh attempt: ...
Stopping service [Tomcat]
```

也就是说：**Tomcat 已经开始初始化了，绑 `AppProperties` 时才失败，于是整个容器刷新被取消、Tomcat 被停掉。** 顺序上"创建 Bean → 绑定配置 → 校验"发生在 Web 服务器正式可用之前，所以外部永远访问不到一个"带着错误配置运行"的应用。

### 7.6 多条违规会一起报出来

同时把邮箱改成 `abc`、生日改成 `2099-01-01`：

```powershell
$env:SPRING_APPLICATION_JSON = '{"student":{"email":"abc","birthday":"2099-01-01"}}'
mvn -pl 02-config spring-boot:run
```

**实测报错**（同一个类里的违规一次性全列出来）：

```
APPLICATION FAILED TO START

Binding to target top.seamohai.config.properties.StudentProperties failed:
    Property: student.birthday
    Value: "2099-01-01"
    Reason: student.birthday 必须是过去的日期
    Property: student.email
    Value: "abc"
    Reason: student.email 邮箱格式不正确
```

**这比"报一个改一个"友好得多**：一次启动就能把所有写错的配置全部修完。

---

## 8. 作业要求 3：给原有属性类加邮箱和日期

### 8.1 加在哪、为什么

要求是"在**原有**代码基础上加邮箱、日期等属性"。本模块原有的属性类就是 `StudentProperties`，所以加在它上面，而不是新开一个类——**属性要跟着它所属的那组配置走**，`student` 下面加 `student.email`、`student.birthday`，一目了然。

### 8.2 代码（第 5.2 节已完整给出，这里只看新增的两块）

```java
    @NotNull(message = "student.birthday 不能为空")
    @Past(message = "student.birthday 必须是过去的日期")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    @NotBlank(message = "student.email 不能为空")
    @Email(message = "student.email 邮箱格式不正确")
    private String email;
```

### 8.3 yml 里补上对应的值

```yaml
student:
  email: 181@163.com
  birthday: 2006-02-18
```

### 8.4 实测：接口里能看到了

```
GET /student/info →
{ ..., "email":"181@163.com", "birthday":"2006-02-18", ... }
```

**`StudentController` 一行都没改**——它返回的是整个 `StudentProperties` 对象，新增字段自动就带出来了。这也是 `@ConfigurationProperties` 的一个好处：**加字段的成本只是加一个 Java 字段 + 一行 yml**。

### 8.5 两个关于日期的技术点

**（1）`LocalDate` 在 YAML 里裸写会被当成时间戳，但实测能绑上。**

YAML 规范里 `2006-02-18` 这种裸值属于 `timestamp` 类型，SnakeYAML 会把它解析成 `java.util.Date`。我一开始担心 `Date` 转不成 `LocalDate` 会导致绑定失败，**实测没问题**（Boot 4.1.1 能正常转换），所以 yml 里裸写 `birthday: 2006-02-18` 是可以的。如果哪天遇到日期绑定报错，把它加上引号变成字符串是最快的兜底办法。

**（2）`@DateTimeFormat` 管"读进来"，`@JsonFormat` 管"写出去"。**

| 注解 | 生效环节 | 作用 |
| --- | --- | --- |
| `@DateTimeFormat(pattern = "yyyy-MM-dd")` | 配置**绑定到字段**（请求参数同理） | 告诉 Spring 用这个格式解析字符串 |
| `@JsonFormat(pattern = "yyyy-MM-dd")` | **序列化返回 JSON** | 告诉 Jackson 用这个格式输出 |

`LocalDate` 默认按 ISO-8601（就是 `yyyy-MM-dd`）解析和输出，所以本模块不写 `@DateTimeFormat` 其实也能跑；写上是为了**把格式显式化**，格式一变（比如 `yyyy/MM/dd`）不至于靠运气。

### 8.6 数据自洽性检查（我自己的问题）

`student.age: 10`，但 `student.birthday: 2006-02-18`——2026 年时 2006 年出生的人已经 20 岁左右了，两个值互相矛盾。

这不是代码 bug，但**演示类数据最好自洽**，否则会被问到。录效果视频时可以这样排：

1. `age: 10` → 录一遍 `/config/value`，SpEL 输出"未成年"；
2. `age: 19` → 重启再录一遍，输出"成年"（`19` 依然满足 `@Min(1)`/`@Max(150)`，也与 2006 年的生日对得上）。

---

## 9. 接口与实测结果（Windows 实操）

### 9.1 接口清单（本模块一共 6 个）

| # | 接口 | 验证什么 | 实测返回 |
| --- | --- | --- | --- |
| 1 | `GET /config/basic` | 基础 `@Value`：`${server.port}`、`${spring.application.name}` | 服务器端口是：9090，应用名称是：配置管理 |
| 2 | `GET /config/my` | 普通 `@Value` 取自定义前缀 | 我的姓名是：申陌海，职业是：学生 |
| 3 | `GET /config/value` | **四种形态的核心**：占位符引用 / 默认值 / 随机值 / SpEL | 见 9.3 |
| 4 | `GET /config/env` | 多环境 + `@Profile` Bean | 当前环境：dev，开发环境；Profile Bean：我是 dev 环境专属的 Bean |
| 5 | `GET /config/app` | `@ConfigurationProperties` + 配置校验 | `{"author":"申陌海","maxCount":100,"name":"配置管理模块","port":8002}` |
| 6 | `GET /student/info` | 复杂结构绑定 + 邮箱/日期字段 | 见 5.4 |

### 9.2 启动

在仓库根目录 `E:\backend-learning\beckend-learning` 执行：

```powershell
mvn -pl 02-config spring-boot:run
```

| 参数 | 含义 |
| --- | --- |
| `-pl 02-config` | 只构建并运行这一个模块（project list） |
| `spring-boot:run` | 调用 Spring Boot 插件直接跑，不用先打包 |

不加 `-am` 的原因：`02-config` 不依赖其它子模块，所以不需要"连带构建依赖模块"。而且 `spring-boot:run` 是直接绑定在命令行的目标，加上 `-am` 会把父 POM 也拉进反应堆，父 POM（`packaging=pom`）上没有主类，容易报错。

用 IDEA 更省事：直接点 `ConfigApplication` 旁边的绿三角。

**实测启动日志**：

```
Starting ConfigApplication using Java 21.0.12.1 with PID 10244 (...)
The following 1 profile is active: "dev"
Devtools property defaults active! ...
Tomcat initialized with port 9090 (http)
Starting Servlet engine: [Apache Tomcat/11.0.24]
Tomcat started on port 9090 (http) with context path '/'
Started ConfigApplication in 1.005 seconds (process running for 1.251)
```

**看到 `Tomcat started on port 9090` 就是成功**，本次启动耗时约 **1 秒**。

### 9.3 实测结果

```
GET /config/basic
服务器端口是：9090，应用名称是：配置管理

GET /config/my
我的姓名是：申陌海，职业是：学生

GET /config/value
占位符引用 author=申陌海；默认值 remark=暂无备注；随机 UUID=89bf0eb7-ab47-4d32-8d02-cccfe166de8d；随机整数=88；SpEL adult=未成年

GET /config/env
当前环境：dev，开发环境；Profile Bean：我是 dev 环境专属的 Bean

GET /config/app
{"author":"申陌海","maxCount":100,"name":"配置管理模块","port":8002}

GET /student/info
{"address":{"city":"南京市","province":"江苏省"},"age":10,"birthday":"2006-02-18",
 "courses":[{"credit":4,"name":"高等数学"},{"credit":3,"name":"大学英语"}],
 "email":"181@163.com","hobbies":["篮球","编程","阅读"],"name":"张三",
 "scores":{"chinese":90,"math":95,"english":88}}
```

启动耗时：**1.005 秒**（process running 1.251 秒）。

### 9.4 查端口、结束进程

```powershell
netstat -ano | findstr :9090          # 最后一列是 PID
taskkill /PID <上一步的PID> /F
```

### 9.5 三个 Windows / 环境相关的坑

**（1）PowerShell 里的 `curl` 不是 curl**，它是 `Invoke-WebRequest` 的别名，参数完全不兼容 → 一律写 **`curl.exe`**，或者直接用 `Invoke-WebRequest`。

**（2）中文乱码可能来自"读取方式"，不是应用**。我用 `Invoke-WebRequest` 取 `/config/app` 时，控制台显示成这样：

```
{"author":"ç³éæµ·","maxCount":100,...}
```

这不是应用输出错了，而是**响应头里 `application/json` 没带 charset，客户端按 ISO-8859-1 解码 UTF-8 字节**造成的显示问题。同一台机器上 `/config/basic`（`text/plain` 带 charset）显示正常，可以对照。看 JSON 用 Apifox / 浏览器看就不会有这个问题。

**（3）Maven 本地仓库有两套，命令行和 IDEA 可能不是同一个**：

| 使用者 | 本地仓库位置 | 表现 |
| --- | --- | --- |
| 命令行 `mvn` | 由 Maven 全局 `conf/settings.xml` 的 `<localRepository>` 决定（我这里是 `D:\Maven\maven_jar`） | 首次运行会自动下载缺失依赖 |
| IDEA | 默认 `C:\Users\<用户名>\.m2\repository` | IDEA 自己解析依赖，不受命令行影响 |

所以出现"IDEA 里能跑、命令行报依赖找不到"是正常的，命令行联网跑一次就会补齐。

### 9.6 回顾一下第 4.3.1 节

`/config/value` 里的 UUID 和随机整数，**刷新页面不会变，只有重启应用才会变**。录像时按"重启 → 请求 → 再重启 → 再请求"的节奏来。

---

## 10. 同步到 Apifox

### 10.1 上传哪 6 个接口

就是 9.1 节那 6 个，**一个都别漏**，因为每个接口对应一个知识点：

| 接口 | 对应知识点 |
| --- | --- |
| `/config/basic`、`/config/my`、`/config/value` | 作业要求 1：`@Value` 的四种形态 |
| `/config/env` | 多环境 + `@Profile` |
| `/config/app` | `@ConfigurationProperties` + 配置校验 |
| `/student/info` | 复杂结构绑定 + 作业要求 3 的邮箱/日期 |

### 10.2 上传设置

- **模块只选 `02-config`**，别把 `01-quickstart` 的 `UserController` 一起传进去；
- 上传后分两个目录：`/config` 放前 5 个，`/student` 放第 6 个；
- 自动生成的**数据模型**（`AppProperties`、`StudentProperties`，含嵌套的 `Address`、`Course`）**不要删**，它们就是"绑定复杂结构"的可视化证据。

### 10.3 环境与断言

建环境 `本地`，基础 URL 填 `http://localhost:9090`（**不是 8080**）。

| 接口 | 建议断言 | 验证的东西 |
| --- | --- | --- |
| 全部 6 个 | 状态码 = 200 | 服务在跑、路径没写错 |
| `/config/value` | 响应**包含** `暂无备注` | 默认值生效 |
| `/config/env` | 响应包含 `dev` 和 `dev 环境专属的 Bean` | profile 生效 + `@Profile` Bean 生效 |
| `/student/info` | `email` = `181@163.com`，`birthday` = `2006-02-18` | 新增字段绑定成功 |

> ⚠️ **不要断言 `/config/value` 的完整 UUID 或随机整数**。它们是启动时随机的，**重启一次断言就失败一次**。要断言就断言"包含 `随机 UUID=`"这种稳定部分。

---

## 11. 我踩过的 5 个坑

### 坑 1：`@Value` 里的名字必须和 yml 的 key 一字不差

```java
@Value("${mqxu.name}")     // 抄示例留下的名字
private String myName;
```

```yaml
seamohai:
  name: 申陌海            # 我 yml 里已经把前缀改成 seamohai 了
```

**报错**：

```
Could not resolve placeholder 'mqxu.name' in value "${mqxu.name}"
```

**原因**：yml 里没有 `mqxu` 这个前缀，`@Value` 又没有写默认值，占位符解析失败 → 创建 Bean 失败 → 应用起不来。

**解决**：改成 `${seamohai.name}`，注释也一起改，避免下次又看错。

**教训**：**抄示例代码时，最危险的不是逻辑，是那些"看起来像字符串"的 key。** 逻辑错了会报错，key 错了可能悄悄拿到 `null`，或者像这次一样直接起不来。

### 坑 2：加了字段却忘了加配置（前缀还写错了）

我一度在控制器里加了两个字段：

```java
@Value("${seamohai.email}")
private String myEmail;

@Value("${seamohai.date}")
private String myDate;
```

但 `email` / `date` 我配在了 `student` 下面，`seamohai` 前缀下只有 `name` 和 `job`。

**结果**：同样是 `Could not resolve placeholder 'seamohai.email'`。

**解决**：这两个字段**直接删掉**。因为"加邮箱、日期"这件事已经由 `student.email` / `student.birthday` 承担了，`seamohai` 再来一份是重复的；而且这两个字段没有任何接口用到，属于死字段。

**教训**：**写 `@Value` 之前先确认 yml 里有没有这个 key。** 配在哪个前缀下，就只能用那个前缀取。

### 坑 3：两个实现类写了同一个 `@Profile`（复制粘贴事故）

```java
@Service
@Profile("dev")            // DevEnvService：对
public class DevEnvService implements EnvService { ... }

@Service
@Profile("dev")            // ProdEnvService：抄过来忘了改！
public class ProdEnvService implements EnvService { ... }
```

这一个字符的疏忽，**两个环境都起不来**：

| 激活的 profile | 报错 |
| --- | --- |
| `dev` | `NoUniqueBeanDefinitionException`：容器里有两个 `EnvService`，注入时不知道该给哪个 |
| `prod` | `NoSuchBeanDefinitionException`：一个都没有 |

**解决**：`ProdEnvService` 改成 `@Profile("prod")`。

**教训**：`@Profile` 的值是**字符串**，编译器管不了。复制粘贴新建类时，先把这类"常量字符串"改掉再看逻辑。类似的还有 `@RequestMapping` 路径、`@Qualifier` 名字。

### 坑 4：`private Data data;` —— `import lombok.Data` 埋的雷

我想加一个日期字段，写成了：

```java
@DateTimeFormat(pattern = "yyyy-MM-dd")
private Data data;
```

**它居然编译通过了**，因为文件顶部有 `import lombok.Data;`——`Data` 在编译器眼里是一个**类型**（Lombok 的注解本质上是接口），于是这行代码变成了"一个类型为 Lombok 注解的字段"。

但运行期 Spring 要把 yml 里的字符串 `2006-02-18` 绑到这个类型上，绑定阶段直接失败。

**解决**：改成正确的类型和名字：

```java
private LocalDate birthday;
```

顺便把 yml 的 `data:` 改成 `birthday:`，并把 `@NotNull(message = "student.birthday 不能为空")` 里的属性和字段名对齐。

**教训**：

- **能编译 ≠ 能运行**。Java 的类型系统在这里恰好"放行"了一个荒谬的类型；
- 字段名要和 `message` 里的属性名对上，否则报错信息会指向一个不存在的配置项；
- 导入通配符（`import lombok.*`）越少越好，避免和业务类型撞名字。

### 坑 5：以为随机值每次请求都会变（认知坑）

我一开始以为 `${random.uuid}` 是"每次取都生成新的"，实测**连续两次请求返回值完全一样**。

**原因**：`@Value` 在 Bean 创建时解析一次并注入字段，之后读取的一直是那个字段。

**解决**：要展示随机性就**重启应用**。

**教训**：`@Value` 的**求值时机是启动时**，不是"取值时"。这一点决定了它不能当"运行期随机数生成器"用——真要在接口里每次生成随机值，得在方法里调用 `UUID.randomUUID()`。

---

## 12. 报错速查表

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| `Could not resolve placeholder 'xxx'` | `@Value` 的 key 在配置里不存在，且没写默认值 | 核对 key 与前缀是否与 yml 一致；或写成 `${key:默认值}` |
| `APPLICATION FAILED TO START` + `Binding to target ... failed` | 配置校验不通过（或类型转换失败） | 看 `Property` / `Value` / `Reason` 三行，按 message 改配置 |
| `NoUniqueBeanDefinitionException` | 同一接口有多个实现被注册（`@Profile` 写重复了） | 检查每个实现的 `@Profile` 值 |
| `NoSuchBeanDefinitionException`（找不到 `EnvService`） | 所有实现都没被注册（`@Profile` 和激活的 profile 不匹配） | 同上，并确认 `spring.profiles.active` |
| 配置校验注解"没反应" | 类上漏了 `@Validated`，或没引 `spring-boot-starter-validation` | 补齐（**静默失效，不报错**，最坑） |
| `找不到符号: 类 NotBlank`（`javax.validation`） | 照抄了 Boot 3 之前的老教程 | 换成 `jakarta.validation.constraints.*` |
| 嵌套对象里的校验不生效 | 嵌套字段没加 `@Valid` | 在嵌套字段上加 `@Valid` |
| `Property: student.data` / 绑定类型转换失败 | 字段类型写错（如 `private Data data`） | 改成 `LocalDate`，并同步改 yml 的 key |
| yml 里多写的配置项没报错 | `ignoreUnknownFields` 默认 `true` | 属正常行为，靠 `@NotNull` 等注解防"少写" |
| `Port 9090 was already in use` | 上次的应用没关 | `netstat -ano \| findstr :9090` → `taskkill /PID <pid> /F` |
| 换了个 profile 但值没变 | profile 没被激活，或值写在了主 `application.yml` 里 | 确认启动日志 `The following N profile is active`；环境相关值只写在 `application-{profile}.yml` |
| 随机值/默认值接口返回"没变化" | 随机值是启动时求值的固定值 | 重启应用；不要靠刷新 |
| JSON 中文显示成 `ç³éæµ·` | 客户端按 ISO-8859-1 解码 UTF-8（响应头没带 charset） | 用 Apifox / 浏览器看；不是应用的 bug |
| 命令行报依赖找不到 | 命令行与 IDEA 用的本地仓库不同 | 联网跑一次让命令行补齐依赖 |
| yml 解析失败 | 用了 Tab 缩进，或冒号后没空格 | 缩进只用空格；写 `port: 9090` 不是 `port:9090` |

---

## 13. 练习

### 13.1 随堂练习（约 10 分钟）

| # | 练习 | 验证点 | 状态 |
| --- | --- | --- | --- |
| 1 | 把 `app.port` 改成 `70000` 启动 | 看到 `APPLICATION FAILED TO START` 和 `Reason: app.port 必须小于等于 65535` | [ ] |
| 2 | 把 `student.email` 改成 `zhangsan`（去掉 `@`） | 报 `student.email 邮箱格式不正确` | [ ] |
| 3 | 把 `student.age` 改成 `19` 后重启 | `/config/value` 的 SpEL 从"未成年"变成"成年" | [ ] |
| 4 | 重启两次对比 `/config/value` | 随机 UUID 和随机整数都变了（证明是启动时求值） | [ ] |

> 每改一次配置都要**重启**，并记得改回正确值再录下一条。

### 13.2 课后练习

- **基础**：给 `StudentProperties` 加一个 `phone` 字段，用 `@Pattern`（或 `@Size`）校验手机号格式，并让 `/student/info` 返回它。
- **进阶**：给 `Address` 嵌套对象加 `@Valid` 和 `@NotBlank`，然后把 `student.address.city` 删掉，观察报错是不是指向嵌套属性。
- **挑战**：新增一个 `@Profile("test")` 的实现类 `TestEnvService`，用 `--spring.profiles.active=test` 启动，说明为什么"激活 test 时容器里只有一个 `EnvService`"。

### 13.3 关键写法提示

```java
// 默认值的两种写法
@Value("${app.remark:暂无备注}")   // 缺配置时用"暂无备注"
@Value("${app.remark:}")           // 缺配置时用空字符串

// SpEL：先解析占位符，再计算
@Value("#{${student.age} >= 18 ? '成年' : '未成年'}")

// 属性类：绑定 + 校验 + 嵌套级联
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "student")
public class StudentProperties {
    @NotBlank(message = "student.name 不能为空")
    private String name;

    @Valid                                   // 嵌套对象要加这个才会级联校验
    private Address address;
}
```

### 13.4 自测：能不能回答这几个问题

1. `${key:默认值}` 里的冒号是干什么的？不写默认值又找不到 key 会怎样？
2. `@Value` 和 `@ConfigurationProperties` 各自适合什么场景？松散绑定是谁支持？
3. `${random.uuid}` 是"每次请求随机"还是"启动随机一次"？怎么验证？
4. `#{${student.age} >= 18 ? '成年' : '未成年'}` 这行的执行顺序是什么？
5. `@Profile` 的值写重复了会怎样？两个环境分别报什么错？
6. 为什么配置校验能让应用"启动失败"，而不是等到接口被调用？
7. `@NotBlank`、`@NotEmpty`、`@NotNull` 三者的区别？
8. 为什么校验注解的包名是 `jakarta.*` 而不是 `javax.*`？
9. `@DateTimeFormat` 和 `@JsonFormat` 分别管哪个环节？

---

## 14. 知识地图与名词速查

### 14.1 本节要点回顾

| 概念 | 一句话 |
| --- | --- |
| `@Value` | 取零散的值，支持占位符、默认值、随机值、SpEL；key 必须一字不差 |
| 占位符 `${}` | 从 Environment 读配置，可以配置引用配置 |
| 默认值 `${k:v}` | 冒号后面是缺省值；**不写就启动失败** |
| 随机值 `${random.*}` | 启动时求值一次，之后固定不变 |
| SpEL `#{}` | 表达式计算，可嵌套 `${}` 做"先读后算" |
| `@ConfigurationProperties` | 一组同前缀配置绑成对象，支持嵌套/List/Map、松散绑定、校验 |
| 松散绑定 | `max-count` / `maxCount` / `MAX_COUNT` 都能绑到 `maxCount` |
| `@Profile` | 按激活的 profile 决定注册哪个 Bean |
| `application-{profile}.yml` | profile 专属配置文件，同名 key 覆盖主文件 |
| `@Validated` + JSR-303 | 配置校验，**启动阶段失败**，不让错误配置进运行期 |
| `@Valid` | 级联校验，让嵌套对象里的注解也生效 |
| `ignoreUnknownFields` | 默认 `true`，多写的 key 被忽略，别指望它发现拼写错误 |

### 14.2 名词速查

| 名词 | 解释 |
| --- | --- |
| **配置绑定（Binding）** | 把配置里的值"填"进 Java 对象的字段，靠 setter 完成 |
| **Environment** | Spring 里所有配置来源的统一抽象（命令行、环境变量、yml 都算） |
| **PropertySource** | Environment 里的一个"配置来源"，报错里的 `Origin` 就是它 |
| **占位符（Placeholder）** | `${...}` 形式，从配置中取值的语法 |
| **SpEL** | Spring Expression Language，`#{...}`，能做运算和判断 |
| **profile** | 一套环境标识（dev/test/prod），决定加载哪些文件、注册哪些 Bean |
| **JSR-303 / Bean Validation** | Java 的校验规范，`@NotNull`、`@Min` 这些注解来自它 |
| **Hibernate Validator** | Bean Validation 的参考实现，真正执行校验的库 |
| **级联校验** | 加了 `@Valid` 后，嵌套对象里的校验注解才会被执行 |
| **松散绑定（Relaxed Binding）** | `@ConfigurationProperties` 特有的"容忍命名风格差异"的能力 |
| **kebab-case** | 短横线小写命名，官方推荐的配置文件写法（`max-count`） |
| **默认值 / 缺省值** | `${key:默认值}` 中冒号后面的部分 |
| **devtools** | 开发期工具，改代码后自动重启应用（日志线程名 `restartedMain`） |

---

## 15. 内容小结与学习小结

### 15.1 内容小结（作业要求 5）

| 案例 | 使用场景 | 本模块的落点 |
| --- | --- | --- |
| `@ConfigurationProperties` | 一组同前缀、结构复杂的配置 | `student`：`List<String>` + `Map<String,Integer>` + 嵌套 `Address` + `List<Course>` |
| 多环境 + `@Profile` | 开发 / 测试 / 生产环境差异 | `application-dev.yml` / `application-prod.yml` + `DevEnvService` / `ProdEnvService` |
| 进阶 `@Value` | 零散字段、默认值、随机值、动态表达式 | `app.author`（占位符引用）、`app.remark:暂无备注`（默认值）、`random.uuid`、`random.int(1,100)`、SpEL 三元 |
| 配置校验 | 启动时兜底，防止非法配置 | `@Validated` + `@NotBlank` / `@Min` / `@Max` / `@NotBlank`+`@Email` / `@NotNull`+`@Past` |

### 15.2 学习小结（作业要求 6）

> 这周的 02-config，一开始我以为配置就是往 yml 里写几个值、用 `@Value` 取出来，做完才发现里面门道不少。
>
> `@Value` 练了四种：引用别的配置、写默认值、随机值和 SpEL 表达式，看着简单，自己写起来老是错。学生这种一整组的配置，用 `@ConfigurationProperties` 绑定更省事，爱好、成绩、地址都能对上，多环境就用 `@Profile` 分 dev 和 prod。最后加 `@Validated`，我把端口故意写成 70000，启动直接报错，这才明白校验是干嘛的——不让写错的配置跑到运行期。
>
> 最折腾的是应用起不来，报了一屏看不懂的错，查了半天才发现 `@Value` 里的名字和 yml 的前缀对不上：示例里是 mqxu，我 yml 改成了 seamohai，忘了 `@Value` 这边也得改。还有随机值和 uuid 刷新页面不变，得重启才变，这个我问了才知道。
>
> 最后把接口同步到 Apifox 跑了一遍，6 个都能通。花了不少时间，但这块总算搞明白了。

### 15.3 作业要求完成度自查

| # | 作业要求 | 状态 | 证据 |
| --- | --- | --- | --- |
| 1 | `@Value` 四种形态 + 配置/服务/接口 | ✅ | `ConfigController`、`EnvService` 体系、`application.yml`；实测见 9.3 |
| 2 | `@Validated` + JSR-303 配置校验 | ✅ | `AppProperties`；实测报错见 7.5 |
| 3 | 加邮箱、日期属性并校验 | ✅ | `StudentProperties.email` / `birthday`；实测 JSON 见 8.4、校验见 7.6 |
| 4 | 接口同步 Apifox 并测试 | ⬜ | 6 个接口清单见 10.1，上传与断言见 10.2 / 10.3 |
| 5 | 内容小结 | ✅ | 15.1 |
| 6 | 运行效果视频 + 学习小结 | ⬜ | 小结见 15.2；视频录制要点见 4.3.1、9.6、13.1 |

### 15.4 我代码里的待修正点

- [ ] `ConfigController` 里 `import org.springframework.http.ResponseEntity;` 没用到，删掉
- [ ] `StudentController`、`StudentPropertiesTest` 里 `import jakarta.annotation.Resource;` 没用到，删掉
- [ ] `StudentProperties.hobbies` 加 `@NotEmpty`，`address` 加 `@Valid`（嵌套校验才会生效）
- [ ] `student.age`（10）与 `student.birthday`（2006-02-18）数据矛盾，改成自洽的一组
- [ ] 端口约定：01 用 8888、02 用 9090，还没统一规则，建议定成 `9000+N` 并写进仓库 README
- [ ] 类头注释：新增的几个类没有 `@author` 和说明
- [ ] 提交：`docs/` 和 02-config 的改动都还没 `git add`

---

## 16. 还没搞懂的问题

1. `${random.int(1,100)}` 的 **1 和 100 是否包含端点**？我实测取到过 88，但没测边界，不能确定是 `[1,100)` 还是 `[1,100]`。
2. `@Value("${student.hobbies}")` 到底能不能注入 YAML 里的列表？（YAML 里它是索引属性 `student.hobbies[0]`，我推测不行，但没实测。）
3. `@ConfigurationProperties` 返回的 JSON 字段顺序为什么是**字母序**（`author, maxCount, name, port`），而不是类里的声明顺序？是 Jackson 的行为还是 Binder 生成的对象有什么特殊之处？
4. `@DateTimeFormat` 不写会不会有区别？`LocalDate` 默认就是 ISO 格式，它到底在什么场景下是必需的？
5. devtools 的热重启（`restartedMain`）会不会**重新生成随机值**？按"Bean 重建"的机制推测会，但我没实测。
6. `@Profile` 的值写成字符串常量容易抄错，有没有编译期就能防错的办法？
7. 校验注解只能加在 `@ConfigurationProperties` 上，那 `@Value` 注入的字段想校验该怎么办？（目前我只能靠"不写默认值，让它解析失败"。）
8. `spring-boot-starter-validation` 不引的话，校验注解是**静默失效**还是会有启动警告？我只知道结果是"不校验"。
9. `mvn -pl 02-config -am spring-boot:run` 里加上 `-am` 到底能不能跑？01 那篇的文档写了 `-am`，我这次没加（02 不依赖兄弟模块）。按机制推测：`-am` 会把父 POM 也纳入反应堆，而父 POM 上 `spring-boot:run` 找不到主类，可能会报错——**这条我没实测**，等哪天顺手验一下。
