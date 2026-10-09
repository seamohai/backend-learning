# 04 · Web 开发进阶：统一响应、全局异常与参数校验

> - 模块：[04-web](../04-web)
> - 工程坐标：`top.seamohai:beckend-learning:0.0.1-SNAPSHOT`
> - 环境：JDK **21** ｜ Spring Boot **4.1.1** ｜ Tomcat **11.0.24** ｜ jakarta.validation-api **3.1.1** ｜ 端口 **8004**
> - 本篇是**自包含学习笔记**：所有概念都在文中解释清楚，不需要另查任何资料
> - 和 01 那篇不一样的地方：**本文的接口返回、启动日志、报错信息全部是实测结果**，不是按代码推导的

---

## 目录

1. [本节目标](#1-本节目标)
2. [背景：Controller 直接返回实体会长出什么问题](#2-背景controller-直接返回实体会长出什么问题)
3. [统一响应体：`Result` 与 `ErrorCode`](#3-统一响应体result-与-errorcode)
4. [分页封装：`PageResult`](#4-分页封装pageresult)
5. [业务异常：`BusinessException`](#5-业务异常businessexception)
6. [全局异常处理：`@RestControllerAdvice`](#6-全局异常处理restcontrolleradvice)
7. [参数校验：`@Valid` 与 JSR-303 注解](#7-参数校验valid-与-jsr-303-注解)
8. [过滤器与拦截器（本模块的三个空类）](#8-过滤器与拦截器本模块的三个空类)
9. [接口与实测结果（Windows 实操）](#9-接口与实测结果windows-实操)
10. [我踩过的 6 个坑](#10-我踩过的-6-个坑)
11. [报错速查表](#11-报错速查表)
12. [练习](#12-练习)
13. [知识地图与名词速查](#13-知识地图与名词速查)
14. [内容小结与学习小结](#14-内容小结与学习小结)
15. [还没搞懂的问题](#15-还没搞懂的问题)

---

## 1. 本节目标

学完这一节，要能独立做到五件事：

1. 用 `Result<T>` + `ErrorCode` 把**所有接口的返回结构统一**；
2. 用 `PageResult<T>` 封装**分页结果**，让前端拿到的结构一致；
3. 用 `BusinessException` + `@RestControllerAdvice` 做**全局异常处理**，把异常翻译成统一响应体；
4. 用 `@Valid` + JSR-303 注解做**参数校验**，并知道它**为什么会静默失效**；
5. 说清**过滤器（Filter）和拦截器（Interceptor）**的区别和适用位置。

一句话概括这五件事的分工：

> **`Result` 规定"回什么形状" → `PageResult` 规定"列表怎么回" → `BusinessException` 标记"这是业务上的失败" → `@RestControllerAdvice` 负责"接住异常并翻译成 Result" → `@Valid` 在**进入方法体之前**拦住不合法的参数。**

五者是配合关系：本模块里 `UserService` 遇到"查不到"就抛 `BusinessException`（不自己拼错误响应），由 `GlobalExceptionHandler` 统一翻译；`UserController` 只负责声明 `@Valid`，不合法的参数**根本进不到方法体**（前提是校验真的生效，见第 7 节）。

---

## 2. 背景：Controller 直接返回实体会长出什么问题

### 2.1 01 那篇的写法，接口一多就乱了

```java
// 反面教材：直接返回实体
@GetMapping("/info")
public User getUserInfo() {
    return new User(1001L, "张三", LocalDate.of(2004, 10, 24));
}
```

单个接口没问题。但接口一多：

| 麻烦事 | 具体表现 |
| --- | --- |
| 返回结构不统一 | 有的返回 `User`、有的返回 `List`、有的返回 `String`，前端要写 N 套解析 |
| 出错时结构突变 | 成功返回 `User`，失败返回 Spring 默认的 `{timestamp,status,error,path}`，前端得判断两种形状 |
| 错误码没地方放 | 想返回"业务错误码 30001"，得自己往实体里塞字段 |
| 异常处理到处写 | 每个方法都 `try-catch`，或者干脆不接，让异常直接冒到客户端 |
| 敏感信息外泄 | 异常堆栈直接返回给客户端，暴露包名、类名、内部实现 |

第 5 条不是假想。**03 那篇实测的 404 就是活证据**：没有全局异常处理时，Spring Boot 默认的 `/error` 会把完整堆栈塞进响应的 `trace` 字段。

### 2.2 本模块要解决的就是这五条

| 麻烦 | 本模块怎么解决 | 对应类 |
| --- | --- | --- |
| 返回结构不统一 | 所有接口返回 `Result<T>` | `Result` |
| 出错时结构突变 | 异常也翻译成 `Result`，形状不变 | `GlobalExceptionHandler` |
| 错误码没地方放 | 错误码抽成枚举 | `ErrorCode` |
| 异常处理到处写 | 集中在一个类里声明 | `@RestControllerAdvice` |
| 敏感信息外泄 | `handleOther` 只回"系统繁忙"，堆栈只进日志 | `GlobalExceptionHandler.handleOther` |

第三条是本节最"工程化"的一点：**接口的形状是契约，契约不该因为成功还是失败而改变。**

---

## 3. 统一响应体：`Result` 与 `ErrorCode`

### 3.1 错误码抽成枚举

```java
@Getter
public enum ErrorCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后再试");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

两个设计要点：

- **`code` 和 `message` 绑定在一起**。调用方只需要 `ErrorCode.NOT_FOUND`，不用同时记住数字和文案，也就不会出现"码是 404、文案写成了参数错误"这种错配；
- **`@Getter` 由 Lombok 生成 `getCode()` / `getMessage()`**，枚举字段是 `final`，编译期就能确定。

> **注意 `SUCCESS` 用的是 `200`**，和 HTTP 状态码数字一样。这埋了一个认知坑：**响应体里的 `code` 和 HTTP 状态码是两回事**（见第 6.4 节，本模块的实测结果是"HTTP 状态码永远是 200，业务码在响应体里"）。

### 3.2 统一响应体

```java
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
```

逐点拆开：

| 写法 | 为什么这么写 |
| --- | --- |
| **构造器是 `private`** | 强制走静态工厂，避免 `new Result<>(...)` 到处传魔法数字 |
| **`ok()` 委托给 `ok(null)`** | 无返回值也返回 `Result<Void>`，形状统一，前端不用特判 |
| **`fail(ErrorCode)`** | 常规失败：码和文案都来自枚举 |
| **`fail(code, message)`** | 特例失败：校验失败要带上**具体的字段错误文案**，不能笼统写"参数错误" |
| **泛型 `<T>`** | `Result<User>`、`Result<PageResult<User>>`、`Result<Void>` 共用一个类 |

### 3.3 实测：成功响应的形状

**实测输出**：

```
GET /users/4
{"code":200,"data":{"address":"江苏南京","age":20,"birthday":"2026-10-09","email":"z@163.com","id":4,"username":"zhangsan"},"message":"success"}
```

三个字段 `code` / `message` / `data` 都在，业务数据在 `data` 里。前端只需要读一次 `code` 就知道成败。

### 3.4 实测：失败响应也是同一个形状

```
GET /users/99999
{"code":404,"data":null,"message":"资源不存在"}
```

**`data` 是 `null`，但字段还在。** 这就是"契约不因成败改变"的直接体现——前端可以无条件写 `if (res.code === 200) { use(res.data) } else { toast(res.message) }`。

---

## 4. 分页封装：`PageResult`

### 4.1 为什么要单独一个类

分页返回的不只是"一页数据"，还需要总数（前端要算总页数）、当前页、每页条数。直接返回 `List<User>` 就丢了后三个信息。

```java
@Data
@AllArgsConstructor
public class PageResult<T> {

    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;

    public static <T> PageResult<T> of(List<T> list, long total, int pageNum, int pageSize) {
        return new PageResult<>(list, total, pageNum, pageSize);
    }
}
```

| 字段 | 含义 | 为什么类型是它 |
| --- | --- | --- |
| `list` | 当前页数据 | 用 `List` 而不是数组，方便 JSON 序列化 |
| `total` | **总条数**（不是总页数） | 用 `long`，条数可能超过 `int` 上限 |
| `pageNum` | 当前第几页（从 **1** 开始） | 用 `int` 够用 |
| `pageSize` | 每页几条 | 用 `int` 够用 |

> **注意 `pageNum` 从 1 开始**，不是 0。这是 Web 分页的常见约定，但和很多数据库的 `LIMIT offset`（从 0 开始）不一样，换算时要 `offset = (pageNum - 1) * pageSize`。本模块的 `UserService.page()` 就是这么算的。

### 4.2 `of` 静态工厂和 `@AllArgsConstructor` 的关系

`@AllArgsConstructor` 已经生成了四参数的构造器，那 `of` 是不是多余？不是：

- **构造器参数是全字段顺序敏感的**，四个参数里 `total` 和 `pageNum` 都是数字，写反了编译器不报错；
- **`of` 有语义**，读代码时 `PageResult.of(list, total, pageNum, pageSize)` 一眼能看出是在构造分页结果。

这是 02 那篇里"能编译 ≠ 能运行"的同类问题：**类型相同的一串参数，顺序错误编译器管不了。**

### 4.3 实测：分页的三种边界情况

**实测输出**：

```
GET /users?pageNum=1&pageSize=2      ->  list 有 2 条，total=6，pageNum=1，pageSize=2
GET /users?pageNum=1&pageSize=100    ->  list 有 6 条（全部），total=6，pageSize=100
GET /users?pageNum=99&pageSize=10    ->  list 是空数组 []，total=6，pageNum=99
```

逐行读一遍，这段输出其实信息量很大：

| 情况 | 表现 | 说明 |
| --- | --- | --- |
| `pageSize` 超过总数 | 返回全部，**不报错** | 服务端不纠正 `pageSize`，原样回显 |
| `pageNum` 越界 | `list` 为空数组，`total` 仍是 6 | **不抛异常**，是"空页"而不是"错误" |
| `total` 始终是总数 | 不随 `pageNum` 变化 | 前端靠它算总页数 |

第三种的实测响应体：

```
{"code":200,"data":{"list":[],"total":6,"pageNum":99,"pageSize":10},"message":"success"}
```

**越过最后一页返回空列表而不是报错，是合理的**：前端翻页翻过头，看到空列表自然就停住了；如果抛异常反而要额外处理。

---

## 5. 业务异常：`BusinessException`

### 5.1 为什么不直接返回 `Result.fail()`

在 `UserService` 里完全可以写成：

```java
public User getById(Long id) {
    User user = store.get(id);
    if (user == null) {
        return null;      // 然后 controller 判空再返回 Result.fail(...)
    }
    return user;
}
```

但这种写法有三个问题：

| 问题 | 说明 |
| --- | --- |
| Service 被迫知道"HTTP 响应" | 服务层本该只关心业务，`Result` 是 Web 层的概念 |
| 判空代码到处扩散 | 每个调用点都要写 `if (x == null) return Result.fail(...)` |
| 深层调用无法中断 | 如果错误发生在三层调用之下，返回值要一层层往上传，很容易漏判 |

抛异常解决了这三条：**Service 只管抛，谁翻译成响应体是 Web 层的事。**

### 5.2 代码

```java
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

两个关键决策：

| 决策 | 原因 |
| --- | --- |
| **继承 `RuntimeException` 而不是 `Exception`** | 受检异常会强迫每一层 `throws`，污染方法签名；业务异常就该是"能不管就不管、由最外层统一接" |
| **`super(errorCode.getMessage())`** | 异常自己的 `getMessage()` 也带上文案，日志里直接能看到"资源不存在"，不用再去查枚举 |

### 5.3 实测：抛出的异常是怎么变成响应的

```java
public User getById(Long id) {
    User user = store.get(id);
    if (user == null) {
        throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    return user;
}
```

**实测输出**：

```
GET /users/99999
{"code":404,"data":null,"message":"资源不存在"}
```

**同时日志里有两条记录**：

```
22:30:35 [http-nio-8004-exec-4] WARN  t.s.w.common.GlobalExceptionHandler [] - 业务异常：资源不存在
22:30:35 [http-nio-8004-exec-4] WARN  o.s.w.s.m.m.a.ExceptionHandlerExceptionResolver [] - Resolved [top.seamohai.web.common.BusinessException: 资源不存在]
```

**第一条是我自己打的，第二条是 Spring 打的。** 这是本模块的一个副作用（见 10.5）：`@ExceptionHandler` 处理完之后，`ExceptionHandlerExceptionResolver` 会以 `WARN` 级别再记录一条 `Resolved [...]`。所以**同一次异常会在日志里出现两次**。

---

## 6. 全局异常处理：`@RestControllerAdvice`

### 6.1 这个注解是什么

`@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody`。

| 注解 | 作用 |
| --- | --- |
| `@ControllerAdvice` | 声明一个"切面"，它的 `@ExceptionHandler` 对**所有 Controller** 生效 |
| `@ResponseBody` | 方法的返回值直接写进响应体（而不是当成视图名） |
| 合起来 `@RestControllerAdvice` | 处理结果直接序列化成 JSON |

**关键点：没有它，`@ExceptionHandler` 只在声明它的那个 Controller 内部生效。** 加了它才是全局的。

### 6.2 本模块处理了哪六类异常

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)          // 1. 业务异常
    @ExceptionHandler(MethodArgumentNotValidException.class)  // 2. 参数校验失败
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)  // 3. 参数类型不匹配
    @ExceptionHandler(HttpMessageNotReadableException.class)      // 4. 请求体读不出来
    @ExceptionHandler(NoResourceFoundException.class)             // 5. 路径不存在
    @ExceptionHandler(Exception.class)                            // 6. 兜底
}
```

逐个说明：

| # | 异常 | 什么时候抛 | 本模块返回 |
| --- | --- | --- | --- |
| 1 | `BusinessException` | 我自己的业务判断 | `Result.fail(e.getErrorCode())` |
| 2 | `MethodArgumentNotValidException` | `@Valid` 校验不通过 | `400` + **具体字段错误文案** |
| 3 | `MethodArgumentTypeMismatchException` | 路径/参数类型转不过去（如 `/users/abc`） | `400` + `参数类型错误` |
| 4 | `HttpMessageNotReadableException` | 请求体不是合法 JSON | `400` + `请求体格式错误` |
| 5 | `NoResourceFoundException` | URL 没有对应的 handler | `Result.fail(ErrorCode.NOT_FOUND)` |
| 6 | `Exception` | 其他一切没接住的 | `500` + `系统繁忙，请稍后再试` |

### 6.3 两个值得单独看的处理

**（1）校验失败要挑出"第一条具体错误"**

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public Result<Void> handleValid(MethodArgumentNotValidException e) {
    String msg = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(DefaultMessageSourceResolvable::getDefaultMessage)
            .orElse("参数校验失败");
    return Result.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
}
```

为什么不直接返回 `参数错误`？因为**"哪个字段错了"才是调用方最需要的信息**。这里用 `findFirst()` 只取第一条，`orElse` 兜住"没有字段错误"的极端情况。

> ⚠️ **注意这段代码在本模块实测中一次都没执行过**——因为校验根本没生效（见 7.4）。它是"写对了但没跑起来"的代码。

**（2）兜底处理只回一句话，堆栈只进日志**

```java
@ExceptionHandler(Exception.class)
public Result<Void> handleOther(Exception e) {
    log.error("系统异常", e);
    return Result.fail(ErrorCode.INTERNAL_ERROR);
}
```

`log.error("系统异常", e)` 把**完整堆栈写进日志**，响应里只给 `系统繁忙，请稍后再试`。这是正确做法：**堆栈是给开发者看的，不该给调用方看**（03 那篇实测的 404 正好是反面教材）。

### 6.4 实测：全局异常处理不会改 HTTP 状态码

这是本节最重要的一条实测结论。

**实测输出**：

```
GET /users/99999
HTTP/1.1 200
{"code":404,"data":null,"message":"资源不存在"}

GET /users/abc
{"code":400,"data":null,"message":"参数类型错误"}

POST /users  请求体 {"username":
{"code":400,"data":null,"message":"请求体格式错误"}
```

**三次"失败"，HTTP 状态码全是 200，业务码才在响应体里。**

逐行读一遍，这段输出其实信息量很大：

| 观察 | 含义 |
| --- | --- |
| HTTP 行是 `HTTP/1.1 200` | 方法正常返回了 `Result` 对象，Spring 认为"处理成功" |
| 响应体里 `code` 是 `404` / `400` | 业务码是我自己在 `Result` 里写的 |
| **两者没有任何联动** | 改 `Result.code` 不会影响 HTTP 状态码 |

**原因**：`@ExceptionHandler` 方法的返回类型是 `Result<Void>`，而且没有加 `@ResponseStatus` 或返回 `ResponseEntity`。Spring 就按"方法正常返回"处理，HTTP 状态码默认 200。

**想让它返回真实的 HTTP 状态码**，有两种改法：

```java
// 改法一：方法上标 @ResponseStatus
@ExceptionHandler(BusinessException.class)
@ResponseStatus(HttpStatus.NOT_FOUND)
public Result<Void> handleBusiness(BusinessException e) { ... }

// 改法二：返回 ResponseEntity，把状态码和响应体一起给
@ExceptionHandler(BusinessException.class)
public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(e.getErrorCode()));
}
```

> **这算 bug 吗？** 不算，但**是个需要明确决定的取舍**。
>
> - **全部返回 200 的理由**：前端只看响应体里的 `code`，HTTP 层不参与业务判断；网关、CDN、监控不会把业务失败误判成"服务不可用"；
> - **返回真实状态码的理由**：符合 HTTP 语义，浏览器、压测工具、APM 能直接识别；对第三方调用方更友好。
>
> **两种都能用，但不能"没想清楚就这样了"。** 本模块属于后者，记在 14.4。

---

## 7. 参数校验：`@Valid` 与 JSR-303 注解

### 7.1 DTO 上的校验注解

```java
@Data
public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @Min(value = 1, message = "年龄不能小于 1")
    @Max(value = 150, message = "年龄不能大于 150")
    private Integer age;

    @NotBlank(message = "邮箱不能为空")
    private String email;
}
```

**为什么用 DTO 而不是直接在实体 `User` 上校验**：`User` 是"存储模型"（有 `id`、`birthday`、`address`），`UserCreateRequest` 是"入参契约"（只有调用方能传的三个字段）。分开之后，**改存储字段不会意外改动对外的入参契约**。

实测的证据很直观：`UserCreateRequest` 里没有 `id`，但创建出来的用户 `id` 是服务端生成的。

### 7.2 常用校验注解

| 注解 | 作用 | 对 `null` 的处理 |
| --- | --- | --- |
| `@NotNull` | 不能是 `null` | 拦 |
| `@NotEmpty` | 不能是 `null`，且长度/集合不能为 0 | 拦 |
| `@NotBlank` | **字符串**不能是 `null`、空串、纯空白 | 拦 |
| `@Min` / `@Max` | 数值上下界 | **放行 `null`** |
| `@Size(min,max)` | 长度/集合大小范围 | 放行 `null` |
| `@Email` | 邮箱格式 | 放行 `null` |

> ⚠️ **`@Min` 这类"范围"注解不拦 `null`**。如果字段既要范围又要必填，得同时加 `@NotNull`。本模块 `UserCreateRequest.age` 只加了 `@Min`/`@Max` 没加 `@NotNull`，所以**不传 `age` 是合法的**——实测 `POST {}` 创建出来的用户 `age` 就是 `null`。

### 7.3 控制器上必须加 `@Valid`

```java
@PostMapping
public Result<User> create(@Valid @RequestBody UserCreateRequest request) {
    return Result.ok(userService.create(request));
}
```

**`@Valid` 加在参数上**，作用是在**进入方法体之前**触发校验。校验不通过就抛 `MethodArgumentNotValidException`，被第 6 节的 `handleValid` 接住。

- 写在参数上：`@Valid @RequestBody XxxRequest req`
- 校验**嵌套对象**时，被嵌套的字段上还要再加一个 `@Valid`（级联），否则嵌套对象里的注解不生效（02 那篇踩过这个坑）。

### 7.4 实测：本模块的校验静默失效了

这是本节最需要记住的一条。

**实测启动日志**：

```
2026-10-09 22:29:09:706 [restartedMain] INFO  o.s.v.b.OptionalValidatorFactoryBean [] - Failed to set up a Bean Validation provider: jakarta.validation.NoProviderFoundException: Unable to create a Configuration, because no Jakarta Validation provider could be found. Add a provider like Hibernate Validator (RI) to your classpath.
```

**应用照常启动成功**（这行是 `INFO` 级，不是 `ERROR`，也没有中断启动）：

```
2026-10-09 22:29:09:981 [restartedMain] INFO  top.seamohai.web.WebApplication [] - Started WebApplication in 2.382 seconds (process running for 2.86)
```

**实测接口行为**：

```
POST /users   {"username":"","age":999,"email":""}
HTTP/1.1 200
{"code":200,"data":{"address":"江苏南京","age":999,"birthday":"2026-10-09","email":"","id":5,"username":""},"message":"success"}
```

**三处约束全违反（`username` 空、`age=999` 超上限、`email` 空），却返回 `code:200`，而且把非法数据真的存进去了。** 紧接着查列表能查到这条脏数据。

再测彻底空的情况：

```
POST /users   {}
HTTP/1.1 200
{"code":200,"data":{"address":"江苏南京","age":null,"birthday":"2026-10-09","email":null,"id":6,"username":null},"message":"success"}
```

**原因**：`04-web/pom.xml` 里只引了**校验注解的接口包**，没有引**校验实现**：

```xml
<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
</dependency>
```

对比 03-logging 的依赖树（`mvn dependency:tree` 实测）：

```
03-logging:
+- org.springframework.boot:spring-boot-starter-validation:jar:4.1.1:compile
|  \- org.springframework.boot:spring-boot-validation:jar:4.1.1:compile
|     \- org.hibernate.validator:hibernate-validator:jar:9.1.3.Final:compile
|        +- jakarta.validation:jakarta.validation-api:jar:3.1.1:compile

04-web:
+- jakarta.validation:jakarta.validation-api:jar:3.1.1:compile     ← 只有接口，没有实现
```

| 依赖 | 提供了什么 | 够不够 |
| --- | --- | --- |
| `jakarta.validation-api` | `@NotBlank`、`@Min` 这些**注解的定义** | 只能编译通过 |
| `spring-boot-starter-validation` | 注解 + **Hibernate Validator 实现** | 校验才真的会执行 |

**解决**：把 `04-web/pom.xml` 里那条换成（或加上）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

**教训**：**"能编译"和"能执行"之间隔着一整层实现。** `@NotBlank` 是注解，注解本身没有行为；真正读注解、执行判断的是 Hibernate Validator。少了它，`@Valid` 就是个没有任何效果的装饰。

> **为什么这个坑特别危险**：它**不报错**。应用启动成功、接口返回 200、日志里只有一行被淹没在启动刷屏中的 `INFO`。生产环境的直接后果是**脏数据入库**。这比一个启动就崩的错误危险得多——**崩溃会立刻被发现，静默失效不会。**

---

## 8. 过滤器与拦截器（本模块的三个空类）

### 8.1 现状：三个空类

本模块有三个"定义了但没实现"的类：

```java
public class RequestTimeFilter {
}
```

```java
public class Loginterceptor {
}
```

```java
public class WebConfig {
}
```

它们**都是空的，也没有被任何地方引用**，所以目前完全不起作用。但类名表达了意图（记录请求耗时、记录日志、注册组件），值得说清它们**本该**落在哪一层。

### 8.2 Filter 和 Interceptor 的区别

| 维度 | Filter（过滤器） | Interceptor（拦截器） |
| --- | --- | --- |
| 规范来源 | **Servlet 规范**（`jakarta.servlet.Filter`） | **Spring MVC**（`HandlerInterceptor`） |
| 运行位置 | 进入 Servlet **之前**、离开之后 | 进入具体 handler **前后** |
| 归属容器 | Servlet 容器（Tomcat） | Spring 的 `DispatcherServlet` |
| 能不能拿到 handler | **拿不到**（还不知道要执行哪个方法） | **能拿到**（`preHandle` 的参数里有） |
| 能不能用 Spring 的 Bean | 注册成 Bean 后可以 | **可以，天然是 Bean** |
| 典型用途 | 编码、CORS、请求体包装、全局耗时 | 鉴权、traceId、日志、参数预处理 |
| 注册方式 | `@Component` / `FilterRegistrationBean` | `@Component` + `WebMvcConfigurer.addInterceptors` |

一句话选型：**还没进 Spring MVC 的活儿（编码、包装请求体）用 Filter；已经进了 MVC、需要知道"要调哪个方法"的活儿（鉴权、traceId）用 Interceptor。**

### 8.3 如果要实现，长什么样

```java
// Filter：记请求耗时（注意：拿不到 handler）
public class RequestTimeFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, resp);          // 放行，别忘
        } finally {
            log.info("耗时 {} ms", System.currentTimeMillis() - start);
        }
    }
}
```

```java
// Interceptor：记请求日志
public class LogInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        return true;                            // 返回 false 就中断，不再往下走
    }
}
```

```java
// 注册拦截器
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final LogInterceptor logInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(logInterceptor).addPathPatterns("/**");
    }
}
```

> ⚠️ **`doFilter` 里必须调 `chain.doFilter()`**，否则请求会被**静默吞掉**——客户端一直等到超时，服务端一条日志都没有。这是 Filter 最常见的低级错误。
>
> ⚠️ **`preHandle` 返回 `false` 会中断请求**，且**不会**触发 `afterCompletion`。要返回 `false` 就得自己保证资源已清理。

### 8.4 三个类名的问题

| 类 | 问题 |
| --- | --- |
| `Loginterceptor` | 命名不规范，应该是 `LogInterceptor`（每个单词首字母大写） |
| `Loginterceptor` | 放在 `common` 包，但按职责该放 `config` 或新建 `interceptor` 包 |
| `WebConfig` | 和 03-logging 的 `WebConfig` 同名，但两个模块互不相干，不冲突；只是**位置不一致**（03 在 `config` 包，04 也在 `config` 包，这个没问题） |

`RequestTimeFilter` 和 `Loginterceptor` 的**职责也有重叠**（都记日志/耗时），实现前应该先决定：**到底用一个 Filter 还是用一个 Interceptor，不要两个都上。**

---

## 9. 接口与实测结果（Windows 实操）

### 9.1 接口清单（本模块一共 5 个）

| 方法 | 路径 | 入参 | 返回 | 对应知识点 |
| --- | --- | --- | --- | --- |
| GET | `/users` | `pageNum`、`pageSize` | `Result<PageResult<User>>` | 分页封装 |
| GET | `/users/{id}` | 路径参数 `id` | `Result<User>` | 统一响应 + 业务异常 |
| POST | `/users` | `UserCreateRequest` | `Result<User>` | `@Valid` 校验 |
| PUT | `/users/{id}` | `UserUpdateRequest` | `Result<User>` | 更新 |
| DELETE | `/users/{id}` | 路径参数 `id` | `Result<Void>` | 无 data 的统一响应 |

### 9.2 启动

```powershell
cd E:\backend-learning\beckend-learning\04-web
mvn spring-boot:run
```

**实测启动日志**（节选）：

```
2026-10-09 22:29:08:201 [restartedMain] INFO  top.seamohai.web.WebApplication [] - Starting WebApplication using Java 21.0.12.1 with PID 96916
2026-10-09 22:29:08:204 [restartedMain] INFO  top.seamohai.web.WebApplication [] - No active profile set, falling back to 1 default profile: "default"
2026-10-09 22:29:09:348 [restartedMain] INFO  o.s.boot.tomcat.TomcatWebServer [] - Tomcat initialized with port 8004 (http)
2026-10-09 22:29:09:523 [restartedMain] INFO  o.a.catalina.core.StandardEngine [] - Starting Servlet engine: [Apache Tomcat/11.0.24]
2026-10-09 22:29:09:706 [restartedMain] INFO  o.s.v.b.OptionalValidatorFactoryBean [] - Failed to set up a Bean Validation provider: jakarta.validation.NoProviderFoundException: ...
2026-10-09 22:29:09:968 [restartedMain] INFO  o.s.boot.tomcat.TomcatWebServer [] - Tomcat started on port 8004 (http) with context path '/'
2026-10-09 22:29:09:981 [restartedMain] INFO  top.seamohai.web.WebApplication [] - Started WebApplication in 2.382 seconds (process running for 2.86)
```

**看到 `Tomcat started on port 8004` 就是成功**，本次启动耗时约 **2.4 秒**。

**注意倒数第三行**（`OptionalValidatorFactoryBean` 那行）——校验器装配失败，但**夹在两组正常日志中间**，启动照常完成。这就是 7.4 那个坑"不容易发现"的原因：它不是错误，只是一条普通的 INFO。

### 9.3 实测结果

```
GET    /users?pageNum=1&pageSize=2   ->  {"code":200,"data":{"list":[...2条],"total":6,"pageNum":1,"pageSize":2},"message":"success"}
GET    /users/4                      ->  {"code":200,"data":{...},"message":"success"}
GET    /users/99999                  ->  {"code":404,"data":null,"message":"资源不存在"}
GET    /users/abc                    ->  {"code":400,"data":null,"message":"参数类型错误"}
POST   /users  (合法)                 ->  {"code":200,"data":{"id":4,...},"message":"success"}
POST   /users  (三处约束全违反)        ->  {"code":200,"data":{"id":5,"username":"","age":999,...},"message":"success"}   ← 校验没生效
POST   /users  (畸形 JSON)            ->  {"code":400,"data":null,"message":"请求体格式错误"}
PUT    /users/4                      ->  {"code":200,"data":{"username":"zhangsan-改","age":20,...},"message":"success"}
DELETE /users/6                      ->  {"code":200,"data":null,"message":"success"}
DELETE /users/99999                  ->  {"code":404,"data":null,"message":"资源不存在"}
```

两个观察：

1. **端口号是从 `application.yml` 读的 8004**。但文件里 `spring.application.name` 写成了 `spring.app;ication.name`（**分号，不是点**），这个 key 根本不存在（见 10.6）——启动日志里也没给任何提示，是静默失效；
2. **`PUT` 的 `age` 没有变化**。请求体里 `age` 传的是 `88`，返回的 `age` 仍是 `20`（见 10.4）。

### 9.4 演示数据长什么样

创建出来的用户有四个字段是**服务端硬编码**的：

```java
user.setAddress("江苏南京");
user.setBirthday(LocalDate.now());
```

实测返回的 `birthday` 是 `"2026-10-09"`，正好是运行当天。**这说明 `LocalDate.now()` 在创建时求值**，不是固定的。

> **演示数据要注意自洽**。现在"地址江苏南京"是硬编码的，而 `birthday` 是"今天"——**一个刚出生的人有 20 岁和 999 岁的 `age`**，数据本身是矛盾的。做演示时最好把 `age` 和 `birthday` 调成能对上的值，否则会被问到。

### 9.5 查端口、结束进程

```powershell
netstat -ano | findstr :8004
taskkill /PID <pid> /F
```

### 9.6 一个关于测试方法本身的坑

用 PowerShell 的 `curl.exe` 发 JSON 请求时，**引号会被 PowerShell 先吃掉一层**：

```
curl: option -d: requires parameter
```

**解决**：把请求体写进文件，用 `--data-binary "@文件路径"`：

```powershell
$json = '{"username":"zhangsan","age":20,"email":"z@163.com"}'
[System.IO.File]::WriteAllText("body.json", $json, (New-Object System.Text.UTF8Encoding($false)))
curl.exe -X POST http://localhost:8004/users -H "Content-Type: application/json" --data-binary "@body.json"
```

**为什么用 `UTF8Encoding($false)`**：`$false` 表示**不写 BOM**。带 BOM 的 JSON 文件会被 Jackson 当成"开头有非法字符"，报解析失败。

---

## 10. 我踩过的 6 个坑

### 坑 1：以为引了 `validation` 就会校验（最坑的一个）

我在 `pom.xml` 里加了：

```xml
<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
</dependency>
```

然后 `@NotBlank`、`@Min`、`@Max` 全都写了，以为就完事了。

**现象**：启动成功、接口 200、什么报错都没有。直到我把 `age` 故意传成 `999`、`username` 传空串，**照样创建成功**。

**实测报错**（其实早就打了，只是我没看见）：

```
INFO  o.s.v.b.OptionalValidatorFactoryBean [] - Failed to set up a Bean Validation provider: jakarta.validation.NoProviderFoundException: Unable to create a Configuration, because no Jakarta Validation provider could be found. Add a provider like Hibernate Validator (RI) to your classpath.
```

**原因**：`jakarta.validation-api` 只有**注解的定义**，没有**执行校验的实现**。真正的实现是 Hibernate Validator，它由 `spring-boot-starter-validation` 带进来。

**解决**：换成 `spring-boot-starter-validation`。

**教训**：**注解只是"标记"，标记本身不产生行为。** 看到 `@NotBlank` 生效，靠的是有一个库去读它。排查"注解没反应"时，第一件事是查**实现库在不在 classpath 上**（用 `mvn dependency:tree`）。

### 坑 2：校验不生效时，不报错也不崩溃

承坑 1。为什么这个坑我拖了很久才发现？

| 表现 | 为什么骗过了我 |
| --- | --- |
| 启动成功 | 校验器装配失败只是 `INFO` |
| 编译通过 | 注解是编译期依赖，`jakarta.validation-api` 够了 |
| 单元测试也不报错 | 没有对"非法入参"写测试 |
| 合法请求正常 | 我一开始只测了合法数据 |

**教训**：**只测"正确路径"的测试等于没测。** 校验这类功能，必须用**故意的非法输入**去验证；测合法输入永远测不出校验有没有生效。这也是 7.4 那条"静默失效比崩溃危险"的来由。

### 坑 3：`application.yml` 里把点写成了分号

```yaml
spring:
  app;ication:          # ← 这里应该是 application
    name: 04-Web 开发进阶
```

**现象**：`spring.application.name` 取不到值，但**启动日志里一条警告都没有**。

**原因**：YAML 把 `app;ication` 当成一个普通的 key 名。Spring 不认识它，按"多余的配置项"忽略（对应 02 那篇的 `ignoreUnknownFields` 默认 `true`）。

**解决**：改成 `application`。

**教训**：**YAML 的 key 是字符串，拼错了编译器管不了、运行时也可能不报错。** 这类"看起来像标识符"的字符串（yml 的 key、`@Value` 里的占位符、`@Profile` 的值、`@RequestMapping` 的路径）都是静默失效的高发区。**改完要验证"值真的读到了"，而不是"文件里写了"。**

### 坑 4：`update` 里少改了一个字段

```java
public User update(Long id, UserUpdateRequest request) {
    User user = getById(id);
    user.setUsername(request.getUsername());
    //user.setAge(request.getAge());       // ← 被注释掉了
    user.setEmail(request.getEmail());
    return user;
}
```

**实测**：`PUT /users/4` 传 `{"username":"zhangsan-改","age":88,"email":"new@163.com"}`，返回的 `age` 仍然是 `20`。

**为什么危险**：接口**返回 200、返回 `success`，看起来完全成功**，但有一个字段没生效。这类"部分成功"的 bug 比直接报错难查得多——调用方以为改成功了。

**教训**：**注释掉的代码是"故意的缺席"，必须给个说法。** 要么删掉（配合 DTO 里也去掉该字段），要么写上为什么暂时不启用。留一行裸注释，过两周自己也说不清是有意还是忘了。

### 坑 5：同一次异常在日志里出现两次

**实测输出**：

```
22:30:35 [http-nio-8004-exec-4] WARN  t.s.w.common.GlobalExceptionHandler [] - 业务异常：资源不存在
22:30:35 [http-nio-8004-exec-4] WARN  o.s.w.s.m.m.a.ExceptionHandlerExceptionResolver [] - Resolved [top.seamohai.web.common.BusinessException: 资源不存在]
```

**原因**：我自己在 `handleBusiness` 里 `log.warn` 了一条，Spring 的 `ExceptionHandlerExceptionResolver` 处理完又以 `WARN` 记了一条 `Resolved [...]`。

**这算问题吗**：算"日志噪音"。排查时同一次异常出现两条，容易误判成"发生了两次错误"。

**解决思路**：要么接受（Spring 那条信息其实更完整，带了异常全名），要么把自己的 `log.warn` 降级/去掉，避免重复。

**教训**：**框架自己会记日志的地方，就别再手工记一遍。** 想知道框架记了什么，先跑一遍看日志。

### 坑 6：响应体里的 `code` 和 HTTP 状态码不是一回事（认知坑）

我一开始以为 `Result.fail(ErrorCode.NOT_FOUND)` 会返回 HTTP 404。

**实测**：`GET /users/99999` 的响应行是 **`HTTP/1.1 200`**，`code` 是 `404`。所有失败场景（业务异常、类型错误、请求体错误）**HTTP 状态码都是 200**。

**原因**：`@ExceptionHandler` 方法返回的是 `Result<Void>` 对象，不是 `ResponseEntity`，也没有 `@ResponseStatus`，Spring 就按"方法正常返回"处理。

**解决**：要么加 `@ResponseStatus` / 返回 `ResponseEntity`，要么**明确接受"HTTP 层永远 200、业务码在响应体"这个约定**，并写进接口文档让调用方知道。

**教训**：**"响应体里的字段"和"HTTP 协议层的字段"是两个不同的东西。** 名字起得像（都叫 `code`）更容易混淆，写文档时要写清"以哪个为准"。

---

## 11. 报错速查表

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| **加了 `@Valid` 但非法参数照样通过** | 只引了 `jakarta.validation-api`，没有校验实现 | 换成 `spring-boot-starter-validation` |
| `NoProviderFoundException: Unable to create a Configuration, because no Jakarta Validation provider could be found` | 同上 | 同上（`mvn dependency:tree` 确认 `hibernate-validator` 在不在） |
| `NoResourceFoundException` 没被自定义 handler 接住 | 没加 `@RestControllerAdvice`，或异常类型写错 | 加 `@RestControllerAdvice`；确认 `@ExceptionHandler` 里的类型 |
| `@ExceptionHandler` 只在某个 Controller 内生效 | 用了 `@ControllerAdvice` 但少了 `@ResponseBody` | 用 `@RestControllerAdvice`，或补 `@ResponseBody` |
| 失败时 HTTP 状态码仍是 200 | `@ExceptionHandler` 返回普通对象，没有 `@ResponseStatus` | 加 `@ResponseStatus`，或返回 `ResponseEntity` |
| 中文错误文案变乱码 | 响应 `Content-Type` 没带 charset | 用 Apifox / 浏览器看；或配 `spring.http.encoding` |
| `HttpMessageNotReadableException` 频繁出现 | 请求体不是合法 JSON，或**文件带了 BOM** | 检查 JSON；写文件时用 `UTF8Encoding($false)` 不写 BOM |
| `MethodArgumentTypeMismatchException` | 路径参数类型转不过去（`/users/abc`） | 属正常防护；给调用方返回明确的"参数类型错误" |
| 请求报 404 但被自己的 handler 接住变成了 `code:404` | 这就是 `NoResourceFoundException` 的处理结果 | 正常；注意 HTTP 状态码仍是 200 |
| yml 里配置项拼错了（`app;ication`）但没有任何提示 | YAML key 是字符串，未知 key 被静默忽略 | 改对；并**验证值真的被读到了** |
| `spring.application.name` 取不到 | 同上 | 同上 |
| `NoUniqueBeanDefinitionException` | 同一接口有多个实现被注册 | 检查 `@Profile` / `@Primary` / `@Qualifier` |
| 更新接口返回成功但某字段没变 | 对应的 setter 被注释掉了 | 检查 Service 里的赋值语句 |
| 同一次异常在日志里出现两条 | 自己 `log` 了一条，框架又 `Resolved` 了一条 | 接受，或去掉自己的那条 |
| `curl: option -d: requires parameter` | PowerShell 把 JSON 里的引号吃掉了 | 请求体写进文件，用 `--data-binary "@文件"` |
| `Port 8004 was already in use` | 上次的应用没关 | `netstat -ano \| findstr :8004` → `taskkill /PID <pid> /F` |
| `@Min` 校验不住 `null` | `@Min`/`@Max`/`@Size` **不拦 `null`** | 需要必填就再加 `@NotNull` |

---

## 12. 练习

### 12.1 随堂练习（约 10 分钟）

| # | 练习 | 验证点 | 状态 |
| --- | --- | --- | --- |
| 1 | 把 `jakarta.validation-api` 换成 `spring-boot-starter-validation` 后重启 | `NoProviderFoundException` 消失 | [ ] |
| 2 | 再发一次 `{"username":"","age":999,"email":""}` | 返回 `code:400` 和**具体字段文案**，如"用户名不能为空" | [ ] |
| 3 | 给 `handleBusiness` 加 `@ResponseStatus(HttpStatus.NOT_FOUND)` | `GET /users/99999` 的 HTTP 行真正变成 404 | [ ] |
| 4 | 把 `age` 的 `//user.setAge(...)` 恢复，再 PUT 一次 | 返回的 `age` 跟着请求变了 | [ ] |
| 5 | 给 `UserCreateRequest.email` 加 `@Email(message = "邮箱格式不正确")`，重启后传 `email: "abc"` | 返回"邮箱格式不正确"，证明格式校验也接上了 | [ ] |
| 6 | 用 `mvn dependency:tree` 分别看 03 和 04 的 validation 依赖 | 03 有 `hibernate-validator`，04 现在只有 `jakarta.validation-api` | [ ] |

> 第 1、2 题必须**连着做**：只换依赖不重测，等于没验证这个坑修好了。

### 12.2 课后练习

- **基础**：给 `ErrorCode` 加两个业务错误码（比如 `ACCOUNT_NOT_FOUND(30001)`、`PASSWORD_ERROR(30002)`），并在 Service 里用上。注意 `ErrorCode` 里注释掉的那三行就是现成的例子。
- **进阶**：把 `RequestTimeFilter` 和 `Loginterceptor` 实现掉（选一个即可），并用实测证明：**Filter 里拿不到 handler，Interceptor 里能拿到**。
- **挑战**：把本模块的 `Result` 换成"HTTP 状态码与业务码一致"的方案（用 `ResponseEntity`），然后回答：**改成一致之后，前端代码是变简单了还是变复杂了？** 说清理由。

### 12.3 关键写法提示

```java
// 统一响应：私有构造 + 静态工厂
@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    private Result(Integer code, String message, T data) { ... }

    public static <T> Result<T> ok() { return ok(null); }
    public static <T> Result<T> ok(T data) { ... }
    public static <T> Result<T> fail(ErrorCode errorCode) { ... }
    public static <T> Result<T> fail(Integer code, String message) { ... }
}

// 业务异常：继承 RuntimeException，别用受检异常
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

// 全局异常处理：@RestControllerAdvice 而不是 @ControllerAdvice
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.fail(e.getErrorCode());
    }

    // 兜底：堆栈进日志，响应只给一句话
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }
}

// 参数校验：DTO 上写注解，参数上加 @Valid
@Data
public class UserCreateRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotNull(message = "年龄不能为空")      // @Min/@Max 不拦 null，要必填得加 @NotNull
    @Min(value = 1, message = "年龄不能小于 1")
    @Max(value = 150, message = "年龄不能大于 150")
    private Integer age;
}

@PostMapping
public Result<User> create(@Valid @RequestBody UserCreateRequest request) { ... }
```

```xml
<!-- 校验要引 starter，不是 api -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

### 12.4 自测：能不能回答这几个问题

1. `Result` 的构造器为什么设成 `private`？`ok()` 为什么要委托给 `ok(null)`？
2. `BusinessException` 为什么继承 `RuntimeException` 而不是 `Exception`？
3. `@RestControllerAdvice` 比 `@ControllerAdvice` 多了什么？少了会怎样？
4. `@ExceptionHandler` 的返回值会不会影响 HTTP 状态码？怎么才能让它影响？
5. `jakarta.validation-api` 和 `spring-boot-starter-validation` 差在哪？为什么缺一个就静默失效？
6. `@NotBlank`、`@NotEmpty`、`@NotNull` 三者的区别？`@Min` 能不能拦 `null`？
7. `PageResult` 里 `total` 为什么用 `long` 而不是 `int`？
8. Filter 和 Interceptor 各自的运行位置在哪？哪个能拿到 handler？
9. `doFilter` 里忘了调 `chain.doFilter()` 会发生什么？
10. `@ExceptionHandler(Exception.class)` 为什么只返回一句话，堆栈去哪了？

---

## 13. 知识地图与名词速查

### 13.1 本节要点回顾

| 概念 | 一句话 |
| --- | --- |
| 统一响应体 `Result<T>` | 所有接口同一个形状：`code` / `message` / `data` |
| `ErrorCode` 枚举 | 错误码与文案绑定，避免"码对文案错" |
| 静态工厂 `ok` / `fail` | 私有构造 + 静态方法，避免到处传魔法数字 |
| `PageResult<T>` | 分页结果：`list` + `total` + `pageNum` + `pageSize` |
| `BusinessException` | 业务失败用异常表达；继承 `RuntimeException` 不污染签名 |
| `@RestControllerAdvice` | `@ControllerAdvice` + `@ResponseBody`，**全局**异常处理 |
| `@ExceptionHandler` | 声明"哪种异常由哪个方法处理" |
| 异常翻译 | 异常 → 统一响应体；堆栈进日志、响应给一句话 |
| `@Valid` | 在**进入方法体之前**触发校验 |
| JSR-303 / Bean Validation | 校验注解的规范；`@NotBlank`、`@Min` 来自它 |
| Hibernate Validator | Bean Validation 的**参考实现**，真正执行校验的库 |
| DTO | 入参契约，和存储实体分开 |
| `@ResponseStatus` | 指定异常处理方法的 HTTP 状态码 |
| `ResponseEntity` | 把状态码 + 响应体 + 响应头一起返回 |
| Filter | Servlet 规范的过滤器，在 MVC 之外，**拿不到 handler** |
| Interceptor | Spring MVC 的拦截器，**能拿到 handler**，是 Bean |

### 13.2 名词速查

| 名词 | 解释 |
| --- | --- |
| **统一响应体** | 所有接口共用的返回结构，让前端解析逻辑只写一次 |
| **错误码（Error Code）** | 标识"失败原因"的数字；**和 HTTP 状态码是两回事** |
| **静态工厂方法** | `static` 的创建方法（`ok`/`fail`），能表达语义、能缓存、能返回子类 |
| **DTO（Data Transfer Object）** | 专门用于传输/入参的对象，与实体分离 |
| **级联校验** | 嵌套对象字段上也要加 `@Valid`，否则内层注解不执行 |
| **`@ControllerAdvice`** | Spring MVC 的全局切面，可统一处理异常、绑定、响应体 |
| **`@ResponseBody`** | 返回值直接写入响应体，不当作视图名解析 |
| **`NoProviderFoundException`** | 找不到 Bean Validation 实现时抛的异常；**只在日志里，不中断启动** |
| **过滤器链（FilterChain）** | 多个 Filter 串联；必须调 `chain.doFilter` 才会继续 |
| **`DispatcherServlet`** | Spring MVC 的前端控制器，Interceptor 就挂在它这一层 |
| **`HandlerInterceptor`** | MVC 拦截器接口，`preHandle` / `postHandle` / `afterCompletion` |
| **`ResponseEntity`** | 带状态码和响应头的返回值封装 |
| **`@ResponseStatus`** | 直接给方法或异常类指定 HTTP 状态码 |
| **兜底处理** | `@ExceptionHandler(Exception.class)`，接住所有没被具体类型匹配的异常 |
| **`ignoreUnknownFields`** | 配置绑定时的行为，默认 `true`；写错的 key 被静默忽略 |
| **静默失效** | 不报错但功能没生效，比直接报错更难发现（校验、配置 key 都属此类） |

---

## 14. 内容小结与学习小结

### 14.1 内容小结

| 案例 | 使用场景 | 本模块的落点 |
| --- | --- | --- |
| 统一响应体 | 所有接口同一形状，前端解析只写一次 | `Result<T>` + `ErrorCode` 枚举 |
| 分页封装 | 列表接口要带总数和页码 | `PageResult<T>`，实测三种边界见 4.3 |
| 业务异常 | Service 层用异常表达失败，不关心 HTTP | `BusinessException` + `UserService.getById` |
| 全局异常处理 | 集中翻译六类异常 | `GlobalExceptionHandler`，见 6.2 |
| 参数校验 | 入参契约与实体分离，进方法体前拦住 | `UserCreateRequest` + `@Valid`（**实测失效**，见 7.4） |
| 过滤器 / 拦截器 | 请求级横切逻辑 | 三个空类，区分见第 8 节 |

### 14.2 学习小结

> 这周的 04-web，本来以为就是"把返回结果包一层"，做完才知道包的这一层里全是坑。
>
> `Result` 和 `ErrorCode`、`PageResult` 这些写起来很快，`@RestControllerAdvice` 也是照着抄的，一开始挺顺利。真正的打击是参数校验——我注解全写上了，`@NotBlank`、`@Min`、`@Max`，启动也正常，我一度以为这块做完了。后来随手把 `age` 传成 999 想试试报错长什么样，结果返回 200，数据还真的存进去了。翻了半天才发现 `pom.xml` 里引的是 `validation-api`，只有注解没有实现。启动日志里其实早就有一行 `NoProviderFoundException`，只是它是 INFO 级，夹在一堆启动日志里被我跳过去了。
>
> 这件事对我震动挺大：**不报错的不等于是对的**。之前 02 那篇踩的坑都会让应用起不来，我能立刻发现；这个是安静的，脏数据直接入库。所以后来我专门补了一条"用非法输入去测"的习惯。
>
> 另一个反直觉的是 HTTP 状态码。我一直以为 `Result.fail(404)` 出去就是 HTTP 404，实测发现响应行永远是 `200`，业务码只在响应体里。查明白之后我反而理解了：**这两套码本来就是分开的**，要不要让它们一致是个设计决定，不是自动的。
>
> 还踩了 yml 里把 `application` 写成 `app;ication` 的坑，一样是不报错就过去了。现在我对"看起来像标识符的字符串"都有点警惕了——yml 的 key、`@Value` 的占位符、`@Profile` 的值，编译器一个都管不了。
>
> 最后 `update` 里那行被注释掉的 `setAge` 也是同类问题：接口返回 success，字段却没改。注释掉的代码比写错的代码更难查，因为它看起来是"故意的"。

### 14.3 作业要求完成度自查

| # | 作业要求 | 状态 | 证据 |
| --- | --- | --- | --- |
| 1 | 统一响应体封装 | ✅ | `Result<T>` + `ErrorCode`；实测见 3.3、3.4 |
| 2 | 分页结果封装 | ✅ | `PageResult<T>`；三种边界实测见 4.3 |
| 3 | 业务异常与全局异常处理 | ✅ | `BusinessException` + `GlobalExceptionHandler`；实测见 5.3、6.4 |
| 4 | 参数校验 | ❌ | 注解已写，但**依赖引错导致静默失效**（见 7.4），`handleValid` 从未执行 |
| 5 | 过滤器 / 拦截器 | ❌ | 三个空类，未实现（见第 8 节） |
| 6 | CRUD 接口齐全 | ✅ | 5 个接口实测全通，见 9.1、9.3 |
| 7 | 接口同步 Apifox 并测试 | ⬜ | 5 个接口清单见 9.1，还没上传 |
| 8 | 内容小结 | ✅ | 14.1 |
| 9 | 运行效果视频 + 学习小结 | ⬜ | 小结见 14.2；录制要点见 12.1 第 1、2、3 题 |

### 14.4 我代码里的待修正点

- [ ] **`pom.xml` 换成 `spring-boot-starter-validation`**（优先级最高，见 7.4）：现在 `@Valid` 是装饰品，脏数据会入库
- [ ] **`application.yml` 的 `app;ication` 改成 `application`**（见 10.3）
- [ ] **决定 HTTP 状态码策略**（见 6.4）：要么加 `@ResponseStatus` / 返回 `ResponseEntity`，要么把"HTTP 永远 200"写进接口文档
- [ ] **`UserService.update` 里 `//user.setAge(request.getAge())` 要有个说法**（见 10.4）：恢复，或者连 DTO 的 `age` 一起删掉
- [ ] **`RequestTimeFilter`、`Loginterceptor`、`WebConfig` 三个空类**：实现或删除，别留着（见第 8 节）
- [ ] **`Loginterceptor` 类名改成 `LogInterceptor`**，并按职责调整包位置
- [ ] **`RequestTimeFilter` 和 `Loginterceptor` 职责重叠**：先决定用 Filter 还是 Interceptor，不要两个都上
- [ ] **`UserCreateRequest.age` 缺 `@NotNull`**：`@Min`/`@Max` 不拦 `null`，现在不传 `age` 也能创建成功
- [ ] **`User.email` 没有 `@Email` 格式校验**：现在 `email` 只要"非空"就行，`z` 这种也能过
- [ ] **`UserCreateRequest` 和 `UserUpdateRequest` 的 `@NotBlank` 文案重复**：可以考虑抽常量，避免两处不一致
- [ ] **同一次异常日志出现两条**（见 10.5）：决定是自己那条降级，还是接受框架那条
- [ ] **类头注释**：`Result` 有 `@author mqxu`（抄来的），`UserService`、`UserController` 等没有说明
- [ ] **端口不统一**：01 用 8888、02 用 9090、03 用 8009、04 用 8004，四种风格。建议定成 `9000+N` 并写进仓库 README

---

## 15. 还没搞懂的问题

1. `OptionalValidatorFactoryBean` 装配失败时，Spring 为什么**只记 INFO 而不中断启动**？如果我希望"校验器没装好就直接启动失败"，有没有配置能做到？我推测要自己写个 `ApplicationListener` 检查，**但没实测**。
2. `@ExceptionHandler(Exception.class)` 的兜底**会不会把 Spring 自己的框架异常也吃掉**（比如 `NoHandlerFoundException`）？本模块实测 `NoResourceFoundException` 是被专门声明的第 5 条接住的，如果删掉那条会不会落到兜底里变成 `500`——**这条我没实测**。
3. 响应体里的 `code` 和 HTTP 状态码不一致时，**网关 / CDN / 监控会不会把业务失败当成成功**？按机制推测会（它们只看 HTTP 层），但**没实测**。
4. `MethodArgumentNotValidException` 里用 `findFirst()` 只取第一条错误。**多条错误能不能一次性全返回**？我推测要把 `getFieldErrors()` 全收集起来拼，但没试过返回多错误的响应体结构该怎么设计。
5. `@Valid` 和 `@Validated` 的区别到底是什么？我目前只知道 `@Validated` 能用在类上做配置校验（02 那篇用过），**在 Controller 参数校验这个场景里两者能不能互换，我没实测**。
6. Filter 里如果想**读两次请求体**（比如先记日志再交给业务），`ServletRequest` 的流只能读一次，需要包装成 `ContentCachingRequestWrapper`。**具体怎么包装、有没有坑，我还没查**。
7. `record` 能不能用来做 DTO（代替 `@Data` 的类）？如果用了 `record`，校验注解还能不能加在组件上？我推测能（加在 record 的组件参数上），**但没实测**。
8. `UserService` 用 `ConcurrentHashMap` + `AtomicLong` 存数据，**进程重启数据就没了**。`idGenerator` 从 1 重新开始，如果前端缓存了旧 id，重启后会指向不同的用户——**这个"重启后 id 语义变化"的问题，我还没想好实际项目里怎么处理**（推测是根本不该用内存存）。
