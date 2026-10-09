---
layout: home

hero:
  name: 后端工程化学习
  text: Spring Boot 多模块实战笔记
  tagline: JDK 21 ｜ Spring Boot 4.1.1 ｜ 边写边跑，踩过的坑都记下来
  actions:
    - theme: brand
      text: 从 01 开始
      link: /01-quickstart
    - theme: alt
      text: 看 02 配置管理
      link: /02-config
    - theme: alt
      text: 看 04 Web 进阶
      link: /04-web

features:
  - title: 自包含
    details: 每个概念都在文中解释清楚，不需要再翻别的资料
  - title: 实测过
    details: 接口返回、启动日志、报错信息都来自真实运行，不是照代码推导的
  - title: 记坑
    details: 每个坑都写清现象、报错原文、原因和解决办法
---

## 笔记列表

| 笔记 | 主要内容 | 端口 |
| --- | --- | --- |
| [01 · 快速入门](/01-quickstart) | 多模块 Maven 骨架、启动类、控制器、实体类、从零跑起第一个接口 | 8888 |
| [02 · 配置管理](/02-config) | `@Value` 四种形态、`@ConfigurationProperties`、多环境 `@Profile`、配置校验 | 9090 |
| [03 · 日志管理](/03-logging) | 日志门面与实现、五个级别、pattern 占位符、`logback-spring.xml`、MDC traceId、脱敏 | 8009 |
| [04 · Web 进阶](/04-web) | 统一响应体 `Result`、分页 `PageResult`、全局异常处理、`@Valid` 参数校验、Filter 与 Interceptor | 8004 |

## 怎么用这个站点

- 左侧目录按模块编号排列，一个模块一篇笔记；
- 右上角有搜索框，可以直接搜关键字（比如 `@Profile`、`traceId`、`@RestControllerAdvice`）；
- 每篇笔记右侧是「本页目录」，长文里跳转很快；
- 笔记里的命令行都是 Windows（PowerShell）环境下的写法。

> 笔记是边学边记的，**结论以实测为准**。文中凡是标了「实测」的，都是我真正跑过一遍的结果。
