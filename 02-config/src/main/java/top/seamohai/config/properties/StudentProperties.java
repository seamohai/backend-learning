package top.seamohai.config.properties;


import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "student")
public class StudentProperties {
    @NotBlank(message = "student.name 不能为空")
    private String name;

    @Min(value = 1, message = "student.age 必须大于等于 1")
    @Max(value = 150, message = "student.age 必须小于等于 150")
    private Integer age;

    private List<String> hobbies;

    private Map<String, Integer> scores;

    private Address address;

    private List<Course> courses;

    @NotNull(message = "student.birthday 不能为空")
    @Past(message = "student.birthday 必须是过去的日期")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    @NotBlank(message = "student.email 不能为空")
    @Email(message = "student.email 邮箱格式不正确")
    private String email;


    @Data
    public static class Address{
        private String province;
        private String city;
    }

    @Data
    public static class Course{
        private String name;
        private Integer credit;
    }


}
