package top.seamohai.zhihu.service;

import top.seamohai.zhihu.entity.Special;

import java.util.List;

public interface SpecialService {
    /**
     * 获取所有数据
     *
     * @return List<Special>
     */
    List<Special> getAll();
    List<Special> getByCategory(String category);
    List<Special> getByPage(int limit, int offset);
    List<Special> getByCategoryAndPage(String category, int limit, int offset);
    long countByCategory(String category);

    /**
     * 更新关注状态
     *
     * @param id          专题 id
     * @param isFollowing "True" / "False"
     * @return 是否更新成功
     */
    boolean updateFollow(Long id, String isFollowing);
}