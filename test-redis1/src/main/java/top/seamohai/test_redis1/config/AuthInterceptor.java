package top.seamohai.test_redis1.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import top.seamohai.test_redis1.common.BusinessException;
import top.seamohai.test_redis1.common.ErrorCode;
import top.seamohai.test_redis1.common.JwtUtils;


@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /**
     * 请求属性名，controller 用 @RequestAttribute 取值
     */
    public static final String ATTR_CLAIMS = "claims";

    private static final String HEADER_NAME = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 跨域预检请求带的是 OPTIONS，浏览器不会给它加 Authorization，直接放行交给 CORS 处理
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HEADER_NAME);
        if (header == null || !header.startsWith(TOKEN_PREFIX)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        Claims claims = jwtUtils.parse(header.substring(TOKEN_PREFIX.length()));
        request.setAttribute(ATTR_CLAIMS, claims);
        return true;
    }
}