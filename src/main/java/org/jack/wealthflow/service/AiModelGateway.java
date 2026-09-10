package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.AgentChatMessage;

import java.util.List;

/**
 * 模型网关，仅供后端服务内部使用，绝不通过 Controller 暴露。
 *
 * <p>负责调用 OpenAI-compatible 的 Chat Completions 接口并返回
 * 模型文本输出；所有失败以安全业务错误抛出，错误信息不包含
 * API Key 或第三方原始响应正文。</p>
 */
public interface AiModelGateway {

    /**
     * @param baseUrl      用户配置的 Base URL（末尾斜杠会被去除）
     * @param model        用户配置的准确模型名称
     * @param apiKey       解密后的 API Key，只用于 Authorization 头
     * @param systemPrompt 后端生成的系统提示词
     * @param messages     user / assistant 对话消息
     * @return 模型输出文本（choices[0].message.content）
     */
    String complete(
            String baseUrl,
            String model,
            String apiKey,
            String systemPrompt,
            List<AgentChatMessage> messages
    );
}
