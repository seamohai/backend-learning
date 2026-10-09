package top.seamohai.web.service;
import org.springframework.stereotype.Service;
import top.seamohai.web.common.BusinessException;
import top.seamohai.web.common.ErrorCode;
import top.seamohai.web.common.PageResult;
import top.seamohai.web.dto.UserCreateRequest;
import top.seamohai.web.dto.UserUpdateRequest;
import top.seamohai.web.entity.User;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


@Service
public class UserService {

    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    public PageResult<User> page(int pageNum, int pageSize) {
        List<User> all = new ArrayList<>(store.values());
        all.sort(Comparator.comparing(User::getId));
        int from = (pageNum - 1) * pageSize;
        int to = Math.min(from + pageSize, all.size());
        List<User> list = from >= all.size() ? List.of() : all.subList(from, to);
        return PageResult.of(list, all.size(), pageNum, pageSize);
    }

    public User getById(Long id) {
        User user = store.get(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return user;
    }

    public User create(UserCreateRequest request) {
        User user = new User();
        user.setId(idGenerator.incrementAndGet());
        user.setUsername(request.getUsername());
        user.setAge(request.getAge());
        user.setEmail(request.getEmail());
        user.setAddress("江苏南京");
        user.setBirthday(LocalDate.now());
        store.put(user.getId(), user);
        return user;
    }

    public User update(Long id, UserUpdateRequest request) {
        User user = getById(id);
        user.setUsername(request.getUsername());
        //user.setAge(request.getAge());
        user.setEmail(request.getEmail());
        return user;
    }

    public void delete(Long id) {
        if (store.remove(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }
}
