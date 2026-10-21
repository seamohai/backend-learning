package top.seamohai.mybatis.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "t_course", autoResultMap = true)
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String teacher;

    private BigDecimal credit;

    private Integer capacity;
    private Integer enrolled;

    /** 剩余名额。下单时扣减 —— 练习 5 的"扣库存"就扣它 */
    private Integer stock;

    private String description;


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
