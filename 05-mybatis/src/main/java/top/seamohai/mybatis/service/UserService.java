package top.seamohai.mybatis.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import top.seamohai.mybatis.dto.UserQuery;
import top.seamohai.mybatis.entity.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface UserService extends IService<User> {

    /* 增 */
    boolean createUser(User user);
    boolean createUserBatch(List<User> users);

    /* 删 */
    boolean removeUser(Long id);
    boolean removeUserBatch(List<Long> ids);
    boolean removeUserByStatus(Integer status);

    /* 改 */
    boolean modifyUser(User user);
    boolean modifyUserStatus(Long id, Integer status);
    boolean recharge(Long id, BigDecimal amount);

    /* 查（基础） */
    User getUser(Long id);
    List<User> listUsers();
    Page<User> pageUsers(long current, long size);

    /* 查（Lambda 条件） */
    List<User> listByCondition(UserQuery query);
    Page<User> pageByCondition(UserQuery query, long current, long size);
    List<User> searchByKeyword(String keyword);
    List<User> listByAgeRange(Integer minAge, Integer maxAge);
    List<User> listTop(Integer limit);
    List<String> listUsernames();
    List<User> listByTag(String tag);
    boolean existsUsername(String username);
    long countByGender(Integer gender);
    Map<String, Object> statistics();
}
