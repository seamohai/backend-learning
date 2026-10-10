package top.seamohai.mybatis.mapper;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.seamohai.mybatis.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mapper 层基础 CRUD 测试（直接操作数据库，不经过 Service）。
 * <p>
 * <b>和 UserServiceTest 的区别</b>：
 * <ul>
 *   <li>这个类直接调 {@code UserMapper}，验证的是<b>数据访问</b>本身；</li>
 *   <li>{@code UserServiceTest} 走 Service，验证的是<b>业务逻辑</b>
 *       （默认值填充、事务回滚、乐观锁等）。</li>
 * </ul>
 * <p>
 * <b>为什么要分开测</b>：这样能快速区分问题出在哪一层 ——
 * 如果这里的测试挂了，说明是数据访问的问题；
 * 如果这里过了、Service 那边挂了，说明是业务逻辑的问题。
 * <p>
 * <b>这个类直接调 Mapper，绕过了 Service 的 applyDefaults</b>，
 * 所以每条数据都必须自己把 NOT NULL 的字段填全
 * （password / gender / memberLevel），否则会报
 * {@code Field 'password' doesn't have a default value}。
 * <p>
 * 类上加 {@code @Transactional}，每个测试方法结束后自动回滚，
 * 不会在库里留下脏数据。
 */
@SpringBootTest
@Transactional
@Slf4j
class UserMapperCrudTest {

    @Autowired
    private UserMapper userMapper;

    /**
     * 构造一个字段填全的合法用户。
     * <p>
     * 因为绕过了 Service，这里必须手动补上所有 NOT NULL 且无默认值的列。
     * 注意 create_time / update_time / deleted / version 不用管 ——
     * 它们由 MyMetaObjectHandler 自动填充。
     */
    private User newValidUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92");
        user.setNickname("测试用户");
        user.setGender(1);
        user.setAge(25);
        user.setMemberLevel("BRONZE");
        user.setPoints(0);
        user.setBalance(BigDecimal.ZERO);
        user.setVip(false);
        user.setStatus(1);
        return user;
    }

    /**
     * 插入：主键回填 + 自动填充。
     * <p>
     * 验证两个 MyBatis-Plus 的关键能力：
     * <ol>
     *   <li>{@code IdType.AUTO} 会把数据库生成的自增主键<b>回填</b>到实体对象；</li>
     *   <li>{@code MyMetaObjectHandler} 会自动填 createTime 等审计字段。</li>
     * </ol>
     */
    @Test
    void testInsert() {
        User user = newValidUser("crud_insert");

        int rows = userMapper.insert(user);
        assertEquals(1, rows, "应该影响 1 行");

        // ① 主键回填：插入前 id 是 null，插入后被数据库的值填上了
        assertNotNull(user.getId(), "IdType.AUTO 应该把自增主键回填到对象里");
        log.info("【插入】主键已回填 → id={}", user.getId());

        // ② 自动填充：我们没设过 createTime，但它有值
        assertNotNull(user.getCreateTime(), "createTime 应该被自动填充");
        assertNotNull(user.getUpdateTime(), "updateTime 应该被自动填充");
        assertEquals(0, user.getVersion(), "version 应该被自动填充为 0");
        assertEquals(0, user.getDeleted(), "deleted 应该被自动填充为 0");

        log.info("【插入】自动填充 → createTime={}, updateTime={}, deleted={}, version={}",
                user.getCreateTime(), user.getUpdateTime(), user.getDeleted(), user.getVersion());
    }

    /**
     * 查询：按主键查，并验证下划线转驼峰映射。
     * <p>
     * data.sql 里 id=1 是 admin，它的 create_time 在库里是
     * {@code 2026-09-01 10:00:00}，映射到 Java 属性是 {@code createTime}。
     */
    @Test
    void testSelectById() {
        User user = userMapper.selectById(1L);

        assertNotNull(user, "id=1 应该存在（data.sql 里的 admin）");
        assertEquals("admin", user.getUsername());

        // 验证驼峰映射：库里的 create_time → Java 的 createTime
        assertNotNull(user.getCreateTime(), "create_time 应该被映射到 createTime");

        // 验证 JSON 列：tags 依赖 autoResultMap = true 才能反序列化成数组
        assertNotNull(user.getTags(), "tags 是 JSON 列，不该是 null");
        assertTrue(user.getTags().contains("管理员"), "tags 应该被反序列化成数组");

        log.info("【查询】id=1 → username={}, createTime={}, tags={}",
                user.getUsername(), user.getCreateTime(), user.getTags());
    }

    /**
     * 查询不存在的 id：返回 null，不抛异常。
     */
    @Test
    void testSelectNotExist() {
        User user = userMapper.selectById(999999L);
        assertNull(user, "查不到的 id 应该返回 null");
        log.info("【查询】不存在的 id → {}", user);
    }

    /**
     * 删除：验证它被改写成了逻辑删除（UPDATE），而不是物理 DELETE。
     */
    @Test
    void testDeleteIsLogical() {
        Long id = 3L;

        int rows = userMapper.deleteById(id);
        assertEquals(1, rows, "应该影响 1 行");

        // 通过 Mapper 查不到了（自动追加 deleted = 0）
        assertNull(userMapper.selectById(id), "逻辑删除后查不到");

        // 但物理行还在，deleted 已经变成 1
        User raw = userMapper.selectRawById(id);
        assertNotNull(raw, "物理行应该还在");
        assertEquals(1, raw.getDeleted(), "deleted 应该被置为 1");

        log.info("【删除】逻辑删除生效 → 物理行还在，deleted={}", raw.getDeleted());
    }

    /**
     * 更新：验证只更新非 null 字段。
     * <p>
     * MyBatis-Plus 默认的字段策略是 NOT_NULL，
     * 只把非 null 的字段拼进 SET 子句，所以不用担心误把没传的字段清空。
     */
    @Test
    void testUpdateByIdOnlyNonNullFields() {
        User user = userMapper.selectById(2L);
        assertNotNull(user);

        String originalUsername = user.getUsername();

        // 只改 nickname
        user.setNickname("只改了昵称");
        int rows = userMapper.updateById(user);
        assertEquals(1, rows, "应该影响 1 行");

        // 重新查，确认 username 没被清空（因为只更新非 null 字段）
        User updated = userMapper.selectById(2L);
        assertEquals("只改了昵称", updated.getNickname());
        assertEquals(originalUsername, updated.getUsername(),
                "username 没传，不该被改动");

        log.info("【更新】nickname={}, username 保持={}",
                updated.getNickname(), updated.getUsername());
    }
}
