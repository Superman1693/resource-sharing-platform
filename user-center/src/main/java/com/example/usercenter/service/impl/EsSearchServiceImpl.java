package com.example.usercenter.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.mapper.ResourceMapper;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.es.NoteDocument;
import com.example.usercenter.model.es.ResourceDocument;
import com.example.usercenter.service.EsSearchService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ES 搜索服务实现
 * 只注入 Mapper，不注入 NoteService/ResourceService，避免循环依赖
 * 不 import jakarta.annotation.Resource，避免与 model.domain.Resource 同名冲突
 */
@Service
@Slf4j
public class EsSearchServiceImpl implements EsSearchService {

    private static final String NOTE_INDEX = "note_index";
    private static final String RESOURCE_INDEX = "resource_index";
    private static final int BULK_SIZE = 200;

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private ResourceMapper resourceMapper;

    /** 热搜词 ZSET（member=关键词，score=搜索次数） */
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 初始化索引 ─────────────────────────────────────────

    @PostConstruct
    public void initIndices() {
        createIndexIfAbsent(NOTE_INDEX, """
                {
                  "mappings": {
                    "properties": {
                      "id":          { "type": "long" },
                      "title":       { "type": "text",  "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
                      "summary":     { "type": "text",  "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
                      "content":     { "type": "text",  "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
                      "author":      { "type": "keyword" },
                      "category":    { "type": "keyword" },
                      "contentType": { "type": "keyword" },
                      "status":      { "type": "keyword" },
                      "viewCount":   { "type": "integer" },
                      "likeCount":   { "type": "integer" },
                      "commentCount":{ "type": "integer" },
                      "isTop":       { "type": "integer" },
                      "publishTime": { "type": "date" },
                      "createTime":  { "type": "date" }
                    }
                  }
                }
                """);

        createIndexIfAbsent(RESOURCE_INDEX, """
                {
                  "mappings": {
                    "properties": {
                      "id":           { "type": "long" },
                      "name":         { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
                      "title":        { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
                      "description":  { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
                      "resourceType": { "type": "keyword" },
                      "category":     { "type": "keyword" },
                      "tag":          { "type": "keyword" },
                      "status":       { "type": "keyword" },
                      "downloadCount":{ "type": "integer" },
                      "createTime":   { "type": "date" }
                    }
                  }
                }
                """);
    }

