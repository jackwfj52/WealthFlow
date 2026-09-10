package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.AgentChatRequest;
import org.jack.wealthflow.dto.AgentChatResponse;

public interface AgentChatService {

    AgentChatResponse chat(AgentChatRequest request);
}
