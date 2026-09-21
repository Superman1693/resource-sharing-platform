package com.example.usercenter.utils;

import com.example.usercenter.model.dto.LoginUserDTO;

/**
 * 创建线程局变量，用于保存当前登录用户
 */
public class UserContext {

    //创建一个ThreadLocal变量，专门存当前登录用户
    private static final ThreadLocal<LoginUserDTO> HOLDER = new ThreadLocal<>();

    //本次请求的「星球作用域」：只有显式带上 X-Star-Id 且校验通过时才有值
    private static final ThreadLocal<Long> STAR_SCOPE = new ThreadLocal<>();

    //存用户：把当前登录用户放进线程里
    public static void set(LoginUserDTO user) { HOLDER.set(user); }

    //取用户：从线程里取出当前登录用户
    public static LoginUserDTO get() { return HOLDER.get(); }

    /**
     * 清理线程变量。
     * <p>注意：必须同时清掉星球作用域，否则 Tomcat 复用线程时会把上一个请求的
     * 星球上下文带到下一个请求，造成串号。</p>
     */
    public static void clear() {
        HOLDER.remove();
        STAR_SCOPE.remove();
    }

    /**
     * 设置本次请求的星球作用域。
     *
     * <p>作用域是「把查询范围收窄到某个星球」的开关：只有它被设置时，租户插件才会
     * 向 {@code note / comment / note_column / knowledge_map} 注入 {@code star_id} 条件；
     * 未设置时保持全平台可读（首页、搜索、热榜等公开浏览场景）。</p>
     *
     * @param starId 星球ID，传 null 表示清除作用域
     */
    public static void setStarScope(Long starId) {
        if (starId == null) {
            STAR_SCOPE.remove();
        } else {
            STAR_SCOPE.set(starId);
        }
    }

    /**
     * 取本次请求的星球作用域，未设置时返回 null
     */
    public static Long getStarScope() {
        return STAR_SCOPE.get();
    }

    /**
     * 本次请求是否处于某个星球的作用域内
     */
    public static boolean hasStarScope() {
        return STAR_SCOPE.get() != null;
    }

    /**
     * 当前用户所属的默认星球（来自 JWT 的 starId claim，登录时写入）
     */
    public static Long getStarId() {
        LoginUserDTO user = HOLDER.get();
        return user == null ? null : user.getStarId();
    }

    /**
     * 当前登录用户ID
     */
    public static Long getUserId() {
        LoginUserDTO user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }
}
