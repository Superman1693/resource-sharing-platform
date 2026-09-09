package com.example.usercenter.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 登录令牌生成器+验证器
 */
@Component  //交给Spring管理
@Slf4j
public class JwtUtils {

    @Value("${spring.jwt.secret}")
    private String secret;//JWT秘钥

    @Value("${spring.jwt.expiration:604800}")
    private long expiration;//过期时间秒数

    //生成安全的签名秘钥
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // 确保密钥长度至少 32 字节（256 位）
        if (keyBytes.length < 32) {
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
            return Keys.hmacShaKeyFor(paddedKey);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
    public long getExpiration() {
        return expiration;
    }

    //生成JWT Token
    public String generateToken(Long userId, Integer userRole, long expirationSeconds) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expirationSeconds * 1000);
        return Jwts.builder()
                .claim("userId", userId)        //存入用户id
                .claim("userRole", userRole)    //存入角色类型
                .issuedAt(now)                      //签发时间
                .expiration(expireDate)             //过期时间
                .signWith(getSigningKey())          //签名防篡改
                .compact();
    }

    /**
     * 解析Token
     * @param token
     * @return
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())//验证签名是否正确
                .build()
                .parseSignedClaims(token)   //解析
                .getPayload();              //获取里面的数据
    }

    /**
     * 验证Token是否有效
     * @param token
     * @return
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取Token剩余有效时间（秒）
     * @param token
     * @return
     */
    public long getRemainingSeconds(String token) {
        try {
            Claims claims = parseToken(token);
            long expireTime = claims.getExpiration().getTime();
            long remaining = (expireTime - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (Exception e) {
            return 0;
        }
    }
}
