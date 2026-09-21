package com.example.usercenter.utils;

import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 敏感词过滤器（DFA 字典树匹配）
 *
 * 词库加载优先级：
 * 1. 外部词库文件（sensitive.words-file 配置，默认 工作目录/sensitive-words.txt，
 *    管理员通过 /admin/sensitive/words 接口更新时写入此文件，重启后依然生效）
 * 2. 内置 classpath:sensitive-words.txt
 *
 * 每行一个词，# 开头为注释。匹配时忽略词中的空白与常见干扰符号（如 "赌 博" 仍可命中）。
 */
@Component
@Slf4j
public class SensitiveWordChecker {

    /**
     * 干扰符：匹配过程中跳过这些字符，防止用符号拆词绕过（如 "赌 博" 仍可命中）。
     * 注意：不含半角冒号 ":" 和斜杠 "/"——它们是 URL 协议/路径分隔符（如 https://），
     * 若作为干扰符会把 "https://b" 中的 s 与 b 跨 "://" 拼接误报为敏感词「s://b」。
     */
    private static final String IGNORE_CHARS = " \t\r\n*#@!！?？。，,.;：~～·—_-+|\\[]{}()（）\"'`";

    /** 单个敏感词最大长度 */
    private static final int MAX_WORD_LENGTH = 50;
    /** 词库最大词条数 */
    private static final int MAX_WORD_COUNT = 10000;

    /** volatile 保证热替换后其他线程立即可见 */
    private volatile TrieNode root = new TrieNode();

    /** 当前生效的词列表（用于管理接口回显） */
    private volatile List<String> currentWords = new ArrayList<>();

    /** 外部词库文件路径（为空则仅使用内置词库） */
    @Value("${sensitive.words-file:}")
    private String externalWordsFile;

    @PostConstruct
    public void init() {
        List<String> words = loadFromExternalFile();
        String source = "外部词库文件";
        if (words == null) {
            words = loadFromClasspath();
            source = "内置词库(classpath)";
        }
        applyWords(words, source);
    }

    /**
     * 重新加载词库（外部文件优先，其次内置），立即生效
     *
     * @return 生效的词条数
     */
    public synchronized int reload() {
        List<String> words = loadFromExternalFile();
        String source = "外部词库文件";
        if (words == null) {
            words = loadFromClasspath();
            source = "内置词库(classpath)";
        }
        applyWords(words, source);
        return currentWords.size();
    }

    /**
     * 获取当前生效的词列表（只读副本）
     */
    public List<String> getWords() {
        return new ArrayList<>(currentWords);
    }

    /**
     * 全量更新词库：写入外部文件并立即生效
     *
     * @return 生效的词条数
     */
    public synchronized int updateWords(List<String> words) {
        if (words == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "词库内容不能为空");
        }
        // 清洗：去空白、去注释行、去重、长度与总量校验
        Set<String> cleaned = new LinkedHashSet<>();
        for (String raw : words) {
            if (raw == null) continue;
            String word = raw.trim().toLowerCase();
            if (word.isEmpty() || word.startsWith("#")) continue;
            if (word.length() > MAX_WORD_LENGTH) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "敏感词「" + word + "」超过 " + MAX_WORD_LENGTH + " 个字符");
            }
            cleaned.add(word);
        }
        if (cleaned.size() > MAX_WORD_COUNT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "词库词条数不能超过 " + MAX_WORD_COUNT);
        }
        if (externalWordsFile == null || externalWordsFile.isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "未配置 sensitive.words-file，无法持久化词库");
        }
        Path path = Paths.get(externalWordsFile);
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write("# 敏感词库（每行一个词，# 开头为注释）\n");
                for (String word : cleaned) {
                    writer.write(word);
                    writer.write('\n');
                }
            }
        } catch (IOException e) {
            log.error("敏感词库写入失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "词库文件写入失败：" + e.getMessage());
        }
        applyWords(new ArrayList<>(cleaned), "管理接口更新");
        return currentWords.size();
    }

    /**
     * 返回文本中命中的第一个敏感词片段；未命中返回 null
     */
    public String findFirst(String text) {
        if (text == null || text.isEmpty()) return null;
        String lower = text.toLowerCase();
        TrieNode trieRoot = this.root;
        for (int start = 0; start < lower.length(); start++) {
            TrieNode node = trieRoot;
            StringBuilder matched = new StringBuilder();
            for (int i = start; i < lower.length(); i++) {
                char c = lower.charAt(i);
                if (isIgnoreChar(c)) {
                    // 词中允许出现干扰符，但仅当已经开始匹配时才跳过
                    if (matched.length() > 0) {
                        matched.append(c);
                    }
                    continue;
                }
                TrieNode next = node.children.get(c);
                if (next == null) break;
                node = next;
                matched.append(c);
                if (node.end) {
                    return matched.toString();
                }
            }
        }
        return null;
    }

    /**
     * 校验文本是否包含敏感词，包含则抛出业务异常
     *
     * @param fieldDesc 字段描述，用于提示（如 "评论内容"、"星球名称"）
     */
    public void check(String fieldDesc, String... texts) {
        for (String text : texts) {
            String hit = findFirst(text);
            if (log.isDebugEnabled()) {
                log.debug("敏感词校验: field={}, textLength={}, hit={}", fieldDesc,
                        text == null ? 0 : text.length(), hit);
            }
            if (hit != null) {
                log.warn("敏感词拦截: field={}, hit={}", fieldDesc, hit);
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        fieldDesc + "包含敏感词「" + hit + "」，请修改后再发布");
            }
        }
    }

    // ========== 内部方法 ==========

    /**
     * 构建新字典树并整体替换（读线程无锁）
     */
    private void applyWords(List<String> words, String source) {
        TrieNode newRoot = new TrieNode();
        for (String word : words) {
            insertWord(newRoot, word);
        }
        this.root = newRoot;
        this.currentWords = new ArrayList<>(words);
        log.info("敏感词库已加载（来源：{}），共 {} 个词", source, words.size());
    }

    private static void insertWord(TrieNode trieRoot, String word) {
        TrieNode node = trieRoot;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (isIgnoreChar(c)) continue;
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.end = true;
    }

    /** 读外部词库文件；文件不存在返回 null（回退内置词库） */
    private List<String> loadFromExternalFile() {
        if (externalWordsFile == null || externalWordsFile.isBlank()) {
            return null;
        }
        Path path = Paths.get(externalWordsFile);
        if (!Files.isReadable(path)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return readWords(reader);
        } catch (IOException e) {
            log.warn("外部敏感词库读取失败，回退内置词库: {}", e.getMessage());
            return null;
        }
    }

    private List<String> loadFromClasspath() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("sensitive-words.txt").getInputStream(),
                StandardCharsets.UTF_8))) {
            return readWords(reader);
        } catch (Exception e) {
            log.error("内置敏感词库加载失败，敏感词过滤将不可用: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private static List<String> readWords(BufferedReader reader) throws IOException {
        List<String> words = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String word = line.trim();
            if (word.isEmpty() || word.startsWith("#")) continue;
            words.add(word.toLowerCase());
        }
        return words;
    }

    private static boolean isIgnoreChar(char c) {
        return IGNORE_CHARS.indexOf(c) >= 0;
    }

    /**
     * DFA 字典树节点
     */
    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean end;
    }
}
