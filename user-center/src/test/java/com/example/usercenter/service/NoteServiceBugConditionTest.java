package com.example.usercenter.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.LikeRecordMapper;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.model.domain.LikeRecord;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.service.impl.NoteServiceImpl;
import com.example.usercenter.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug Condition 探索性测试 —— 修复前运行
 *
 * 目标：在未修复代码上暴露竞态条件，证明 Bug 存在。
 * 预期结果：测试 FAILS（失败即证明 Bug 存在）。
 *
 * Validates: Requirements 1.1, 1.2, 1.3
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Bug Condition 探索性测试（修复前运行，预期失败）")
public class NoteServiceBugConditionTest {

    @Mock
    private NoteMapper noteMapper;

    @Mock
    private LikeRecordMapper likeRecordMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private HotRankService hotRankService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Spy
    @InjectMocks
    private NoteServiceImpl noteService;

    private static final Long NOTE_ID = 1L;
    private static final Long USER_ID = 1L;

    private Note testNote;

    @BeforeEach
    void setUp() {
        // 准备一个存在的笔记
        testNote = new Note();
        testNote.setId(NOTE_ID);
        testNote.setTitle("测试笔记");
        testNote.setLikeCount(0);
        testNote.setViewCount(0);
        testNote.setCommentCount(0);

        // 通过 spy 直接 mock getById，绕过 ServiceImpl 的 baseMapper 依赖
        doReturn(testNote).when(noteService).getById(NOTE_ID);

        // 模拟 incrementLikeCount 成功
        when(noteMapper.incrementLikeCount(NOTE_ID)).thenReturn(1);

        // 模拟 incrementViewCount 成功
        when(noteMapper.incrementViewCount(NOTE_ID)).thenReturn(1);

        // 模拟 likeRecordMapper.insert 成功
        when(likeRecordMapper.insert(any(LikeRecord.class))).thenReturn(1);

        // 模拟 hotRankService（不抛异常）
        doNothing().when(hotRankService).updateHotScore(anyLong(), anyDouble());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // =========================================================================
    // 测试 1 - 并发点赞竞态
    // 使用 CountDownLatch 让 10 个线程同时调用 likeNote(noteId=1, userId=1)
    // 断言 like_count 增量 = 1（未修复代码上此断言将失败，增量可能为 2-10）
    // =========================================================================
    @Test
    @DisplayName("测试1: 并发点赞竞态 —— 10线程同时点赞，断言 like_count 增量=1（预期失败）")
    void test1_concurrentLikeRaceCondition() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // 修复后代码：直接 insert，依赖唯一约束。
        // 模拟：第1次 insert 成功，后续 insert 抛出 DuplicateKeyException（模拟数据库唯一约束）
        when(likeRecordMapper.insert(any(LikeRecord.class)))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_user_target'"));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 每个线程设置自己的 UserContext（模拟同一用户的并发请求）
                    UserContext.set(new LoginUserDTO(USER_ID, 0, "token"));
                    startLatch.await(); // 等待统一起跑
                    noteService.likeNote(NOTE_ID, null);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    exceptionCount.incrementAndGet();
                    exceptions.add(e);
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    UserContext.clear();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 所有线程同时开始
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 统计 incrementLikeCount 被调用的次数（代表 like_count 的实际增量）
        int likeCountIncrement = mockingDetails(noteMapper)
                .getInvocations()
                .stream()
                .filter(inv -> inv.getMethod().getName().equals("incrementLikeCount"))
                .mapToInt(inv -> 1)
                .sum();

        System.out.println("=== 测试1 结果 ===");
        System.out.println("成功调用次数（like_count 增量）: " + likeCountIncrement);
        System.out.println("抛出异常次数: " + exceptionCount.get());
        System.out.println("期望 like_count 增量 = 1，实际 = " + likeCountIncrement);

        // 断言：like_count 增量应该恰好为 1
        // 修复后代码：第1次 insert 成功 → incrementLikeCount 被调用1次；
        // 后续 insert 抛出 DuplicateKeyException → 转换为 BusinessException，不调用 incrementLikeCount
        assertEquals(1, likeCountIncrement,
                "Bug 已修复：并发点赞 like_count 增量 = " + likeCountIncrement + "，期望 = 1。" +
                "修复方案：依赖数据库唯一约束 uk_user_target 保证幂等，消除 TOCTOU 竞态窗口。");
    }

