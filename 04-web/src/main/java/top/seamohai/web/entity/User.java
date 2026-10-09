package top.seamohai.web.entity;
import lombok.Data;

import java.time.LocalDate;


@Data
public class User {
    private Long id;
    private String username;
    private Integer age;
    private String email;
    private LocalDate birthday;
    private String address;
}
