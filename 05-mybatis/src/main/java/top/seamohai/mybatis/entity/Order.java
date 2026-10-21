package top.seamohai.mybatis.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体（练习 5）。
 * <p>
 * 这张表的存在意义不是"多一张表"，而是提供一个<b>跨表事务</b>的场景：
 * 一次下单要同时改两张表 ——
 * <pre>
 * t_course：stock = stock - 1     （扣名额）
 * t_order ：INSERT 一条订单        （记流水）
 * </pre>
 * 这两步必须在<b>同一个事务</b>里：要么都成功，要么都回滚。
 * 不能出现"名额扣了但订单没建"或者"订单建了但名额没扣"。
 * <p>
 * 本类刻意把 05 学过的能力都用上了：
 * <ul>
 *   <li>{@code @TableLogic} —— 订单取消不做物理删除，只打标记；</li>
 *   <li>{@code @TableField(fill = ...)} —— 审计字段自动填充；</li>
 *   <li>{@code @Version} —— 订单状态变更时的乐观锁；</li>
 *   <li>{@code amount} 用 {@code BigDecimal} —— 金额不能用浮点。</li>
 * </ul>
 */
@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号。表上有唯一索引 uk_order_no —— 它是后面验证事务回滚的"失败注入点" */
    private String orderNo;

    private Long courseId;

    private Long userId;

    /** 订单金额。用 BigDecimal 对应 DECIMAL，不用 Float/Double（浮点会算错账） */
    private BigDecimal amount;

    /** 1 已创建 / 0 已取消 */
    private Integer status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
}
