package top.seamohai.mybatis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.seamohai.mybatis.entity.Course;
import top.seamohai.mybatis.service.CourseService;

import java.util.List;

/**
 * 课程接口层。
 * <p>
 * 本模块的接口只有 5 个，目的是把 <b>saveBatch 与 removeByIds</b> 这两个批量能力
 * 暴露成 HTTP 接口，方便用 Apifox 直观演示。
 * <p>
 * <b>⚠️ 一个重要提醒：接口层面看不到"批量"的真相。</b>
 * <p>
 * 调 {@code POST /courses/batch} 插 200 条，接口会很快返回成功，
 * 从请求和响应上<b>完全看不出</b>这 200 条底层是怎么发出去的。
 * 只有打开 SQL 日志才会发现：MyBatis-Plus 的 saveBatch 发出去的
 * 仍然是<b>200 条独立的 INSERT</b>，它并不把 SQL 合并成一条多值 INSERT
 * （除非 JDBC URL 上开了 {@code rewriteBatchedStatements=true}）。
 * <p>
 * 所以"批量"这个练习的真正结论要用<b>测试类 + SQL 日志</b>来验证，
 * 接口只负责演示"功能可用"。
 * <p>
 * 关于路径顺序：{@code /courses/batch}、{@code /courses/count} 这类
 * <b>字面量路径</b>必须和 {@code /courses/{id}} 区分开。Spring 的匹配规则是
 * "字面量优先于路径变量"，所以 {@code GET /courses/count} 不会被
 * {@code GET /courses/{id}} 抢走（这和 UserController 里的
 * {@code /users/count} 是同一个模式）。
 */
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /* ==================== 增 ==================== */

    /**
     * 批量新增课程（对应 {@code saveBatch}）。
     * <p>
     * <b>请求</b>：{@code POST /courses/batch}，请求体是课程对象的 JSON 数组。
     * <pre>
     * [
     *   { "name": "Java 程序设计", "teacher": "张老师", "credit": 3.5, "capacity": 60 },
     *   { "name": "数据结构",     "teacher": "李老师", "credit": 3.0, "capacity": 50 }
     * ]
     * </pre>
     * <p>
     * <b>返回</b>：成功处理的条数（即请求数组的长度，不是数据库真实影响行数）。
     * <p>
     * <b>三个要点</b>：
     * <ul>
     *   <li>整个批量操作在<b>一个事务</b>里。只要有一条失败（比如课程名重复
     *       撞上 {@code uk_name} 唯一索引），<b>整批都回滚</b>，
     *       不会出现"前一半成功、后一半失败"；</li>
     *   <li>{@code create_time} / {@code update_time} / {@code deleted} / {@code version}
     *       由 MyMetaObjectHandler 自动填充，<b>请求体里不用传</b>；</li>
     *   <li>{@code id} 不要传，让数据库自增 —— 插入后 MyBatis-Plus 会把
     *       生成的主键<b>回填</b>到每个对象里。</li>
     * </ul>
     */
    @PostMapping("/batch")
    public int createBatch(@RequestBody List<Course> courses) {
        courseService.createCourseBatch(courses);
        return courses.size();
    }

    /* ==================== 查 ==================== */

    /**
     * 查询全部课程。
     * <p>
     * <b>请求</b>：{@code GET /courses}
     * <p>
     * <b>返回</b>：课程列表，按 id 升序。已被逻辑删除的课程<b>不会出现</b>
     * （MyBatis-Plus 自动追加了 {@code AND deleted = 0}）。
     */
    @GetMapping
    public List<Course> list() {
        return courseService.list();
    }

    /**
     * 统计课程总数。
     * <p>
     * <b>请求</b>：{@code GET /courses/count}
     * <p>
     * <b>返回</b>：数字。同样自动过滤掉逻辑删除的记录。
     * <p>
     * <b>怎么用它做验收</b>：批量新增后调一次、批量删除后再调一次，
     * 对比数字变化，就能确认操作真的生效了。
     */
    @GetMapping("/count")
    public long count() {
        return courseService.count();
    }

    /**
     * 按主键查询课程详情。
     * <p>
     * <b>请求</b>：{@code GET /courses/1}
     * <p>
     * <b>返回</b>：课程对象；<b>查不到时返回 null</b>（HTTP 状态码仍是 200）。
     * <p>
     * <b>这个接口是验证"逻辑删除"的关键</b>：执行批量删除之后再调它，
     * 会返回 {@code null} —— 但去数据库里查，那一行其实还在，
     * 只是 {@code deleted} 被置成了 1。
     */
    @GetMapping("/{id}")
    public Course getById(@PathVariable Long id) {
        return courseService.getById(id);
    }

    /* ==================== 删 ==================== */

    /**
     * 批量删除课程（对应 {@code removeByIds}）。
     * <p>
     * <b>请求</b>：{@code DELETE /courses/batch?ids=1,2,3}
     * <p>
     * <b>返回</b>：{@code true} 表示操作执行了。
     * <p>
     * <b>⚠️ 返回值不能用来判断删掉了几条</b> —— 它只表示"没有抛异常"。
     * 如果 3 个 id 里只有 1 个真实存在，照样返回 {@code true}。
     * 想知道真实影响行数，只能看 SQL 日志，或再调一次 {@code /courses/count}。
     * <p>
     * <b>这是逻辑删除，不是物理删除。</b> 实际执行的是：
     * <pre>
     * UPDATE t_course SET deleted=1, update_time=? WHERE id IN (?, ?, ?) AND deleted=0
     * </pre>
     * 所以数据行还在库里，只是查询时被自动过滤掉了。
     * <p>
     * <b>为什么用查询参数而不是请求体</b>：HTTP 规范里 DELETE 带请求体
     * 的支持情况不一致（有些代理和客户端会丢掉 body），
     * 所以用 {@code ?ids=1,2,3} 这种形式。Spring 会自动把逗号分隔的
     * 字符串转成 {@code List<Long>}。
     */
    @DeleteMapping("/batch")
    public boolean deleteBatch(@RequestParam List<Long> ids) {
        return courseService.removeCourseBatch(ids);
    }
}
