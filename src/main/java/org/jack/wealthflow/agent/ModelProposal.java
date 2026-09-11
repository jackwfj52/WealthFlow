package org.jack.wealthflow.agent;

/**
 * 经 ModelReplyParser 严格校验后的模型操作建议（仅内部使用）。
 */
public sealed interface ModelProposal
        permits ModelSnapshotProposal, ModelDeleteProposal {
}
