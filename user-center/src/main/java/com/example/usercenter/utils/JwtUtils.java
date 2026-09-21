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

    /**
     * 生成 JWT Token
     *
     * @param userId            用户ID
     * @param userRole          用户角色（0=普通用户，1=管理员）
     * @param starId            当前所属星球ID（多租户标识，可为 null）
     * @param expirationSeconds 有效期（秒）
     */
    public String generateToken(Long userId, Integer userRole, Long starId, long expirationSeconds) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expirationSeconds * 1000);

        JwtBuilder builder = Jwts.builder()
                .claim("userId", userId)        //存入用户id
                .claim("userRole", userRole)    //存入角色类型
                .issuedAt(now)                  //签发时间
                .expiration(expireDate)         //过期时间
                .signWith(getSigningKey());     //签名防篡改

        // 多租户：把 starId 写入 claim，供 AuthInterceptor 回填 UserContext，
        // 进而供 TenantInterceptor 做行级隔离。
        // 用户未加入任何星球时 starId 为 null，此时不写该 claim
        // （避免序列化出 "starId": null，也避免下游误判为「租户 0」）。
        if (starId != null) {
            builder.claim("starId", starId);
        }

        return builder.compact();
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
