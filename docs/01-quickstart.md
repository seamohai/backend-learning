# 01 · 快速入门：从零跑起第一个 Spring Boot 应用

> - 模块：[01-quickstart](../01-quickstart)
> - 工程坐标：`top.seamohai:beckend-learning:0.0.1-SNAPSHOT`
> - 环境：JDK **21** ｜ Spring Boot **4.1.1** ｜ Maven 多模块 ｜ 端口 **8888**
> - 本篇是**自包含学习笔记**：所有概念都在文中解释清楚，不需要另查任何资料

---

## 目录

1. [本节目标](#1-本节目标)
2. [背景：Spring Boot 解决什么问题](#2-背景spring-boot-解决什么问题)
3. [工程结构：多模块 Maven 是怎么组织的](#3-工程结构多模块-maven-是怎么组织的)
4. [父 POM 逐段拆解](#4-父-pom-逐段拆解)
5. [启动类：应用是怎么起来的](#5-启动类应用是怎么起来的)
6. [实体类：数据怎么被承载](#6-实体类数据怎么被承载)
7. [控制器：URL 怎么映射到方法](#7-控制器url-怎么映射到方法)
8. [配置文件：端口与配置读取](#8-配置文件端口与配置读取)
9. [跑起来并验证（Windows 实操）](#9-跑起来并验证windows-实操)
10. [我代码里的 4 个待修正点](#10-我代码里的-4-个待修正点)
11. [报错速查表](#11-报错速查表)
12. [练习](#12-练习)
13. [知识地图与名词速查](#13-知识地图与名词速查)
14. [还没搞懂的问题](#14-还没搞懂的问题)

---

## 1. 本节目标

学完这一节，要能独立做到三件事：

1. 用 Maven 建出一个最小的 Spring Boot 模块骨架；
2. 启动应用，写出一个返回 JSON 的 REST 接口；
3. 说清**启动类、控制器、实体类**三者的分工。

一句话概括分工：

> **启动类**把容器拉起来（入口）→ **控制器**把 URL 映射到方法上（对外门面）→ **实体类**承载数据（结构定义）。

---

## 2. 背景：Spring Boot 解决什么问题

### 2.1 不用 Spring Boot 的时候有多麻烦

一个传统的 Java Web 项目要自己搞定：

| 麻烦事 | 具体表现 |
| --- | --- |
| 容器 | 要单独下载 Tomcat，把打好的 war 包丢进 `webapps` 目录 |
| 配置 | 一堆 XML：`web.xml` 配 Servlet，`applicationContext.xml` 配 Bean |
| 依赖 | 引入 Spring MVC 要手动对齐十几个 jar 的版本，版本不匹配就 `NoSuchMethodError` |
| 重复劳动 | 每个新项目都要把上面这些再来一遍 |

### 2.2 Spring Boot 的四个核心思路

| 思路 | 含义 | 在本模块中的体现 |
| --- | --- | --- |
| **约定优于配置** | 能猜的就不让你写 | 配置文件里只写 `server.port`，其余全用默认值 |
| **起步依赖（starter）** | 一个依赖拉起一整组相关依赖 | 只需 `spring-boot-starter-webmvc`，Tomcat + Spring MVC + Jackson 全都进来了 |
| **自动装配** | 按 classpath 上有什么，自动配好组件 | 有 Jackson 就自动配好 JSON 消息转换器，我一行没写 |
| **内嵌容器** | Tomcat 是应用的一部分，不是外部环境 | `main` 方法一跑就是一个 Web 服务，不用部署 war |

> **重要认知**：Spring Boot 不是替代 Spring，它是 Spring 的**开箱即用封装**。@RestController、依赖注入这些能力本身都是 Spring Framework 提供的，Spring Boot 负责让它们"不用配就能用"。

### 2.3 先建立三个词汇

后面会反复出现，一开始就要有正确印象：

- **容器（ApplicationContext）**：Spring 启动后维护的一个"对象仓库"，管理对象的创建、装配、销毁；
- **Bean**：被放进容器、由 Spring 管理的对象。`UserController`、`User` 如果交给 Spring 管，它们就是 Bean；
- **依赖注入（DI）/ 控制反转（IoC）**：你不在代码里 `new`，而是让 Spring 把建好的对象"塞"给你。

本模块的控制器里还是直接 `new User(...)`，**这是刻意的最简版本**；等到讲配置那一节会正式换成注入。

---

## 3. 工程结构：多模块 Maven 是怎么组织的

### 3.1 继承关系

```
spring-boot-starter-parent 4.1.1              ← Spring 官方父 POM：锁定所有依赖版本
        ↑ 继承
top.seamohai:beckend-learning:0.0.1-SNAPSHOT  ← 本仓库父 POM（packaging = pom）
        ↑ 继承
01-quickstart                                 ← 子模块：只写自己的 artifactId
```

三层继承各管什么：

| 层级 | 负责 |
| --- | --- |
| `spring-boot-starter-parent` | 版本仲裁：所有 Spring / 第三方依赖写不写版本号都行，它定 |
| 本仓库父 POM | 声明 14 个子模块 + 本课程要用的依赖和插件 + JDK 版本 |
| `01-quickstart` | 只有自己的坐标。`groupId`、`version` 都从父 POM 继承，不用重复写 |

### 3.2 为什么要拆成 14 个模块

每个模块是一个**独立可运行的知识点**，好处：

- 各模块端口不同，可以同时启动互不干扰；
- 学 MyBatis 时不会被 Redis 的依赖干扰，出问题范围小；
- 每个模块都能独立 `mvn spring-boot:run`，适合边学边跑。

### 3.3 本模块的真实目录

```
01-quickstart
├── pom.xml                                      # 15 行，只写坐标
├── src/main/java/top/seamohai/quickstart
│   ├── QuickStartApplication.java               # 启动类       11 行
│   ├── controller/UserController.java           # 控制器       23 行
│   └── entity/User.java                         # 实体类       17 行
├── src/main/resources/application.yml           # 配置：端口 8888
└── target/                                      # 编译产物（已被 .gitignore 忽略，不进版本库）
```

---

## 4. 父 POM 逐段拆解

`pom.xml` 只有 15 行，却能让整个模块跑起来，力量全来自父 POM（仓库根目录 `pom.xml`）。

### 4.1 子模块的 pom

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>top.seamohai</groupId>
        <artifactId>beckend-learning</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>01-quickstart</artifactId>
</project>
```

要点：

- `<parent>` 的三个坐标必须和父 POM **一字不差**，写错直接报 `Non-resolvable parent POM`；
- 注意父 POM 的 `artifactId` 拼写是 **`beckend-learning`**（少一个 `a`），不是 `backend-learning`；
- `groupId`、`version` 都不用写——继承来的。这就是"约定优于配置"在构建层面的体现。

### 4.2 父 POM 提供了什么

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
    <relativePath/>
</parent>
<groupId>top.seamohai</groupId>
<artifactId>beckend-learning</artifactId>
<version>0.0.1-SNAPSHOT</version>
<packaging>pom</packaging>
```

- `packaging=pom`：这个模块本身不产出 jar，它的存在只是为了"被继承"；
- `<relativePath/>` 留空：表示去**本地/远程仓库**找父 POM，而不是在当前目录的上一级找。

### 4.3 依赖清单

| 依赖 | scope | 作用 | 我需要做什么 |
| --- | --- | --- | --- |
| `spring-boot-starter-webmvc` | compile | 内嵌 Tomcat + Spring MVC + Jackson | 不用管 |
| `spring-boot-devtools` | runtime, optional | 改代码自动重启 | 开发期有用 |
| `lombok` | optional | 编译期生成 getter/setter，源码更干净 | IDEA 要装 Lombok 插件 |
| `spring-boot-starter-webmvc-test` | test | 写测试用 | 以后写测试时用 |

**scope 的含义**（Maven 基础，值得记住）：

| scope | 编译时可用 | 运行时可入包 | 典型用途 |
| --- | --- | --- | --- |
| `compile`（默认） | ✅ | ✅ | 业务依赖 |
| `runtime` | ❌ | ✅ | 驱动、devtools 这类只在运行时需要 |
| `test` | 仅测试代码 | ❌ | JUnit、测试 starter |
| `optional=true` | 不传递给下游 | — | 工具类依赖，避免污染使用方 |

### 4.4 构建插件

```xml
<plugin>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

负责 `mvn package` 时把普通 jar **重新打包成可执行 jar**（可以直接 `java -jar` 运行，Tomcat 打在里面）。

```xml
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>org.projectlombok:lombok</path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

显式告诉编译器"用 Lombok 作为注解处理器"。**别删**——删了可能编译不报错但 `@Data` 生成的 getter 消失，变成运行时找不到方法。

### 4.5 一个值得记住的工程取舍

父 POM 把依赖写在 **`<dependencies>`** 而不是 `<dependencyManagement>` 里。区别是：

| 写法 | 效果 |
| --- | --- |
| `<dependencies>`（本仓库用的） | **所有子模块强制继承**，谁都躲不掉 |
| `<dependencyManagement>` | 只**锁定版本**，由子模块按需声明 |

学习仓库图省事用第一种；真实项目通常用第二种，避免给不需要的模块塞进一堆依赖（比如给一个纯工具模块塞进整个 Web 容器）。

---

## 5. 启动类：应用是怎么起来的

```java
package top.seamohai.quickstart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class QuickStartApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuickStartApplication.class, args);
    }
}
```

### 5.1 `@SpringBootApplication` 拆开看

它是一个**组合注解**，等于同时开三件事：

| 内含注解 | 作用 |
| --- | --- |
| `@SpringBootConfiguration`（里面还有 `@Configuration`） | 声明这是个配置类，可以在这里定义 Bean |
| `@EnableAutoConfiguration` | 开启自动装配：读取各 jar 里 `META-INF/spring/...AutoConfiguration.imports` 清单，按 classpath 决定加载哪些配置类 |
| `@ComponentScan` | 扫描**当前包及其子包**，把带 `@Component`/`@Controller`/`@Service` 等注解的类注册成 Bean |

> ⚠️ **包扫描规则决定了启动类的位置**：`@ComponentScan` 默认只扫启动类**所在包及子包**。启动类在 `top.seamohai.quickstart`，所以 `controller`、`entity` 都能扫到。一旦把启动类挪进 `controller` 子包，兄弟包 `entity` 就扫不到了——这类问题表现为"Bean 找不到"，很难一眼看出原因。

### 5.2 `SpringApplication.run()` 做了什么

按顺序大致是六步：

1. 推断应用类型（是 Servlet Web 应用还是普通应用）→ 决定用哪个 `ApplicationContext` 实现；
2. 加载 `ApplicationContextInitializer` 和监听器；
3. 准备 `Environment`：读取命令行参数、环境变量、`application.yml`；
4. 创建并**刷新**容器：扫描 Bean、完成自动装配；
5. 启动内嵌 **Tomcat**，把 `DispatcherServlet` 注册进去；
6. 发布"启动完成"事件，开始监听端口。

**为什么 `main` 方法能跑出一个 Web 服务**：因为 Tomcat 是**内嵌**的，它作为普通 Java 对象在同一个 JVM 里被创建和启动，不是外部安装的服务器进程。

### 5.3 启动成功的标志

控制台出现这一行就是成功：

```
Tomcat started on port 8888
```

---

## 6. 实体类：数据怎么被承载

```java
package top.seamohai.quickstart.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String name;
    private LocalDate birthday;
}
```

### 6.1 三个 Lombok 注解到底生成了什么

`@Data` 是组合注解，等价于 `@Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor`。也就是说上面 17 行代码，等价于手写下面这一大坨：

```java
public class User {
    private Long id;
    private String name;
    private LocalDate birthday;

    public User() {}                                              // @NoArgsConstructor
    public User(Long id, String name, LocalDate birthday) {...}    // @AllArgsConstructor

    public Long getId() { return id; }                             // @Data → @Getter
    public void setId(Long id) { this.id = id; }                   // @Data → @Setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }

    @Override public String toString() {...}                       // @Data → @ToString
    @Override public boolean equals(Object o) {...}                // @Data → @EqualsAndHashCode
    @Override public int hashCode() {...}
}
```

理解这一点很重要：**Lombok 不是运行时魔法**，它在**编译期**把上面这些方法写进 `.class` 文件。所以：

- 它依赖注解处理器配置（见 4.4），配置丢了方法就真没了；
- IDE 里如果不装 Lombok 插件，会看到满屏"找不到 getter"的红线，但编译其实是成功的。

### 6.2 为什么 `id` 用 `Long` 而不是 `long`

| 类型 | 没赋值时 | 能否区分"没赋值"和"值就是 0" |
| --- | --- | --- |
| `long`（基本类型） | `0` | ❌ 分不清 |
| `Long`（包装类型） | `null` | ✅ 能 |

**规范**：POJO / 实体类的属性统一用包装类型。这直接关系到数据库场景——数据库里 `NULL` 和 `0` 是两回事，用基本类型会把 `NULL` 悄悄变成 `0`。

### 6.3 命名规范

| 元素 | 规范 | 示例 |
| --- | --- | --- |
| 类名 | UpperCamelCase | `UserController`、`QuickStartApplication` |
| 方法 / 变量 | lowerCamelCase | `getUserInfo`、`birthday` |
| 包名 | 全小写 | `top.seamohai.quickstart.entity` |
| 常量 | 全大写下划线 | `MAX_PAGE_SIZE` |

---

## 7. 控制器：URL 怎么映射到方法

```java
package top.seamohai.quickstart.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.seamohai.quickstart.entity.User;

import java.time.LocalDate;

@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/info")
    private User getUserInfo(){                      // ⚠️ 见 7.5：这里必须是 public
        return new User(1001L,"张三", LocalDate.of(2005,10,24));
    }

    @GetMapping("/hello")
    public String hello(){
        return "hello";
    }
}
```

### 7.1 `@RestController` = `@Controller` + `@ResponseBody`

| | `@Controller` | `@RestController` |
| --- | --- | --- |
| 返回值被当作 | **视图名**，交给视图解析器去找页面 | **响应体**，交给消息转换器直接写出去 |
| 结果 | 跳转到一个 HTML 页面 | 返回 JSON / 文本 |
| 适用场景 | 服务端渲染页面（Thymeleaf / JSP） | 前后端分离、提供接口 |

用 `@Controller` 想让某个方法返回 JSON，就得在该方法上单独加 `@ResponseBody`。`@RestController` 只是把这件事预置在类级别了。

### 7.2 URL 是怎么拼出来的

规则：**类级 `@RequestMapping` 前缀 + 方法级路径 = 完整路径**。

| 类级 | 方法级 | 完整路径 | 处理方法 |
| --- | --- | --- | --- |
| `/user` | `/hello` | `GET /user/hello` | `hello()` |
| `/user` | `/info` | `GET /user/info` | `getUserInfo()` |

加上端口，实际地址就是 `http://localhost:8888/user/hello`。

### 7.3 HTTP 方法的语义

| 方法 | 语义 | 典型 URL | 对应注解 |
| --- | --- | --- | --- |
| GET | 查询，**不应改变数据** | `GET /user/1001` | `@GetMapping` |
| POST | 新增 | `POST /user` | `@PostMapping` |
| PUT | 全量更新 | `PUT /user/1001` | `@PutMapping` |
| DELETE | 删除 | `DELETE /user/1001` | `@DeleteMapping` |

本节只用 GET。**记住这条设计原则**：用 URL 定位资源，用 HTTP 方法表达动作。所以是 `DELETE /user/1001` 而不是 `GET /user/delete?id=1001`。

### 7.4 返回值是怎么变成响应的

一个请求进来后，完整链路是：

```
浏览器请求
   → Tomcat 接收
   → DispatcherServlet（前端控制器，所有请求的统一入口）
   → HandlerMapping：按 URL 找到匹配的 HandlerMethod（就是我的 hello 方法）
   → HandlerAdapter：真正调用这个方法
   → 返回值处理器 + HttpMessageConverter：把返回值写进响应体
   → 响应发回浏览器
```

其中"怎么写进响应体"由返回值类型决定：

| 返回类型 | 走的转换器 | Content-Type | 实际输出 |
| --- | --- | --- | --- |
| `String` | `StringHttpMessageConverter` | `text/plain` | `hello` |
| `User` 对象 | `MappingJackson2HttpMessageConverter` | `application/json` | `{"id":1001,"name":"张三","birthday":"2005-10-24"}` |

**两个容易意外的点**：

1. 返回 `String` 时**不是 JSON**，是纯文本。想让 `"hello"` 也变成 JSON 字符串（带引号），得返回对象或自己包装。
2. `LocalDate` 输出成 `2005-10-24` 而不是时间戳，是因为 Spring Boot 默认关闭了 Jackson 的 `WRITE_DATES_AS_TIMESTAMPS`，并按 ISO-8601 格式（`yyyy-MM-dd`）序列化 Java 8 时间类型。

### 7.5 ⚠️ 接口方法必须是 `public`

我的 `getUserInfo` 现在是 `private`，**必须改**。原因不是"形式主义"，而是：

Spring MVC 注册处理器时，会去找这个方法的**可调用版本**。两种情形：

| 情形 | 结果 |
| --- | --- |
| 控制器**没有被 Spring 代理** | 私有方法靠反射 `setAccessible` **仍可能被调通** → 所以现在很可能看不出错，**这才是最危险的地方** |
| 控制器**被代理了**（加了 `@Transactional`、日志切面、`@PreAuthorize` 等） | 框架找不到可调用的公开方法，直接抛 `IllegalStateException`：`Need to invoke method '...' found on proxy for target class '...' but cannot find proxy that declares this method` |

也就是说：**"现在能跑"不等于"写对了"**，它是一颗等 AOP 上来才引爆的地雷。

> **规则**：对外暴露的接口方法一律 `public`。这条错误在社区里出现频率很高，绝大多数案例就是 `@RequestMapping` 系列方法写成了非 public。
> 参考：[Spring Framework 该错误的说明](https://errors.standardbeagle.com/spring-projects/spring-framework/need-to-invoke-method-s-found-on-proxy-for-targ/)

### 7.6 参数接收的三种方式（后面会用到）

| 场景 | 注解 | 示例 URL | 写法 |
| --- | --- | --- | --- |
| 查询参数 | `@RequestParam` | `/user/hello?name=张三` | `hello(@RequestParam String name)` |
| 路径变量 | `@PathVariable` | `/user/1001` | `getById(@PathVariable Long id)` |
| 请求体 JSON | `@RequestBody` | POST 的 body | `add(@RequestBody User user)` |

---

## 8. 配置文件：端口与配置读取

```yaml
server:
  port: 8888
```

### 8.1 为什么用 yml 而不是 properties

两种写法等价：

```yaml
# application.yml：层级结构，重复前缀不用重写
server:
  port: 8888
  servlet:
    context-path: /api
```

```properties
# application.properties：扁平键值，前缀要重复
server.port=8888
server.servlet.context-path=/api
```

配置一多，yml 的层级优势就明显了。Spring Boot 会**自动加载** `src/main/resources/application.yml` 或 `application.properties`，不用任何额外声明。

### 8.2 YAML 语法三个坑

1. **缩进只能用空格，绝不能用 Tab**（用了直接报解析错误）；
2. 冒号后面**必须有一个空格**：`port: 8888`，写成 `port:8888` 会被当成一个整体字符串；
3. 层级靠缩进表达，同级缩进量必须一致。

### 8.3 配置的优先级

同一项配置在多处出现时，**优先级高的覆盖低的**（简化版，从高到低）：

```
命令行参数（--server.port=9999）
  > 操作系统环境变量
  > 外置的 application.yml（jar 包同级目录）
  > jar 包内的 application.yml
  > 代码里的默认值
```

这个顺序以后很有用：不改代码、不改配置文件，也能临时换端口调试。

### 8.4 本模块的端口约定

我这里是 `8888`，`02-config` 用的是 `9090`，**目前没有统一规则**。

建议定一条并写进仓库 README，比如"第 N 个模块用 `9000 + N`"，否则以后同时启动多个模块会互相踩端口，排查耗时。

### 8.5 顺带记住：Boot 4 的 starter 名字变了

我用的父 POM 里是：

```xml
<artifactId>spring-boot-starter-webmvc</artifactId>
<artifactId>spring-boot-starter-webmvc-test</artifactId>
```

而网上大量教程写的是 `spring-boot-starter-web` / `spring-boot-starter-test`。**不要混抄**——以自己项目的父 POM 为准。Boot 4 对 starter 做过拆分和改名，官方也提供了 "Classic Starter POMs" 作为过渡。
参考：[Spring Boot 4.0 迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

---

## 9. 跑起来并验证（Windows 实操）

> ⏳ **本节尚未实测**。命令按当前代码（端口 8888）写好，但我还没实际启动过一次，期望输出是按代码推导的。跑通后请把结果填进 9.4。

### 9.1 启动

在仓库根目录 `E:\backend-learning\beckend-learning` 执行：

```powershell
mvn -pl 01-quickstart -am spring-boot:run
```

参数含义：

| 参数 | 含义 |
| --- | --- |
| `-pl 01-quickstart` | 只构建指定的模块（project list） |
| `-am` | 连带构建它依赖的模块（also make） |
| `spring-boot:run` | 调用 Spring Boot 插件，直接跑起来（不用先打包） |
| `-o`（可选） | 离线模式，依赖都已下载过时可加快构建 |

看到 `Tomcat started on port 8888` 即成功。

另一种方式（更接近生产）：

```powershell
mvn -pl 01-quickstart -am package        # 打成可执行 jar
java -jar 01-quickstart\target\01-quickstart-0.0.1-SNAPSHOT.jar
```

### 9.2 验证

**另开一个终端**（启动那个窗口被日志占住了）：

```powershell
curl.exe http://localhost:8888/user/hello
# 期望：hello（text/plain）

curl.exe http://localhost:8888/user/info
# 期望：{"id":1001,"name":"张三","birthday":"2005-10-24"}
```

### 9.3 Windows 上的三个坑

1. **PowerShell 里的 `curl` 不是 curl**，它是 `Invoke-WebRequest` 的别名，参数完全不兼容 → 一律写 **`curl.exe`**。
2. **查端口占用 / 结束进程**：

   ```powershell
   netstat -ano | findstr :8888      # 最后一列是 PID
   taskkill /PID <上一步的PID> /F
   ```

3. **中文乱码**：先执行 `chcp 65001` 把控制台切到 UTF-8。

### 9.4 实测结果

```
（待填）
启动耗时：
/user/hello 实际输出：
/user/info  实际输出：
遇到的报错：
```

---

## 10. 我代码里的 4 个待修正点

- [ ] **`getUserInfo` 的 `private` → `public`**（原理见 7.5，优先级最高）
- [ ] **明确 `hello` 接口的契约**：现在返回 `"hello"`，如果约定是 `"hello world"` 就改代码，否则把"返回 hello"记进接口说明。**接口的行为要能被明确描述**，不能靠记忆
- [ ] **统一端口约定**：现在 01=8888、02=9090，想清楚 03~14 用什么，写进 README
- [ ] **补类头注释**：三个类都没有注释，`@author` 该写自己的名字

另外两条自查习惯：

- `target/` 已被 `.gitignore` 忽略，**别手动 `git add -f`** 把编译产物提交进去；
- 新建类时顺手检查包名是否与目录一致（不一致会导致启动报"无法解析主类"）。

---

## 11. 报错速查表

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| `Whitelabel Error Page` 404 | 路径写错 / 少了 `@RestController` | 检查类级前缀 + 方法级路径的拼接结果，注意别漏了端口后的完整路径 |
| `Port 8888 was already in use` | 之前起的服务没关 | `netstat -ano \| findstr :8888` → `taskkill /PID <pid> /F` |
| 返回的不是 JSON 而是报错 | 实体类缺 getter | Jackson 靠 getter 序列化，检查 `@Data` 是否生效 |
| 启动报「无法解析主类」/ 找不到 Bean | 启动类包名与目录不一致，或启动类不在最外层包 | 确保启动类在 `top.seamohai.quickstart` 下 |
| 改了代码没生效 | devtools 未生效 / 没重新编译 | 重启；或确认 IDEA 开了自动编译 |
| 加了切面后接口 500，报 `Need to invoke method ...` | 控制器方法不是 `public` | 改 `public`（见 7.5） |
| 依赖名照抄教程导致编译失败 | 教程用的是旧 starter 名 | 以自己父 POM 为准（`spring-boot-starter-webmvc`） |
| 满屏红线提示找不到 getter | IDE 没装 Lombok 插件 / 没开注解处理 | 装 Lombok 插件 + 打开 Settings → Annotation Processors |
| `Non-resolvable parent POM` | 子模块 `<parent>` 坐标写错 | 与父 POM 的 `groupId/artifactId/version` 逐字核对 |
| 控制台中文乱码 | 控制台编码不是 UTF-8 | `chcp 65001` |

---

## 12. 练习

### 12.1 随堂练习（约 10 分钟）

| # | 练习 | 验证点 | 状态 |
| --- | --- | --- | --- |
| 1 | 给 `User` 加 `email` 字段，并让 `/user/info` 返回它 | `curl.exe /user/info` 能看到 `"email":...` | [ ] |
| 2 | 新增 `GET /user/hello?name=张三`，用 `@RequestParam` 接收，返回 `hello, 张三` | 带参访问返回正确拼接 | [ ] |
| 3 | 重启后确认服务仍监听 8888 | `curl.exe /user/hello` 有输出 | [ ] |

### 12.2 课后练习

- **基础**：给 `User` 增加 `email`、`phone` 两个字段，并让 `/user/info` 完整返回。
- **进阶**：实现 `GET /user/{id}`，用 `@PathVariable` 按 id 返回不同用户。
- **挑战**：写一个返回 `List<User>` 的 `/user/list`，观察 JSON 数组格式；并用一段话说明 `@RestController` 与 `@Controller` 的区别及各自适用场景。

### 12.3 关键写法提示

```java
// 练习 2：接收查询参数（访问 /user/hello?name=张三）
@GetMapping("/hello")
public String hello(@RequestParam String name) {
    return "hello, " + name;
}

// 进阶：路径参数（访问 /user/1001）
@GetMapping("/{id}")
public User getUserById(@PathVariable Long id) {
    // 提示：return new User(id, "张三", LocalDate.of(2005, 10, 24));
}

// 挑战：返回集合，JSON 会变成数组 [ {...}, {...} ]
@GetMapping("/list")
public List<User> list() {
    // 提示：List.of(new User(...), new User(...))
}
```

### 12.4 自测：能不能回答这几个问题

1. 为什么启动类必须放在最外层的包？
2. `@RestController` 和 `@Controller` 在"返回值去哪"这件事上有什么区别？
3. 返回 `String` 和返回 `User` 对象，响应体的格式和 Content-Type 分别是什么？
4. `@Data` 帮我生成了哪几个方法？它在编译期还是运行期生效？
5. 为什么实体类属性要用 `Long` 而不是 `long`？
6. `@RequestParam`、`@PathVariable`、`@RequestBody` 分别取的是请求的哪一部分？

---

## 13. 知识地图与名词速查

### 13.1 本节要点回顾

| 概念 | 一句话 |
| --- | --- |
| Spring Boot | 约定优于配置 + 起步依赖 + 自动装配 + 内嵌容器 |
| 继承链 | `spring-boot-starter-parent` → 本仓库父 POM → 子模块 |
| 启动类 | `@SpringBootApplication` = 配置 + 自动装配 + 组件扫描，必须放最外层包 |
| `@RestController` | 返回值直接作为响应体，由消息转换器写出去 |
| URL 映射 | 类级 `@RequestMapping` 前缀 + 方法级 `@GetMapping` 子路径 |
| 实体类 + Lombok | `@Data` 编译期生成样板方法，属性用包装类型 |
| 配置文件 | `application.yml` 自动加载，命令行参数优先级最高 |

### 13.2 名词速查

| 名词 | 解释 |
| --- | --- |
| **Bean** | 由 Spring 容器创建和管理的对象 |
| **IoC（控制反转）** | 对象的创建权从"你自己 new"反转给"容器" |
| **DI（依赖注入）** | 容器把依赖对象主动"塞"给你，而不是你自己去取 |
| **starter** | 一组依赖的打包入口，引一个拉起一串 |
| **自动装配** | 按 classpath 上有什么自动配好组件，少写甚至不写配置 |
| **DispatcherServlet** | 前端控制器，所有请求的统一入口，负责分发到对应方法 |
| **HandlerMapping** | 维护"URL → 处理方法"的映射关系 |
| **HandlerMethod** | 被封装起来的控制器方法（含它所在的 Bean 和方法信息） |
| **HttpMessageConverter** | 负责 Java 对象 ↔ 请求/响应体（JSON、文本）的转换 |
| **Jackson** | JSON 序列化库，Spring Boot 默认集成 |
| **Lombok** | 编译期生成样板代码的注解处理器 |
| **devtools** | 开发期工具，改代码后自动重启应用 |
| **POJO** | 普通 Java 对象，只有属性和 getter/setter |

---

## 14. 还没搞懂的问题

1. `private` 的接口方法在**没有任何切面**时，到底会不会被成功调用？打算按第 9 节实测确认。
2. 端口约定：01=8888、02=9090，到底统一成什么规则？后面 12 个模块怎么排？
3. 父 POM 用 `<dependencies>` 让所有模块强制继承，什么时候应该换成 `<dependencyManagement>`？
4. `spring-boot-devtools` 的自动重启，和我手动重跑 `mvn spring-boot:run`，本质区别是什么？
5. `DispatcherServlet` 那一步的"返回值处理器"在第 6 类返回值（比如 `ResponseEntity`）时会怎么变化？
