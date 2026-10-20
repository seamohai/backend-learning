package top.seamohai.zhihu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import top.seamohai.zhihu.entity.Special;
import top.seamohai.zhihu.mapper.SpecialMapper;
import top.seamohai.zhihu.service.SpecialService;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/19
 * @description SpecialServiceImpl
 **/
@Service
public class SpecialServiceImpl implements SpecialService {
    @Resource
    private SpecialMapper specialMapper;

    @Override
    public List<Special> getAll() {
        LambdaQueryWrapper<Special> wrapper = new LambdaQueryWrapper<>();
        //根据更新时间降序排列
        wrapper.orderByDesc(Special::getUpdated);
        return specialMapper.selectList(wrapper);
    }
    @Override
    public List<Special> getByCategory(String category) {
        return specialMapper.selectByCategory(category);
    }

    @Override
    public List<Special> getByPage(int limit, int offset) {
        return specialMapper.selectByPage(limit, offset);
    }
    @Override
    public List<Special> getByCategoryAndPage(String category, int limit, int offset) {
        return specialMapper.selectByCategoryAndPage(category, limit, offset);
    }

    @Override
    public long countByCategory(String category) {
        return specialMapper.countByCategory(category);
    }

    @Override
    public boolean updateFollow(Long id, String isFollowing) {
        // 只塞 id 和目标字段，MyBatis-Plus 默认只更新非 null 字段，
        // 所以这里生成的 SQL 是 UPDATE t_special SET is_following = ? WHERE id = ?
        Special special = new Special();
        special.setId(id);
        special.setIsFollowing(isFollowing);
        return specialMapper.updateById(special) > 0;
    }
}