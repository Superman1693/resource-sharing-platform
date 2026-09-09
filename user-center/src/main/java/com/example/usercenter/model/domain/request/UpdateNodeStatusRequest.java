package com.example.usercenter.model.domain.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class UpdateNodeStatusRequest implements Serializable {
    /**
     * 节点ID
     */
    private Long nodeId;

    /**
     * 节点状态：pending/in_progress/completed
     */
    private String status;
}
