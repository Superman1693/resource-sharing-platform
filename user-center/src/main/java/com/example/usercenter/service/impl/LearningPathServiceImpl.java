package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.LearningPathMapper;
import com.example.usercenter.mapper.LearningPathNodeMapper;
import com.example.usercenter.model.domain.LearningPath;
import com.example.usercenter.model.domain.LearningPathNode;
import com.example.usercenter.service.LearningPathService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LearningPathServiceImpl extends ServiceImpl<LearningPathMapper, LearningPath> implements LearningPathService {

    private final LearningPathMapper pathMapper;
    private final LearningPathNodeMapper nodeMapper;

    @Override
    public List<LearningPath> getUserPaths(Long userId) {
        return this.list(new LambdaQueryWrapper<LearningPath>()
                .eq(LearningPath::getUserId, userId)
                .orderByAsc(LearningPath::getSortOrder));
    }

    @Override
    public LearningPath getPathDetail(Long pathId, Long userId) {
        LearningPath path = this.getById(pathId);
        if (path == null || !path.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "学习路径不存在");
        }
        return path;
    }

    @Override
    public List<LearningPathNode> getPathNodes(Long pathId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<LearningPathNode>()
                .eq(LearningPathNode::getPathId, pathId)
                .orderByAsc(LearningPathNode::getSortOrder));
    }

    @Override
    public void updateNodeStatus(Long nodeId, Long userId, String status) {
        LearningPathNode node = nodeMapper.selectById(nodeId);
        if (node == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "节点不存在");
        LearningPath path = this.getById(node.getPathId());
        if (path == null || !path.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权操作");
        }
        if (!List.of("pending", "in_progress", "completed").contains(status)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的状态值");
        }
        node.setStatus(status);
        nodeMapper.updateById(node);
    }
}
