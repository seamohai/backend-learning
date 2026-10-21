package top.seamohai.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.seamohai.mybatis.entity.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按主键查询记录，<b>绕过逻辑删除过滤</b>。
     * <p>
     * 为什么需要它：实体上有 {@code @TableLogic}，BaseMapper 的所有查询方法
     * 都会被自动追加 {@code AND deleted = 0}，所以逻辑删除之后
     * 用 {@code selectById} 永远查不到那一行。
     * <p>
     * 但有时候就是需要看到"已经被删掉"的数据，比如：
     * <ul>
     *   <li>验证逻辑删除确实是打标记、而不是把行真的删了；</li>
     *   <li>做数据恢复功能，把误删的记录改回 {@code deleted = 0}；</li>
     *   <li>做归档/清理任务，查出所有已删除的记录统一处理。</li>
     * </ul>
     * <p>
     * 关键点：这里用 {@code @Select} <b>手写原生 SQL</b>，
     * MyBatis-Plus 的逻辑删除拦截器<b>不会改写手写的 SQL</b>，
     * 所以这条语句能查到 {@code deleted = 1} 的行。
     * <p>
     * ⚠️ 查出来的可能是"已删除"的数据，业务代码里不要随意使用 ——
     * 它主要是给测试和数据维护场景用的。
     *
     * @param id 主键
     * @return 该行记录（无论是否被逻辑删除）；不存在时返回 null
     */
    @Select("SELECT id, username, nickname, deleted, version FROM t_user WHERE id = #{id}")
    User selectRawById(@Param("id") Long id);

    /**
     * 模拟慢查询：让数据库"睡"指定秒数再返回。<b>仅用于连接池压测。</b>
     * <p>
     * 为什么需要它：本机 MySQL 太快，一条普通查询只要 1~3 ms，
     * 连接占用时间极短，无论并发多高都很难把连接池压到超时。
     * 这个 SQL 用 MySQL 的 {@code SLEEP(n)} 函数强行让这条查询耗时 n 秒，
     * 于是连接会被<b>持续占用 n 秒</b>，这样才能复现
     * {@code Connection is not available, request timed out}。
     * <p>
     * <b>⚠️ 生产代码里绝对不会出现这种 SQL</b>，它纯粹是教学实验用的探针 ——
     * 用来把"慢 SQL / 长事务占住连接"这个抽象的因果，
     * 变成可以亲眼看到的超时异常。
     *
     * @param seconds 睡眠秒数
     * @return 固定返回 1（{@code SELECT SLEEP(n)} 的结果是 0 或 1）
     */
    @Select("SELECT SLEEP(#{seconds})")
    Integer slowQuery(@Param("seconds") int seconds);
}
