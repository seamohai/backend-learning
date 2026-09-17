package top.seamohai.config.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("prod")
public class ProdEnvService implements EnvService {
    @Override
    public String envInfo() {
        return "我是 prod 环境专属的 Bean";
    }
}
