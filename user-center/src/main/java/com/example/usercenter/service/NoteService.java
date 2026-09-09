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
     * 获取笔记详情
     *
     * @param id 笔记ID
     * @return 笔记对象，不存在时返回 null
     */
    Note getNoteDetail(Long id);

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
}
