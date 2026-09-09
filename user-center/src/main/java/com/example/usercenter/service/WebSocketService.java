package com.example.usercenter.service;

/**
 * WebSocket 推送服务接口
 */
public interface WebSocketService {

    /**
     * 向指定用户推送通知
     *
     * @param userId  目标用户 ID
     * @param payload 推送内容（JSON 对象）
     */
    void sendToUser(Long userId, Object payload);

    /**
     * 向所有订阅者广播
     *
     * @param destination 目标路径（如 /topic/announcement）
     * @param payload     推送内容
     */
    void broadcast(String destination, Object payload);
}
