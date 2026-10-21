package top.seamohai.mybatis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.seamohai.mybatis.dto.UserQuery;
import top.seamohai.mybatis.entity.User;
import top.seamohai.mybatis.mapper.UserMapper;
import top.seamohai.mybatis.service.UserService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String DEFAULT_PASSWORD = "123456";
    private static final int BATCH_SIZE = 100;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean createUser(User user) {
        applyDefaults(user);

        // 第一步：先写库
        save(user);

        // 第二步：后续业务校验失败，抛异常观察第一步是否被回滚
        // age 列是 TINYINT UNSIGNED，-1 这类负数在数据库层就会被拒绝，这里校验的是业务上不合理的取值
        if (user.getAge() != null && (user.getAge() < 0 || user.getAge() > 150)) {
            throw new RuntimeException("年龄超出合理范围，回滚");
        }
        return true;
    }

    /**
     * 批量新增用户。
     * <p>
     * 用 saveBatch 而不是 for 循环里一条条 save：前者是 JDBC 批处理，
     * 把多条 INSERT 攒起来一次发给数据库，网络往返次数少，快很多。
     * 第二个参数是批大小，攒够 BATCH_SIZE 条就发一批。
     * <p>
     * 这里必须加 @Transactional：批量写要么全成功、要么全回滚，
     * 不能出现"前 50 条进去了、后 50 条失败"的半截数据。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean createUserBatch(List<User> users) {
        if (users == null || users.isEmpty()) {
            return false;
        }
        // 每条都补默认值，否则 NOT NULL 的 password / gender / member_level 会插入失败
        users.forEach(this::applyDefaults);
        return saveBatch(users, BATCH_SIZE);
    }

    /**
     * 按主键删除用户。
     * <p>
     * 注意：实体上有 @TableLogic，所以 removeById 实际执行的是
     * UPDATE t_user SET deleted = 1 WHERE id = ?，物理行还在库里。
     * 这是逻辑删除，不是真删。
     */
    @Override
    public boolean removeUser(Long id) {
        return removeById(id);
    }

    /**
     * 按主键批量删除（同样是逻辑删除）。
     */
    @Override
    public boolean removeUserBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return removeByIds(ids);
    }

    /**
     * 按状态批量删除。
     * <p>
     * 用条件构造器指定删除条件，等价于：
     * UPDATE t_user SET deleted = 1 WHERE status = ? AND deleted = 0
     * 后面的 deleted = 0 是逻辑删除自动追加的，不用自己写。
     */
    @Override
    public boolean removeUserByStatus(Integer status) {
        return remove(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, status));
    }

    @Override
    public boolean modifyUser(User user) {
        if (user.getId() == null) {
            throw new IllegalArgumentException("更新时必须携带主键 id");
        }
        if (getById(user.getId()) == null) {
            throw new IllegalArgumentException("用户不存在：" + user.getId());
        }
        return updateById(user);
    }

    @Override
    public boolean modifyUserStatus(Long id, Integer status) {
        return update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getStatus, status));
    }

    @Override
    public boolean recharge(Long id, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("充值金额必须大于 0");
        }
        User user = getById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在：" + id);
        }
        user.setBalance(user.getBalance().add(amount));
        // 实体带 version，updateById 会拼上 AND version = ? 并把版本加一
        return updateById(user);
    }

    @Override
    public boolean modifyEmailByUsername(String username, String email) {
        // 注意这里是 || 不是 &&：只要【任意一个】为空就要拦下来。
        // 写成 && 就成了"两个都为空才拦"，逻辑正好反了。
        // 另外用 hasText 而不是 isEmpty：isEmpty("   ") 是 false，
        // 纯空白字符串会被放过。
        if (!StringUtils.hasText(username) || !StringUtils.hasText(email)) {
            throw new IllegalArgumentException("用户名和邮箱都不能为空");
        }
        return update(new LambdaUpdateWrapper<User>()
                .eq(User::getUsername, username)
                .set(User::getEmail, email));
    }

    /**
     * 按主键查用户。
     * <p>
     * 查不到返回 null（不抛异常），调用方要自己判空。
     * SQL 会自动带上 deleted = 0，所以已逻辑删除的用户查不到。
     */
    @Override
    public User getUser(Long id) {
        return getById(id);
    }

    /**
     * 查全部用户。
     * <p>
     * 同样自动过滤掉 deleted = 1 的行，所以 data.sql 里那条被删的 zhaoliu 不会出现。
     */
    @Override
    public List<User> listUsers() {
        return list();
    }

    /**
     * 分页查全部用户。
     * <p>
     * Page(current, size) 里 current 是第几页（从 1 开始），size 是每页条数。
     * 返回的 Page 对象上能拿到 records（当页数据）、total（总条数）、
     * pages（总页数）、current、size 这些信息，前端直接就能用。
     * <p>
     * 前提是注册了 PaginationInnerInterceptor 插件（见 MybatisPlusConfig），
     * 否则分页不生效，会一次性把所有数据都查出来。
     */
    @Override
    public Page<User> pageUsers(long current, long size) {
        return page(new Page<>(current, size));
    }

    /**
     * 按条件查用户列表（条件全为空时等价于查全部）。
     */
    @Override
    public List<User> listByCondition(UserQuery query) {
        return list(buildCondition(query));
    }

    /**
     * 按条件分页查用户。
     */
    @Override
    public Page<User> pageByCondition(UserQuery query, long current, long size) {
        return page(new Page<>(current, size), buildCondition(query));
    }

    @Override
    public List<User> searchByKeyword(String keyword) {
        return list(new LambdaQueryWrapper<User>()
                .and(StringUtils.hasText(keyword),
                        w -> w.like(User::getUsername, keyword)
                                .or()
                                .like(User::getNickname, keyword))
                .orderByAsc(User::getId));
    }

    /**
     * 按年龄区间查用户，按年龄升序返回。
     * <p>
     * between 的 min / max 是允许为 null 的：哪个为 null，哪个条件就不进 SQL。
     * 比如只传 minAge=20，生成的 SQL 就只有 age >= 20，没有上界。
     */
    @Override
    public List<User> listByAgeRange(Integer minAge, Integer maxAge) {
        return list(new LambdaQueryWrapper<User>()
                .ge(minAge != null, User::getAge, minAge)
                .le(maxAge != null, User::getAge, maxAge)
                .orderByAsc(User::getAge));
    }

    @Override
    public List<User> listTop(Integer limit) {
        int size = limit == null || limit <= 0 ? 3 : Math.min(limit, 50);
        return list(new LambdaQueryWrapper<User>()
                .orderByDesc(User::getPoints)
                .last("LIMIT " + size));
    }

    @Override
    public List<String> listUsernames() {
        return listObjs(new LambdaQueryWrapper<User>()
                .select(User::getUsername)
                .orderByAsc(User::getId), Object::toString);
    }

    @Override
    public List<User> listByTag(String tag) {
        return list(new LambdaQueryWrapper<User>()
                .apply("JSON_CONTAINS(tags, JSON_QUOTE({0}))", tag)
                .orderByAsc(User::getId));
    }

    /**
     * 判断用户名是否已存在。
     * <p>
     * 用 exists 而不是先 count 再比大小：exists 生成的是
     * SELECT EXISTS(...) 或 LIMIT 1 这类查询，找到一条就返回，不用扫完整个表。
     * <p>
     * 注意 username 列上有唯一索引 uk_username，真正的唯一性靠数据库保证，
     * 这个方法主要用于"注册前先给用户一个友好提示"，挡不住并发。
     */
    @Override
    public boolean existsUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return exists(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    /**
     * 统计某个性别的用户数。gender 传 null 时统计全部。
     */
    @Override
    public long countByGender(Integer gender) {
        return count(new LambdaQueryWrapper<User>()
                .eq(gender != null, User::getGender, gender));
    }

    @Override
    public Map<String, Object> statistics() {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> aggregate = getMap(new QueryWrapper<User>()
                .select("COUNT(*) AS totalCount",
                        "IFNULL(SUM(balance), 0) AS totalBalance",
                        "IFNULL(MAX(age), 0) AS maxAge"));
        if (aggregate != null) {
            result.putAll(aggregate);
        }

        result.put("genderCount", listMaps(new QueryWrapper<User>()
                .select("gender", "COUNT(*) AS cnt")
                .groupBy("gender")
                .orderByAsc("gender")));
        return result;
    }

    /**
     * 根据查询条件拼装 LambdaQueryWrapper。
     * <p>
     * 核心技巧：每个条件的第一个参数是 boolean，为 false 时这一小段就不进 SQL。
     * 所以调用方传 null 的字段会自动被跳过，不用写一堆 if 判断。
     * <p>
     * 三个容易被忽略的点：
     * <ol>
     *   <li>query 本身可能为 null（比如 GET /users 不带任何参数），
     *       这里兜底成空对象，后面就不用反复判空了；</li>
     *   <li>keyword 要用 and(w -> ...) 包一层括号，
     *       否则它里面的 or 会把前面所有条件一起"放开"，
     *       变成 (gender=1 AND status=1) OR username LIKE ...，结果就不对了；</li>
     *   <li>orderByDesc 是最后加的，SQL 末尾会拼上 ORDER BY id DESC。</li>
     * </ol>
     */
    private LambdaQueryWrapper<User> buildCondition(UserQuery query) {
        UserQuery condition = query == null ? new UserQuery() : query;
        return new LambdaQueryWrapper<User>()
                .eq(condition.getGender() != null, User::getGender, condition.getGender())
                .eq(condition.getStatus() != null, User::getStatus, condition.getStatus())
                .eq(condition.getVip() != null, User::getVip, condition.getVip())
                .eq(StringUtils.hasText(condition.getMemberLevel()),
                        User::getMemberLevel, condition.getMemberLevel())
                .ge(condition.getMinAge() != null, User::getAge, condition.getMinAge())
                .le(condition.getMaxAge() != null, User::getAge, condition.getMaxAge())
                .and(StringUtils.hasText(condition.getKeyword()),
                        w -> w.like(User::getUsername, condition.getKeyword())
                                .or()
                                .like(User::getNickname, condition.getKeyword()))
                .orderByDesc(User::getId);
    }

    private void applyDefaults(User user) {
        if (!StringUtils.hasText(user.getUsername())) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            user.setPassword(sha256(DEFAULT_PASSWORD));
        }
        if (user.getGender() == null) {
            user.setGender(0);
        }
        if (user.getMemberLevel() == null) {
            user.setMemberLevel("BRONZE");
        }
        if (user.getPoints() == null) {
            user.setPoints(0);
        }
        if (user.getBalance() == null) {
            user.setBalance(BigDecimal.ZERO);
        }
        if (user.getVip() == null) {
            user.setVip(false);
        }
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }
}
