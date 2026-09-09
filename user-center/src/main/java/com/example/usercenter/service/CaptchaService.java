package com.example.usercenter.service;

/**
 * 验证码服务接口
 */
public interface CaptchaService {

    /**
     * 生成图形验证码
     * @return 验证码唯一标识（key）
     */
    String generateCaptcha();

    /**
     * 校验验证码
     * @param captchaKey 验证码唯一标识
     * @param captchaCode 用户输入的验证码
     * @return 是否校验成功
     */
    boolean verifyCaptcha(String captchaKey, String captchaCode);

    /**
     * 获取验证码图片的 Base64 编码
     * @param captchaKey 验证码唯一标识
     * @return Base64 编码的图片
     */
    String getCaptchaImageBase64(String captchaKey);
}
