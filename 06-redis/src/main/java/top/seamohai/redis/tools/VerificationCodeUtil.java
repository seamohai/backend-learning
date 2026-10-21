package top.seamohai.redis.tools;

import java.security.SecureRandom;

/**
 * 数字验证码工具类
 *
 * @author 申陌海
 */
public final class VerificationCodeUtil {

    /** 4 位验证码的取值范围：1000 ~ 9999（不含 10000），保证首位不为 0 */
    private static final int MIN = 1000;
    private static final int BOUND = 10000;

    /** 验证码是安全敏感值，必须用 SecureRandom，不能用 Random / Math.random() */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 工具类，禁止实例化 */
    private VerificationCodeUtil() {
    }

    /**
     * 生成 4 位数字验证码，范围 1000 ~ 9999
     */
    public static String generate() {
        return String.valueOf(RANDOM.nextInt(MIN, BOUND));
    }

    /**
     * 生成指定长度的数字验证码，允许前导 0（如 6 位时可能出现 001234）
     *
     * @param length 位数，必须大于 0
     */
    public static String generate(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("验证码位数必须大于 0，当前为：" + length);
        }
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }
}