package top.seamohai.mybatis.pool;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.seamohai.mybatis.entity.Course;
import top.seamohai.mybatis.service.CourseService;
import top.seamohai.mybatis.service.UserService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 练习 4：连接池压测实验。
 * <p>
 * <b>⚠️ 这个类绝对不能加 {@code @Transactional}！</b>
 * <p>
 * 因为 Spring 在事务期间会把 Connection 绑定到当前线程，
 * 同一个线程的多次查询复用同一条连接。那样的话无论并发多少，
 * 真正被占用的连接数都很小，池永远不会满，实验就做不出来。
 * <p>
 * 代价是：测试产生的数据不会自动回滚，必须自己清理。
 */
@SpringBootTest
@Slf4j
class ConnectionPoolTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private top.seamohai.mybatis.mapper.UserMapper userMapper;

    /** 读一下 HikariCP 池子的实时状态 */
    private String poolStatus() {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return "(不是 HikariDataSource)";
        }
        var mx = hikari.getHikariPoolMXBean();
        if (mx == null) {
            return "(MXBean 不可用)";
        }
        return String.format("池大小上限=%d 当前总连接=%d 活跃=%d 空闲=%d 等待取连接的线程=%d",
                hikari.getMaximumPoolSize(),
                mx.getTotalConnections(), mx.getActiveConnections(),
                mx.getIdleConnections(), mx.getThreadsAwaitingConnection());
    }

    /** 实验一：单次查询耗时 —— 决定"需要多少并发才够压垮池" */
    @Test
    void experiment1_singleQueryLatency() {
        log.info("【实验 1】池状态（压测前）：{}", poolStatus());

        // 预热，避开首次连接的建连开销
        for (int i = 0; i < 20; i++) {
            userService.getById(1L);
        }

        int rounds = 200;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            userService.getById(1L);
        }
        long costNs = System.nanoTime() - start;

        double avgMs = costNs / 1_000_000.0 / rounds;
        log.info("【实验 1】单次 getById 平均耗时 = {} ms（{} 次共 {} ms）",
                String.format("%.3f", avgMs), rounds, String.format("%.1f", costNs / 1_000_000.0));

        // 推算：池大小为 1 时，理论吞吐上限
        double qpsWithPool1 = 1000.0 / avgMs;
        log.info("【实验 1】按此耗时推算 —— 池大小=1 时理论吞吐上限约 {} QPS", String.format("%.0f", qpsWithPool1));
        log.info("【实验 1】也就是说：3 秒超时窗口内，池大小=1 能服务约 {} 个请求",
                String.format("%.0f", qpsWithPool1 * 3));

        assertTrue(avgMs > 0);
    }

    /** 实验二：高并发读 —— 看池子会不会被压垮 */
    @Test
    void experiment2_concurrentRead() throws Exception {
        int threads = 60;
        log.info("【实验 2】池状态（压测前）：{}", poolStatus());
        log.info("【实验 2】准备用 {} 个线程【同时】发请求", threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);      // 发令枪：保证"同时"开始
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        List<String> errors = new CopyOnWriteArrayList<>();
        AtomicInteger maxAwaiting = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    userService.getById(1L);
                    ok.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                    if (errors.size() < 3) {
                        errors.add(e.getClass().getSimpleName() + ": "
                                + String.valueOf(e.getMessage()).replaceAll("\\s+", " "));
                    }
                } finally {
                    if (dataSource instanceof HikariDataSource h &&
                            h.getHikariPoolMXBean() != null) {
                        maxAwaiting.accumulateAndGet(
                                h.getHikariPoolMXBean().getThreadsAwaitingConnection(), Math::max);
                    }
                    doneGate.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startGate.countDown();                 // 开跑
        boolean finished = doneGate.await(60, TimeUnit.SECONDS);
        long cost = System.currentTimeMillis() - start;
        pool.shutdownNow();

        log.info("【实验 2】耗时 = {} ms，完成={}", cost, finished);
        log.info("【实验 2】成功 = {}，失败 = {}", ok.get(), fail.get());
        log.info("【实验 2】过程中等待取连接的线程数峰值 = {}", maxAwaiting.get());
        log.info("【实验 2】池状态（压测后）：{}", poolStatus());
        errors.forEach(e -> log.error("【实验 2】失败样例：{}", e));

        assertTrue(ok.get() > 0, "至少应该有请求成功");
    }

    /** 实验三：并发写 —— 批量插入会长时间占用连接，更容易压垮小池子 */
    @Test
    void experiment3_concurrentHeavyWrite() throws Exception {
        int threads = 8;
        int rowsPerThread = 200;
        String tag = "pool" + System.currentTimeMillis();

        log.info("【实验 3】池状态（压测前）：{}", poolStatus());
        log.info("【实验 3】{} 个线程，每个批量插入 {} 条（会长时间占用连接）", threads, rowsPerThread);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        List<String> errors = new CopyOnWriteArrayList<>();
        AtomicInteger maxAwaiting = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    startGate.await();
                    List<Course> list = IntStream.range(0, rowsPerThread)
                            .mapToObj(i -> {
                                Course c = new Course();
                                c.setName(tag + "-t" + tid + "-" + i);
                                c.setTeacher("压测老师");
                                c.setCredit(new java.math.BigDecimal("2.0"));
                                c.setCapacity(10);
                                c.setEnrolled(0);
                                return c;
                            })
                            .toList();
                    courseService.createCourseBatch(list);
                    ok.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                    if (errors.size() < 3) {
                        errors.add(e.getClass().getSimpleName() + ": "
                                + String.valueOf(e.getMessage()).replaceAll("\\s+", " "));
                    }
                } finally {
                    if (dataSource instanceof HikariDataSource h &&
                            h.getHikariPoolMXBean() != null) {
                        maxAwaiting.accumulateAndGet(
                                h.getHikariPoolMXBean().getThreadsAwaitingConnection(), Math::max);
                    }
                    doneGate.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startGate.countDown();
        boolean finished = doneGate.await(120, TimeUnit.SECONDS);
        long cost = System.currentTimeMillis() - start;
        pool.shutdownNow();

        log.info("【实验 3】耗时 = {} ms，完成={}", cost, finished);
        log.info("【实验 3】成功线程 = {}，失败线程 = {}", ok.get(), fail.get());
        log.info("【实验 3】过程中等待取连接的线程数峰值 = {}", maxAwaiting.get());
        log.info("【实验 3】池状态（压测后）：{}", poolStatus());
        errors.forEach(e -> log.error("【实验 3】失败样例：{}", e));

        // 清理：把这次插进去的数据物理删掉（逻辑删除会占用唯一索引，影响后续运行）
        cleanup(tag);
    }

    /**
     * 实验四：用慢查询把连接占住 —— <b>这个才是真正的复现</b>。
     * <p>
     * 原理：连接池只有 1 条连接，每个请求要占用它 1 秒（SLEEP(1)）。
     * 当 20 个线程同时来抢时，第 1 个拿走连接，其余 19 个排队。
     * 排到第 4 个开始，等待时间就超过 connection-timeout=3000ms
     * → 抛出 SQLTransientConnectionException。
     * <p>
     * 换句话说：<b>能不能压垮池，取决于"单次占用连接的时间 × 排队人数"
     * 有没有超过 connection-timeout</b>，而不只是"池小"。
     */
    @Test
    void experiment4_slowQueryTimeout() throws Exception {
        int threads = 20;
        int sleepSeconds = 1;

        log.info("【实验 4】池状态（压测前）：{}", poolStatus());
        log.info("【实验 4】{} 个线程，每个执行 SELECT SLEEP({})，即占用连接 {} 秒",
                threads, sleepSeconds, sleepSeconds);
        log.info("【实验 4】连接池上限=1，connection-timeout=3000ms —— 预期会大量超时");

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        List<String> errors = new CopyOnWriteArrayList<>();
        AtomicInteger maxAwaiting = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    userMapper.slowQuery(sleepSeconds);
                    ok.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                    if (errors.size() < 3) {
                        errors.add(e.getClass().getName() + ": "
                                + String.valueOf(e.getMessage()).replaceAll("\\s+", " "));
                    }
                } finally {
                    if (dataSource instanceof HikariDataSource h &&
                            h.getHikariPoolMXBean() != null) {
                        maxAwaiting.accumulateAndGet(
                                h.getHikariPoolMXBean().getThreadsAwaitingConnection(), Math::max);
                    }
                    doneGate.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startGate.countDown();
        boolean finished = doneGate.await(120, TimeUnit.SECONDS);
        long cost = System.currentTimeMillis() - start;
        pool.shutdownNow();

        log.info("【实验 4】耗时 = {} ms，完成={}", cost, finished);
        log.info("【实验 4】成功 = {}，失败 = {}", ok.get(), fail.get());
        log.info("【实验 4】等待取连接的线程数峰值 = {}", maxAwaiting.get());
        log.info("【实验 4】池状态（压测后）：{}", poolStatus());
        errors.forEach(e -> log.error("【实验 4】失败样例：{}", e));
    }

    /**
     * 实验五：<b>最直白的复现</b> —— 绕过 MyBatis，直接从 DataSource 抢连接。
     * <p>
     * 实验四走的是 MyBatis，报错被包了两层
     * （MyBatisSystemException → CannotGetJdbcConnectionException），
     * 反而看不到 Hikari 的原始信息。
     * <p>
     * 这个实验直接调 {@code dataSource.getConnection()}，抛出来的就是
     * Hikari 的原始异常，能看到那句关键的话：
     * <pre>
     * HikariPool-1 - Connection is not available, request timed out after 3000ms
     * </pre>
     * 这句话本身就说明了问题的性质 —— <b>不是"连不上数据库"，
     * 而是"等不到池子里的连接"</b>。数据库一直是好的。
     */
    @Test
    void experiment5_rawConnectionTimeout() throws Exception {
        int threads = 8;
        int holdSeconds = 6;      // 故意远大于 connection-timeout(3s)

        log.info("【实验 5】池状态（压测前）：{}", poolStatus());
        log.info("【实验 5】{} 个线程同时抢连接，抢到后各持有 {} 秒（超时线是 3 秒）",
                threads, holdSeconds);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        List<String> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                Connection conn = null;
                try {
                    startGate.await();
                    conn = dataSource.getConnection();          // ← 就在这一行超时
                    ok.incrementAndGet();
                    Thread.sleep(holdSeconds * 1000L);
                } catch (Exception e) {
                    fail.incrementAndGet();
                    errors.add(e.getClass().getName() + " ==> " + e.getMessage());
                } finally {
                    if (conn != null) {
                        try { conn.close(); } catch (SQLException ignored) { }
                    }
                    doneGate.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startGate.countDown();
        doneGate.await(120, TimeUnit.SECONDS);
        long cost = System.currentTimeMillis() - start;
        pool.shutdownNow();

        log.info("【实验 5】耗时 = {} ms", cost);
        log.info("【实验 5】成功抢到连接 = {}，超时失败 = {}", ok.get(), fail.get());
        errors.stream().distinct().forEach(e -> log.error("【实验 5】原始报错：{}", e));

        // 不预设池大小的结论：池够大时全部成功，池太小时大量超时。
        // 这里只断言"至少有一个线程成功拿到过连接"，避免配置一变测试就红。
        assertTrue(ok.get() > 0, "至少应该有一个线程能拿到连接");
    }

    /** 用原生 SQL 物理删除，绕过逻辑删除，避免唯一索引被占 */
    private void cleanup(String tag) {        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("DELETE FROM t_course WHERE name LIKE ?")) {
            ps.setString(1, tag + "%");
            int n = ps.executeUpdate();
            log.info("【清理】物理删除 {} 条压测数据（name LIKE '{}%'）", n, tag);
        } catch (Exception e) {
            log.error("【清理】失败：{}", e.getMessage());
        }
    }
}
