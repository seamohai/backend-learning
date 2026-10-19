package top.seamohai.test_redis1.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Data
@Configuration
@ConfigurationProperties(prefix = "seamohai.sms")
public class SmsProperties {

    /**
     * 是否使用模拟通道。true 时不调用短信 SDK，验证码只写日志和 Redis，方便本地调试
     */
    private boolean mockEnabled = true;

    /**
     * 验证码有效期，单位秒
     */
    private long codeExpireSeconds = 300;

    /**
     * 同一个手机号两次发送之间的最小间隔，单位秒
     */
    private long sendIntervalSeconds = 60;

    /**
     * 同一个手机号一天最多发送的条数
     */
    private long sendDailyLimit = 10;

    /**
     * 同一个验证码最多允许输错的次数，超过就作废
     */
    private int maxFailCount = 5;
}