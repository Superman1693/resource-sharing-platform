package com.example.usercenter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.usercenter.model.domain.Knowledge;
import com.example.usercenter.model.domain.Note;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 知识地图服务接口
 * @author zy
 */
public interface KnowledgeService extends IService<Knowledge> {
    /**
     * 获取知识地图
     */
    Knowledge getKnowledgeMap(Long starId);

    /**
     * 保存知识地图
     */
    boolean saveKnowledgeMap(Knowledge knowledge, HttpServletRequest request);

    /**
     * 获取知识地图节点内容
     */
    List<Note> getNodeContent(String nodeId);

    /**
     * 笔记创建/更新后同步到所属星球的知识地图（自动生成节点+连线）
     */
    void syncNodeFromNote(Note note);

    /**
     * 笔记删除后从所属星球的知识地图移除节点及相关连线
     */
    void removeNodeFromNote(Long starId, Long noteId);

    /**
     * 全量重建星球知识地图的自动节点与连线（管理员手动触发）
     */
    void regenerateMap(Long starId);
}
