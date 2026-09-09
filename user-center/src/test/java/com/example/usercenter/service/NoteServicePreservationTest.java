package com.example.usercenter.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.common.ErrorCode;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Preservation 属性测试 —— 修复前运行，建立基线
 *
 * 目标：验证非 Bug 条件输入（首次点赞、单次浏览量）的行为在修复前后保持不变。
 * 预期结果：测试全部 PASSES（建立基线，修复后不得回归）。
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Preservation 属性测试（修复前运行，预期全部通过）")
public class NoteServicePreservationTest {

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

    // =========================================================================
    // Bug Condition 判断函数（与 bugfix.md 伪代码一致）
    // =========================================================================

    /**
     * isBugCondition_Like: callCount > 1 AND sameUser AND sameNote
     * 非 Bug 条件 = callCount == 1（首次点赞）
     */
    private boolean isBugCondition_Like(Long userId, Long noteId, int callCount) {
        return callCount > 1;
    }

    /**
     * isBugCondition_View: concurrentCount > 1（简化：单次调用不触发 Bug）
     * 非 Bug 条件 = concurrentCount == 1（单次调用）
     */
    private boolean isBugCondition_View(Long noteId, int concurrentCount) {
        return concurrentCount > 1;
    }

    // =========================================================================
    // 辅助方法：为给定 noteId 准备 mock 笔记
    // =========================================================================
    private Note prepareNote(Long noteId) {
        Note note = new Note();
        note.setId(noteId);
        note.setTitle("测试笔记-" + noteId);
        note.setLikeCount(0);
        note.setViewCount(0);
        note.setCommentCount(0);
        doReturn(note).when(noteService).getById(noteId);
        return note;
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // =========================================================================
    // 观察 1 & Property 3：首次点赞（NOT isBugCondition_Like）行为不变
    //
    // FOR ALL (userId, noteId) WHERE NOT isBugCondition_Like(userId, noteId, 1)
    //   ASSERT likeNote(noteId, userId) = true
    //   ASSERT like_count 增量 = 1
    //   ASSERT like_record 插入成功
    //
    // Validates: Requirements 3.1
    // =========================================================================

    @ParameterizedTest(name = "首次点赞 userId={0}, noteId={1}")
    @CsvSource({
        "2, 2",
        "1, 3",
        "5, 10",
        "100, 200",
        "999, 1"
    })
    @DisplayName("Property 3: 首次点赞（callCount=1）返回 true，like_count +1，like_record 插入成功")
    void property3_firstLike_returnsTrue_likeCountIncremented(Long userId, Long noteId) {
        // 前置条件：callCount=1，NOT isBugCondition_Like
        assertFalse(isBugCondition_Like(userId, noteId, 1),
                "前置条件：callCount=1 不应触发 Bug 条件");

        // 准备 mock
        prepareNote(noteId);
        when(likeRecordMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(likeRecordMapper.insert(any(LikeRecord.class))).thenReturn(1);
        when(noteMapper.incrementLikeCount(noteId)).thenReturn(1);
        doNothing().when(hotRankService).updateHotScore(anyLong(), anyDouble());

        // 设置登录用户
        UserContext.set(new LoginUserDTO(userId, 0, "token"));

        // 执行
        boolean result = noteService.likeNote(noteId, null);

        // 断言：返回 true
        assertTrue(result, "首次点赞应返回 true（Requirements 3.1）");

        // 断言：like_count 原子 +1（incrementLikeCount 被调用一次）
        verify(noteMapper, times(1)).incrementLikeCount(noteId);

        // 断言：like_record 插入成功（insert 被调用一次）
        verify(likeRecordMapper, times(1)).insert(any(LikeRecord.class));
    }

    // =========================================================================
    // 观察 2：未登录调用 likeNote() 抛出 NOT_LOGIN
    //
    // Validates: Requirements 3.2
    // =========================================================================

    @Test
    @DisplayName("观察 2: 未登录调用 likeNote() 抛出 NOT_LOGIN 异常")
    void observation2_notLoggedIn_throwsNotLogin() {
        // 未设置 UserContext（模拟未登录）
        UserContext.clear();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> noteService.likeNote(2L, null),
                "未登录应抛出 BusinessException");

        assertEquals(ErrorCode.NOT_LOGIN.getCode(), ex.getCode(),
                "异常码应为 NOT_LOGIN（Requirements 3.2）");
    }

    // =========================================================================
    // 观察 3：笔记不存在时抛出 PARAMS_ERROR("笔记不存在")
    //
    // Validates: Requirements 3.3
    // =========================================================================

    @Test
    @DisplayName("观察 3: 笔记不存在时抛出 PARAMS_ERROR(\"笔记不存在\")")
    void observation3_noteNotFound_throwsParamsError() {
        Long nonExistentNoteId = 9999L;
        UserContext.set(new LoginUserDTO(2L, 0, "token"));

        // 模拟笔记不存在
        doReturn(null).when(noteService).getById(nonExistentNoteId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> noteService.likeNote(nonExistentNoteId, null),
                "笔记不存在应抛出 BusinessException");

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode(),
                "异常码应为 PARAMS_ERROR（Requirements 3.3）");
        assertEquals("笔记不存在", ex.getDescription(),
                "异常描述应为 '笔记不存在'（Requirements 3.3）");
    }

    // =========================================================================
    // 观察 4 & Property 4：单次调用 increaseViewCount（NOT isBugCondition_View）
    //
    // FOR ALL noteId WHERE NOT isBugCondition_View(noteId, 1)
    //   ASSERT increaseViewCount(noteId) = true
    //   ASSERT view_count +1
    //
    // Validates: Requirements 3.4
    // =========================================================================

