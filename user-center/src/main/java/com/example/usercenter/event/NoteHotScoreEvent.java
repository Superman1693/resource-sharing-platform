package com.example.usercenter.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 笔记热度分更新事件
 * <p>
 * 用于替代 NoteServiceImpl 中的 @Async 自调用问题。
 * Spring AOP 代理无法拦截同类内部方法调用，导致 @Async 注解失效，
 * 改用事件驱动模式后，监听器处于独立 Bean 中，@Async 可正常生效。
 */
@Getter
public class NoteHotScoreEvent extends ApplicationEvent {

    private final Long noteId;

    public NoteHotScoreEvent(Object source, Long noteId) {
        super(source);
        this.noteId = noteId;
    }
}
