package top.seamohai.config.service;


import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
public class DevEnvService implements EnvService {
    @Override
    public String envInfo() {
        return "我是 dev 环境专属的 Bean";
    }
}
