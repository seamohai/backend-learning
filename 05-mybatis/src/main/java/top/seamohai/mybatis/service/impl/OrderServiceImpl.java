package top.seamohai.mybatis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.seamohai.mybatis.entity.Order;
import top.seamohai.mybatis.mapper.OrderMapper;
import top.seamohai.mybatis.service.CourseService;
import top.seamohai.mybatis.service.OrderService;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单服务实现（练习 5）。
 * <p>
 * ============================================================
 * <b>你要动手的地方只有一个：{@link #createOrder} 方法体</b>
 * ============================================================
 * <p>
 * 其余方法已经写好，照着看能明白本类的风格。
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    /** 跨表操作要调课程服务，所以这里注入它（Spring 会自动装配） */
    private final CourseService courseService;

    /**
     * 构造器注入。
     * <p>
     * <b>注意这里为什么必须写构造方法</b>：{@code ServiceImpl} 没有无参构造之外的
     * 可用构造，而我们要额外注入 {@code CourseService}。
     * 用构造器注入的好处是依赖可以是 {@code final}，且启动时就确定不为 null。
     * <p>
     * （如果嫌麻烦，也可以改成字段注入 {@code @Autowired private CourseService courseService;}，
     * 但那样就不能用 final 了。）
     */
    public OrderServiceImpl(CourseService courseService) {
        this.courseService = courseService;
    }

    // ============================================================
    // ★★★ 练习 5 的核心：请你实现这个方法 ★★★
    // ============================================================

    /**
     * 下单：扣名额 + 建订单，两步必须原子。
     * <p>
     * <b>实现步骤（按顺序照做即可）：</b>
     * <ol>
     *   <li><b>方法上加注解</b>：
     *       {@code @Transactional(rollbackFor = Exception.class)}
     *       —— 加在 {@code @Override} 上面或下面都行，习惯上放最前面；</li>
     *   <li><b>扣名额</b>：
     *       {@code boolean deducted = courseService.deductStock(courseId);}
     *       <br>如果 {@code !deducted}，说明名额不足或课程不存在，
     *       <b>抛一个 RuntimeException</b>（例如 {@code new IllegalStateException("名额不足，下单失败")}）。
     *       <br>⚠️ 这一步最容易漏：{@code deductStock} 返回 false 只是"影响 0 行"，
     *       <b>它自己不会抛异常</b>，你不抛，事务就会照常提交，库存白扣或白不扣；</li>
     *   <li><b>建订单</b>：{@code new Order()} 填好
     *       {@code orderNo} / {@code courseId} / {@code userId} / {@code amount} / {@code status}，
     *       然后 {@code save(order)}。
     *       <br>审计字段（createTime/updateTime/deleted/version）<b>不用填</b>，
     *       MetaObjectHandler 会自动补；</li>
     *   <li><b>返回 true</b>。</li>
     * </ol>
     * <p>
     * <b>验证你的实现对不对</b>：如果第 3 步因为订单号重复而失败（撞唯一索引），
     * 那么第 2 步扣掉的名额<b>必须被回滚回去</b>。这正是事务的价值。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean createOrder(Long courseId, Long userId, String orderNo) {
        // ============================================================
        boolean deduct = courseService.deductStock(courseId);
        if(!deduct){
            throw new IllegalStateException("名额不足或课程不存在，下单失败");
        }

        Order order = new Order();
        order.setCourseId(courseId);
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setAmount(new BigDecimal("99.00"));
        order.setStatus(1);
        save(order);

        return true;
        // 提示：需要的类都已经 import 好了 —— Order、BigDecimal 可以直接用。
        //      扣名额：courseService.deductStock(courseId)
        //      保存：  save(order)
        //
        // 写完之后，跑 OrderServiceTest 里的 testCreateOrderRollback，
        // 它会验证"订单号重复时，扣掉的名额有没有还回来"。
        // ============================================================
    }

    // ============================================================
    // 下面两个方法已经写好，供你参考本类的写法
    // ============================================================

    /**
     * 查询某个课程下的所有订单，按 id 倒序。
     */
    @Override
    public List<Order> listByCourse(Long courseId) {
        return list(new LambdaQueryWrapper<Order>()
                .eq(courseId != null, Order::getCourseId, courseId)
                .orderByDesc(Order::getId));
    }

    /**
     * 取消订单：状态改 0，并把名额还回去。
     * <p>
     * 这里演示了"条件更新 + 跨表事务"的组合：
     * <ul>
     *   <li>先用<b>条件更新</b>把订单状态从 1 改成 0。
     *       如果影响 0 行，说明订单不存在 / 已被删 / 已经是取消状态，
     *       这时<b>直接返回 false，不还名额</b> —— 防止重复取消把名额还多了；</li>
     *   <li>只有真正改成功了，才执行还名额（又是跨表操作，同一事务）。</li>
     * </ul>
     * <p>
     * 对比一下 {@code CourseServiceImpl.deductStock}：那里是 {@code stock - 1}，
     * 这里是 {@code stock + 1}，思路完全对称。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean cancelOrder(Long orderId) {
        if (orderId == null) {
            return false;
        }

        // 第 1 步：条件更新 —— 只有"状态为 1（已创建）"的订单才能取消
        boolean cancelled = update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 1)          // ★ 关键：重复取消匹配不到，防止把名额还多
                .set(Order::getStatus, 0));
        if (!cancelled) {
            // 订单不存在 / 已取消 / 已逻辑删除 —— 都不该还名额
            return false;
        }

        // 第 2 步：把名额还回去
        Order order = getById(orderId);
        if (order != null) {
            courseService.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<top.seamohai.mybatis.entity.Course>()
                    .eq(top.seamohai.mybatis.entity.Course::getId, order.getCourseId())
                    .setSql("stock = stock + 1"));
        }
        return true;
    }
}
