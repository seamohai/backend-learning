package top.seamohai.redis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/redis")
@RequiredArgsConstructor
public class RedisController {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * String：最基础的类型，文本、数字、序列化后的 JSON 都放它里面
     */
    @PostMapping("/string")
    public Map<String, Object> setString(@RequestParam String key,
                                         @RequestParam String value,
                                         // 默认失效时间为 30 秒
                                         @RequestParam(defaultValue = "30") long ttlSeconds) {

        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("value", value);
        result.put("ttlSeconds", stringRedisTemplate.getExpire(key));
        return result;
    }
}