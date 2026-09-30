package org.jack.wealthflow.service;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.mapper.AiPermissionMapper;
import org.jack.wealthflow.model.AiPermissionLevel;
import org.springframework.stereotype.Service;

import static org.jack.wealthflow.agent.AgentInputs.invalid;

@Service
@RequiredArgsConstructor
public class AiPermissionService {
    private final AiPermissionMapper mapper;

    public AiPermissionLevel current() {
        String level = mapper.findLevel();
        if (level == null) throw invalid("AI权限设置未初始化");
        try {
            return AiPermissionLevel.valueOf(level);
        } catch (IllegalArgumentException e) {
            throw invalid("AI权限设置无效");
        }
    }

    public AiPermissionLevel update(String value) {
        AiPermissionLevel level;
        try {
            level = AiPermissionLevel.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw invalid("AI权限只能选择只读、审核或直接执行");
        }
        if (mapper.updateLevel(level.name()) != 1) throw invalid("AI权限设置保存失败");
        return level;
    }
}
