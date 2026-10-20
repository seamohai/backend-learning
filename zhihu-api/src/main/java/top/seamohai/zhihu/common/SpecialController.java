package top.seamohai.zhihu.common;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import top.seamohai.zhihu.entity.Special;
import top.seamohai.zhihu.service.SpecialService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/special")
public class SpecialController {
    @Resource
    private SpecialService specialService;

    @GetMapping("all")
    public ResponseResult getAll() {
        List<Special> all = specialService.getAll();
        return ResponseResult.builder()
                .code(200)
                .msg("数据获取成功")
                .data(all)
                .build();
    }
    @GetMapping("/category/{category}")
    public ResponseResult getByCategory(@PathVariable String category) {
        List<Special> specials = specialService.getByCategory(category);
        return ResponseResult.builder()
                .code(200)
                .msg("数据获取成功")
                .data(specials)
                .build();
    }
    @GetMapping("/page")
    public ResponseResult getByPage(@RequestParam int limit, @RequestParam int offset) {
        Map<String, Object> map = new HashMap<>();
        List<Special> specials = specialService.getByPage(limit, offset);
        map.put("specials", specials);
        map.put("total", specialService.getAll().size());
        return ResponseResult.builder()
                .code(200)
                .msg("数据获取成功")
                .data(map)
                .build();
    }
    @GetMapping("/list")
    public ResponseResult list(@RequestParam(required = false) String category,
                               @RequestParam(defaultValue = "3") int limit,
                               @RequestParam(defaultValue = "0") int offset) {
        Map<String, Object> map = new HashMap<>();
        map.put("specials", specialService.getByCategoryAndPage(category, limit, offset));
        map.put("total", specialService.countByCategory(category));
        return ResponseResult.builder()
                .code(200)
                .msg("数据获取成功")
                .data(map)
                .build();
    }

    @PutMapping("/{id}/follow")
    public ResponseResult updateFollow(@PathVariable Long id,
                                       @RequestParam String isFollowing) {
        boolean ok = specialService.updateFollow(id, isFollowing);
        return ResponseResult.builder()
                .code(ok ? 200 : 404)
                .msg(ok ? "更新成功" : "专题不存在")
                .data(ok)
                .build();
    }
}
