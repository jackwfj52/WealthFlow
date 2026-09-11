package org.jack.wealthflow.agent;

/**
 * 经 ModelReplyParser 严格校验后的模型输出。
 *
 * <p>kind 只能是 answer、propose_create_snapshot 或 propose_delete_snapshots；
 * answer 的 proposal 恒为 null；提出草案时 proposal 恒非空且类型与 kind 对应。</p>
 */
public record ModelReply(
        String kind,
        String reply,
        ModelProposal proposal
) {
    public static final String KIND_ANSWER = "answer";
    public static final String KIND_PROPOSE_CREATE_SNAPSHOT =
            "propose_create_snapshot";
    public static final String KIND_PROPOSE_DELETE_SNAPSHOTS =
            "propose_delete_snapshots";

    public static ModelReply answer(String reply) {
        return new ModelReply(KIND_ANSWER, reply, null);
    }

    public static ModelReply propose(
            String reply,
            ModelSnapshotProposal proposal
    ) {
        return new ModelReply(KIND_PROPOSE_CREATE_SNAPSHOT, reply, proposal);
    }

    public static ModelReply proposeDelete(
            String reply,
            ModelDeleteProposal proposal
    ) {
        return new ModelReply(KIND_PROPOSE_DELETE_SNAPSHOTS, reply, proposal);
    }
}
