package top.seamohai.zhihu.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_special")
public class Special {
    private Long id;
    private String title;
    @TableField("introduction")
    private String description;
    private String banner;
    private String isFollowing;
    private Integer followersCount;
    private Integer viewCount;
    private Long updated;
    private String category;
}