package top.seamohai.test_redis1.service;

import top.seamohai.test_redis1.dto.LoginRequest;
import top.seamohai.test_redis1.dto.LoginResponse;


public interface SmsService {
    /**
     * 发送验证码短信。发送失败、校验不通过都通过 BusinessException 往外抛
     *
     * @param phone 手机号
     */
    void sendSms(String phone);

    /**
     * 验证码登录
     *
     * @param loginRequest 登录请求参数
     * @return 登录响应
     */
    LoginResponse login(LoginRequest loginRequest);
}