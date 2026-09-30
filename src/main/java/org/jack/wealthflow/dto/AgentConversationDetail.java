package org.jack.wealthflow.dto;

import java.util.List;

public record AgentConversationDetail(AgentConversation conversation,
                                      List<AgentConversationMessage> messages,
                                      boolean hasMore) { }
