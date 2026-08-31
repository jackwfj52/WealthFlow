package org.jack.wealthflow.model;

import lombok.Data;

@Data
public class PendingAction {

    private String id;

    private PendingActionType actionType;

    private PendingActionStatus status;

    /**
     * 已校验的操作参数 JSON。
     * 例如：创建快照的日期、分类 ID 与金额。
     */
    private String payloadJson;

    /**
     * 面向用户展示的操作说明，不能由模型直接作为最终真相。
     */
    private String displaySummary;

    private String createdAt;

    private String expiresAt;

    private String executedAt;

    private String failureMessage;
}