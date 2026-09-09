package com.example.usercenter.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoginUserDTO {
    private Long userId;
    private Integer userRole;
    private String token;

    /**
     * 当前用户所属星球 ID（用于多租户数据隔离）
     * 如果用户不属于任何星球，该字段为 null
     */
    private Long starId;

    public LoginUserDTO(Long userId, Integer userRole, String token) {
        this.userId = userId;
        this.userRole = userRole;
        this.token = token;
    }

    public LoginUserDTO(Long userId, Integer userRole, String token, Long starId) {
        this.userId = userId;
        this.userRole = userRole;
        this.token = token;
        this.starId = starId;
    }
}
