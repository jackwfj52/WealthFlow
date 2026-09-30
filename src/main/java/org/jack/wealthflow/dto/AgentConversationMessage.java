package org.jack.wealthflow.dto;

public record AgentConversationMessage(String id, String role, String content,
                                       AgentChatResponse response, String actionId,
                                       String createdAt) { }
