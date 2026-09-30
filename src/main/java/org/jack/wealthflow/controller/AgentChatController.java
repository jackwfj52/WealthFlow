package org.jack.wealthflow.controller;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.AgentChatRequest;
import org.jack.wealthflow.dto.AgentChatResponse;
import org.jack.wealthflow.dto.ApiResponse;
import org.jack.wealthflow.service.AgentChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 助手聊天接口。
 *
 * <p>模型只能产生普通回答或结构化快照操作建议；
 * 服务端按照当前AI权限档位决定拒绝写入、返回待确认草案或校验后直接执行。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent")
public class AgentChatController {

    private final AgentChatService agentChatService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AgentChatResponse>> chat(
            @RequestBody(required = false) AgentChatRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(agentChatService.chat(request))
        );
    }
}