    // =========================================================================
    // 测试 2 - 重复点赞未拦截
    // 顺序调用两次 likeNote(noteId=1, userId=1)
    // 断言第二次抛出 BusinessException("已点赞，请勿重复操作")
    // 未修复代码上：selectCount 始终返回 0（模拟竞态场景），第二次也成功
    // =========================================================================
    @Test
    @DisplayName("测试2: 重复点赞未拦截 —— 第二次调用应抛出异常（预期失败）")
    void test2_duplicateLikeNotBlocked() {
        UserContext.set(new LoginUserDTO(USER_ID, 0, "token"));

        // 修复后代码：直接 insert，依赖唯一约束。
        // 第1次 insert 成功，第2次 insert 抛出 DuplicateKeyException（模拟数据库唯一约束）
        when(likeRecordMapper.insert(any(LikeRecord.class)))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_user_target'"));

        System.out.println("=== 测试2 结果 ===");

        // 第一次点赞 —— 应该成功
        boolean firstResult = noteService.likeNote(NOTE_ID, null);
        assertTrue(firstResult, "第一次点赞应该成功");
        System.out.println("第一次点赞：成功（符合预期）");

        // 第二次点赞 —— 修复后代码捕获 DuplicateKeyException，转换为 BusinessException
        // 断言第二次应该抛出 BusinessException("已点赞，请勿重复操作")
        BusinessException exception = assertThrows(BusinessException.class,
                () -> noteService.likeNote(NOTE_ID, null),
                "修复验证：第二次点赞应抛出 BusinessException(\"已点赞，请勿重复操作\")。" +
                "修复方案：捕获 DuplicateKeyException 并转换为 BusinessException，依赖数据库唯一约束保证幂等。");

        assertEquals("已点赞，请勿重复操作", exception.getDescription(),
                "异常描述应为 '已点赞，请勿重复操作'");
        System.out.println("第二次点赞：抛出异常（符合期望行为）");
    }

    // =========================================================================
    // 测试 3 - 浏览量无缓冲
    // 调用 increaseViewCount(noteId=1)
    // 断言 Redis key "note:view:1" 存在且值 = 1
    // 未修复代码上：increaseViewCount 直接写库，不写 Redis，此 key 不存在
    // =========================================================================
    @Test
    @DisplayName("测试3: 浏览量无缓冲 —— 调用后 Redis key note:view:1 应存在（预期失败）")
    void test3_viewCountNoRedisBuffer() {
        System.out.println("=== 测试3 结果 ===");

        // 修复后代码：调用 redisTemplate.opsForValue().increment("note:view:{noteId}")
        // 配置 redisTemplate mock：opsForValue() 返回 valueOperations，increment 返回 1L
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("note:view:" + NOTE_ID)).thenReturn(1L);

        boolean result = noteService.increaseViewCount(NOTE_ID);
        assertTrue(result, "increaseViewCount 应返回 true");

        // 验证：修复后代码调用了 redisTemplate.opsForValue().increment("note:view:1")
        verify(valueOperations, times(1)).increment("note:view:" + NOTE_ID);
        System.out.println("验证：redisTemplate.opsForValue().increment(\"note:view:1\") 被调用（Redis 缓冲生效）");

        // 断言：修复后不应该直接调用 noteMapper.incrementViewCount（Redis 可用时走缓冲路径）
        verify(noteMapper, never()).incrementViewCount(NOTE_ID);
        System.out.println("验证：noteMapper.incrementViewCount 未被调用（Redis 缓冲路径正确）");
    }
}
