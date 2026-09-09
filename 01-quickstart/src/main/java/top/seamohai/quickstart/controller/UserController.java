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
    private User getUserInfo(){
        return new User(1001L,"张三", LocalDate.of(2005,10,24));
    }

    @GetMapping("/hello")
    public String hello(){
        return "hello";
    }
}
