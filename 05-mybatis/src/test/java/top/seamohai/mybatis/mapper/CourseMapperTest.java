package top.seamohai.mybatis.mapper;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.seamohai.mybatis.entity.Course;
import top.seamohai.mybatis.service.CourseService;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@Transactional
@Slf4j
class CourseMapperTest {

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseMapper courseMapper;

    private Course buildCourse(String name) {
        Course c = new Course();
        c.setName(name);
        c.setTeacher("张老师");
        c.setCredit(new BigDecimal("2.5"));
        c.setCapacity(50);
        c.setEnrolled(0);
        c.setDescription("测试课程");
        return c;
    }

    @Test
    void testSaveBatch() {
        List<Course> list = IntStream.range(0, 10)
                .mapToObj(i -> buildCourse("批量课程-" + i))
                .toList();

        boolean ok = courseService.createCourseBatch(list);
        assertTrue(ok);

        // 主键回填：批量插入后每条的 id 都应该有值
        list.forEach(c -> assertNotNull(c.getId(), "主键应该被回填"));
        log.info("批量插入 10 条，首个 id = {}", list.get(0).getId());
    }

    @Test
    void testLoopInsert() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            courseService.createCourse(buildCourse("循环课程-" + i));
        }
        long cost = System.currentTimeMillis() - start;
        log.info("【对照】循环单条插入 100 条耗时 = {} ms", cost);
    }

    @Test
    void testBatchInsert() {
        List<Course> list = IntStream.range(0, 100)
                .mapToObj(i -> buildCourse("批量100-" + i))
                .toList();

        long start = System.currentTimeMillis();
        courseService.createCourseBatch(list);
        long cost = System.currentTimeMillis() - start;
        log.info("【对照】saveBatch 插入 100 条耗时 = {} ms", cost);
    }

    @Test
    void testRemoveByIds() {
        // 先插两条
        Course a = buildCourse("待删课程-A");
        Course b = buildCourse("待删课程-B");
        courseService.createCourse(a);
        courseService.createCourse(b);

        boolean ok = courseService.removeCourseBatch(List.of(a.getId(), b.getId()));
        assertTrue(ok);

        // ① 通过 Mapper 查不到了
        assertNull(courseMapper.selectById(a.getId()));

        // ② 但物理行还在 —— 用你之前加的 selectRawById 那种手写 SQL？
        //    t_course 还没有这个方法，先跳过，靠 SQL 日志观察：
        //    应该看到 UPDATE t_course SET deleted=1 WHERE id IN (...)
        log.info("删除完成，请看 SQL 日志确认是 UPDATE 而不是 DELETE");
    }

    @Test
    void testAutoFill() {
        Course c = buildCourse("自动填充测试");
        courseService.createCourse(c);

        assertNotNull(c.getCreateTime(), "createTime 应该被自动填充");
        assertNotNull(c.getUpdateTime(), "updateTime 应该被自动填充");
        assertEquals(0, c.getDeleted(), "deleted 应该被自动填充为 0");
        assertEquals(0, c.getVersion(), "version 应该被自动填充为 0");
    }

    @Test
    void testRemoveEmptyList() {
        // 传空集合会怎样？我在 Service 里做了兜底返回 false
        assertFalse(courseService.removeCourseBatch(List.of()));
        assertFalse(courseService.removeCourseBatch(null));
    }

}