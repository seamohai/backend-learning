package top.seamohai.mybatis.service;

import com.baomidou.mybatisplus.spring.service.IService;
import top.seamohai.mybatis.entity.Order;

import java.util.List;

/**
 * 订单服务（练习 5：跨表事务原子性）。
 * <p>
 * <b>你要实现的只有 {@link #createOrder} 一个方法</b>，它在实现类里。
 * 下面三个方法都已经/将由实现类提供。
 */
public interface OrderService extends IService<Order> {

    /**
     * 下单：<b>扣课程名额 + 创建订单</b>，两步在同一个事务里。
     * <p>
     * <b>这是练习 5 的核心。</b>实现要求：
     * <ol>
     *   <li>方法上必须加 {@code @Transactional(rollbackFor = Exception.class)}；
     *       不加的话两步各自独立提交，"原子性"无从谈起；</li>
     *   <li><b>先扣名额</b>（调 {@code courseService.deductStock(courseId)}）。
     *       它返回 {@code false} 表示名额不足/课程不存在，此时<b>必须主动抛异常</b>——
     *       因为它只是"影响 0 行"，<b>不会自己抛异常，事务也就不会回滚</b>；</li>
     *   <li><b>再建订单</b>（{@code save(order)}），订单号要唯一；</li>
     *   <li>两步的顺序建议"先扣库存、后建订单"：<b>扣不动就别建订单</b>，
     *       少一次无谓的写入。</li>
     * </ol>
     * <p>
     * <b>怎么验证原子性</b>：故意让第二步失败（比如传一个<b>已经存在的订单号</b>，
     * 撞上 {@code uk_order_no} 唯一索引），然后检查<b>第一步扣掉的库存有没有被还原</b>。
     * 还原了 = 事务生效；没还原 = 事务没生效。
     *
     * @param courseId 课程 id
     * @param userId   下单用户 id
     * @param orderNo  订单号（需唯一，重复会触发唯一索引冲突 → 用来验证回滚）
     * @return true = 下单成功
     */
    boolean createOrder(Long courseId, Long userId, String orderNo);

    /**
     * 查询某个课程下的所有订单。
     * <p>
     * 顺带复习 {@code LambdaQueryWrapper}：按 courseId 过滤，按 id 倒序。
     * 注意已逻辑删除的订单不会出现（自动追加 {@code deleted = 0}）。
     */
    List<Order> listByCourse(Long courseId);

    /**
     * 取消订单：把状态改成 0，并把名额<b>还回去</b>。
     * <p>
     * 又是一个跨表事务：改 {@code t_order} + 改 {@code t_course}。
     * <p>
     * <b>加分项</b>：还给谁要判断——只有"当前状态是已创建"的订单才需要还名额，
     * 重复取消不能重复还。想想条件更新怎么写（提示：{@code eq(status, 1)} 放进 WHERE）。
     */
    boolean cancelOrder(Long orderId);
}
