package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.KnowledgeMapper;
import com.example.usercenter.model.domain.Knowledge;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.service.KnowledgeService;
import com.example.usercenter.service.NoteService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.example.usercenter.contant.UserConstant.ADMIN_ROLE;
import static com.example.usercenter.contant.UserConstant.USER_LOGIN_STATE;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.utils.UserContext;

/**
 * 知识地图服务实现类
 * @author zy
 */
@Service
@Slf4j
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge> implements KnowledgeService {

    @Resource
    private NoteService noteService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Knowledge getKnowledgeMap(Long starId) {
        QueryWrapper<Knowledge> queryWrapper = new QueryWrapper<>();
        if (starId != null) {
            queryWrapper.eq("star_id", starId);
        }
        queryWrapper.orderByDesc("update_time");
        queryWrapper.last("LIMIT 1");

        Knowledge knowledge = this.getOne(queryWrapper);
        if (knowledge == null) {
            // 返回空的知识地图
            knowledge = new Knowledge();
            knowledge.setNodeList(java.util.Collections.emptyList());
            knowledge.setEdgeList(java.util.Collections.emptyList());
            return knowledge;
        }

        // 解析节点和边
        processKnowledgeData(knowledge);
        return knowledge;
    }

    @Override
    public boolean saveKnowledgeMap(Knowledge knowledge, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null || !isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        if (knowledge == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识地图数据不能为空");
        }

        // 将 nodeList/edgeList 序列化为 JSON，存入 config 字段
        try {
            Map<String, Object> configMap = new java.util.HashMap<>();
            configMap.put("nodes", knowledge.getNodeList() != null ? knowledge.getNodeList() : java.util.Collections.emptyList());
            configMap.put("edges", knowledge.getEdgeList() != null ? knowledge.getEdgeList() : java.util.Collections.emptyList());
            knowledge.setConfig(objectMapper.writeValueAsString(configMap));
        } catch (Exception e) {
            log.error("地图数据序列化失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "地图数据序列化失败");
        }

        // 查询是否已存在
        QueryWrapper<Knowledge> queryWrapper = new QueryWrapper<>();
        if (knowledge.getStarId() != null) {
            queryWrapper.eq("star_id", knowledge.getStarId());
        }
        Knowledge existing = this.getOne(queryWrapper);

        if (existing != null) {
            existing.setConfig(knowledge.getConfig());
            existing.setUpdateTime(new Date());
            return this.updateById(existing);
        } else {
            // 新增时补充必填字段
            if (knowledge.getName() == null || knowledge.getName().isBlank()) {
                knowledge.setName("星球知识地图");
            }
            knowledge.setCreateTime(new Date());
            knowledge.setUpdateTime(new Date());
            return this.save(knowledge);
        }
    }

    @Override
    public List<Note> getNodeContent(String nodeId) {
        if (nodeId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "节点ID不能为空");
        }
        // 自动节点 ID 形如 note_{noteId}，优先按 noteId 直接查笔记
        if (nodeId.startsWith("note_")) {
            try {
                Long noteId = Long.parseLong(nodeId.substring(5));
                Note note = noteService.getById(noteId);
                if (note != null && "published".equals(note.getStatus())) {
                    return java.util.Collections.singletonList(note);
                }
                return java.util.Collections.emptyList();
            } catch (NumberFormatException ignored) {
                // 降级到关键词搜索
            }
        }
        // 降级：用 nodeId 当关键词搜索
        com.example.usercenter.model.domain.request.NoteQueryRequest queryRequest =
                new com.example.usercenter.model.domain.request.NoteQueryRequest();
        queryRequest.setKeyword(nodeId);
        queryRequest.setSortType("hot");
        return noteService.getNoteList(queryRequest).getRecords();
    }

    // ===================== 自动同步：笔记 ↔ 知识地图 =====================

    @Override
    @Async("asyncTaskExecutor")
    public void syncNodeFromNote(Note note) {
        if (note == null || note.getStarId() == null || note.getId() == null) return;
        try {
            Knowledge map = getOrCreateMap(note.getStarId());
            List<Knowledge.KnowledgeNode> nodes = parseNodes(map);
            List<Knowledge.KnowledgeEdge> edges = parseEdges(map);
            String nodeId = "note_" + note.getId();

            Knowledge.KnowledgeNode existing = nodes.stream()
                    .filter(n -> nodeId.equals(n.getId())).findFirst().orElse(null);

            boolean published = "published".equals(note.getStatus());
            if (!published) {
                // 未发布：移除节点及相关连线
                if (existing != null) {
                    nodes.remove(existing);
                    edges.removeIf(e -> nodeId.equals(e.getSource()) || nodeId.equals(e.getTarget()));
                }
            } else if (existing == null) {
                // 新建自动节点
                Knowledge.KnowledgeNode node = new Knowledge.KnowledgeNode();
                node.setId(nodeId);
                node.setLabel(note.getTitle());
                node.setType("concept");
                node.setNoteId(note.getId());
                node.setCategory(note.getCategory());
                node.setTags(parseTags(note.getTags()));
                node.setAuto(true);
                node.setHidden(false);
                int[] pos = autoLayout(nodes, note.getCategory());
                node.setX(pos[0]);
                node.setY(pos[1]);
                nodes.add(node);
                addAutoEdges(node, nodes, edges);
            } else {
                // 更新元数据，保留 x/y/hidden（管理员调整不被覆盖）
                existing.setLabel(note.getTitle());
                existing.setCategory(note.getCategory());
                existing.setTags(parseTags(note.getTags()));
                String nid = nodeId;
                edges.removeIf(e -> (nid.equals(e.getSource()) || nid.equals(e.getTarget())) && Boolean.TRUE.equals(e.getAuto()));
                addAutoEdges(existing, nodes, edges);
            }
            saveConfig(map, nodes, edges);
        } catch (Exception e) {
            log.error("同步知识地图节点失败 noteId={}", note.getId(), e);
        }
    }

    @Override
    @Async("asyncTaskExecutor")
    public void removeNodeFromNote(Long starId, Long noteId) {
        if (starId == null || noteId == null) return;
        try {
            Knowledge map = getMap(starId);
            if (map == null) return;
            List<Knowledge.KnowledgeNode> nodes = parseNodes(map);
            List<Knowledge.KnowledgeEdge> edges = parseEdges(map);
            String nodeId = "note_" + noteId;
            boolean removed = nodes.removeIf(n -> nodeId.equals(n.getId()));
            edges.removeIf(e -> nodeId.equals(e.getSource()) || nodeId.equals(e.getTarget()));
            if (removed) saveConfig(map, nodes, edges);
        } catch (Exception e) {
            log.error("移除知识地图节点失败 noteId={}", noteId, e);
        }
    }

    @Override
    public void regenerateMap(Long starId) {
        if (starId == null) return;
        try {
            Knowledge map = getOrCreateMap(starId);
            List<Knowledge.KnowledgeNode> nodes = parseNodes(map);
            List<Knowledge.KnowledgeEdge> edges = parseEdges(map);
            // 保留手动节点（非 auto）与手动连线（非 auto），重建所有 auto
            List<Knowledge.KnowledgeNode> manualNodes = nodes.stream()
                    .filter(n -> !Boolean.TRUE.equals(n.getAuto()))
                    .collect(java.util.stream.Collectors.toList());
            List<Knowledge.KnowledgeEdge> manualEdges = edges.stream()
                    .filter(e -> !Boolean.TRUE.equals(e.getAuto()))
                    .collect(java.util.stream.Collectors.toList());

            // 查该星球所有已发布笔记（getNoteList 不按 starId 过滤，需手动过滤）
            com.example.usercenter.model.domain.request.NoteQueryRequest q =
                    new com.example.usercenter.model.domain.request.NoteQueryRequest();
            q.setPageSize(100);
            List<Note> notes = noteService.getNoteList(q).getRecords().stream()
                    .filter(n -> starId.equals(n.getStarId()) && "published".equals(n.getStatus()))
                    .collect(java.util.stream.Collectors.toList());

            List<Knowledge.KnowledgeNode> autoNodes = new java.util.ArrayList<>();
            for (Note n : notes) {
                Knowledge.KnowledgeNode node = new Knowledge.KnowledgeNode();
                node.setId("note_" + n.getId());
                node.setLabel(n.getTitle());
                node.setType("concept");
                node.setNoteId(n.getId());
                node.setCategory(n.getCategory());
                node.setTags(parseTags(n.getTags()));
                node.setAuto(true);
                node.setHidden(false);
                // 保留已调整过的 x/y/hidden
                nodes.stream().filter(x -> ("note_" + n.getId()).equals(x.getId())).findFirst()
                        .ifPresent(old -> { node.setX(old.getX()); node.setY(old.getY()); node.setHidden(old.getHidden()); });
                if (node.getX() == null || node.getY() == null) {
                    int[] pos = autoLayout(autoNodes, n.getCategory());
                    node.setX(pos[0]);
                    node.setY(pos[1]);
                }
                autoNodes.add(node);
            }
            List<Knowledge.KnowledgeEdge> autoEdges = new java.util.ArrayList<>();
            for (Knowledge.KnowledgeNode node : autoNodes) {
                addAutoEdges(node, autoNodes, autoEdges);
            }
            List<Knowledge.KnowledgeNode> allNodes = new java.util.ArrayList<>(manualNodes);
            allNodes.addAll(autoNodes);
            List<Knowledge.KnowledgeEdge> allEdges = new java.util.ArrayList<>(manualEdges);
            allEdges.addAll(autoEdges);
            saveConfig(map, allNodes, allEdges);
        } catch (Exception e) {
            log.error("重建知识地图失败 starId={}", starId, e);
        }
    }

    // ===================== 辅助方法 =====================

    private Knowledge getOrCreateMap(Long starId) {
        QueryWrapper<Knowledge> qw = new QueryWrapper<>();
        qw.eq("star_id", starId);
        Knowledge map = this.getOne(qw);
        if (map == null) {
            map = new Knowledge();
            map.setStarId(starId);
            map.setName("星球知识地图");
            map.setConfig("{\"nodes\":[],\"edges\":[]}");
            map.setCreateTime(new Date());
            map.setUpdateTime(new Date());
            this.save(map);
        }
        return map;
    }

    private Knowledge getMap(Long starId) {
        QueryWrapper<Knowledge> qw = new QueryWrapper<>();
        qw.eq("star_id", starId);
        return this.getOne(qw);
    }

    private List<Knowledge.KnowledgeNode> parseNodes(Knowledge map) {
        if (map.getConfig() == null || map.getConfig().isBlank()) return new java.util.ArrayList<>();
        try {
            Map<String, Object> cfg = objectMapper.readValue(map.getConfig(), new TypeReference<Map<String, Object>>() {});
            Object nodesObj = cfg.get("nodes");
            if (nodesObj == null) return new java.util.ArrayList<>();
            String json = objectMapper.writeValueAsString(nodesObj);
            return objectMapper.readValue(json, new TypeReference<List<Knowledge.KnowledgeNode>>() {});
        } catch (Exception e) {
            log.warn("解析 nodes 失败: {}", e.getMessage());
            return new java.util.ArrayList<>();
        }
    }

    private List<Knowledge.KnowledgeEdge> parseEdges(Knowledge map) {
        if (map.getConfig() == null || map.getConfig().isBlank()) return new java.util.ArrayList<>();
        try {
            Map<String, Object> cfg = objectMapper.readValue(map.getConfig(), new TypeReference<Map<String, Object>>() {});
            Object edgesObj = cfg.get("edges");
            if (edgesObj == null) return new java.util.ArrayList<>();
            String json = objectMapper.writeValueAsString(edgesObj);
            return objectMapper.readValue(json, new TypeReference<List<Knowledge.KnowledgeEdge>>() {});
        } catch (Exception e) {
            log.warn("解析 edges 失败: {}", e.getMessage());
            return new java.util.ArrayList<>();
        }
    }

    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) return java.util.Collections.emptyList();
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /** 按 category 分区域自动布局，返回 [x, y] */
    private int[] autoLayout(List<Knowledge.KnowledgeNode> existingNodes, String category) {
        Map<String, int[]> centers = new java.util.HashMap<>();
        centers.put("frontend", new int[]{180, 260});
        centers.put("backend", new int[]{620, 260});
        centers.put("algorithm", new int[]{400, 130});
        centers.put("database", new int[]{200, 430});
        centers.put("other", new int[]{600, 430});
        int[] center = centers.getOrDefault(category, centers.get("other"));
        long count = existingNodes.stream()
                .filter(n -> category != null && category.equals(n.getCategory())).count();
        double angle = count * (2 * Math.PI / 8); // 每节点 45 度
        int radius = 55;
        int x = (int) (center[0] + radius * Math.cos(angle));
        int y = (int) (center[1] + radius * Math.sin(angle));
        return new int[]{x, y};
    }

    /** 为 node 生成自动连线：同 category + 共享 tag，去重（无向） */
    private void addAutoEdges(Knowledge.KnowledgeNode node, List<Knowledge.KnowledgeNode> allNodes, List<Knowledge.KnowledgeEdge> edges) {
        String nid = node.getId();
        for (Knowledge.KnowledgeNode other : allNodes) {
            if (other.getId().equals(nid)) continue;
            if (!Boolean.TRUE.equals(other.getAuto())) continue; // 只连自动节点
            boolean sameCategory = node.getCategory() != null && node.getCategory().equals(other.getCategory());
            boolean shareTag = shareTag(node.getTags(), other.getTags());
            if (!sameCategory && !shareTag) continue;
            String a = nid, b = other.getId();
            String s = a.compareTo(b) <= 0 ? a : b;
            String t = a.compareTo(b) <= 0 ? b : a;
            boolean exists = edges.stream().anyMatch(e -> s.equals(e.getSource()) && t.equals(e.getTarget()));
            if (exists) continue;
            Knowledge.KnowledgeEdge edge = new Knowledge.KnowledgeEdge();
            edge.setSource(s);
            edge.setTarget(t);
            edge.setAuto(true);
            edges.add(edge);
        }
    }

    private boolean shareTag(List<String> a, List<String> b) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) return false;
        for (String t : a) {
            if (b.contains(t)) return true;
        }
        return false;
    }

    private void saveConfig(Knowledge map, List<Knowledge.KnowledgeNode> nodes, List<Knowledge.KnowledgeEdge> edges) {
        try {
            Map<String, Object> cfg = new java.util.HashMap<>();
            cfg.put("nodes", nodes != null ? nodes : java.util.Collections.emptyList());
            cfg.put("edges", edges != null ? edges : java.util.Collections.emptyList());
            map.setConfig(objectMapper.writeValueAsString(cfg));
            map.setUpdateTime(new Date());
            this.updateById(map);
        } catch (Exception e) {
            log.error("保存知识地图配置失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "知识地图保存失败");
        }
    }

    /**
     * 处理知识地图数据（从 config JSON 解析 nodeList/edgeList）
     */
    private void processKnowledgeData(Knowledge knowledge) {
        if (knowledge.getConfig() != null && !knowledge.getConfig().isBlank()) {
            try {
                Map<String, Object> configMap = objectMapper.readValue(knowledge.getConfig(),
                        new TypeReference<Map<String, Object>>() {});
                Object nodesObj = configMap.get("nodes");
                Object edgesObj = configMap.get("edges");
                if (nodesObj != null) {
                    String nodesJson = objectMapper.writeValueAsString(nodesObj);
                    knowledge.setNodeList(objectMapper.readValue(nodesJson,
                            new TypeReference<List<Knowledge.KnowledgeNode>>() {}));
                } else {
                    knowledge.setNodeList(java.util.Collections.emptyList());
                }
                if (edgesObj != null) {
                    String edgesJson = objectMapper.writeValueAsString(edgesObj);
                    knowledge.setEdgeList(objectMapper.readValue(edgesJson,
                            new TypeReference<List<Knowledge.KnowledgeEdge>>() {}));
                } else {
                    knowledge.setEdgeList(java.util.Collections.emptyList());
                }
            } catch (Exception e) {
                log.error("地图数据反序列化失败", e);
                knowledge.setNodeList(java.util.Collections.emptyList());
                knowledge.setEdgeList(java.util.Collections.emptyList());
            }
        } else {
            knowledge.setNodeList(java.util.Collections.emptyList());
            knowledge.setEdgeList(java.util.Collections.emptyList());
        }
    }

    /**
     * 获取登录用户
     */
    private User getLoginUser(HttpServletRequest request) {
        LoginUserDTO dto = UserContext.get();
        if (dto == null) return null;
        User user = new User();
        user.setId(dto.getUserId());
        user.setUserRole(dto.getUserRole());
        return user;
    }

    /**
     * 判断是否为管理员
     */
    private boolean isAdmin(User user) {
        return user != null && user.getUserRole() != null && user.getUserRole() == ADMIN_ROLE;
    }
}
