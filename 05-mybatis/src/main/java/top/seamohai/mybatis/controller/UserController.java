package top.seamohai.mybatis.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.seamohai.mybatis.dto.UserQuery;
import top.seamohai.mybatis.entity.User;
import top.seamohai.mybatis.service.UserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户接口层（Controller）。
 * <p>
 * 职责很单一：<b>接收参数 → 调用 Service → 返回结果</b>。
 * 这里不写任何业务逻辑，也不直接碰 Mapper —— 业务规则和事务都在 Service 层。
 * <p>
 * {@code @RestController} = {@code @Controller} + {@code @ResponseBody}，
 * 所以每个方法的返回值会被自动转成 JSON，不需要手动序列化。
 * <p>
 * {@code @RequestMapping("/users")} 定义了这一层所有接口的公共前缀。
 * <p>
 * {@code @RequiredArgsConstructor} 是 Lombok 提供的：它会为所有
 * {@code final} 字段生成一个构造方法，Spring 就用这个构造方法把
 * UserService 注入进来。这是官方推荐的<b>构造器注入</b>写法，
 * 比 {@code @Autowired} 字段注入更好（字段可以是 final，方便测试）。
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    /** 依赖的是接口而不是实现类，方便以后替换实现或做 AOP 代理 */
    private final UserService userService;

    /* ==================== 增 ==================== */

    /**
     * 新增一个用户。
     * <p>
     * <b>请求</b>：{@code POST /users}，请求体是 JSON 格式的用户对象。
     * <p>
     * <b>返回</b>：插入成功后的用户对象。注意这里返回的是<b>传进来的那个对象本身</b>，
     * 而不是重新查一遍 —— 因为 MyBatis-Plus 在插入后会把数据库生成的自增主键
     * <b>回填</b>到对象里（这就是为什么返回的 JSON 里能带上 id）。
     * <p>
     * <b>几个细节</b>：
     * <ul>
     *   <li>请求体里没传的字段会由 Service 的 applyDefaults 补上默认值
     *       （password 默认 123456 的摘要、gender=0、memberLevel=BRONZE 等）；</li>
     *   <li>createTime / updateTime / deleted / version 由 MyMetaObjectHandler 自动填充，
     *       同样不用传；</li>
     *   <li>password 标了 {@code @JsonProperty(access = WRITE_ONLY)}，
     *       所以能传进来，但<b>不会出现在返回的 JSON 里</b>；</li>
     *   <li>如果 age 超过 150，Service 会抛异常触发事务回滚，接口返回 500，
     *       数据库里不会留下这条数据。</li>
     * </ul>
     */
    @PostMapping
    public User create(@RequestBody User user) {
        userService.createUser(user);
        return user;
    }

    /**
     * 批量新增用户。
     * <p>
     * <b>请求</b>：{@code POST /users/batch}，请求体是用户对象的<b>JSON 数组</b>。
     * <p>
     * <b>返回</b>：成功处理的条数（其实是请求里数组的长度，不是数据库真实影响行数）。
     * <p>
     * <b>为什么要有这个接口</b>：Service 内部用的是 {@code saveBatch}，
     * 也就是 JDBC 批处理，多条 INSERT 攒成一批发给数据库，
     * 比循环调单条插入快得多（网络往返次数少）。
     * 批大小由 Service 里的 BATCH_SIZE 常量控制（100）。
     * <p>
     * <b>注意</b>：整个批量操作在一个事务里，只要有一条失败，
     * 整批都会回滚，不会出现"前一半成功、后一半失败"的半截数据。
     */
    @PostMapping("/batch")
    public int createBatch(@RequestBody List<User> users) {
        userService.createUserBatch(users);
        return users.size();
    }

    /* ==================== 查 ==================== */

    /**
     * 按主键查询用户详情。
     * <p>
     * <b>请求</b>：{@code GET /users/1}，id 通过路径变量传入。
     * <p>
     * <b>返回</b>：完整的用户对象；<b>查不到时返回 null</b>（HTTP 状态码仍是 200，不报错）。
     * <p>
     * <b>两个容易忽略的点</b>：
     * <ul>
     *   <li>已逻辑删除的用户<b>查不到</b> —— MyBatis-Plus 自动给 SQL 追加了
     *       {@code AND deleted = 0}；</li>
     *   <li>tags 字段是 JSON 列，依赖实体上的 {@code autoResultMap = true}
     *       配合 {@code Jackson3TypeHandler} 才能正确反序列化成数组，
     *       漏了配置这里会返回 null。</li>
     * </ul>
     */
    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return userService.getUser(id);
    }

    /**
     * 按条件查询用户列表（不分页）。
     * <p>
     * <b>请求</b>：{@code GET /users?gender=1&minAge=20&maxAge=30}。
     * 查询条件由 {@link UserQuery} 承载，参数通过 URL 查询串传入（不是 JSON）。
     * <p>
     * <b>返回</b>：符合条件的用户列表，按 id 倒序。
     * <p>
     * <b>条件可选</b>：所有参数都不传时等价于查全部；
     * 传了哪个就用哪个，没传的条件<b>不会出现在 SQL 里</b>。
     * 支持的参数：keyword、gender、memberLevel、vip、status、minAge、maxAge。
     * <p>
     * <b>排序注意</b>：这里按 id 倒序，和 {@code /age-range} 按年龄升序不同。
     */
    @GetMapping
    public List<User> list(UserQuery query) {
        return userService.listByCondition(query);
    }

    /**
     * 分页 + 条件查询用户。
     * <p>
     * <b>请求</b>：{@code GET /users/page?current=1&size=3&gender=1}。
     * 前两个参数是分页参数，其余和 {@code /users} 的条件参数一样。
     * <p>
     * <b>返回</b>：MyBatis-Plus 的 {@link Page} 对象，序列化后的 JSON 结构是：
     * <pre>
     * {
     *   "records": [ ...本页数据... ],
     *   "total":   4,      // 符合条件的总条数
     *   "size":    3,      // 每页条数
     *   "current": 1,      // 当前第几页
     *   "pages":   2       // 总页数
     * }
     * </pre>
     * 前端做分页控件需要的信息全在里面，不用再单独查一次总数。
     * <p>
     * <b>两个坑</b>：
     * <ul>
     *   <li>{@code current} 从 <b>1</b> 开始，不是 0；</li>
     *   <li>分页能生效的前提是注册了 {@code PaginationInnerInterceptor} 插件
     *       （见 MybatisPlusConfig），没注册的话 SQL 里不会拼 LIMIT，
     *       会把整张表的数据全查出来。</li>
     * </ul>
     */
    @GetMapping("/page")
    public Page<User> page(UserQuery query,
                           @RequestParam(defaultValue = "1") long current,
                           @RequestParam(defaultValue = "10") long size) {
        return userService.pageByCondition(query, current, size);
    }

    /**
     * 关键词模糊查询。
     * <p>
     * <b>请求</b>：{@code GET /users/keyword?keyword=张}。
     * <p>
     * <b>返回</b>：username <b>或</b> nickname 包含该关键词的用户列表，按 id 升序。
     * <p>
     * <b>为什么参数是必填的</b>：这里是 {@code @RequestParam String keyword}
     * （没写 required = false），所以不传参数会直接报 400。
     * <p>
     * <b>一个 SQL 细节</b>：生成的条件是
     * {@code WHERE ... AND (username LIKE ? OR nickname LIKE ?)}，
     * 那个<b>括号很关键</b> —— 没有它，OR 会把前面的所有条件一起"放开"，
     * 变成 {@code (其它条件) OR username LIKE ?}，结果就错了。
     */
    @GetMapping("/keyword")
    public List<User> keyword(@RequestParam String keyword) {
        return userService.searchByKeyword(keyword);
    }

    /**
     * 按年龄区间查询用户。
     * <p>
     * <b>请求</b>：{@code GET /users/age-range?minAge=20&maxAge=25}。
     * <p>
     * <b>返回</b>：年龄在区间内的用户，按<b>年龄升序</b>排列。
     * <p>
     * <b>两个参数都可选</b>（{@code required = false}）：
     * <ul>
     *   <li>只传 minAge → 只有下界，SQL 里只有 {@code age >= ?}；</li>
     *   <li>只传 maxAge → 只有上界；</li>
     *   <li>都不传 → 没有区间条件，等于查全部。</li>
     * </ul>
     * 实现上靠的是条件构造器的"第一个参数是布尔值"这个重载：
     * 值为 false 时该条件直接不进 SQL。
     */
    @GetMapping("/age-range")
    public List<User> ageRange(@RequestParam(required = false) Integer minAge,
                               @RequestParam(required = false) Integer maxAge) {
        return userService.listByAgeRange(minAge, maxAge);
    }

    /**
     * 取积分最高的前 N 个用户。
     * <p>
     * <b>请求</b>：{@code GET /users/top?limit=2}，不传 limit 默认取 3 条。
     * <p>
     * <b>返回</b>：按 points 倒序的前 N 条。
     * <p>
     * <b>为什么单独做一个接口</b>：这是"排行榜"这类需求的通用形态，
     * 底层用 {@code .last("LIMIT " + size)} 把 LIMIT 直接拼到 SQL 末尾。
     * <p>
     * <b>安全提醒</b>：{@code last()} 是<b>原样拼字符串</b>，不走预编译占位符，
     * 所以<b>绝对不能拼用户传进来的原始字符串</b>（会 SQL 注入）。
     * 这里 Service 里做了 {@code Math.min(limit, 50)} 的钳制，
     * 保证拼进去的一定是算过的数字，不会超过 50 条。
     */
    @GetMapping("/top")
    public List<User> top(@RequestParam(defaultValue = "3") Integer limit) {
        return userService.listTop(limit);
    }

    /**
     * 只查询所有用户名。
     * <p>
     * <b>请求</b>：{@code GET /users/usernames}，无参数。
     * <p>
     * <b>返回</b>：字符串数组，比如 {@code ["admin","zhangsan","lisi","wangwu"]}。
     * <p>
     * <b>意义在于"只查一列"</b>：底层用 {@code .select(User::getUsername)}
     * 指定了只查 username 这一个字段，SQL 变成
     * {@code SELECT username FROM t_user ...}，
     * 而不是把所有 23 个字段都查出来 —— 数据量小，也更快。
     * <p>
     * 这种写法适合做下拉框、自动补全这类只要一个字段的场景。
     */
    @GetMapping("/usernames")
    public List<String> usernames() {
        return userService.listUsernames();
    }

    /**
     * 按标签查询用户（查询 JSON 列）。
     * <p>
     * <b>请求</b>：{@code GET /users/tag?tag=Java}。
     * <p>
     * <b>返回</b>：tags 数组里包含该标签的用户，按 id 升序。
     * <p>
     * <b>底层用的是 MySQL 的 JSON 函数</b>：
     * {@code WHERE JSON_CONTAINS(tags, JSON_QUOTE(?))}。
     * 也就是说 tags 这一列虽然存的是 JSON 文本，但可以直接在数据库里查询，
     * 这是 MySQL 5.7+ 才支持的能力。
     * <p>
     * <b>关于 {0} 占位符</b>：{@code apply()} 里的 {@code {0}} 会被 MyBatis
     * 替换成预编译占位符 {@code ?}，拼进去的是<b>参数值</b>而不是 SQL 片段，
     * 所以这里是安全的，不像 {@code last()} 那样有注入风险。
     */
    @GetMapping("/tag")
    public List<User> byTag(@RequestParam String tag) {
        return userService.listByTag(tag);
    }

    /**
     * 判断用户名是否已被占用。
     * <p>
     * <b>请求</b>：{@code GET /users/exists?username=admin}。
     * <p>
     * <b>返回</b>：{@code true} 表示已存在，{@code false} 表示可用。
     * <p>
     * <b>典型用途</b>：注册页面用户输入用户名时实时提示"该用户名已被注册"。
     * <p>
     * <b>但这挡不住并发</b>：两个请求同时查到"不存在"、然后同时插入，
     * 依然会出问题。真正的唯一性靠数据库的 {@code uk_username} 唯一索引保证，
     * 这个方法只是给用户一个友好提示，<b>不能替代数据库约束</b>。
     */
    @GetMapping("/exists")
    public boolean exists(@RequestParam String username) {
        return userService.existsUsername(username);
    }

    /**
     * 统计用户数量。
     * <p>
     * <b>请求</b>：{@code GET /users/count?gender=1}，gender 可以不传。
     * <p>
     * <b>返回</b>：符合条件的用户总数。传了 gender 就只统计该性别，
     * 不传则统计全部。
     * <p>
     * <b>注意</b>：统计结果自动排除了逻辑删除的记录（{@code deleted = 1} 不计入）。
     */
    @GetMapping("/count")
    public long count(@RequestParam(required = false) Integer gender) {
        return userService.countByGender(gender);
    }

    /**
     * 汇总统计。
     * <p>
     * <b>请求</b>：{@code GET /users/statistics}，无参数。
     * <p>
     * <b>返回</b>：一个 Map，包含两部分：
     * <ul>
     *   <li><b>整体聚合</b>（单行）：{@code totalCount}（总人数）、
     *       {@code totalBalance}（余额总和）、{@code maxAge}（最大年龄）；</li>
     *   <li><b>分组统计</b>：{@code genderCount}，是按性别分组的数组，
     *       每组有 gender 和 cnt 两个值。</li>
     * </ul>
     * <p>
     * <b>实现方式比较特殊</b>：它没有走普通的实体查询，
     * 而是用 {@code getMap()} 取单行聚合结果、{@code listMaps()} 取多行分组结果，
     * SQL 里直接写了 {@code COUNT(*)}、{@code SUM(balance)}、{@code GROUP BY gender}
     * 这类聚合函数 —— 这是 MyBatis-Plus 在"统计报表"场景下的用法，
     * 返回的不再是实体对象，而是原始的 Map。
     */
    @GetMapping("/statistics")
    public Map<String, Object> statistics() {
        return userService.statistics();
    }

    /* ==================== 改 ==================== */

    /**
     * 按主键更新用户。
     * <p>
     * <b>请求</b>：{@code PUT /users/2}，请求体是<b>只包含要改的字段</b>的 JSON，
     * 比如 {@code {"nickname":"张三改名","age":21}}。
     * <p>
     * <b>返回</b>：更新<b>之后</b>从数据库重新查出来的完整用户对象
     * （而不是把请求体原样返回）—— 这样调用方能立刻看到 updateTime 等被自动填充后的最新值。
     * <p>
     * <b>两个细节</b>：
     * <ul>
     *   <li>方法开头有一句 {@code user.setId(id)}：把路径里的 id 塞进对象。
     *       这样即使请求体里没写 id（或者写了错的 id），也以 URL 上的为准，
     *       避免"路径说改 2 号、体里却写了 3 号"这种不一致；</li>
     *   <li>MyBatis-Plus 默认<b>只更新非 null 字段</b>，
     *       所以请求体里没传的字段不会被覆盖成 null，不用担心误清空数据。</li>
     * </ul>
     * <p>
     * <b>乐观锁的前提</b>：只有请求体里带了 {@code version} 字段时，
     * SQL 才会拼上 {@code AND version = ?} 做并发校验。
     * 如果客户端不传 version（比如 {@code {"nickname":"x"}}），
     * 这次更新就<b>不做版本检查</b>。要不要让前端回传版本号，
     * 取决于业务对并发冲突的容忍程度。
     */
    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        userService.modifyUser(user);
        return userService.getUser(id);
    }

    /**
     * 只修改用户状态（启用 / 禁用）。
     * <p>
     * <b>请求</b>：{@code PUT /users/2/status?status=0}。
     * <p>
     * <b>返回</b>：{@code true} 表示更新成功，{@code false} 表示没有匹配的记录。
     * <p>
     * <b>为什么不用上面的 PUT /users/{id}</b>：那个接口需要传整个用户 JSON，
     * 而"启用/禁用"这种操作只需要改一个字段。这里用的是
     * {@code LambdaUpdateWrapper} 的 {@code .set()} 方法，
     * 生成的 SQL 只更新 status 一个列：
     * {@code UPDATE t_user SET status=?, update_time=? WHERE id=? AND deleted=0}。
     * <p>
     * <b>注意这里没有做乐观锁校验</b> —— 因为它没有先把实体查出来，
     * 实体上没有 version 值，MP 就不会拼版本条件。
     * 对"改状态"这种操作来说通常是可接受的。
     */
    @PutMapping("/{id}/status")
    public boolean updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return userService.modifyUserStatus(id, status);
    }

    /**
     * 给用户充值（余额累加）。
     * <p>
     * <b>请求</b>：{@code POST /users/3/recharge?amount=100.25}。
     * <p>
     * <b>返回</b>：充值<b>之后</b>的用户对象，里面能看到新的 balance 和 version。
     * <p>
     * <b>为什么是 POST 而不是 PUT</b>：严格说充值不是"替换整个资源"，
     * 而是"对余额做一次加法"，属于动作型接口，用 POST 更贴切。
     * <p>
     * <b>这个接口是乐观锁的最佳示例</b>：Service 内部先把用户查出来
     * （此时实体带上了 version），改了 balance 之后再 updateById，
     * MP 生成的 SQL 会自动带上 {@code AND version = ?} 并把版本加一。
     * 如果这期间有别人改过这条记录，更新会影响 0 行，本次充值就失败了。
     * <p>
     * <b>参数校验在 Service 里</b>：amount 为 null 或小于等于 0 会抛异常，
     * 接口返回 500。所以余额是 {@code DECIMAL} 而不是浮点，
     * 多次充值累加不会出现精度误差。
     */
    @PostMapping("/{id}/recharge")
    public User recharge(@PathVariable Long id, @RequestParam BigDecimal amount) {
        userService.recharge(id, amount);
        return userService.getUser(id);
    }

    /* ==================== 删 ==================== */

    /**
     * 按主键删除用户。
     * <p>
     * <b>请求</b>：{@code DELETE /users/1}。
     * <p>
     * <b>返回</b>：{@code true} 表示删除成功，{@code false} 表示没找到该记录
     * （比如 id 不存在，或者它已经被删过了）。
     * <p>
     * <b>这是"逻辑删除"，不是真删！</b> 实体上有 {@code @TableLogic} 注解，
     * 所以实际执行的是：
     * {@code UPDATE t_user SET deleted=1, update_time=? WHERE id=? AND deleted=0}。
     * <p>
     * 也就是说：<b>数据行还躺在库里</b>，只是被打了删除标记。
     * <ul>
     *   <li>之后通过任何正常查询都<b>查不到</b>它（自动追加 {@code deleted = 0}）；</li>
     *   <li>想看到它，必须绕过逻辑删除条件直接查库；</li>
     *   <li>好处是数据可恢复、可追溯，坏处是表会持续变大，
     *       真正清理要靠定时任务或归档脚本。</li>
     * </ul>
     */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return userService.removeUser(id);
    }

    /**
     * 批量删除用户（同样是逻辑删除）。
     * <p>
     * <b>请求</b>：{@code DELETE /users/batch?ids=8,9}。
     * Spring 会自动把逗号分隔的字符串转成 {@code List<Long>}。
     * <p>
     * <b>返回</b>：{@code true} 表示操作执行了，
     * 但<b>不能从返回值判断删掉了几条</b> —— 它只表示"没有异常"。
     * 想确认实际影响行数，得看 SQL 日志。
     * <p>
     * {@code @DeleteMapping} 本身不能带请求体（HTTP 规范里 DELETE 的 body
     * 支持情况不一致），所以这里用查询参数传 ids，而不是 JSON 数组。
     */
    @DeleteMapping("/batch")
    public boolean deleteBatch(@RequestParam List<Long> ids) {
        return userService.removeUserBatch(ids);
    }

    /**
     * 按状态批量删除（同样是逻辑删除）。
     * <p>
     * <b>请求</b>：{@code DELETE /users/status/0}，删除所有 status=0（已禁用）的用户。
     * <p>
     * <b>返回</b>：{@code true} 表示执行成功。
     * <p>
     * <b>底层 SQL</b>：{@code UPDATE t_user SET deleted=1, update_time=? WHERE status=? AND deleted=0}。
     * 末尾那个 {@code deleted=0} 是逻辑删除自动追加的，不用自己写。
     * <p>
     * <b>⚠️ 这类接口要小心</b>：它一次会影响多条记录，
     * 如果 status 传错（比如传了 1），可能把大量正常用户"删"掉。
     * 真实项目里这种批量操作一般会加权限校验和二次确认。
     */
    @DeleteMapping("/status/{status}")
    public boolean deleteByStatus(@PathVariable Integer status) {
        return userService.removeUserByStatus(status);
    }
}
