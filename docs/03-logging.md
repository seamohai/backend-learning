# 03 · 日志管理：日志门面、级别、traceId 与滚动文件

> - 模块：[03-logging](../03-logging)
> - 工程坐标：`top.seamohai:beckend-learning:0.0.1-SNAPSHOT`
> - 环境：JDK **21** ｜ Spring Boot **4.1.1** ｜ Tomcat **11.0.24** ｜ Hibernate Validator **9.1.3** ｜ 端口 **8009**
> - 本篇是**自包含学习笔记**：所有概念都在文中解释清楚，不需要另查任何资料
> - 和 01 那篇不一样的地方：**本文的接口返回、启动日志、报错信息全部是实测结果**，不是按代码推导的

---

## 目录

1. [本节目标](#1-本节目标)
2. [背景：为什么不能再用 `System.out.println`](#2-背景为什么不能再用-systemoutprintln)
3. [日志的五个基本概念](#3-日志的五个基本概念)
4. [日志门面与实现：SLF4J 和 Logback 的关系](#4-日志门面与实现slf4j-和-logback-的关系)
5. [用 `@Slf4j` 打日志：五个级别与实测输出](#5-用-slf4j-打日志五个级别与实测输出)
6. [日志格式：pattern 占位符逐个拆解](#6-日志格式pattern-占位符逐个拆解)
7. [自定义 `logback-spring.xml`：appender 与 profile 分流](#7-自定义-logback-springxmlappender-与-profile-分流)
8. [traceId：用 MDC 把一次请求的日志串起来](#8-traceid用-mdc-把一次请求的日志串起来)
9. [敏感信息脱敏：`LogMaskUtil`](#9-敏感信息脱敏logmaskutil)
10. [接口与实测结果（Windows 实操）](#10-接口与实测结果windows-实操)
11. [我踩过的 5 个坑](#11-我踩过的-5-个坑)
12. [报错速查表](#12-报错速查表)
13. [练习](#13-练习)
14. [知识地图与名词速查](#14-知识地图与名词速查)
15. [内容小结与学习小结](#15-内容小结与学习小结)
16. [还没搞懂的问题](#16-还没搞懂的问题)

---

## 1. 本节目标

学完这一节，要能独立做到四件事：

1. 说清**日志门面（SLF4J）和日志实现（Logback）**的分工，知道 Spring Boot 默认装了谁；
2. 用 `@Slf4j` 打日志，并按 `TRACE / DEBUG / INFO / WARN / ERROR` 五个级别正确选用；
3. 看懂并改得动 `logging.pattern.console` 里的 `%d`、`%clr`、`%X` 这些占位符；
4. 用 **MDC + 拦截器** 给每个请求生成 traceId，把一次请求散落的日志串成一条线。

一句话概括这四件事的分工：

> **门面负责"代码里怎么写" → 实现负责"最终写到哪、写成什么样" → 级别决定"这条日志该不该出现" → traceId 决定"这一堆日志是不是同一次请求"。**

四者是叠加关系，不是替代关系：本模块的 `LoggingController` 只管用 `log.info()` 写法（门面），`logback-spring.xml` 决定输出到控制台还是文件（实现），`root level` 决定级别阈值，`TraceInterceptor` 往里塞 traceId（MDC）。

---

## 2. 背景：为什么不能再用 `System.out.println`

### 2.1 用 `System.out.println` 调试的三个后果

```java
// 反面教材
System.out.println("用户创建成功，id = " + id);
```

| 麻烦事 | 具体表现 |
| --- | --- |
| 关不掉 | 上线后想关掉调试输出，得改代码、重新打包 |
| 没级别 | 想只看错误，做不到；所有输出混在一起 |
| 没时间、没线程 | 出问题时不知道"什么时候、哪个线程"打的 |
| 并发下串行 | 多线程同时打，两行输出可能交错成一团乱码 |
| 写不进文件 | 想留档要自己写文件流，还得处理滚动、删除旧文件 |

### 2.2 日志框架解决的正是这五条

| 麻烦 | 日志框架怎么解决 | 在本模块的体现 |
| --- | --- | --- |
| 关不掉 | **级别阈值**在配置里调，不改代码 | `logback-spring.xml` 里 `<root level="INFO">` |
| 没级别 | 五级分层，可分别设阈值 | `logging.pattern.console` 里 `%-5level` |
| 没时间/线程 | 格式占位符自动带上 | pattern 里的 `%d` 和 `%thread` |
| 并发串行 | 实现内部对输出加了同步 | 实测 `[http-nio-8009-exec-2]` 各自成行 |
| 写不进文件 | **appender + rollingPolicy** | `FILE` appender + `TimeBasedRollingPolicy` |

第三条是本节最"工程化"的一点：**日志是给别人（未来的你、运维、排障的同事）看的，所以要能被机器过滤、被检索、被归档，而不只是"能看见"。**

---

## 3. 日志的五个基本概念

后面会反复出现，一开始就要有正确印象：

- **门面（Facade）**：代码里调用的那套 API，本模块是 SLF4J 的 `log.info(...)`；
- **实现（Binding）**：真正干活的库，本模块是 Logback；
- **Logger**：按类名分层的日志对象，`@Slf4j` 生成的 `log` 就是它；名字是本类的全限定名；
- **Appender**：一条日志"往哪写"的出口，可以配多个（控制台一个、文件一个）；
- **Level**：级别阈值，低于阈值的日志**根本不进入后续处理**，不是"写了不显示"。

> **重要认知**：级别判断发生在**日志调用点**。`log.debug("...")` 在 `DEBUG` 被关闭时，连字符串拼接都不会执行。所以"多打日志会影响性能"这句话要分情况：写 `log.debug("x=" + x)` 确实会拼字符串，但 SLF4J 推荐写成 `log.debug("x={}", x)`，被关闭时零开销。

---

## 4. 日志门面与实现：SLF4J 和 Logback 的关系

### 4.1 为什么要分两层

如果代码里直接调 Logback 的 API，将来想换 Log4j2，就得改遍所有类。所以业界做法是**面向门面编程**：

| 角色 | 本模块里是谁 | 我写的代码碰不碰它 |
| --- | --- | --- |
| 门面（API） | `org.slf4j.Logger` | 碰，`log.info(...)` |
| 实现（Binding） | Logback（`logback-classic`） | 不碰，只写配置 |
| 桥接 | `spring-boot-starter-logging` 带的一堆 `jul-to-slf4j`、`log4j-to-slf4j` | 不碰 |

`spring-boot-starter-webmvc` 里已经带了 `spring-boot-starter-logging`，所以**什么都不用加**就有日志能力。写 `log.info()` 时用的 `org.slf4j.Logger`，运行时由 Logback 接管。

### 4.2 怎么确认用的是谁

看启动日志里这一行就够了（本模块实测）：

```
o.s.boot.tomcat.TomcatWebServer - Tomcat started on port 8009 (http) with context path '/'
```

能看出 `o.s.boot...` 这种**被缩写的 logger 名**，说明格式化是 Logback 在做（缩写规则来自 `%logger{36}`）。

另外 Spring Boot 启动时会打印的一行 `Hibernate Validator 9.1.3.Final` 也是同类证据——本模块引了 `spring-boot-starter-validation`，所以校验器的版本出现在日志里。

> **规则**：想换日志实现（比如换 Log4j2），只需要在 Maven 里排除 `spring-boot-starter-logging` 再引 `spring-boot-starter-log4j2`，**业务代码一行都不用改**。这就是门面的价值。

---

## 5. 用 `@Slf4j` 打日志：五个级别与实测输出

### 5.1 `@Slf4j` 生成了什么

```java
@RestController
@RequestMapping
@Slf4j
public class LoggingController {

    @GetMapping("/demo")
    public String demo(){
        log.info("处理 /log/demo 请求");
        log.warn("这是一条警告日志");
        log.error("这是一条错误日志");
        return "ok";
    }
}
```

`@Slf4j` 是 Lombok 的注解，编译期往类里塞了一个字段，等价于手写：

```java
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LoggingController.class);
```

两个要点：

- 它是 `static final`，**每个类一个**，不是每个请求一个；
- `getLogger(Class)` 传的是类对象，所以 logger 名 = 类全限定名。这就是日志里 `t.s.l.controller.LoggingController` 的来历（`%logger{36}` 把 `top.seamohai.logging.controller` 缩写成了 `t.s.l.controller`）。

### 5.2 五个级别怎么选

| 级别 | 什么时候用 | 本模块需要它吗 |
| --- | --- | --- |
| `TRACE` | 比 DEBUG 还细，逐帧追踪 | 不用 |
| `DEBUG` | 开发期排障细节（入参、分支走向） | 示例里没用，但实际项目最多 |
| `INFO` | 正常的关键业务节点（请求进来、任务完成） | 用，`处理 /log/demo 请求` |
| `WARN` | 不正常但还能继续（兜底、重试、降级） | 用，`这是一条警告日志` |
| `ERROR` | 出错了，需要人看 | 用，`这是一条错误日志` |

> **记住这条选用原则**：**级别由"要不要人介入"决定，不由"代码在哪个分支"决定。** 用户输错密码是 `WARN` 还是 `INFO` 取决于你要不要盯它，不是因为它属于"异常"就一律 `ERROR`。

### 5.3 实测输出

**实测输出**：

```
22:27:35 [http-nio-8009-exec-2] INFO  t.s.l.controller.LoggingController [b6aec9b8ac334d49 top.seamohai.logging.controller.LoggingController] - 处理 /log/demo 请求
22:27:35 [http-nio-8009-exec-2] WARN  t.s.l.controller.LoggingController [b6aec9b8ac334d49 top.seamohai.logging.controller.LoggingController] - 这是一条警告日志
22:27:35 [http-nio-8009-exec-2] ERROR t.s.l.controller.LoggingController [b6aec9b8ac334d49 top.seamohai.logging.controller.LoggingController] - 这是一条错误日志
```

三点观察：

1. **三条日志时间相同、线程相同**（`http-nio-8009-exec-2`），因为它们是同一次请求里顺序执行的；
2. **traceId 相同**（`b6aec9b8ac334d49`）——同一次请求的日志被串了起来，这就是第 8 节要讲的东西；
3. **级别是右对齐的**（`INFO ` 后面多一个空格），因为 pattern 里写的是 `%-5level`。

### 5.4 占位符写法：`{}` 而不是 `+`

```java
log.info("处理 " + path + " 请求");        // 反例：级别关闭时仍会拼字符串
log.info("处理 {} 请求", path);            // 正解：级别关闭时零开销
```

`{}` 由 SLF4J 在**确认要输出之后**才替换，避免无谓的字符串拼接。

---

## 6. 日志格式：pattern 占位符逐个拆解

本模块的 `application.yml`：

```yaml
logging:
  pattern:
    console: '%d{HH:mm:ss} %clr([%thread]){yellow} %clr(%-5level) %clr(%logger{36}){cyan} [%X{traceId} %clr(%logger){bg_blue}] - %msg%n'
```

逐个占位符拆开：

| 占位符 | 作用 | 实测输出里对应哪一段 |
| --- | --- | --- |
| `%d{HH:mm:ss}` | 时间，`{}` 里是格式 | `22:27:35` |
| `%clr(...){yellow}` | 给内容上色，颜色是 `yellow` | 线程名那段是黄色的 |
| `%thread` | 线程名 | `http-nio-8009-exec-2` |
| `%-5level` | 级别，`-` 表示左对齐，`5` 是宽度 | `INFO `（补了一个空格） |
| `%logger{36}` | logger 名，超长就缩写到 36 字符 | `t.s.l.controller.LoggingController` |
| `%X{traceId}` | 从 MDC 取 key 为 `traceId` 的值 | `b6aec9b8ac334d49` |
| `%msg` | 日志正文 | `处理 /log/demo 请求` |
| `%n` | 换行 | 行尾 |

### 6.1 一个真实存在的小毛病

看 pattern 末尾这段：

```
[%X{traceId} %clr(%logger){bg_blue}]
```

`%X{traceId}` 取到 traceId 之后，紧跟着的 ` %clr(%logger){bg_blue}` 是**又一次输出 logger 名**，于是同一行里 logger 名出现了两次：

```
[b6aec9b8ac334d49 top.seamohai.logging.controller.LoggingController]
```

第一次是缩写过的（`t.s.l.controller.LoggingController`，来自 `%logger{36}`），第二次是完整的（来自这里的 `%clr(%logger){bg_blue}`）。**这不是 bug，是我为了让 traceId 有背景色而误加的**——想让 traceId 本身带蓝底，应该写成 `%clr([%X{traceId}]){bg_blue}`。

> 这条已经记进 15.4 的待修正点。它不影响功能，但**日志里重复的信息会白占宽度**，在长 logger 名下会把 `%msg` 挤到很右边。

### 6.2 颜色是怎么来的

`%clr` 不是 Logback 原生的转换词，它由 Spring Boot 提供。自定义 `logback-spring.xml` 时要自己注册：

```xml
<conversionRule conversionWord="clr" class="org.springframework.boot.logging.logback.ColorConverter"/>
```

本模块确实写了这一行（见 7.2），所以 `%clr` 能生效。实测输出里有真实的 ANSI 转义序列，证明颜色真的输出了。

---

## 7. 自定义 `logback-spring.xml`：appender 与 profile 分流

### 7.1 为什么文件名叫 `logback-spring.xml` 而不是 `logback.xml`

| 文件名 | 谁解析 | 能用 `<springProfile>` 吗 |
| --- | --- | --- |
| `logback.xml` | Logback 自己，**在 Spring 启动之前** | 不能 |
| `logback-spring.xml` | Spring Boot 接管后解析 | 能 |

**结论：要按 profile 分流日志，必须用 `logback-spring.xml`。** 用 `logback.xml` 的话 `<springProfile>` 会被当成未知标签忽略，分流静默失效。

### 7.2 本模块的配置

```xml
<configuration>
    <!-- 注册 Spring Boot 的 %clr 颜色转换器（自定义 logback-spring.xml 不会自动注册） -->
    <conversionRule conversionWord="clr" class="org.springframework.boot.logging.logback.ColorConverter"/>

    <!-- 控制台日志格式从 application.yml 的 logging.pattern.console 读取 -->
    <springProperty scope="context" name="CONSOLE_LOG_PATTERN" source="logging.pattern.console" defaultValue="..."/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder><pattern>${CONSOLE_LOG_PATTERN}</pattern></encoder>
    </appender>

    <!-- 按天滚动的文件 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/app.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder><pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{traceId}] - %msg%n</pattern></encoder>
    </appender>

    <root level="INFO"><appender-ref ref="CONSOLE"/></root>

    <springProfile name="dev">
        <root level="INFO"><appender-ref ref="CONSOLE"/></root>
    </springProfile>

    <springProfile name="prod">
        <root level="WARN"><appender-ref ref="FILE"/></root>
    </springProfile>
</configuration>
```

三个关键点：

- **`<springProperty>` 把 yml 的值读进配置**：`source="logging.pattern.console"` 指向 `application.yml`，这样格式只在 yml 里维护一份；
- **`<file>logs/app.log</file>` 是相对路径**，相对于**进程的工作目录**，不是相对于 classpath。所以在 IDEA 里跑和在目标目录里跑，文件落点可能不同；
- **`<root>` 可以出现多次**，这一点很反直觉，见 7.3。

### 7.3 实测：`<root>` 声明多次会"叠加"而不是"覆盖"

这是我实测才搞清楚的一点，值得单独写出来。

**实测输出（`--spring.profiles.active=prod`）**：

```
22:28:50 [http-nio-8009-exec-2] WARN  t.s.l.controller.LoggingController [57bb0f4d691540d5 top.seamohai.logging.controller.LoggingController] - 这是一条警告日志
22:28:50 [http-nio-8009-exec-2] ERROR t.s.l.controller.LoggingController [57bb0f4d691540d5 top.seamohai.logging.controller.LoggingController] - 这是一条错误日志
```

同时 `logs/app.log` 里也有（文件用的是 FILE 自己的 pattern，没有颜色代码）：

```
2026-10-09 22:28:50.943 [http-nio-8009-exec-2] WARN  t.s.l.controller.LoggingController [57bb0f4d691540d5] - 这是一条警告日志
2026-10-09 22:28:50.945 [http-nio-8009-exec-2] ERROR t.s.l.controller.LoggingController [57bb0f4d691540d5] - 这是一条错误日志
```

逐行读一遍，这段输出其实信息量很大：

| 观察 | 含义 |
| --- | --- |
| 控制台**出现了** WARN/ERROR | `prod` 分支只写了 `FILE`，按理不该再往控制台写 |
| 控制台的格式是 `HH:mm:ss`（来自 yml 的 console pattern） | 说明 `CONSOLE` appender 仍然是激活的 |
| 文件里格式是 `yyyy-MM-dd HH:mm:ss.SSS`（来自 FILE 自己的 pattern） | `FILE` appender 也激活了 |
| **INFO 级一条都没有**（包括 Tomcat 启动那几行） | 级别阈值确实被抬到了 `WARN` |

也就是说：**级别取到了 `prod` 的 `WARN`，但 appender 是"默认 root 的 CONSOLE + prod root 的 FILE"两个都在。**

**原因**：Logback 解析配置时，`<root>` 的 `level` 是**后设置覆盖先设置**，但 `<appender-ref>` 是**追加**到同一个 root logger 上的。所以三处 `<root>` 声明合起来变成了"level=WARN，appender = CONSOLE + CONSOLE + FILE"。Logback 对重复的 appender-ref 会去重，实际是 `CONSOLE + FILE`。

**怎么改才符合意图**（prod 只写文件、不写控制台）：

```xml
<springProfile name="prod">
    <root level="WARN">
        <appender-ref ref="FILE"/>
    </root>
</springProfile>
```

问题在于**默认那条 `<root>` 不能省**（否则没匹配 profile 时无处输出），但它的 `CONSOLE` 会一直跟着。干净的做法是用 `<springProfile name="!prod">` 包住控制台那条：

```xml
<springProfile name="!prod">
    <root level="INFO"><appender-ref ref="CONSOLE"/></root>
</springProfile>
<springProfile name="prod">
    <root level="WARN"><appender-ref ref="FILE"/></root>
</springProfile>
```

**教训**：**`<root>` 不是"声明一次定终身"，多次声明是合并语义。** 写 profile 分流时，先把"默认态"想清楚，再想"某环境要额外加什么 / 要排除什么"。

### 7.4 顺便解释启动时那行警告

启动时 stderr 会出现这样一行：

```
22:27:19,353 |-WARN in ch.qos.logback.core.model.processor.AppenderModelHandler - Appender named [FILE] not referenced. Skipping further processing.
```

**原因**：没激活 `prod` 时，`FILE` appender 定义了却没有任何 `<root>` 或 `<logger>` 引用它。Logback 会跳过它的初始化。

**这条警告是无害的**（01/02 那些没自定义日志的模块不会出现，因为它们的 classpath 里没有这个 xml）。但它提示了一个事实：**appender 定义和 appender 引用是两件事**，定义了不用等于白定义。

---

## 8. traceId：用 MDC 把一次请求的日志串起来

### 8.1 要解决的问题

一次请求可能打十几条日志，散落在 controller、service、mapper 各处。线上并发跑的时候，这些日志在文件里是交错的：

```
22:27:47 ... exec-7 ... 处理 /log/demo 请求
22:27:47 ... exec-8 ... 处理 /log/demo 请求
22:27:47 ... exec-7 ... 这是一条警告日志
22:27:47 ... exec-8 ... 这是一条警告日志
```

靠线程名能勉强分辨，但**线程会被复用**，而且异步、线程池一上来就失效。正确做法是给每个请求一个**唯一编号**，打在每条日志里。

### 8.2 MDC 是什么

**MDC（Mapped Diagnostic Context）**：SLF4J 提供的一个"**和当前线程绑定**的 Map"，往里放 key-value，日志 pattern 里就能用 `%X{key}` 取出来。

```java
MDC.put("traceId", "abc123");
log.info("...");        // pattern 里的 %X{traceId} 会输出 abc123
MDC.clear();            // 用完必须清
```

**关键性质**：MDC 底层是 `ThreadLocal`，所以**只对当前线程可见**。这既是它的优点（天然隔离并发请求），也是它的坑（见 11.3）。

### 8.3 拦截器实现

```java
@Component
public class TraceInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        MDC.put("traceId", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束必须清理，避免线程复用导致串号
        MDC.clear();
    }
}
```

这行生成逻辑拆开看：

| 片段 | 作用 | 结果 |
| --- | --- | --- |
| `UUID.randomUUID().toString()` | 生成 UUID | `b6aec9b8-ac33-4d49-...` |
| `.replace("-", "")` | 去掉 4 个短横线 | `b6aec9b8ac334d49...` |
| `.substring(0, 16)` | 截前 16 位 | `b6aec9b8ac334d49` |

**为什么截 16 位**：完整 UUID 是 36 字符，太长会挤压日志正文。16 位十六进制 = 64 bit，同一秒内的并发请求碰撞概率可以忽略。

### 8.4 注册拦截器

```java
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TraceInterceptor traceInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traceInterceptor).addPathPatterns("/**");
    }
}
```

两点：

- `implements WebMvcConfigurer` + 重写 `addInterceptors` 是注册拦截器的**标准姿势**（不用 XML）；
- `addPathPatterns("/**")` 表示所有路径。**不写这句，拦截器不会生效**——默认只拦根路径，实际上等于不拦。

### 8.5 实测：traceId 真的唯一且隔离

**实测输出**（连续 6 次请求 `/demo`，其中 4 次是并发发的）：

```
22:27:47 [http-nio-8009-exec-7]  这是一条警告日志  -> traceId 7bd47bd42df24bed
22:27:47 [http-nio-8009-exec-8]  这是一条警告日志  -> traceId b3e4c9b0c3e347c5
22:27:47 [http-nio-8009-exec-9]  这是一条警告日志  -> traceId 3d14354ef3bc44d7
22:27:48 [http-nio-8009-exec-10] 这是一条警告日志  -> traceId 7c93db3f1ffe400c
22:27:48 [http-nio-8009-exec-1]  这是一条警告日志  -> traceId d9b1498deb00462f
22:27:48 [http-nio-8009-exec-2]  这是一条警告日志  -> traceId 8c71e68a3d474634
22:27:48 [http-nio-8009-exec-3]  这是一条警告日志  -> traceId 96c88dfa918a4695
```

**6 次请求 6 个不同的 traceId，一个都没重复**，而且每次请求的 `INFO/WARN/ERROR` 三条共用同一个 traceId。并发那 4 条虽然时间戳都在 `22:27:47`，但 traceId 各不相同——**MDC 是按线程隔离的，所以并发之间不会串号**。

### 8.6 一个必须知道的顺序问题

`afterCompletion` 里调 `MDC.clear()` 而不是 `MDC.remove("traceId")`。两者区别：

| 写法 | 效果 |
| --- | --- |
| `MDC.remove("traceId")` | 只删这一个 key，别的 key 留着 |
| `MDC.clear()` | 清空整个 MDC |

本模块用的是 `clear()`，**在只用了一个 key 的情况下是对的**，而且更保险。但如果将来还往 MDC 里放了 `userId`、`tenantId`，就要注意别被这个 `clear()` 一起清掉（或者干脆都放一起清，反而更安全）。

> **规则**：**`preHandle` 里 put，`afterCompletion` 里 clear，必须成对。** 少了 clear，Tomcat 线程被复用时会带着上一个请求的 traceId，日志就串号了——这比没有 traceId 更危险，因为它会**误导排查方向**。

---

## 9. 敏感信息脱敏：`LogMaskUtil`

### 9.1 为什么日志要脱敏

日志会被：写进文件、采集到日志平台、被运维和开发看到、可能在排障时截图发群。手机号、身份证、银行卡一旦进了日志，就等于**多了一份不受管控的副本**。

```java
public final class LogMaskUtil {

    private LogMaskUtil() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
```

### 9.2 三个设计细节

| 写法 | 为什么这么写 |
| --- | --- |
| `public final class` | 工具类不该被继承，防止有人覆写行为 |
| `private LogMaskUtil() {}` | 私有构造，防止 `new` 出来一个没意义的实例 |
| `length() != 11` 就原样返回 | 长度不对**不要硬截**，否则会抛 `StringIndexOutOfBoundsException` |
| `phone == null` 也原样返回 | 脱敏工具本身不能变成新的 NPE 来源 |

实测效果（`13812345678`）：

```
138****5678
```

前 3 位 + `****` + 后 4 位。保留了"能不能看出是哪个号"的判断力，同时隐藏了完整号码。

> **这条要记住**：**脱敏要"够用"，不是"越狠越好"。** 全打码成 `***********` 会让排障时无法区分两个用户；保留头尾才既能排查又不泄露。

### 9.3 本模块的现状

`LogMaskUtil` 目前**没有任何地方调用它**，属于"写好了但没接上"的状态。真实项目里的接法有两种：

- **手动**：在打日志时自己包一层，`log.info("手机号 {}", LogMaskUtil.maskPhone(phone))`；
- **自动**：自定义 Logback 的 `converter`，在 pattern 里对所有输出做正则替换（更彻底，但性能开销和误伤风险都更大）。

我目前只做到"工具类"，**没有落到实际日志里**，这一点记在 15.4。

---

## 10. 接口与实测结果（Windows 实操）

### 10.1 接口清单（本模块一共 2 个）

| 方法 | 路径 | 返回 | 对应知识点 |
| --- | --- | --- | --- |
| GET | `/hello` | `日志管理 模块` | 最小可用接口 |
| GET | `/demo` | `ok` | 三个级别的日志 + traceId |

> ⚠️ **注意 `/demo` 里那句日志文案**：代码写的是 `log.info("处理 /log/demo 请求")`，但实际映射路径是 `/demo`，没有 `/log` 前缀。日志文案和真实路径不一致，排查时会误导（见 15.4）。

### 10.2 启动

```powershell
cd E:\backend-learning\beckend-learning\03-logging
mvn spring-boot:run
```

**实测启动日志**（节选）：

```
22:27:19,353 |-WARN in ch.qos.logback.core.model.processor.AppenderModelHandler - Appender named [FILE] not referenced. Skipping further processing.
22:27:19 [background-preinit] INFO  o.h.validator.internal.util.Version [...] - HV000001: Hibernate Validator 9.1.3.Final
22:27:19 [restartedMain] INFO  t.s.logging.LoggingApplication [...] - Starting LoggingApplication using Java 21.0.12.1 with PID 93024
22:27:19 [restartedMain] INFO  t.s.logging.LoggingApplication [...] - No active profile set, falling back to 1 default profile: "default"
22:27:20 [restartedMain] INFO  o.s.boot.tomcat.TomcatWebServer [...] - Tomcat initialized with port 8009 (http)
22:27:20 [restartedMain] INFO  o.a.catalina.core.StandardEngine [...] - Starting Servlet engine: [Apache Tomcat/11.0.24]
22:27:21 [restartedMain] INFO  o.s.boot.tomcat.TomcatWebServer [...] - Tomcat started on port 8009 (http) with context path '/'
22:27:21 [restartedMain] INFO  t.s.logging.LoggingApplication [...] - Started LoggingApplication in 2.527 seconds (process running for 5.439)
```

**看到 `Tomcat started on port 8009` 就是成功**，本次启动耗时约 **2.5 秒**。

启动过程里的 banner 是 `banner.txt` 提供的 ASCII 图（那只兔子），配合 `${AnsiColor.BRIGHT_GREEN}` 输出绿色。**banner 在日志系统初始化之前打印**，所以它没有时间戳、不受 pattern 影响。

### 10.3 实测结果

```
GET /hello  ->  日志管理 模块
GET /demo   ->  ok
```

`/hello` 的实测响应头：

```
HTTP/1.1 200
Content-Type: text/plain;charset=UTF-8
Content-Length: 19
```

**注意 `charset=UTF-8`**：这是中文能正常显示的原因。如果返回的是 `application/json` 而没带 charset，客户端可能按 ISO-8859-1 解码，中文就会变成乱码（02 那篇踩过这个坑）。

`/demo` 触发三条日志（见 5.3），并且**每次请求的 traceId 都不同**（见 8.5）。

### 10.4 实测 404 的表现

```
GET /nope
```

```
HTTP/1.1 404
Content-Type: application/json

{"timestamp":"2026-10-09T14:27:48.859Z","status":404,"error":"Not Found",
 "trace":"org.springframework.web.servlet.resource.NoResourceFoundException: No static resource nope for request '/nope'. ...",
 "message":"No static resource nope.","path":"/nope"}
```

**得出结论：本模块没有全局异常处理，404 走的是 Spring Boot 默认的 `/error` 机制。** 证据有三条：

1. 响应体是 Boot 默认的 `timestamp/status/error/path` 结构，不是我定义的统一响应体；
2. `trace` 字段里**带完整堆栈**，把内部类名、包结构全暴露了；
3. 异常类名是 `NoResourceFoundException`，且提示"找不到静态资源"——因为请求先被当成静态资源找了一遍。

这正好是 04-web 那篇要解决的问题（统一响应体 + 全局异常处理）。**03 和 04 的对照关系就在这里。**

### 10.5 查端口、结束进程

```powershell
netstat -ano | findstr :8009
taskkill /PID <pid> /F
```

> **这条踩过坑**：应用**启动成功不等于进程还活着**。我实测时用一次性 shell 起了个后台进程，日志明明打印了 `Tomcat started on port 8009`，紧接着 `netstat` 却查不到端口——因为父 shell 退出时把它一起回收了。**判断服务在不在，要看 `netstat` 的 LISTENING，不要只看日志里那句启动成功。**

---

## 11. 我踩过的 5 个坑

### 坑 1：`%clr` 在自定义 `logback-spring.xml` 里不生效

照抄 Spring Boot 默认配置到自己的 xml 里，`%clr` 直接变成字面量输出。

**原因**：`%clr` 是 **Spring Boot 注册的转换词**，不是 Logback 原生的。Spring Boot 用自己的 `logback-spring.xml` 时会自动注册，**换成自定义文件后这个注册就没了**。

**解决**：在 `<configuration>` 里手动加一行：

```xml
<conversionRule conversionWord="clr" class="org.springframework.boot.logging.logback.ColorConverter"/>
```

**教训**：**"默认配置能用的东西"不等于"我自己写也能用"。** 官方默认配置文件里往往有一堆隐式注册，抄的时候要连"注册代码"一起抄。

### 坑 2：格式写在 yml 里，xml 里读不到

一开始把 pattern 直接写在 `logback-spring.xml` 里，但改格式得改两处（yml 一处、xml 一处），容易不一致。

**解决**：用 `<springProperty>` 从 yml 读进来：

```xml
<springProperty scope="context" name="CONSOLE_LOG_PATTERN" source="logging.pattern.console" defaultValue="..."/>
```

**注意 `defaultValue` 必须写**：yml 里没配时会用默认值，不写的话 pattern 变成空字符串，控制台日志会只剩正文、没有时间和级别。

**教训**：**`<springProperty>` 是 xml 和 yml 之间的桥**，配置只维护一份；但桥那头可能断（key 写错、没配），所以要给 `defaultValue` 兜底。

### 坑 3：MDC 用完不清理，日志串号

一开始只在 `preHandle` 里 put，没写 `afterCompletion`。

**现象**：Tomcat 的线程是复用的（线程池），同一个线程处理完请求 A 再处理请求 B 时，`MDC` 里**还留着 A 的 traceId**，于是 B 的日志打出了 A 的编号。

**为什么危险**：这不是"少了个编号"，而是**给了一个错误的编号**。排查时会顺着 traceId 找到完全不相干的请求，方向全错。

**解决**：加 `afterCompletion` 调 `MDC.clear()`。

**教训**：**`ThreadLocal` 类的东西，借了就一定要还。** MDC、`RequestContextHolder`、事务上下文都一样：**在同一个线程上，上一个请求的残留就是下一个请求的脏数据。**

### 坑 4：`MDC.clear()` 还是 `MDC.remove()`

**现象**：一开始用 `remove("traceId")`，清掉之后如果代码里还读了别的 MDC key，就还是残留值。

**解决**：本模块只用了一个 key，改成 `clear()` 更保险。

**教训**：**先用 `clear()`，等真的需要保留别的 key 时再考虑精细清理。** 在"清不干净会串号"和"清多了会丢数据"之间，前者更危险。

### 坑 5：以为日志级别抬高了就不会写文件（认知坑）

我在 `prod` 分支里只写了 `FILE` appender，以为控制台就不会输出了。

**实测**：控制台**照样输出了** WARN 和 ERROR（见 7.3 的完整输出）。

**原因**：`<root>` 多次声明是**合并语义**——`level` 覆盖，但 `appender-ref` 追加。默认那条 `<root>` 的 `CONSOLE` 并没有被 replace 掉。

**解决**：用 `<springProfile name="!prod">` 把控制台那条排除掉，或者接受"prod 也往控制台写"。

**教训**：**配置里的"看起来会覆盖"和"实际是追加"，是两类完全不同的语义。** 遇到"明明改了却没生效"，第一反应应该是查**有没有别的地方也在配同一个东西**，而不是怀疑框架。

---

## 12. 报错速查表

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 输出里出现字面量 `%clr` 或颜色代码不生效 | 自定义 xml 没注册 `ColorConverter` | 加 `<conversionRule conversionWord="clr" .../>` |
| `<springProfile>` 完全不生效，两个环境日志一模一样 | 文件名写成了 `logback.xml` | 改名 `logback-spring.xml`（必须由 Spring 解析） |
| 控制台日志没有时间/级别，只剩正文 | `<springProperty>` 没找到 source 且没写 `defaultValue` | 核对 yml 里的 key；给 `defaultValue` 兜底 |
| `Appender named [FILE] not referenced` | 当前 profile 下没有 root/logger 引用它 | 无害；或在对应 profile 里引用它 |
| 日志里 `[%X{traceId}]` 位置是空的 | 请求没经过拦截器，或 `addPathPatterns` 没写 | 检查 `addInterceptors` 是否真的注册了 |
| 拦截器完全不生效 | 忘记写 `.addPathPatterns("/**")` | 补上（默认匹配范围极小） |
| **上一个请求的 traceId 出现在本次请求日志里** | `afterCompletion` 里没清 MDC | 加 `MDC.clear()`，必须与 `preHandle` 成对 |
| 日志文件没生成 | `<file>` 是相对路径，落点取决于进程工作目录 | 用绝对路径，或确认工作目录 |
| 抬高到 `WARN` 后控制台仍有输出 | `<root>` 是合并语义，`appender-ref` 追加 | 用 `!prod` 排除控制台，或接受现状 |
| `StringIndexOutOfBoundsException` 出现在脱敏工具里 | 没校验长度就 `substring` | 先判断 `length() != 11` 原样返回 |
| 中文日志在日志文件里变乱码 | 读取时用了错误的编码（不是写的错） | 用 UTF-8 读；`logback` 默认写 UTF-8 |
| `Port 8009 was already in use` | 上次的应用没关 | `netstat -ano \| findstr :8009` → `taskkill /PID <pid> /F` |
| 启动成功但 `netstat` 查不到端口 | 进程随临时 shell 退出被回收了 | 用能占住的终端启动，别用一次性 shell 起后台进程 |

---

## 13. 练习

### 13.1 随堂练习（约 10 分钟）

| # | 练习 | 验证点 | 状态 |
| --- | --- | --- | --- |
| 1 | 加一个 `log.debug("调试信息")`，默认启动看输出 | DEBUG 低于 root 的 INFO，**不输出** | [ ] |
| 2 | yml 里加 `logging.level.top.seamohai.logging=DEBUG` 再看 | 只放开自己包的 DEBUG，框架日志不受影响 | [ ] |
| 3 | 连续请求 `/demo` 5 次，对比 traceId | 5 次各不相同，且每次的 3 条日志共用 1 个 | [ ] |
| 4 | 注释掉 `afterCompletion` 里的 `MDC.clear()`，快速连发请求 | 出现"两次请求同一个 traceId"的串号现象 | [ ] |
| 5 | 用 `--spring.profiles.active=prod` 启动 | 控制台仍有 WARN/ERROR，但 `logs/app.log` 里也有 | [ ] |

> 第 4 题做完**一定要把 `MDC.clear()` 改回来**。这是有实际危害的坑，不要留在代码里。

### 13.2 课后练习

- **基础**：修掉 15.4 里 `%clr(%logger){bg_blue}` 重复输出 logger 的问题，把背景色挪到 traceId 上。
- **进阶**：把 `LogMaskUtil` 真正接进日志里，给 `LoggingController` 加一个带 `phone` 参数的接口，日志里输出脱敏后的号码。
- **挑战**：给 `TraceInterceptor` 加一个"优先复用上游传来的 traceId"的逻辑——如果请求头里有 `X-Trace-Id` 就用它，没有才生成。想清楚**为什么微服务架构下必须这么做**。

### 13.3 关键写法提示

```java
// 门面 + 占位符（不要用 + 拼字符串）
@Slf4j
public class XxxService {
    public void doSomething(String userId) {
        log.info("开始处理，userId={}", userId);
    }
}

// MDC：put 和 clear 必须成对
public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
    MDC.put("traceId", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    return true;
}
public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
    MDC.clear();
}

// 拦截器注册：必须有 addPathPatterns
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(traceInterceptor).addPathPatterns("/**");
}

// 脱敏：先校验再截取
public static String maskPhone(String phone) {
    if (phone == null || phone.length() != 11) {
        return phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(7);
}
```

```xml
<!-- 自定义 logback-spring.xml 必加：注册 %clr -->
<conversionRule conversionWord="clr" class="org.springframework.boot.logging.logback.ColorConverter"/>

<!-- 从 yml 读格式，defaultValue 兜底 -->
<springProperty scope="context" name="CONSOLE_LOG_PATTERN"
                source="logging.pattern.console" defaultValue="%d{HH:mm:ss} %-5level %logger{36} [%X{traceId}] - %msg%n"/>
```

### 13.4 自测：能不能回答这几个问题

1. SLF4J 和 Logback 谁是门面、谁是实现？想换实现要不要改业务代码？
2. `log.debug("x=" + x)` 和 `log.debug("x={}", x)` 在 DEBUG 关闭时的开销差在哪？
3. `logback.xml` 和 `logback-spring.xml` 的区别是什么？哪个能用 `<springProfile>`？
4. `%clr` 为什么必须手动注册？不注册会打印成什么？
5. MDC 底层是什么数据结构？为什么它能天然隔离并发请求？
6. 为什么 `MDC.clear()` 是必须的？不清会出现什么现象，为什么比"没有 traceId"更危险？
7. `<root>` 声明多次时，`level` 和 `appender-ref` 各自的语义是什么？
8. 为什么 `<file>logs/app.log</file>` 的落点会"不一定"？
9. `${AnsiColor.BRIGHT_GREEN}` 为什么在 banner 里能用，而 pattern 里要用 `%clr`？
10. 03 的 404 为什么返回了一整段堆栈？这暴露了什么问题？

---

## 14. 知识地图与名词速查

### 14.1 本节要点回顾

| 概念 | 一句话 |
| --- | --- |
| 日志门面 SLF4J | 代码里调用的 API 层；换实现不改业务代码 |
| 日志实现 Logback | 真正决定输出格式、目标、滚动的库；Boot 默认 |
| `@Slf4j` | Lombok 编译期生成 `static final Logger log`，logger 名 = 类全限定名 |
| 五个级别 | `TRACE < DEBUG < INFO < WARN < ERROR`；级别由"要不要人介入"决定 |
| `{}` 占位符 | 确认要输出后才替换，级别关闭时零开销 |
| `logging.pattern.console` | 控制台格式配置，pattern 占位符在这里写 |
| `%d` / `%thread` / `%-5level` / `%logger{36}` / `%msg` / `%n` | 时间 / 线程 / 级别（左对齐宽 5）/ logger（缩写 36）/ 正文 / 换行 |
| `%X{key}` | 从 MDC 取值，traceId 就靠它输出 |
| `%clr(...){颜色}` | Spring Boot 提供的上色转换词，**自定义 xml 必须手动注册** |
| `logback-spring.xml` | 由 Spring 解析，支持 `<springProfile>` 和 `<springProperty>` |
| appender | 日志出口，可配多个；`CONSOLE` 和 `FILE` 各一个 |
| `TimeBasedRollingPolicy` | 按时间滚动 + `maxHistory` 控制保留天数 |
| MDC | 与线程绑定的 Map（底层 `ThreadLocal`），`%X{}` 从这里取值 |
| traceId | 一次请求的唯一编号；本模块取 UUID 前 16 位 |
| `HandlerInterceptor` | 拦截器；`preHandle` 进、`afterCompletion` 出 |
| `WebMvcConfigurer` | 注册拦截器的标准姿势，**必须写 `addPathPatterns`** |
| 脱敏 | 保留头尾、隐藏中间，要"够用"而不是"越狠越好" |
| banner | 启动横幅，在日志初始化**之前**打印，不受 pattern 影响 |

### 14.2 名词速查

| 名词 | 解释 |
| --- | --- |
| **门面（Facade）** | 对外统一的 API 层，屏蔽底层实现差异，如 SLF4J |
| **Binding** | 门面和具体实现之间的桥梁，决定了运行时用哪个实现 |
| **Logger** | 按名字分层的日志对象；名字通常是类全限定名，可被继承层级过滤 |
| **Appender** | 日志的"出口"，一条日志可以同时送给多个 appender |
| **Encoder / Pattern** | 决定一条日志被格式化成什么样；`PatternLayoutEncoder` 用 pattern |
| **conversionRule** | 把 `%xxx` 这类自定义转换词注册到 Logback |
| **`<springProperty>`** | 把 Spring 的配置值读进 logback 上下文，支持 `defaultValue` |
| **`<springProfile>`** | 按激活的 profile 决定哪段配置生效；**只有 `logback-spring.xml` 支持** |
| **MDC（Mapped Diagnostic Context）** | 映射诊断上下文，线程绑定的 key-value 容器 |
| **ThreadLocal** | Java 的线程本地变量；MDC 隔离性的来源，也是串号坑的根源 |
| **traceId / 链路追踪** | 一次请求的唯一标识，用于把散落日志串成一条链 |
| **滚动（Rolling）** | 日志文件按时间或大小切分并清理旧文件 |
| **`maxHistory`** | 保留多少个历史文件（按时间滚动时是"天"） |
| **脱敏（Masking）** | 输出前把敏感字段部分遮蔽，兼顾排障与合规 |
| **`restartedMain`** | devtools 热重启后的主线程名，看到它说明代码改动触发了重启 |
| **ANSI 转义序列** | 终端上色的控制字符，如 `\u001b[32m`；重定向到文件时会变成乱码 |

---

## 15. 内容小结与学习小结

### 15.1 内容小结

| 案例 | 使用场景 | 本模块的落点 |
| --- | --- | --- |
| `@Slf4j` + 五级日志 | 按"要不要人介入"选级别 | `LoggingController.demo()` 连打 INFO/WARN/ERROR |
| 自定义 pattern | 控制输出的形状与颜色 | `logging.pattern.console` + `%clr` 上色 |
| `logback-spring.xml` | 按环境分流 appender 与级别 | `dev` → 控制台 INFO；`prod` → 文件 WARN |
| 滚动文件 | 生产环境留档 + 自动清理 | `RollingFileAppender` + `maxHistory=30` |
| MDC + 拦截器 | 把一次请求的日志串成一条链 | `TraceInterceptor` 生成 16 位 traceId |
| 脱敏工具 | 敏感字段不进日志 | `LogMaskUtil.maskPhone()` |

### 15.2 学习小结

> 这周的 03-logging，一开始我以为"打日志"就是 `System.out.println` 换了个写法，做完才发现里面全是配置和约定。
>
> 门面和实现分开这件事我原来完全没概念，一直以为 `log.info` 就是 Logback 的方法。搞明白 SLF4J 是门面之后，才发现自己写的代码根本不知道底层是谁——这也是为什么换实现不用改代码。
>
> 真正折腾的是 `logback-spring.xml`。我照着自己的理解写了 dev 和 prod 两套，以为 prod 就只写文件了，实测发现控制台照样有输出。查了半天才反应过来 `<root>` 写了三次不是"最后一次生效"，而是合在一起的：级别是最后一次的，appender 是全都加上。这个坑我印象最深，因为它"看起来明明是对的"。
>
> traceId 那块反而是最有成就感的。看着日志里同一串 16 位编号把一次请求的 INFO、WARN、ERROR 串起来，一下就明白了链路追踪是干嘛的。不过我也踩了不清 MDC 的坑，后来连发请求看到两个请求用了同一个编号，才理解为什么 `preHandle` 和 `afterCompletion` 必须成对。
>
> 另外注意到 03 的 404 直接吐了一大段堆栈，把内部包名都暴露了。这个在 04-web 会用统一响应体和全局异常处理解决掉，两篇正好能对照着看。

### 15.3 作业要求完成度自查

| # | 作业要求 | 状态 | 证据 |
| --- | --- | --- | --- |
| 1 | 日志框架选型与依赖说明 | ✅ | 4.1；SLF4J 门面 + Logback 实现，starter 自带 |
| 2 | 五级日志与选用原则 | ✅ | 5.2、5.3；实测输出见 5.3 |
| 3 | 自定义日志格式（pattern） | ✅ | 6；`logging.pattern.console` 逐字段拆解 |
| 4 | 文件输出与滚动策略 | ✅ | 7.2、7.3；实测 `logs/app.log` 见 7.3 |
| 5 | 多环境日志分流（profile） | ✅ | 7.2、7.3；`dev` / `prod` 实测对照 |
| 6 | traceId 链路追踪 | ✅ | 8；实测 6 次请求 6 个不同 traceId，见 8.5 |
| 7 | 敏感信息脱敏 | ⬜ | 工具类已写（9），**但没有接入实际日志** |
| 8 | 接口同步 Apifox 并测试 | ⬜ | 2 个接口清单见 10.1，还没上传 |
| 9 | 内容小结 | ✅ | 15.1 |
| 10 | 运行效果视频 + 学习小结 | ⬜ | 小结见 15.2；录制要点见 13.1 第 3、4、5 题 |

### 15.4 我代码里的待修正点

- [ ] **`logging.pattern.console` 里 logger 名重复输出**：`[%X{traceId} %clr(%logger){bg_blue}]` 应该是 `%clr([%X{traceId}]){bg_blue}`，现在同一行打了两次 logger（见 6.1）
- [ ] **`LoggingController` 里的日志文案和真实路径不符**：写的是 `处理 /log/demo 请求`，实际映射是 `/demo`（见 10.1）
- [ ] **`LogMaskUtil` 没有被任何地方调用**：工具类写好了但没接上（见 9.3）
- [ ] **`LoggingController` 里 `import org.springframework.boot.autoconfigure.SpringBootApplication;` 没用到**，删掉
- [ ] **`@RequestMapping` 加在类上但没写路径**：等价于 `""`，建议直接删掉这个注解，让 `@GetMapping("/hello")` 自己表达
- [ ] **`prod` 分支的控制台 appender 没有排除**：`<root>` 合并导致 prod 仍往控制台写（见 7.3），要么接受、要么用 `!prod`
- [ ] **03 的 404 没有统一处理**：返回了 Boot 默认结构还带完整堆栈（见 10.4），04-web 的做法可以反向移植回来
- [ ] **端口不统一**：01 用 8888、02 用 9090、03 用 8009、04 用 8004，四种风格。建议定成 `9000+N` 并写进仓库 README
- [ ] **类头注释**：`TraceInterceptor`、`LogMaskUtil`、`LoggingController` 都没有 `@author` 和说明
- [ ] **`logs/` 目录已加进 `.gitignore`**：确认过了，但 `target/` 里的 `cp03.txt` 这类临时文件别提交

---

## 16. 还没搞懂的问题

1. `%logger{36}` 的缩写规则到底是什么？我实测看到 `o.s.boot.tomcat.TomcatWebServer` 和 `t.s.l.controller.LoggingController`，前者把中间段缩写成了首字母、后者保留了完整包路径，**具体阈值和算法我没查**。
2. `<root>` 多次声明时 `appender-ref` 是"追加"，这是我的实测结论；但**如果两个 `<root>` 的 level 一个高一个低，最终生效的到底是不是"最后一个"**？我只测了 prod 比默认更严格的情况。
3. `MDC.clear()` 会不会影响 Logback 自己往 MDC 里放的东西？（比如某些 appender 依赖的内部 key）我推测不会，但**没实测**。
4. `%X{traceId}` 在**异步请求**（`@Async`、`Callable` 返回值）里还能不能取到？按 `ThreadLocal` 的机制推测**取不到**，得手动传（`MDC.getCopyOfContextMap()`），**这条我没实测**。
5. `LogMaskUtil` 如果要做成**自动脱敏**，用 Logback 的 `converter` 还是 `MessageConverter`？两者的性能开销差多少？我还没查到结论。
6. `spring.output.ansi.enabled: always` 强制开了 ANSI，**重定向到文件时那些转义字符就会写进文件**。这在实际使用中是不是个问题？我实测在文件里确实看到了 `\u001b[32m` 这类字符，但没确认**生产环境该不该设成 `always`**。
7. `TimeBasedRollingPolicy` 的 `maxHistory=30` 到了 30 天之后，**删除是自动发生的还是要额外配 `cleanHistoryOnStart`**？我没等到 30 天，**没实测**。
8. 03 的 404 请求**没有经过 `TraceInterceptor` 吗**？我看到的 404 响应里没有 traceId 信息，但按 `addPathPatterns("/**")` 应该会被拦到——**这条我没细查**。
