package com.example.usercenter.service;

import com.example.usercenter.common.PageResult;
import com.example.usercenter.model.domain.PointsAccount;
import com.example.usercenter.model.domain.PointsLog;
import com.example.usercenter.model.domain.SignInRecord;

import java.util.List;

/**
 * 积分服务：签到、积分账户、流水
 */
public interface PointsService {

    /** 签到（今日已签返回 false） */
    boolean signIn(Long userId);

    /** 获取积分账户（不存在返回 null） */
    PointsAccount getAccount(Long userId);

    /** 今日是否已签到 */
    boolean isSignedToday(Long userId);

    /** 积分流水（分页） */
    PageResult<PointsLog> getPointsLog(Long userId, int page, int pageSize);

    /** 某月签到日历（month 格式 yyyy-MM，null 取当月） */
    List<SignInRecord> getSignCalendar(Long userId, String month);

    /** 通用加分（发布/获赞/评论触发），失败不阻塞主流程 */
    void addPoints(Long userId, int change, String type, String refType, Long refId, String remark);

    /** 按现有贡献值初始化积分账户（管理员，幂等：已存在账户跳过） */
    int migrateFromContribution();

    /**
     * 用积分解锁付费笔记。
     *
     * <p>免费笔记 / 作者本人 / 已购买过的情况直接返回 {@code true}（幂等）；
     * 余额不足抛 {@code PARAMS_ERROR}。作者获得成交额的 90%，平台抽成 10%。</p>
     */
    boolean unlockNote(Long userId, Long noteId);

    /**
     * 打赏笔记作者（可重复）。
     *
     * @param amount 打赏积分，需在 1 ~ 500 之间
     */
    boolean tipNote(Long userId, Long noteId, int amount);

    /** 该用户是否已解锁某笔记（仅查购买记录；免费笔记恒为 false） */
    boolean hasUnlocked(Long userId, Long noteId);

    // ------------------------- 积分消耗价目（调整价格只改这里） -------------------------

    /** 自助置顶：单次消耗积分 */
    int BOOST_COST = 50;

    /** 自助置顶时长（小时） */
    int BOOST_HOURS = 24;

    /** 创建星球消耗积分 */
    int CREATE_STAR_COST = 500;

    // ------------------------- 每日获取上限 -------------------------

    /**
     * 每日通过「平台发放」可获得的积分上限。
     *
     * <p>只约束平台凭空发放的部分（签到/发布/被赞/评论），<b>不约束用户间转移</b>：
     * 解锁收入、打赏收入、资源收入是别人花掉的积分转过来的，不是增发，
     * 若一并限流会导致作者收不到应有报酬。</p>
     */
    int DAILY_EARN_LIMIT = 200;

    /** 计入「平台发放」的流水类型（用于每日上限与对账口径） */
    List<String> EARN_TYPES = List.of("sign", "comment", "like", "publish");

    /** 计入「消耗」的流水类型（用于对账口径，排除退回等正数） */
    List<String> SPEND_TYPES = List.of("unlock", "tip", "boost", "resource_download", "create_star");

    /**
     * 该用户当前等级可设置的内容售价上限（笔记解锁价 / 资源下载价），按累计获得积分定级。
     *
     * <p>0 表示不可定价（新手等级）。</p>
     */
    int getPriceLimit(Long userId);

    /** 该用户今日已获得的积分（仅计平台发放类型，用于展示每日额度进度） */
    int getTodayEarned(Long userId);

    /**
     * 通用积分消费（余额不足抛 {@code PARAMS_ERROR}）。
     *
     * @return 扣减后的余额
     */
    int spend(Long userId, int amount, String type, String refType, Long refId, String remark);

    /**
     * 解锁资源并记录下载（免费与付费统一入口）。
     *
     * <p>行为：</p>
     * <ul>
     *   <li>已有下载记录 → 直接放行，<b>不重复计数、不重复扣分</b>（幂等）；</li>
     *   <li>付费资源 → 校验余额、扣分，上传者获得 90%（平台抽成 10%）；</li>
     *   <li>首次产生记录时把 {@code resource.download_count} +1 ——
     *       这也修掉了原来「刷新下载接口即可无限刷计数」的问题。</li>
     * </ul>
     *
     * @return 恒为 true（失败会抛异常）
     */
    boolean unlockResource(Long userId, Long resourceId);

    /** 该用户是否已有该资源的下载记录（对免费资源同样适用） */
    boolean hasPurchasedResource(Long userId, Long resourceId);
}
