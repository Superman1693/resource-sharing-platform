package com.example.usercenter.model.domain.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送私信请求
 */
@Data
public class SendMessageRequest implements Serializable {

    /** 接收者用户ID */
    private Long receiverId;

    /** 消息内容 */
    private String content;
}
