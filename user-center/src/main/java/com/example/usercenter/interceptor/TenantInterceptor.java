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
 * 多租户拦截器（MyBatis-Plus {@link TenantLineHandler}）
 *
 * <p>作用：为 SQL 自动追加 {@code star_id = 当前星球} 条件，实现按星球的行级隔离。</p>
 *
 * <h3>为什么改成「作用域 + 白名单」而不是原来的「全局 + 忽略表」</h3>
 * <p>本平台是<b>公开的知识社区</b>：用户即使没有加入某个星球，也要能浏览该星球的详情页，
 * 以及搜索、热榜等公开内容。如果无条件对所有表注入 {@code star_id = 我所属的星球}，会出现两类问题：</p>
 * <ol>
 *   <li><b>SQL 直接报错</b>：{@code comment}、{@code resource}、{@code learning_path}、
 *       {@code message} 等表根本没有 {@code star_id} 列，注入条件会报
 *       {@code Unknown column 'star_id' in 'where clause'}；</li>
 *   <li><b>查询结果互斥</b>：{@code note}、{@code star_member} 等本身已有
 *       {@code eq("star_id", 正在浏览的星球)} 的显式条件，再叠加一层
 *       {@code star_id = 我的默认星球}，两个等值条件必须同时成立才命中，
 *       结果是「查看别的星球 → 一条内容都查不到」。</li>
 * </ol>
 *
 * <p>因此改为：<b>只有当前请求显式声明了星球上下文（请求头 {@code X-Star-Id}，
 * 由 {@link AuthInterceptor} 校验成员身份后写入 {@link UserContext}），
 * 且目标表在白名单内时，才注入租户条件。</b>
 * 这样既让隔离真正生效（星球作用域内的查询被严格收窄），
 * 又不影响首页 / 搜索 / 热榜等需要跨星球读取的公开场景。</p>
 *
 * <h3>白名单为什么只有这 4 张表</h3>
 * <ul>
 *   <li>{@code note}：有 {@code star_id}，星球内容的核心载体；</li>
 *   <li>{@code comment}：新增 {@code star_id} 列，由所属笔记派生；</li>
 *   <li>{@code note_column}：星球内专栏，天然按星球归属；</li>
 *   <li>{@code knowledge_map}：星球内知识图谱，天然按星球归属。</li>
 * </ul>
 * <p>其余表要么没有 {@code star_id} 列，要么本身是「按 user_id / receiver_id / note_id
 * 隔离」的全局表（如 {@code like_record}、{@code notification}、{@code note_collection}），
 * 刻意不参与星球维度的过滤。</p>
 *
 * <p>注意：租户列是 {@code star_id}，不是通用文档里常写的 {@code tenant_id}。</p>
 *
 * @author zy
 */
@Component
public class TenantInterceptor implements TenantLineHandler {

    /**
     * 租户 ID 字段名，与数据库表中的列对应
     */
    private static final String TENANT_ID_COLUMN = "star_id";

    /**
     * 参与星球租户过滤的表（白名单）。
     * 必须同时满足「有 star_id 列」与「按星球归属语义明确」，缺一不可。
     */
    private static final List<String> TENANT_SCOPED_TABLES = Arrays.asList(
            "note",           // 笔记（星球内容的核心载体）
            "comment",        // 评论（star_id 由所属笔记派生）
            "note_column",    // 专栏（星球内合集）
            "knowledge_map"   // 知识图谱（星球内图谱）
    );

    /**
     * 是否跳过租户过滤。
     *
     * <p>三种情况一律跳过，保证公开浏览不受影响：</p>
     * <ol>
     *   <li>未登录（{@code UserContext.get() == null}）；</li>
     *   <li>已登录但没带星球上下文（未传 {@code X-Star-Id}，或不是该星球成员）；</li>
     *   <li>目标表不在白名单内。</li>
     * </ol>
     */
    @Override
    public boolean ignoreTable(String tableName) {
        LoginUserDTO loginUser = UserContext.get();
        if (loginUser == null) {
            return true;                      // 未登录：放行公开数据
        }
        if (!UserContext.hasStarScope()) {
            return true;                      // 没有星球上下文：不做收窄
        }
        // 已进入星球作用域：只对白名单内的表生效
        return !TENANT_SCOPED_TABLES.contains(tableName);
    }

    /**
     * 返回租户 ID：优先取本次请求的星球作用域，其次回退到用户的默认星球。
     */
    @Override
    public Expression getTenantId() {
        Long scope = UserContext.getStarScope();
        if (scope != null) {
            return new LongValue(scope);
        }
        LoginUserDTO loginUser = UserContext.get();
        if (loginUser != null && loginUser.getStarId() != null) {
            return new LongValue(loginUser.getStarId());
        }
        // 兜底占位值：正常流程不会走到这里（ignoreTable 已拦住无作用域的请求）
        return new LongValue(0);
    }

    @Override
    public String getTenantIdColumn() {
        return TENANT_ID_COLUMN;
    }
}
