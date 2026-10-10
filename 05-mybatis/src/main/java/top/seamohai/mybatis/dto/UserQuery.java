package top.seamohai.mybatis.dto;

import lombok.Data;

@Data
public class UserQuery {
    private String keyword;        // 匹配 username 或 nickname
    private Integer gender;
    private String memberLevel;
    private Boolean vip;
    private Integer status;
    private Integer minAge;
    private Integer maxAge;
}