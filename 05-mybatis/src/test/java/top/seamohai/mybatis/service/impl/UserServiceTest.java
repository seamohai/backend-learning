package top.seamohai.mybatis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.seamohai.mybatis.entity.User;
import top.seamohai.mybatis.mapper.UserMapper;
import top.seamohai.mybatis.service.UserService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 随堂 5 个练习的验证（对应 05-mybatis.md 第 8.1 节）。
 * <p>
 * 这 5 个练习各自验证 MyBatis-Plus 的一个能力：
 * <ol>
 *   <li>查不到记录时返回 null 而不是抛异常；</li>
 *   <li>LambdaQueryWrapper 多条件同时生效；</li>
 *   <li>事务：抛异常后先前的写操作被回滚；</li>
 *   <li>逻辑删除：数据"看不见了"但物理行还在；</li>
 *   <li>乐观锁：并发更新时第二次被拦下。</li>
 * </ol>
 * <p>
 * 跑之前先确保 MySQL 已启动。因为 application.yml 里配了
 * {@code spring.sql.init.mode=always}，每次启动都会用 schema.sql
 * 重建表、用 data.sql 灌入示例数据，所以不用担心脏数据。
 * <p>
 * 每个涉及写操作的用例末尾都做了<b>数据复原</b>，可以反复运行。
 */
@SpringBootTest
@Slf4j
class UserServiceTest {

    @Autowired
    private UserService userService;

    /** 用来绕过逻辑删除、直接查物理行，见 UserMapper#selectRawById 的注释 */
    @Autowired
    private UserMapper userMapper;

    // ==================== 练习 1 ====================

    /**
     * 练习 1：用 getById 查询一个不存在的 id → 返回 null，不报错。
     * <p>
     * 这条验证的是"查不到"时的行为：MyBatis-Plus 不抛异常，返回 null。
     * 所以调用方<b>必须自己判空</b>，否则后面直接用就会 NPE。
     */
    @Test
    void practice1_getNotExistReturnsNull() {
        // 999999 这个 id 肯定不存在（示例数据里自增 id 才到 6）
        User user = userService.getById(999999L);

        // 关键断言：返回 null，而不是抛异常
        assertNull(user, "查一个不存在的 id，应该返回 null");

        log.info("【练习 1】查不存在的 id → 返回：{}", user);

        // 顺带对比："查不到"有两条触发路径，结果都是 null：
        //   ① id 根本不存在（上面这行）
        //   ② id 存在，但那行被逻辑删除了（下面这行）
        assertNull(userService.getById(5L), "id=5 被逻辑删除，同样查不到");
        log.info("【练习 1】id=5 是已逻辑删除的记录，查询同样返回 null");
    }

    // ==================== 练习 2 ====================

    /**
     * 练习 2：用 LambdaQueryWrapper 按 age > 18 且 vip = true 查询。
     * <p>
     * 验证点是"<b>两个条件都生效</b>"，不是"能查出数据"。
     * 所以断言里要检查：结果非空，且每一条都同时满足两个条件。
     * <p>
     * 按 data.sql 的数据，符合的是 admin(35,vip=1)、lisi(26,vip=1)、
     * wangwu(31,vip=1)，即 3 条。
     */
    @Test
    void practice2_lambdaQueryTwoConditions() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .gt(User::getAge, 18)        // age > 18，注意是严格大于
                .eq(User::getVip, true);     // 并且 vip = true

        List<User> users = userService.list(wrapper);

        users.forEach(u -> log.info("  【练习 2】命中 → id={}, username={}, age={}, vip={}",
                u.getId(), u.getUsername(), u.getAge(), u.getVip()));

        // 断言 1：确实查到了数据
        assertFalse(users.isEmpty(), "应该能查到符合条件的用户");

        // 断言 2：每一条都同时满足两个条件 —— 这才叫"两个条件都生效"。
        // 如果只生效了其中一个，这里就会失败。
        assertTrue(users.stream().allMatch(u -> u.getAge() > 18 && Boolean.TRUE.equals(u.getVip())),
                "结果里出现了不满足条件的记录，说明某个条件没生效");

