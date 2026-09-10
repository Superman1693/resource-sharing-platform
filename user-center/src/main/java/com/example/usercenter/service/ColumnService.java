package com.example.usercenter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.usercenter.model.domain.NoteColumn;

import java.util.List;

/**
 * 笔记专栏服务接口（星球内合集：作者把系列笔记串成专栏）
 * @author zy
 */
public interface ColumnService extends IService<NoteColumn> {

    /**
     * 创建专栏
     * @param column 专栏信息（title/starId 必填）
     * @param userId 当前用户ID
     * @return 创建成功的专栏对象
     */
    NoteColumn createColumn(NoteColumn column, Long userId);

    /**
     * 更新专栏（仅作者/管理员）
     */
    boolean updateColumn(Long id, NoteColumn column, Long userId);

    /**
     * 删除专栏（逻辑删除 + 解绑专栏下笔记）
     */
    boolean deleteColumn(Long id, Long userId);

    /**
     * 查询专栏列表（按星球或作者）
     * @param starId 星球ID，可空
     * @param authorId 作者ID，可空
     * @param includeArchived 是否包含已完结
     */
    List<NoteColumn> listColumns(Long starId, Long authorId, boolean includeArchived);

    /**
     * 获取专栏详情（含专栏下 published 笔记列表，按章节序号升序）
     */
    NoteColumn getColumnDetail(Long id);

    /**
     * 把笔记加入专栏（校验笔记作者==专栏作者且未归属其他专栏）
     * @param collectionId 专栏ID
     * @param noteId 笔记ID
     * @param sortOrder 章节序号
     * @param userId 操作者ID
     */
    boolean addNoteToColumn(Long collectionId, Long noteId, Integer sortOrder, Long userId);

    /**
     * 把笔记移出专栏
     */
    boolean removeNoteFromNote(Long noteId, Long userId);

    /**
     * 查询作者的专栏列表
     */
    List<NoteColumn> listMyColumns(Long userId);
}
