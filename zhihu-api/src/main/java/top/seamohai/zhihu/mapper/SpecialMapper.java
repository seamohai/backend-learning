package top.seamohai.zhihu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.seamohai.zhihu.entity.Special;

import java.util.List;


@Mapper
public interface SpecialMapper extends BaseMapper<Special> {
    List<Special> selectByCategory(String category);
    List<Special> selectByPage(@Param("limit") int limit, @Param("offset") int offset);
    List<Special> selectByCategoryAndPage(@Param("category") String category,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    long countByCategory(@Param("category") String category);
}