        // 断言 3：反向验证 —— 确认某些"只满足一个条件"的用户确实被过滤掉了。
        // 这比断言"总数等于 3"更稳：以后增删数据也不会误报。
        // zhangsan(age=20,vip=0) 满足年龄但不满足 vip，就不该出现在结果里。
        assertTrue(users.stream().noneMatch(u -> "zhangsan".equals(u.getUsername())),
                "zhangsan 的 vip=false，不该出现在结果里");

        log.info("【练习 2】共命中 {} 条（示例数据里预期 2~3 条，取决于 wangwu 是否被逻辑删除）",
                users.size());
    }

    // ==================== 练习 3 ====================

    /**
     * 练习 3：新增一条 age = 200 的记录 → 抛异常，count 不变（事务回滚）。
     * <p>
     * <b>为什么 200 能通过数据库校验</b>：age 列是 {@code TINYINT UNSIGNED}，
     * 上限 255，所以 200 能正常插入。正因为"插入成功了"，
     * 后面抛异常触发回滚才有意义 —— 否则测的就是数据库约束，而不是事务。
     * <p>
     * <b>验证要点</b>：不仅要看到异常，更要确认 count 没变、
     * 数据库里查不到这条记录。
     */
    @Test
    void practice3_transactionRollback() {
        String username = "practice3_bad_age";

        long countBefore = userService.count();
        log.info("【练习 3】插入前的总记录数：{}", countBefore);

        // 构造一条 age=200 的用户。
        // Service 内部的顺序是：先 save() 真写库，再校验 age 并抛异常。
        User bad = new User();
        bad.setUsername(username);
        bad.setAge(200);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.createUser(bad),
                "age=200 应该触发业务校验并抛异常");
        log.info("【练习 3】抛出的异常信息：{}", ex.getMessage());

        // 核心断言 1：总数没变 → 说明前面那条 INSERT 被回滚了
        long countAfter = userService.count();
        assertEquals(countBefore, countAfter, "事务应该回滚，总记录数不能变化");

        // 核心断言 2：按用户名也查不到这条记录
        assertNull(userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)), "回滚后不应该留下这条数据");

        log.info("【练习 3】回滚后的总记录数：{}（与插入前一致）", countAfter);
    }

    // ==================== 练习 4 ====================

    /**
     * 练习 4：逻辑删除一条记录后 selectById → 返回 null，直接查库能看到 deleted = 1。
     * <p>
     * <b>这个练习的关键在第二半句</b>：光看"查不到"是说明不了问题的 ——
     * 物理删除也一样查不到。真正要证明的是"<b>行还在库里，只是 deleted 被置成了 1</b>"。
     * <p>
     * 所以必须绕过逻辑删除去查：用 {@code QueryWrapper} 手写条件
     * （直接写列名 {@code "id"}），MyBatis-Plus 就不会自动追加
     * {@code deleted = 0}，这样才看得到被"删"的行。
     */
    @Test
    void practice4_logicDelete() {
        Long targetId = 4L;

        // 第 1 步：确认这条记录存在
        User before = userService.getById(targetId);
        assertNotNull(before, "id=4 应该存在（data.sql 里的 wangwu）");
        log.info("【练习 4】删除前：username={}, deleted={}",
                before.getUsername(), before.getDeleted());

        // 第 2 步：逻辑删除
        boolean removed = userService.removeUser(targetId);
        assertTrue(removed, "删除操作应该返回 true");
        log.info("【练习 4】removeUser({}) 返回 {}", targetId, removed);

        // 第 3 步：通过 Service 查不到了（自动追加了 deleted = 0）
        assertNull(userService.getById(targetId),
                "逻辑删除后，正常查询应该查不到");
        log.info("【练习 4】Service 层查询结果：null");

        // 第 4 步（核心）：绕过逻辑删除，直接查物理行。
        // 注意：这一步用 QueryWrapper 是【做不到】的 —— BaseMapper 的方法
        // 一律会被追加 deleted = 0，哪怕自己写了 .select() 也一样。
        // 唯一可靠的办法是手写 SQL，见 UserMapper#selectRawById。
        User raw = userMapper.selectRawById(targetId);

        assertNotNull(raw, "物理行应该还在库里！如果这里是 null，说明发生了物理删除");
        assertEquals(1, raw.getDeleted(), "deleted 应该被置为 1");
        log.info("【练习 4】绕过逻辑删除 → 物理行还在：id={}, username={}, deleted={}",
                raw.getId(), raw.getUsername(), raw.getDeleted());

        // 第 5 步：复原数据，保证测试可以反复运行
        boolean restored = userService.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, targetId)
                .set(User::getDeleted, 0));
        log.info("【练习 4】数据已复原，结果={}", restored);
    }

    // ==================== 练习 5 ====================

    /**
     * 练习 5：用同一个实体对象连续调两次 updateById → 第二次返回 false，被乐观锁拦下。
     * <p>
     * <b>为什么不能用 Apifox 接口测</b>：
     * 走 HTTP 时每次请求都会新建一个 User 对象，
     * Controller 每次都重新从数据库拿到最新的 version，
     * 所以两次请求都会成功，永远复现不出冲突。
     * <p>
     * 乐观锁要模拟的是「<b>两个人同时读到同一份旧数据</b>」这个场景，
     * 而这只有在同一个方法里持有同一个对象时才能构造出来。
     */
    @Test
    void practice5_optimisticLock() {
        Long targetId = 2L;
        User user = userService.getById(targetId);
        assertNotNull(user, "id=2 的用户应该存在（data.sql 里的 zhangsan）");

        // 记下原始状态，测试结束要复原
        String originalNickname = user.getNickname();
        Integer versionBefore = user.getVersion();

        log.info("【练习 5】准备：id={}, nickname={}, version={}",
                targetId, originalNickname, versionBefore);

        // 关键前提：必须先把实体查出来，version 才有值。
        // 自己 new 一个只设 id 的 User，version 是 null，
        // MyBatis-Plus 就不会拼版本条件，乐观锁【完全不生效】。
        assertNotNull(versionBefore, "version 不能为 null，否则乐观锁不会生效");

        // 第 1 次更新：应该成功
        user.setNickname("第一次改");
        boolean first = userService.modifyUser(user);
        log.info("【练习 5】第 1 次：返回={}, 对象里的 version 变成={}",
                first, user.getVersion());
        assertTrue(first, "第一次更新应该成功");

        // 构造冲突：把对象"退回"到旧版本，模拟"另一个人手里还是旧数据"
        user.setVersion(versionBefore);
        log.info("【练习 5】手动把对象里的 version 退回 {}，模拟并发场景", versionBefore);

        // 第 2 次更新：应该失败
        // MyBatis-Plus 生成：UPDATE ... WHERE id=? AND version=0 AND deleted=0
        // 而数据库里 version 已经是 1 了 → 匹配不上 → 影响 0 行
        user.setNickname("第二次改");
        boolean second = userService.modifyUser(user);
        log.info("【练习 5】第 2 次：返回={}（期望 false）", second);
        assertFalse(second, "第二次更新应该被乐观锁拦下，返回 false");

        // 验证数据库状态（必须重新查，不能用手里那个被改过的对象）
        User latest = userService.getById(targetId);
        assertNotNull(latest);
        log.info("【练习 5】数据库最终值：nickname={}, version={}",
                latest.getNickname(), latest.getVersion());

        assertEquals("第一次改", latest.getNickname(),
                "数据库里应该是第一次改的值，第二次没生效");
        assertEquals(versionBefore + 1, latest.getVersion(),
                "version 只应该被第一次更新推进 1");

        // 复原数据
        boolean restored = userService.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, targetId)
                .set(User::getNickname, originalNickname)
                .set(User::getVersion, versionBefore));
        log.info("【练习 5】数据已复原，结果={}", restored);
    }
}
