package com.example.usercenter.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.RandomGenerator;
import com.example.usercenter.service.CaptchaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务实现
 * 使用 hutool 生成图形验证码，Redis 存储验证码
 */
@Service
@Slf4j
public class CaptchaServiceImpl implements CaptchaService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 验证码 Redis key 前缀
     */
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";

    /**
     * 验证码过期时间（5分钟）
     */
    private static final long CAPTCHA_EXPIRE_MINUTES = 5;

    /**
     * 验证码长度
     */
    private static final int CAPTCHA_LENGTH = 4;

    /**
     * 验证码图片宽度
     */
    private static final int CAPTCHA_WIDTH = 160;

    /**
     * 验证码图片高度
     */
    private static final int CAPTCHA_HEIGHT = 50;

    @Override
    public String generateCaptcha() {
        //1. 生成唯一标识
        String captchaKey = UUID.randomUUID().toString().replace("-", "");

        //2.创建 LineCaptcha（带干扰线的验证码）
        // 参数：宽、高、验证码长度、干扰线数量
        LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, CAPTCHA_LENGTH, 30);

        //3. 自定义字体（增大字号，让文字更清晰居中）
        //加粗30号字体
        Font font = new Font("SansSerif", Font.BOLD, 30);
        lineCaptcha.setFont(font);

        // 创建自定义随机生成器（使用字母加数字）
        RandomGenerator randomGenerator = new RandomGenerator("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz", CAPTCHA_LENGTH);
        lineCaptcha.setGenerator(randomGenerator);

        //4. 生成验证码
        lineCaptcha.createCode();

        //5. 获取验证码文本
        String captchaCode = lineCaptcha.getCode();

        //6. 存储到 Redis，设置过期时间
        String redisKey = CAPTCHA_KEY_PREFIX + captchaKey;
        stringRedisTemplate.opsForValue().set(redisKey, captchaCode.toLowerCase(), CAPTCHA_EXPIRE_MINUTES, TimeUnit.MINUTES);

        //7. 将验证码图片转为 Base64
        String imageBase64 = captchaToBase64(lineCaptcha);

        log.info("生成验证码: key={}, code={}", captchaKey, captchaCode);

        //8. 返回 key 和 图片 Base64
        return captchaKey + ":" + imageBase64;
    }

    @Override
    //校验验证码，验证码一次性有效，用过就删，验证码不区分大小写
    public boolean verifyCaptcha(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaCode == null) {
            return false;
        }

        String redisKey = CAPTCHA_KEY_PREFIX + captchaKey;
        //1.根据key去Redis取真实验证码
        String storedCode = stringRedisTemplate.opsForValue().get(redisKey);
        //2.取不到=过期
        if (storedCode == null) {
            log.warn("验证码已过期或不存在: key={}", captchaKey);
            return false;
        }

        //3. 验证成功后删除验证码（一次性使用）
        if (storedCode.equalsIgnoreCase(captchaCode.trim())) {
            stringRedisTemplate.delete(redisKey);
            log.info("验证码校验成功: key={}", captchaKey);
            return true;
        }

        log.warn("验证码错误: key={}, expected={}, actual={}", captchaKey, storedCode, captchaCode);
        return false;
    }

    @Override
    public String getCaptchaImageBase64(String captchaKey) {
        // 这个方法暂时不需要，因为图片在 generateCaptcha 时就返回了
        return null;
    }

    /**
     * 将验证码图片转为 Base64 编码
     */
    private String captchaToBase64(LineCaptcha captcha) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            captcha.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            log.error("验证码图片转 Base64 失败", e);
            return null;
        }
    }
}
