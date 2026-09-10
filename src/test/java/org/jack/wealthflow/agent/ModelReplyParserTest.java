package org.jack.wealthflow.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.constant.MessageConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelReplyParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    private ModelReplyParser parser;

    @BeforeEach
    void setUp() {
        parser = new ModelReplyParser(objectMapper);
    }

    @Test
    void shouldParseValidAnswer() {
        ModelReply reply = parser.parse("""
                {
                  "kind": "answer",
                  "reply": "你的资产主要集中在现金。",
                  "proposal": null
                }
                """);

        assertEquals(ModelReply.KIND_ANSWER, reply.kind());
        assertEquals("你的资产主要集中在现金。", reply.reply());
        assertNull(reply.proposal());
    }

    @Test
    void shouldRejectAnswerWithNonEmptyProposal() {
        ModelReply reply = parser.parse("""
                {
                  "kind": "answer",
                  "reply": "好的",
                  "proposal": {
                    "snapshotDate": "2026-09-10",
                    "items": [{"categoryId": "1", "amount": "5000.00"}]
                  }
                }
                """);

        assertFallback(reply);
    }

    @Test
    void shouldRejectUnknownKind() {
        ModelReply reply = parser.parse("""
                { "kind": "delete_all_data", "reply": "已删除", "proposal": null }
                """);

        assertFallback(reply);
    }

    @Test
    void shouldFallbackOnInvalidJson() {
        ModelReply reply = parser.parse("不是 JSON {{{");

        assertFallback(reply);
    }

    @Test
    void shouldFallbackOnBlankOutput() {
        assertFallback(parser.parse("   "));
        assertFallback(parser.parse(null));
    }

    @Test
    void shouldParseFencedJson() {
        ModelReply reply = parser.parse("""
                ```json
                {
                  "kind": "answer",
                  "reply": "围栏内的回答",
                  "proposal": null
                }
                ```
                """);

        assertEquals(ModelReply.KIND_ANSWER, reply.kind());
        assertEquals("围栏内的回答", reply.reply());
    }

    @Test
    void shouldParseValidProposal() {
        ModelReply reply = parser.parse("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建 2026-09-10 的资产快照，确认后才会写入。",
                  "proposal": {
                    "snapshotDate": "2026-09-10",
                    "items": [
                      {"categoryId": "1", "amount": "5000.00"},
                      {"categoryId": 2, "amount": "12000.5"}
                    ]
                  }
                }
                """);

        assertEquals(ModelReply.KIND_PROPOSE_CREATE_SNAPSHOT, reply.kind());
        ModelSnapshotProposal proposal = reply.proposal();
        assertEquals(LocalDate.of(2026, 9, 10), proposal.snapshotDate());
        assertEquals(2, proposal.items().size());
        assertEquals(1L, proposal.items().get(0).categoryId());
        assertEquals(new BigDecimal("5000.00"),
                proposal.items().get(0).amount());
        assertEquals(2L, proposal.items().get(1).categoryId());
        assertEquals(new BigDecimal("12000.5"),
                proposal.items().get(1).amount());
    }

    @Test
    void shouldRejectProposalWithoutProposal() {
        ModelReply reply = parser.parse("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建快照",
                  "proposal": null
                }
                """);

        assertFallback(reply);
    }

    @Test
    void shouldRejectInvalidSnapshotDate() {
        ModelReply reply = parser.parse("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建快照",
                  "proposal": {
                    "snapshotDate": "2026-13-45",
                    "items": [{"categoryId": "1", "amount": "5000.00"}]
                  }
                }
                """);

        assertFallback(reply);
    }

    @Test
    void shouldRejectNonTextualSnapshotDate() {
        ModelReply reply = parser.parse("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建快照",
                  "proposal": {
                    "snapshotDate": 20260910,
                    "items": [{"categoryId": "1", "amount": "5000.00"}]
                  }
                }
                """);

        assertFallback(reply);
    }

    @Test
    void shouldRejectZeroAmount() {
        assertFallbackOnAmount("0");
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertFallbackOnAmount("-5000.00");
    }

    @Test
    void shouldRejectAmountWithMoreThanTwoDecimals() {
        assertFallbackOnAmount("5000.001");
    }

    @Test
    void shouldRejectNonNumericAmount() {
        assertFallbackOnAmount("很多钱");
    }

    @Test
    void shouldRejectInvalidCategoryId() {
        ModelReply reply = parser.parse("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建快照",
                  "proposal": {
                    "snapshotDate": "2026-09-10",
                    "items": [{"categoryId": "abc", "amount": "5000.00"}]
                  }
                }
                """);

        assertFallback(reply);
    }

    @Test
    void shouldRejectFractionalCategoryId() {
        ModelReply reply = parser.parse("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建快照",
                  "proposal": {
                    "snapshotDate": "2026-09-10",
                    "items": [{"categoryId": 1.5, "amount": "5000.00"}]
                  }
                }
                """);

        assertFallback(reply);
    }

    @Test
    void shouldRejectDuplicateCategoryIds() {
        ModelReply reply = parser.parse("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建快照",
                  "proposal": {
                    "snapshotDate": "2026-09-10",
                    "items": [
                      {"categoryId": "1", "amount": "5000.00"},
                      {"categoryId": "1", "amount": "12000.00"}
                    ]
                  }
                }
                """);

        assertFallback(reply);
    }

    @Test
    void shouldRejectEmptyItems() {
        ModelReply reply = parser.parse("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建快照",
                  "proposal": {
                    "snapshotDate": "2026-09-10",
                    "items": []
                  }
                }
                """);

        assertFallback(reply);
    }

    @Test
    void shouldRejectBlankReply() {
        ModelReply reply = parser.parse("""
                { "kind": "answer", "reply": "   ", "proposal": null }
                """);

        assertFallback(reply);
    }

    @Test
    void shouldAcceptAmountWithExactlyTwoDecimals() {
        ModelReply reply = parser.parse("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建快照",
                  "proposal": {
                    "snapshotDate": "2026-09-10",
                    "items": [{"categoryId": "1", "amount": "5000.00"}]
                  }
                }
                """);

        assertEquals(ModelReply.KIND_PROPOSE_CREATE_SNAPSHOT, reply.kind());
        assertEquals(new BigDecimal("5000.00"),
                reply.proposal().items().get(0).amount());
    }

    private void assertFallbackOnAmount(String amount) {
        ModelReply reply = parser.parse("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建快照",
                  "proposal": {
                    "snapshotDate": "2026-09-10",
                    "items": [{"categoryId": "1", "amount": "%s"}]
                  }
                }
                """.formatted(amount));

        assertFallback(reply);
    }

    private void assertFallback(ModelReply reply) {
        assertEquals(ModelReply.KIND_ANSWER, reply.kind());
        assertEquals(MessageConstant.AGENT_REPLY_PARSE_FALLBACK, reply.reply());
        assertNull(reply.proposal());
    }
}
