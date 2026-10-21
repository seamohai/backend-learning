package top.seamohai.mybatis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.seamohai.mybatis.entity.Course;
import top.seamohai.mybatis.mapper.CourseMapper;
import top.seamohai.mybatis.service.CourseService;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper,Course> implements CourseService {
    private static final int BATCH_SIZE = 100;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean createCourseBatch(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return false;
        }
        // 补默认值（NOT NULL 列），再批量写
        courses.forEach(this::applyDefaults);
        return saveBatch(courses, BATCH_SIZE);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean removeCourseBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return removeByIds(ids);      // 注意：这是逻辑删除
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean createCourse(Course course) {
        applyDefaults(course);
        return save(course);
    }

    @Override
    public List<Course> listByTeacher(String teacher) {
        return list(new LambdaQueryWrapper<Course>()
                .eq(StringUtils.hasText(teacher), Course::getTeacher, teacher)
                .orderByAsc(Course::getId));
    }

    /**
     * 扣减课程名额。
     * <p>
     * <b>这是练习 5 最关键的一段代码，核心是"把判断放进 WHERE 里"。</b>
     * <p>
     * <b>❌ 错误写法（先查再改）：</b>
     * <pre>
     * Course c = getById(courseId);
     * if (c.getStock() > 0) {          // ← 判断在 Java 里
     *     c.setStock(c.getStock() - 1);
     *     updateById(c);
     * }
     * </pre>
     * 问题：从"查到 stock=1"到"写回 stock=0"之间有一个<b>时间窗口</b>。
     * 两个线程同时查到 stock=1，都认为"还有名额"，于是都扣 ——
     * 结果 stock 被扣成 -1（或者后写的覆盖先写的），<b>超卖</b>。
     * <p>
     * <b>✅ 正确写法（判断交给数据库）：</b>
     * <pre>
     * UPDATE t_course SET stock = stock - 1
     * WHERE id = ? AND stock > 0 AND deleted = 0
     * </pre>
     * "stock > 0" 是 WHERE 条件，MySQL 在执行这条 UPDATE 时会<b>对那一行加锁</b>，
     * 两个线程的 UPDATE 必须排队。第一个把 stock 减到 0；
     * 第二个再执行时 WHERE 里的 {@code stock > 0} 不成立，<b>匹配 0 行</b>，
     * 于是返回 false —— 名额不足被正确拦下。<b>不会超卖。</b>
     * <p>
     * <b>三处细节：</b>
     * <ul>
     *   <li>{@code setSql("stock = stock - 1")} 让减法在<b>数据库里</b>完成，
     *       而不是"读出来 -1 再写回去"，避免了丢失更新；
     *       ⚠️ 这里拼的是常量表达式，<b>绝不能拼用户输入</b>（和 {@code last()} 同理）；</li>
     *   <li>返回 {@code true} 只表示<b>影响行数 > 0</b>。名额不足、课程不存在、
     *       课程已逻辑删除，这三种情况都返回 {@code false}，<b>而且都不抛异常</b> ——
     *       所以调用方<b>必须自己判断返回值并决定要不要抛错</b>，否则事务不会回滚；</li>
     *   <li>{@code deleted = 0} 是 {@code @TableLogic} 自动追加的，
     *       所以扣不到"已删除"的课程 —— 这是期望行为。</li>
     * </ul>
     *
     * @param courseId 课程 id
     * @return true = 扣减成功；false = 名额不足 / 课程不存在 / 已被逻辑删除
     */
    @Override
    public boolean deductStock(Long courseId) {
        if (courseId == null) {
            return false;
        }
        return update(new LambdaUpdateWrapper<Course>()
                .eq(Course::getId, courseId)
                .gt(Course::getStock, 0)              // ★ 判断进 WHERE，由数据库保证原子性
                .setSql("stock = stock - 1"));        // ★ 减法在数据库里做
    }

    private void applyDefaults(Course course) {
        if (!StringUtils.hasText(course.getName())) {
            throw new IllegalArgumentException("课程名不能为空");
        }
        if (!StringUtils.hasText(course.getTeacher())) {
            throw new IllegalArgumentException("授课教师不能为空");
        }
        if (course.getCredit() == null) {
            course.setCredit(BigDecimal.ZERO);
        }
        if (course.getCapacity() == null) {
            course.setCapacity(0);
        }
        if (course.getEnrolled() == null) {
            course.setEnrolled(0);
        }
        if (course.getStock() == null) {
            course.setStock(0);
        }
    }
}
