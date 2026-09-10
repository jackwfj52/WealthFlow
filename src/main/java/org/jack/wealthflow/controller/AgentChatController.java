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
 * <p>模型只能产生普通回答或创建快照草案建议；
 * 任何数据写入都必须经用户点击确认后走独立确认接口。</p>
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
