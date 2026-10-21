package top.seamohai.schedule.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


@Slf4j
@Service
public class DailyWeatherService {

    private final OkHttpClient okHttpClient = new OkHttpClient();

    @Resource
    private JavaMailSender mailSender;

    @Value("${weather.api.key}")
    private String weatherApiKey;

    @Value("${weather.api.city}")
    private String cityId;

    @Value("${weather.api.city-name}")
    private String cityName;

    @Value("${weather.api.url}")
    private String weatherApiUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${email.recipients}")
    private String[] toEmails;

    /**
     * 定时任务，每天 9:22 执行
     */
    @Scheduled(cron = "30 52 9 * * ?")
    public void sendDailyWeather() {
        try {
            // 1. 调用和风天气API获取天气预报
            String weatherResponse = getWeatherData();
            if (weatherResponse == null) {
                log.error("【天气早报】获取天气数据失败");
                return;
            }

            // 2. 解析API响应（提取当天天气）
            JSONObject weatherJson = JSON.parseObject(weatherResponse);
            if (!"200".equals(weatherJson.getString("code"))) {
                log.error("【天气早报】和风返回错误码：{}，响应：{}", weatherJson.getString("code"), weatherResponse);
                return;
            }
            JSONArray daily = weatherJson.getJSONArray("daily");
            if (daily == null || daily.isEmpty()) {
                log.error("【天气早报】daily 为空，响应：{}", weatherResponse);
                return;
            }
            JSONObject todayWeather = daily.getJSONObject(0);

            String date = todayWeather.getString("fxDate");
            String tempMax = todayWeather.getString("tempMax");
            String tempMin = todayWeather.getString("tempMin");
            String textDay = todayWeather.getString("textDay");
            String windDirDay = todayWeather.getString("windDirDay");
            String windScaleDay = todayWeather.getString("windScaleDay");

            // 3. 构造邮件内容
            String subject = String.format("【每日天气早报】%s %s天气",
                    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")), cityName);
            String content = String.format("""
                    📅 日期：%s
                    🌤 天气：%s
                    🌡 温度：%s℃ ~ %s℃
                    🧭 风向：%s
                    💨 风力：%s级
                    💡 提示：出门请根据天气增减衣物，注意交通安全！
                    """, date, textDay, tempMin, tempMax, windDirDay, windScaleDay);

            // 4. 发送邮件
            sendEmail(subject, content);
            log.info("【天气早报】邮件发送成功，收件人：{}", String.join(",", toEmails));
        } catch (Exception e) {
            log.error("【天气早报】执行失败", e);
        }
    }

    /**
     * 调用和风天气API，获取3天天气预报
     */
    private String getWeatherData() throws Exception {
        String requestUrl = String.format("%s?location=%s&key=%s", weatherApiUrl, cityId, weatherApiKey);
        Request request = new Request.Builder().url(requestUrl).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
            log.error("【天气早报】天气接口请求失败，HTTP {}", response.code());
            return null;
        }
    }

    /**
     * 发送邮件
     */
    private void sendEmail(String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmails);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }
}