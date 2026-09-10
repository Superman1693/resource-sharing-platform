package com.example.usercenter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.request.NoteQueryRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 笔记服务接口
 * @author zy
 */
public interface NoteService extends IService<Note> {
    /**
     * 获取笔记列表（分页）
     *
     * @param request 查询请求参数
     * @return 分页笔记结果
     */
    PageResult<Note> getNoteList(NoteQueryRequest request);

    /**
     * 获取笔记详情（带权限过滤：非作者非管理员访问非 published 笔记抛 NOT_FOUND）
     *
     * @param id      笔记ID
     * @param userId  当前用户ID（可空，匿名访客）
     * @param isAdmin 是否管理员
     * @return 笔记对象，不存在时抛异常
     */
    Note getNoteDetail(Long id, Long userId, boolean isAdmin);

    /**
     * 创建笔记
     *
     * @param note    笔记信息
     * @param request HTTP 请求（用于获取当前用户）
     * @return 创建成功的笔记对象
     */
    Note createNote(Note note, HttpServletRequest request);

    /**
     * 更新笔记
     *
     * @param id      笔记ID
     * @param note    更新内容
     * @param request HTTP 请求（用于权限校验）
     * @return 更新成功返回 true
     */
    boolean updateNote(Long id, Note note, HttpServletRequest request);

    /**
     * 删除笔记
     *
     * @param id      笔记ID
     * @param request HTTP 请求（用于权限校验）
     * @return 删除成功返回 true
     */
    boolean deleteNote(Long id, HttpServletRequest request);

    /**
     * 点赞笔记
     *
     * @param id      笔记ID
     * @param request HTTP 请求（用于获取当前用户）
     * @return 点赞成功返回 true，取消点赞也返回 true
     */
    boolean likeNote(Long id, HttpServletRequest request);

    /**
     * 增加浏览量
     *
     * @param id 笔记ID
     * @return 操作成功返回 true
     */
    boolean increaseViewCount(Long id);

    /**
     * 搜索内容（管理员）
     *
     * @param keyword     关键词
     * @param contentType 内容类型
     * @param status      状态筛选
     * @param authorId    作者ID，为 null 时不过滤
     * @return 符合条件的笔记列表
     */
    List<Note> searchContent(String keyword, String contentType, String status, Long authorId);

    /**
     * 置顶/取消置顶笔记（仅管理员）
     * @param id 笔记ID
     * @param isTop 1-置顶 0-取消置顶
     */
    boolean topNote(Long id, Integer isTop);

    /**
     * 异步递增评论数
     * @param noteId 笔记ID
     */
    void asyncIncrementCommentCount(Long noteId);

    /**
     * 审核通过笔记（管理员）—— 统一走 NoteService，同步 ES/布隆/content_count/积分/publish_time
     * @param id 笔记ID
     */
    boolean approveNote(Long id);

    /**
     * 拒绝笔记（管理员）—— 若原 published 则从 ES 移除并 content_count-1
     * @param id 笔记ID
     */
    boolean rejectNote(Long id);

    /**
     * 查询当前用户的笔记（草稿箱/我的内容，按 status 筛选）
     * @param userId 用户ID
     * @param status 状态筛选（draft/scheduled/published/pending/rejected），为 null 查全部
     * @param page 页码
     * @param pageSize 每页数量
     */
    PageResult<Note> getMyNotes(Long userId, String status, Integer page, Integer pageSize);

    /**
     * 发布定时笔记（由 ScheduledPublishTask 到点调用）：转 published + 触发发布副作用（含积分）
     * @param noteId 笔记ID
     */
    boolean publishScheduledNote(Long noteId);

    /**
     * 获取已发布笔记详情（多级缓存 @Cacheable，全员共享 published 内容）。
     * 非 published 抛 NOT_FOUND（不缓存）；布隆过滤器防穿透。
     * 付费星球脱敏由 Controller 层做副本，不污染此缓存。
     * @param id 笔记ID
     */
    Note getNoteDetailPublished(Long id);

    /**
     * 标记笔记为待审核（举报触发），同步清 noteDetail/noteList 缓存
     * @param id 笔记ID
     */
    boolean markNotePending(Long id);
}
