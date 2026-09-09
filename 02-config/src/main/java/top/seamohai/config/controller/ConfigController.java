package top.seamohai.config.controller;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/config")
public class ConfigController {

    @Value("${server.port}")
    private Integer serverport;

    @Value("${spring.application.name}")
    private String appName;

    @Value("${seamohai.name}")
    private String myName;

    @Value("${seamohai.job}")
    private String myjob;


    @GetMapping("/basic")
    public String  getBasicInfo(){
        return "服务器端口是: " + this.serverport + ",应用名称是: " +  this.appName;
    }

    @GetMapping("/my")
    public String  getMyInfo(){
        return "我的名字是: " + this.myName + ",职业是: " +  this.myjob;
    }
}
