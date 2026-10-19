package top.seamohai.test_redis1.dto;

import lombok.Data;


@Data
public class LoginRequest {
    private String phone;
    private String code;
}