package com.example.usercenter.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.utils.UserContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 多租户拦截器
 * 实现行级数据隔离：自动在 SQL 中注入 tenant_id 条件
 *
 * 租户策略：
 *   - 基于「星球（star）」作为租户单位
 *   - 普通用户只能看到自己所属星球的数据
 *   - 管理员（role=1）可查看所有数据
 *   - 不需要租户隔离的表通过 ignoreTables 配置
 */
@Component
public class TenantInterceptor implements TenantLineHandler {

    /**
     * 租户 ID 字段名
     * 与数据库表中的字段对应
     */
    private static final String TENANT_ID_COLUMN = "star_id";

    /**
     * 不需要租户隔离的表
     * 这些表的数据是全局共享的
     */
    private static final List<String> IGNORE_TABLES = Arrays.asList(
            "user",               // 用户表（全局）
            "note",               // 笔记表（公开数据，star_id 未实际使用）
            "comment",            // 评论表（无 star_id 字段）
            "resource",           // 资源表（无 star_id 字段）
            "star",               // 星球表（本身就是租户维度）
            "knowledge_node",     // 知识节点（无 star_id 字段）
            "admin_log",          // 管理员操作日志（全局）
            "notification",       // 通知表（按 receiver_id 隔离，非租户）
            "like_record",        // 点赞记录（按 user_id 隔离）
            "view_record",        // 浏览记录（按 note_id 隔离）
            "note_collection",    // 笔记收藏（按 user_id 隔离，全局）
            "tag",                // 标签（全局共享）
            "note_tag",           // 笔记-标签关联（全局共享）
            "follow",             // 关注关系（全局）
            "captcha",            // 验证码（全局）
            "error_log"           // 错误日志（全局）
    );

    /**
     * 是否跳过租户过滤（用于未登录用户的公开接口）
     * 当用户未登录时，不添加 star_id 条件，允许浏览公开数据
     */
    private static boolean skipTenantFilter() {
        LoginUserDTO loginUser = UserContext.get();
        return loginUser == null || loginUser.getStarId() == null;
    }

    @Override
    public Expression getTenantId() {
        // 返回当前用户的 starId 作为租户 ID
        LoginUserDTO loginUser = UserContext.get();
        if (loginUser == null || loginUser.getStarId() == null) {
            // 未登录用户返回一个占位值，实际会被 ignoreTable 或 getTenantId 的特殊处理跳过
            return new LongValue(0);
        }
        return new LongValue(loginUser.getStarId());
    }

    @Override
    public String getTenantIdColumn() {
        return TENANT_ID_COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // 未登录用户跳过所有表的租户过滤（允许浏览公开数据）
        if (skipTenantFilter()) {
            return true;
        }
        // 忽略不需要租户隔离的表
        return IGNORE_TABLES.contains(tableName);
    }
}
