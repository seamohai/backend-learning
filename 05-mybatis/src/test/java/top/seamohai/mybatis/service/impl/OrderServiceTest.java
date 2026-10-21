package top.seamohai.mybatis.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.seamohai.mybatis.entity.Course;
import top.seamohai.mybatis.entity.Order;
import top.seamohai.mybatis.service.CourseService;
import top.seamohai.mybatis.service.OrderService;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 练习 5 验收：跨表事务的原子性。
 * <p>
 * <b>⚠️ 这个类故意【不加】{@code @Transactional}，原因见下。</b>
 * <p>
 * 本练习的关键用例是"让事务中途失败，看前一步有没有回滚"。
 * 如果测试类自己带事务，那么 {@code createOrder} 会加入这个外层事务，
 * 失败时只是把外层事务标记为 rollback-only，
 * 后续查询还会受事务影响，很难干净地观察"到底回滚了没有"。
 * <p>
 * 所以这里不加事务：{@code createOrder} 自己开一个完整事务，
 * 失败就完整回滚，然后我们在<b>事务之外</b>查数据库，看到的就是最终结果。
 * 代价是测试数据不会自动清理，所以用 {@link #cleanup()} 手动物理删除。
 */
@SpringBootTest
@Slf4j
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private DataSource dataSource;

    /** 本测试用的课程名前缀，方便清理 */
    private static final String PREFIX = "order-test-";

    private Long courseId;

    /** 每个用例前：造一门课，库存 5 */
    @BeforeEach
    void setUp() {
        cleanup();
        Course c = new Course();
        c.setName(PREFIX + System.nanoTime());
        c.setTeacher("事务测试老师");
        c.setCredit(new BigDecimal("2.0"));
        c.setCapacity(10);
        c.setEnrolled(0);
        c.setStock(5);                      // ← 库存 5，够测
        courseService.createCourse(c);
        courseId = c.getId();
        log.info("【准备】课程 id={}，初始库存=5", courseId);
    }

    /** 每个用例后：物理删除测试数据，避免残留（逻辑删除会占唯一索引） */
    @AfterEach
    void tearDown() {
        cleanup();
    }

    /** 用原生 SQL 物理删除 t_course 和 t_order 里本次测试的数据 */
    private void cleanup() {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM t_course WHERE name LIKE ?")) {
                ps.setString(1, PREFIX + "%");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM t_order WHERE order_no LIKE ?")) {
                ps.setString(1, PREFIX + "%");
                ps.executeUpdate();
            }
        } catch (Exception e) {
            log.warn("清理失败：{}", e.getMessage());
        }
    }

    /** 读课程的实时库存 */
    private int stockOf(Long id) {
        Course c = courseService.getById(id);
        assertNotNull(c, "课程应该存在");
        return c.getStock();
    }

    // ============================================================
    // 用例 1：成功路径 —— 订单 +1，库存 -1
    // ============================================================

    @Test
    void testCreateOrderSuccess() {
        int before = stockOf(courseId);
        long orderCountBefore = orderService.count();

        boolean ok = orderService.createOrder(courseId, 1L, PREFIX + "ok-1");
        assertTrue(ok, "库存充足时下单应该成功");

        assertEquals(before - 1, stockOf(courseId), "库存应该减 1");
        assertEquals(orderCountBefore + 1, orderService.count(), "订单应该多 1 条");
        log.info("【用例 1】成功路径通过：库存 {} → {}", before, stockOf(courseId));
    }

    // ============================================================
    // 用例 2：库存为 0 时下单 → 失败，且订单不落地
    // ============================================================

    @Test
    void testCreateOrderOutOfStock() {
        // 先把库存扣光（连续下单 5 次，初始库存是 5）
        for (int i = 0; i < 5; i++) {
            orderService.createOrder(courseId, 1L, PREFIX + "drain-" + i);
        }
        assertEquals(0, stockOf(courseId), "5 次下单后库存应该为 0");

        long orderCountBefore = orderService.count();

        // 第 6 次：库存不足，应该抛异常
        assertThrows(Exception.class,
                () -> orderService.createOrder(courseId, 1L, PREFIX + "overflow"),
                "库存为 0 时下单应该失败");

        assertEquals(0, stockOf(courseId), "库存已经是 0，不该被扣成负数");
        assertEquals(orderCountBefore, orderService.count(), "失败的订单不该落地");
        log.info("【用例 2】库存耗尽路径通过：库存保持 0，订单数未增加");
    }

    // ============================================================
    // 用例 3：★ 核心 —— 中途失败时，已经扣掉的库存必须回滚
    // ============================================================

    /**
     * 这是练习 5 的**验收核心**。
     * <p>
     * 失败注入方式：<b>故意用一个已经存在的订单号</b>，
     * 让它撞上 {@code uk_order_no} 唯一索引。
     * <p>
     * 执行顺序是：
     * <pre>
     * 1. deductStock 成功（库存 5 → 4）      ← 已经改了库
     * 2. save(order) 撞唯一索引，抛异常      ← 在这一步炸
     * 3. 事务回滚 → 第 1 步的库存必须回到 5
     * </pre>
     * 如果事务没生效，库存会停在 4 —— <b>名额被白扣了</b>，这就是"订单没建但库存没了"的严重 bug。
     */
    @Test
    void testCreateOrderRollback() {
        String duplicateNo = PREFIX + "dup";

        // 第一次：正常下单，占掉这个订单号（库存 5 → 4）
        orderService.createOrder(courseId, 1L, duplicateNo);
        int stockAfterFirst = stockOf(courseId);
        long orderCountAfterFirst = orderService.count();
        log.info("【用例 3】第一次下单成功：库存=5 → {}，订单数={}", stockAfterFirst, orderCountAfterFirst);

        // 第二次：用同样的订单号 → 第二步撞唯一索引失败
        // 第一步的 deductStock 已经执行过，如果事务生效，它必须被回滚
        assertThrows(Exception.class,
                () -> orderService.createOrder(courseId, 1L, duplicateNo),
                "重复订单号应该触发唯一索引冲突");

        int stockAfterFail = stockOf(courseId);
        long orderCountAfterFail = orderService.count();

        log.info("【用例 3】第二次失败后：库存={}，订单数={}", stockAfterFail, orderCountAfterFail);

        // ★ 核心断言：库存没有被多扣
        assertEquals(stockAfterFirst, stockAfterFail,
                "事务没生效！失败的那次下单把库存也扣掉了（库存应该回到 " + stockAfterFirst + "）");
        // 订单也没多
        assertEquals(orderCountAfterFirst, orderCountAfterFail,
                "失败的订单不该落地");

        log.info("【用例 3】★ 原子性验证通过：失败的下单没有留下任何痕迹");
    }

    // ============================================================
    // 用例 4：取消订单要把名额还回去（加分项）
    // ============================================================

    @Test
    void testCancelOrderReturnsStock() {
        orderService.createOrder(courseId, 1L, PREFIX + "cancel-1");
        int afterOrder = stockOf(courseId);

        Order order = orderService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, PREFIX + "cancel-1"));
        assertNotNull(order, "订单应该存在");

        assertTrue(orderService.cancelOrder(order.getId()), "取消应该成功");
        assertEquals(afterOrder + 1, stockOf(courseId), "取消后库存应该还回来");

        // 重复取消：不该再还一次
        int afterCancel = stockOf(courseId);
        assertFalse(orderService.cancelOrder(order.getId()), "重复取消应该返回 false");
        assertEquals(afterCancel, stockOf(courseId), "重复取消不能再还名额");
        log.info("【用例 4】取消订单路径通过");
    }

    // ============================================================
    // 用例 6：★ 反证 —— 如果去掉 @Transactional，同样的流程会「扣了不还」
    // ============================================================

    /**
     * 这个用例是练习 5 的<b>对照实验</b>，用来证明"回滚确实是事务带来的"。
     * <p>
     * 光看 {@code testCreateOrderRollback} 通过，只能说明"库存没被多扣"，
     * 但万一它本来就扣不动呢？所以这里<b>把事务去掉，复刻同样的两步</b>：
     * <pre>
     * 第 1 步：deductStock(courseId)   → 成功，库存 5 → 4
     * 第 2 步：插入一条重复订单号       → 抛异常
     * 没有 @Transactional → 第 1 步【不会被回滚】→ 库存停在 4
     * </pre>
     * <b>对比结论</b>：
     * <ul>
     *   <li>有事务（{@code testCreateOrderRollback}）：库存回到 5 → 回滚生效；</li>
     *   <li>无事务（本用例）：库存停在 4 → <b>名额被白扣，这就是原子性缺失的真实后果</b>。</li>
     * </ul>
     * <p>
     * ⚠️ 这里不用 {@code orderService.createOrder}（它带事务），
     * 而是用「独立的两次调用」模拟"没有事务边界的两个写操作"。
     */
    @Test
    void testWithoutTransactionStockIsLost() throws Exception {
        String duplicateNo = PREFIX + "no-tx";

        // 先占掉这个订单号（这一步本身是成功的事务）
        orderService.createOrder(courseId, 1L, duplicateNo);
        int stockBefore = stockOf(courseId);
        log.info("【用例 6】占用订单号后：库存 = {}", stockBefore);

        // ---- 下面模拟"没有事务"的两步 ----

        // 第 1 步：扣库存 —— 独立提交（deductStock 方法本身没有 @Transactional）
        boolean deducted = courseService.deductStock(courseId);
        assertTrue(deducted, "第 1 步应该扣成功");
        log.info("【用例 6】第 1 步扣库存成功：库存 {} → {}", stockBefore, stockOf(courseId));

        // 第 2 步：插入重复订单号 —— 必然撞 uk_order_no 唯一索引
        Exception ex = assertThrows(Exception.class, () -> {
            Order dup = new Order();
            dup.setOrderNo(duplicateNo);          // 已经存在
            dup.setCourseId(courseId);
            dup.setUserId(1L);
            dup.setAmount(new BigDecimal("99.00"));
            dup.setStatus(1);
            orderService.save(dup);
        });
        log.info("【用例 6】第 2 步失败（预期）：{}", ex.getClass().getSimpleName());

        // ★ 核心对比：没有事务，第 1 步扣掉的库存【不会回来】
        int stockAfter = stockOf(courseId);
        log.info("【用例 6】★ 没有事务时：库存 = {}（对比：有事务时应该还是 {}）",
                stockAfter, stockBefore);

        assertEquals(stockBefore - 1, stockAfter,
                "没有事务时，第 1 步扣掉的库存不会回滚 —— 这正是缺少原子性的后果");

        // 手工把库存补回来，保持环境干净（这个用例的副作用不该留给别人）
        courseService.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Course>()
                .eq(Course::getId, courseId)
                .setSql("stock = stock + 1"));
    }

    // ============================================================
    // 用例 5：逻辑删除 + 自动填充是否对 t_order 生效
    // ============================================================

    @Test
    void testOrderAutoFillAndLogicDelete() {
        orderService.createOrder(courseId, 1L, PREFIX + "fill-1");

        Order order = orderService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, PREFIX + "fill-1"));
        assertNotNull(order);

        assertNotNull(order.getCreateTime(), "createTime 应该被自动填充");
        assertNotNull(order.getUpdateTime(), "updateTime 应该被自动填充");
        assertEquals(0, order.getDeleted(), "deleted 应该被自动填充为 0");
        assertEquals(0, order.getVersion(), "version 应该被自动填充为 0");

        // 逻辑删除
        orderService.removeById(order.getId());
        assertNull(orderService.getById(order.getId()), "逻辑删除后应该查不到");
        log.info("【用例 5】自动填充 + 逻辑删除都生效");
    }
}
