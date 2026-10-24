package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.AgentChatRequest;
import org.jack.wealthflow.dto.AgentChatResponse;

public interface AgentChatService {

    AgentChatResponse chat(AgentChatRequest request);

    default AgentChatResponse chat(AgentChatRequest request,
            java.util.function.Consumer<org.jack.wealthflow.dto.AgentProgressEvent> progress) {
        return chat(request);
    }
}
