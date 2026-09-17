package top.seamohai.config.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.seamohai.config.properties.AppProperties;
import top.seamohai.config.service.EnvService;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {

    private final AppProperties appProperties;
    private final EnvService envService;

    // ---------- 基础 @Value 注入 ----------
    @Value("${server.port}")
    private Integer serverPort;

    @Value("${spring.application.name}")
    private String appName;

    @Value("${seamohai.name}")
    private String myName;

    @Value("${seamohai.job}")
    private String myJob;

    // ---------- 占位符引用：app.author 在 yml 中引用了 seamohai.name ----------
    @Value("${app.author}")
    private String author;

    // ---------- 默认值：app.remark 未配置时使用冒号后的默认值 ----------
    @Value("${app.remark:暂无备注}")
    private String remark;

    // ---------- 随机值 ----------
    @Value("${random.uuid}")
    private String randomUuid;

    @Value("${random.int(1,100)}")
    private Integer randomInt;

    // ---------- SpEL 表达式：先解析 ${student.age} 再计算三元表达式 ----------
    @Value("#{${student.age} >= 18 ? '成年' : '未成年'}")
    private String adult;

    // ---------- 多环境配置：值来自 application-{profile}.yml ----------
    @Value("${env.name}")
    private String envName;

    @Value("${env.description}")
    private String envDescription;

    @GetMapping("/basic")
    public String getBasicInfo() {
        return "服务器端口是：" + this.serverPort + "，应用名称是：" + appName;
    }

    @GetMapping("/my")
    public String getMyInfo() {
        return "我的姓名是：" + this.myName + "，职业是：" + myJob;
    }

    @GetMapping("/value")
    public String getValueCases() {
        return "占位符引用 author=" + author
                + "；默认值 remark=" + remark
                + "；随机 UUID=" + randomUuid
                + "；随机整数=" + randomInt
                + "；SpEL adult=" + adult;
    }

    @GetMapping("/env")
    public String getEnv() {
        return "当前环境：" + envName + "，" + envDescription + "；Profile Bean：" + envService.envInfo();
    }

    @GetMapping("/app")
    public AppProperties getApp() {
        return appProperties;
    }
}

