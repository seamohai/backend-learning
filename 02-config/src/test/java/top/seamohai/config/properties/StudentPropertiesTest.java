package top.seamohai.config.properties;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@Slf4j
public class StudentPropertiesTest {

    @Resource
    private StudentProperties studentProperties;

    @Test
    public void printStudentInfo() {
        log.info("学生信息: {}", studentProperties);

        log.info("学生姓名: {}", studentProperties.getName());
        log.info("学生语文成绩: {}", studentProperties.getScores().get("chinese"));

        log.info("学生邮箱: {}", studentProperties.getEmail());
        log.info("学生日期: {}", studentProperties.getBirthday());
    }
}