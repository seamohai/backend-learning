package top.seamohai.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.seamohai.mybatis.entity.Order;

/**
 * 订单 Mapper。
 * <p>
 * 接口体是空的，所有单表 CRUD 由 {@code BaseMapper} 提供。
 * <p>
 * <b>别忘 {@code @Mapper}</b>：本项目的启动类上没有 {@code @MapperScan}，
 * 每个 Mapper 必须自己标 {@code @Mapper}，否则 Spring 不会把它注册成 Bean，
 * 注入时会报 {@code NoSuchBeanDefinitionException}。
 * （这个坑编译期发现不了，只有启动时才炸。）
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