    private void createIndexIfAbsent(String index, String mappingJson) {
        try {
            boolean exists = esClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(index)))
                    .value();
            if (!exists) {
                esClient.indices().create(CreateIndexRequest.of(c -> c
                        .index(index)
                        .withJson(new java.io.StringReader(mappingJson))));
                log.info("ES 索引 {} 创建成功", index);
            }
        } catch (Exception e) {
            log.warn("ES 索引 {} 初始化失败（ES 可能未启动）: {}", index, e.getMessage());
        }
    }

    // ── 笔记搜索 ───────────────────────────────────────────

    @Override
    public PageResult<NoteDocument> searchNotes(String keyword, String category,
                                                 String contentType, String sortType,
                                                 int page, int pageSize) {
        // 热搜词计数（Redis ZSET，失败不影响搜索结果）
        if (StringUtils.isNotBlank(keyword)) {
            try { redisTemplate.opsForZSet().incrementScore("search:hot", keyword.trim(), 1.0); }
            catch (Exception e) { log.warn("热搜记录失败", e); }
        }
        try {
            int from = (page - 1) * pageSize;
            SearchRequest.Builder req = new SearchRequest.Builder()
                    .index(NOTE_INDEX).from(from).size(pageSize);

            List<Query> mustQueries = new ArrayList<>();
            List<Query> filterQueries = new ArrayList<>();

            if (StringUtils.isNotBlank(keyword)) {
                mustQueries.add(Query.of(q -> q.multiMatch(m -> m
                        .query(keyword)
                        .fields("title^3", "summary^2", "content^1")
                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                        .fuzziness("AUTO"))));

                req.highlight(h -> h
                    .preTags("<em>")
                    .postTags("</em>")
                    .fields("title", f -> f)
                    .fields("summary", f -> f)
                    .fields("content", f -> f.fragmentSize(120).numberOfFragments(1)));
            }

            filterQueries.add(Query.of(q -> q.term(t -> t.field("status").value("published"))));
            if (StringUtils.isNotBlank(category)) {
                filterQueries.add(Query.of(q -> q.term(t -> t.field("category").value(category))));
            }
            if (StringUtils.isNotBlank(contentType)) {
                filterQueries.add(Query.of(q -> q.term(t -> t.field("contentType").value(contentType))));
            }

            final List<Query> fm = mustQueries;
            final List<Query> ff = filterQueries;
            req.query(q -> q.bool(b -> {
                if (!fm.isEmpty()) b.must(fm);
                b.filter(ff);
                return b;
            }));

            if ("latest".equals(sortType)) {
                req.sort(s -> s.field(f -> f.field("publishTime").order(SortOrder.Desc)));
            } else if (StringUtils.isBlank(keyword)) {
                req.sort(s -> s.field(f -> f.field("viewCount").order(SortOrder.Desc)));
                req.sort(s -> s.field(f -> f.field("likeCount").order(SortOrder.Desc)));
            }

            SearchResponse<NoteDocument> response = esClient.search(req.build(), NoteDocument.class);
            List<NoteDocument> docs = response.hits().hits().stream()
                    .map(hit -> {
                        NoteDocument source = hit.source();
                        if (source == null) {
                            return null;
                        }
                        applyNoteHighlight(hit, source);
                        return source;
                    })
                    .filter(Objects::nonNull)
                    .toList();
            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            return new PageResult<>(total, page, pageSize, docs);
        } catch (Exception e) {
            log.error("ES 搜索笔记失败: {}", e.getMessage(), e);
            return new PageResult<>(0, page, pageSize, List.of());
        }
    }

    @Override
    @Async("asyncTaskExecutor")
    public void indexNote(Note note) {
        if (note == null || note.getId() == null) return;
        try {
            NoteDocument doc = NoteDocument.from(note);
            esClient.index(IndexRequest.of(i -> i
                    .index(NOTE_INDEX)
                    .id(String.valueOf(note.getId()))
                    .document(doc)));
        } catch (Exception e) {
            log.error("ES 索引笔记失败, noteId={}: {}", note.getId(), e.getMessage());
        }
    }

    @Override
    @Async("asyncTaskExecutor")
    public void deleteNote(Long noteId) {
        try {
            esClient.delete(DeleteRequest.of(d -> d.index(NOTE_INDEX).id(String.valueOf(noteId))));
        } catch (Exception e) {
            log.error("ES 删除笔记失败, noteId={}: {}", noteId, e.getMessage());
        }
    }

    @Override
    public int syncAllNotes() {
        try {
            List<Note> notes = noteMapper.selectList(
                    new QueryWrapper<Note>().eq("status", "published").eq("is_delete", 0));

            int successCount = 0;
            List<Note> batch = new ArrayList<>(BULK_SIZE);
            for (Note note : notes) {
                fillNoteTagListIfNeeded(note);
                batch.add(note);
                if (batch.size() >= BULK_SIZE) {
                    successCount += bulkIndexNotes(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                successCount += bulkIndexNotes(batch);
            }

            log.info("ES 全量同步笔记完成，成功 {} 条，总计 {} 条", successCount, notes.size());
            return successCount;
        } catch (Exception e) {
            log.error("ES 全量同步笔记失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    // ── 资源搜索 ───────────────────────────────────────────

    @Override
    public PageResult<ResourceDocument> searchResources(String keyword, String tag,
                                                         String category, int page, int pageSize) {
        try {
            int from = (page - 1) * pageSize;
            SearchRequest.Builder req = new SearchRequest.Builder()
                    .index(RESOURCE_INDEX).from(from).size(pageSize);

            List<Query> mustQueries = new ArrayList<>();
            List<Query> filterQueries = new ArrayList<>();

            if (StringUtils.isNotBlank(keyword)) {
                mustQueries.add(Query.of(q -> q.multiMatch(m -> m
                        .query(keyword)
                        .fields("name^3", "title^2", "description^1")
                        .fuzziness("AUTO"))));

                req.highlight(h -> h
                    .preTags("<em>")
                    .postTags("</em>")
                    .fields("name", f -> f)
                    .fields("title", f -> f)
                    .fields("description", f -> f.fragmentSize(120).numberOfFragments(1)));
            }

            filterQueries.add(Query.of(q -> q.term(t -> t.field("status").value("enabled"))));
            // 类型筛选：与数据库 resource_type 列对应（ES 文档字段为 resourceType）
            if (StringUtils.isNotBlank(tag)) {
                filterQueries.add(Query.of(q -> q.term(t -> t.field("resourceType").value(tag))));
            }
            if (StringUtils.isNotBlank(category)) {
                filterQueries.add(Query.of(q -> q.term(t -> t.field("category").value(category))));
            }

            final List<Query> fm = mustQueries;
            final List<Query> ff = filterQueries;
            req.query(q -> q.bool(b -> {
                if (!fm.isEmpty()) b.must(fm);
                b.filter(ff);
                return b;
            }));
            req.sort(s -> s.field(f -> f.field("downloadCount").order(SortOrder.Desc)));

            SearchResponse<ResourceDocument> response = esClient.search(req.build(), ResourceDocument.class);
            List<ResourceDocument> docs = response.hits().hits().stream()
                    .map(hit -> {
                        ResourceDocument source = hit.source();
                        if (source == null) {
                            return null;
                        }
                        applyResourceHighlight(hit, source);
                        return source;
                    })
                    .filter(Objects::nonNull)
                    .toList();
            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            return new PageResult<>(total, page, pageSize, docs);
        } catch (Exception e) {
            log.error("ES 搜索资源失败: {}", e.getMessage(), e);
            return new PageResult<>(0, page, pageSize, List.of());
        }
    }

    @Override
    @Async("asyncTaskExecutor")
    public void indexResource(com.example.usercenter.model.domain.Resource resource) {
        if (resource == null || resource.getId() == null) return;
        try {
            ResourceDocument doc = ResourceDocument.from(resource);
            esClient.index(IndexRequest.of(i -> i
                    .index(RESOURCE_INDEX)
                    .id(String.valueOf(resource.getId()))
                    .document(doc)));
        } catch (Exception e) {
            log.error("ES 索引资源失败, resourceId={}: {}", resource.getId(), e.getMessage());
        }
    }

    @Override
    @Async("asyncTaskExecutor")
    public void deleteResource(Long resourceId) {
        try {
            esClient.delete(DeleteRequest.of(d -> d.index(RESOURCE_INDEX).id(String.valueOf(resourceId))));
        } catch (Exception e) {
            log.error("ES 删除资源失败, resourceId={}: {}", resourceId, e.getMessage());
        }
    }

    @Override
    public int syncAllResources() {
        try {
            List<com.example.usercenter.model.domain.Resource> resources = resourceMapper.selectList(
                    new QueryWrapper<com.example.usercenter.model.domain.Resource>()
                            .eq("status", "enabled").eq("is_delete", 0));

            int successCount = 0;
            List<com.example.usercenter.model.domain.Resource> batch = new ArrayList<>(BULK_SIZE);
            for (com.example.usercenter.model.domain.Resource resource : resources) {
                fillResourceTagListIfNeeded(resource);
                batch.add(resource);
                if (batch.size() >= BULK_SIZE) {
                    successCount += bulkIndexResources(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                successCount += bulkIndexResources(batch);
            }

            log.info("ES 全量同步资源完成，成功 {} 条，总计 {} 条", successCount, resources.size());
            return successCount;
        } catch (Exception e) {
            log.error("ES 全量同步资源失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    private void fillNoteTagListIfNeeded(Note note) {
        if (note.getTagList() == null && StringUtils.isNotBlank(note.getTags())) {
            try {
                note.setTagList(objectMapper.readValue(note.getTags(), new TypeReference<>() {}));
            } catch (Exception e) {
                log.warn("解析 note tags 失败, noteId={}", note.getId(), e);
                note.setTagList(Collections.emptyList());
            }
        }
    }

    private void fillResourceTagListIfNeeded(com.example.usercenter.model.domain.Resource resource) {
        if (resource.getTagList() == null && StringUtils.isNotBlank(resource.getTags())) {
            try {
                resource.setTagList(objectMapper.readValue(resource.getTags(), new TypeReference<>() {}));
            } catch (Exception e) {
                log.warn("解析 resource tags 失败, resourceId={}", resource.getId(), e);
                resource.setTagList(Collections.emptyList());
            }
        }
    }

    private int bulkIndexNotes(List<Note> notes) throws Exception {
        if (notes.isEmpty()) {
            return 0;
        }
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (Note note : notes) {
            if (note == null || note.getId() == null) {
                continue;
            }
            NoteDocument doc = NoteDocument.from(note);
            bulkBuilder.operations(op -> op.index(idx -> idx
                    .index(NOTE_INDEX)
                    .id(String.valueOf(note.getId()))
                    .document(doc)));
        }

        BulkResponse response = esClient.bulk(bulkBuilder.build());
        int failed = 0;
        if (response.errors()) {
            for (var item : response.items()) {
                if (item.error() != null) {
                    failed++;
                    log.error("ES 批量索引笔记失败, noteId={}, reason={}", item.id(), item.error().reason());
                }
            }
        }
        return notes.size() - failed;
    }

    private int bulkIndexResources(List<com.example.usercenter.model.domain.Resource> resources) throws Exception {
        if (resources.isEmpty()) {
            return 0;
        }
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (com.example.usercenter.model.domain.Resource resource : resources) {
            if (resource == null || resource.getId() == null) {
                continue;
            }
            ResourceDocument doc = ResourceDocument.from(resource);
            bulkBuilder.operations(op -> op.index(idx -> idx
                    .index(RESOURCE_INDEX)
                    .id(String.valueOf(resource.getId()))
                    .document(doc)));
        }

        BulkResponse response = esClient.bulk(bulkBuilder.build());
        int failed = 0;
        if (response.errors()) {
            for (var item : response.items()) {
                if (item.error() != null) {
                    failed++;
                    log.error("ES 批量索引资源失败, resourceId={}, reason={}", item.id(), item.error().reason());
                }
            }
        }
        return resources.size() - failed;
    }

    private void applyNoteHighlight(Hit<NoteDocument> hit, NoteDocument doc) {
        Map<String, List<String>> highlightMap = hit.highlight();
        if (highlightMap == null || highlightMap.isEmpty()) {
            return;
        }
        doc.setHighlights(highlightMap);
        doc.setTitle(getHighlightedValue(highlightMap, "title", doc.getTitle()));
        doc.setSummary(getHighlightedValue(highlightMap, "summary", doc.getSummary()));
        doc.setContent(getHighlightedValue(highlightMap, "content", doc.getContent()));
    }

    private void applyResourceHighlight(Hit<ResourceDocument> hit, ResourceDocument doc) {
        Map<String, List<String>> highlightMap = hit.highlight();
        if (highlightMap == null || highlightMap.isEmpty()) {
            return;
        }
        doc.setHighlights(highlightMap);
        doc.setName(getHighlightedValue(highlightMap, "name", doc.getName()));
        doc.setTitle(getHighlightedValue(highlightMap, "title", doc.getTitle()));
        doc.setDescription(getHighlightedValue(highlightMap, "description", doc.getDescription()));
    }

    private String getHighlightedValue(Map<String, List<String>> highlightMap, String field, String defaultValue) {
        List<String> values = highlightMap.get(field);
        if (values == null || values.isEmpty() || StringUtils.isBlank(values.get(0))) {
            return defaultValue;
        }
        return values.get(0);
    }
}
