package top.seamohai.mybatis.service;

import com.baomidou.mybatisplus.spring.service.IService;
import top.seamohai.mybatis.entity.Course;

import java.util.List;

public interface CourseService extends IService<Course> {
    /** 批量新增课程（练习 saveBatch） */
    boolean createCourseBatch(List<Course> courses);

    /** 批量删除课程（练习 removeByIds，注意是逻辑删除） */
    boolean removeCourseBatch(List<Long> ids);

    /** 单个新增，用来和批量做对照实验 */
    boolean createCourse(Course course);

    /** 按教师查课程（顺带复习 LambdaQueryWrapper） */
    List<Course> listByTeacher(String teacher);

    /**
     * 扣减课程名额（练习 5 的"扣库存"）。
     * <p>
     * <b>必须在 WHERE 里判断"还有没有名额"，不能先查再改</b>，否则并发下会超卖。
     * 详见实现类的注释。
     *
     * @param courseId 课程 id
     * @return true = 扣减成功；false = 名额不足，或课程不存在 / 已被逻辑删除
     */
    boolean deductStock(Long courseId);
}
