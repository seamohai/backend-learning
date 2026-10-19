package top.seamohai.test_redis1.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.seamohai.test_redis1.common.Result;
import top.seamohai.test_redis1.dto.LoginRequest;
import top.seamohai.test_redis1.dto.LoginResponse;
import top.seamohai.test_redis1.service.SmsService;


@RestController
@RequestMapping("/sms")
@RequiredArgsConstructor
public class SmsController {
    private final SmsService smsService;

    @PostMapping("/send")
    public Result<String> send(@RequestParam String phone) {
        smsService.sendSms(phone);
        return Result.ok("短信发送成功");
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        return Result.ok(smsService.login(loginRequest));
    }
}
