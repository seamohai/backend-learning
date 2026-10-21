package top.seamohai.schedule.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class TaskService {
    @Scheduled(cron = "59 42 * * * ?")

    public void cronTask() {
        log.info("Cron任务 执行时间：{},执行线程：{}", LocalDateTime.now(),Thread.currentThread().getName());
    }
}
