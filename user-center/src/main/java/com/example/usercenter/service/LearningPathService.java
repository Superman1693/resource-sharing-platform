package com.example.usercenter.service;

import com.example.usercenter.model.domain.LearningPath;
import com.example.usercenter.model.domain.LearningPathNode;

import java.util.List;

public interface LearningPathService {
    List<LearningPath> getUserPaths(Long userId);
    LearningPath getPathDetail(Long pathId, Long userId);
    List<LearningPathNode> getPathNodes(Long pathId);
    void updateNodeStatus(Long nodeId, Long userId, String status);
}
