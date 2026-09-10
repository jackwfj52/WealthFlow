package org.jack.wealthflow.agent;

/**
 * 经 ModelReplyParser 严格校验后的模型输出。
 *
 * <p>kind 只能是 answer 或 propose_create_snapshot；
 * answer 的 proposal 恒为 null；propose_create_snapshot 的 proposal 恒非空。</p>
 */
public record ModelReply(
        String kind,
        String reply,
        ModelSnapshotProposal proposal
) {
    public static final String KIND_ANSWER = "answer";
    public static final String KIND_PROPOSE_CREATE_SNAPSHOT =
            "propose_create_snapshot";

    public static ModelReply answer(String reply) {
        return new ModelReply(KIND_ANSWER, reply, null);
    }

    public static ModelReply propose(
            String reply,
            ModelSnapshotProposal proposal
    ) {
        return new ModelReply(KIND_PROPOSE_CREATE_SNAPSHOT, reply, proposal);
    }
}