    @ParameterizedTest(name = "单次浏览量 noteId={0}")
    @CsvSource({
        "2",
        "1",
        "5",
        "100",
        "999"
    })
    @DisplayName("Property 4: 单次调用 increaseViewCount 返回 true，view_count +1")
    void property4_singleViewCount_returnsTrue_viewCountIncremented(Long noteId) {
        // 前置条件：concurrentCount=1，NOT isBugCondition_View
        assertFalse(isBugCondition_View(noteId, 1),
                "前置条件：单次调用不应触发 Bug 条件");

        // 准备 mock（修复后优先走 Redis 路径）
        Note note = prepareNote(noteId);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        doNothing().when(hotRankService).updateHotScore(anyLong(), anyDouble());

        // 执行
        boolean result = noteService.increaseViewCount(noteId);

        // 断言：返回 true
        assertTrue(result, "单次 increaseViewCount 应返回 true（Requirements 3.4）");

        // 断言：Redis increment 被调用一次（view_count 通过 Redis 缓冲 +1）
        verify(valueOperations, times(1)).increment("note:view:" + noteId);
    }

    // =========================================================================
    // 观察 5：Redis 不可用时 increaseViewCount() 降级写库，返回 true
    //
    // 在未修复代码中，increaseViewCount 直接调用 noteMapper.incrementViewCount（无 Redis）
    // 此行为在修复后（Redis 不可用降级路径）应保持不变
    //
    // Validates: Requirements 3.5
    // =========================================================================

    @Test
    @DisplayName("观察 5: Redis 不可用时 increaseViewCount() 降级写库，返回 true")
    void observation5_redisUnavailable_fallbackToDb_returnsTrue() {
        Long noteId = 2L;

        // 准备 mock：模拟 Redis 不可用（opsForValue().increment() 抛出 RedisConnectionFailureException）
        prepareNote(noteId);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis connection refused"));
        when(noteMapper.incrementViewCount(noteId)).thenReturn(1);
        doNothing().when(hotRankService).updateHotScore(anyLong(), anyDouble());

        // 执行（Redis 不可用，降级写库）
        boolean result = noteService.increaseViewCount(noteId);

        // 断言：返回 true
        assertTrue(result, "Redis 不可用降级写库应返回 true（Requirements 3.5）");

        // 断言：直接调用了 noteMapper.incrementViewCount（降级写库）
        verify(noteMapper, times(1)).incrementViewCount(noteId);
    }

    // =========================================================================
    // 综合属性测试：多组 (userId, noteId) 首次点赞，全部满足 NOT isBugCondition_Like
    // 模拟属性测试的"对所有满足条件的输入"语义
    //
    // Validates: Requirements 3.1
    // =========================================================================

    @Test
    @DisplayName("综合属性: 所有 NOT isBugCondition_Like(callCount=1) 的输入，likeNote 返回 true 且 like_count +1")
    void propertyTest_allNonBugConditionLike_likeNoteReturnsTrue() {
        // 生成多组非 Bug 条件输入（callCount=1 的首次点赞场景）
        long[][] inputs = {
            {2L, 2L}, {3L, 5L}, {10L, 20L}, {50L, 100L}, {1L, 1L}
        };

        for (long[] input : inputs) {
            Long userId = input[0];
            Long noteId = input[1];

            // 验证前置条件
            assertFalse(isBugCondition_Like(userId, noteId, 1),
                    String.format("输入 (userId=%d, noteId=%d, callCount=1) 不应触发 Bug 条件", userId, noteId));

            // 重置 mock 状态
            reset(noteMapper, likeRecordMapper, hotRankService);

            // 准备 mock
            prepareNote(noteId);
            when(likeRecordMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(likeRecordMapper.insert(any(LikeRecord.class))).thenReturn(1);
            when(noteMapper.incrementLikeCount(noteId)).thenReturn(1);
            doNothing().when(hotRankService).updateHotScore(anyLong(), anyDouble());

            UserContext.set(new LoginUserDTO(userId, 0, "token"));

            // 执行并断言
            boolean result = noteService.likeNote(noteId, null);
            assertTrue(result,
                    String.format("首次点赞 (userId=%d, noteId=%d) 应返回 true", userId, noteId));
            verify(noteMapper, times(1)).incrementLikeCount(noteId);
            verify(likeRecordMapper, times(1)).insert(any(LikeRecord.class));

            UserContext.clear();
        }
    }

    // =========================================================================
    // 综合属性测试：多组 noteId 单次浏览量，全部满足 NOT isBugCondition_View
    //
    // Validates: Requirements 3.4
    // =========================================================================

    @Test
    @DisplayName("综合属性: 所有 NOT isBugCondition_View(concurrentCount=1) 的输入，increaseViewCount 返回 true 且 view_count +1")
    void propertyTest_allNonBugConditionView_increaseViewCountReturnsTrue() {
        Long[] noteIds = {2L, 3L, 10L, 50L, 100L};

        for (Long noteId : noteIds) {
            // 验证前置条件
            assertFalse(isBugCondition_View(noteId, 1),
                    String.format("输入 (noteId=%d, concurrentCount=1) 不应触发 Bug 条件", noteId));

            // 重置 mock 状态
            reset(noteMapper, hotRankService, redisTemplate, valueOperations);

            // 准备 mock（修复后优先走 Redis 路径）
            prepareNote(noteId);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(anyString())).thenReturn(1L);
            doNothing().when(hotRankService).updateHotScore(anyLong(), anyDouble());

            // 执行并断言
            boolean result = noteService.increaseViewCount(noteId);
            assertTrue(result,
                    String.format("单次 increaseViewCount (noteId=%d) 应返回 true", noteId));
            verify(valueOperations, times(1)).increment("note:view:" + noteId);
        }
    }
}
