package org.jack.wealthflow.controller;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.*;
import org.jack.wealthflow.service.AgentConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/conversations")
public class AgentConversationController {
    private final AgentConversationService conversations;

    public record SendRequest(String providerId, String message) { }
    public record RenameRequest(String title) { }
    public record ImportRequest(List<AgentChatMessage> messages) { }

    @GetMapping
    public ApiResponse<List<AgentConversation>> list() {
        return ApiResponse.success(conversations.list());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AgentConversation>> create() {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(conversations.create()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AgentConversationDetail> detail(@PathVariable String id,
                                                         @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(conversations.detail(id, page));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AgentConversation> rename(@PathVariable String id,
                                                  @RequestBody RenameRequest request) {
        return ApiResponse.success(conversations.rename(id, request == null ? null : request.title()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        conversations.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<AgentConversationMessage> send(@PathVariable String id,
                                                       @RequestBody SendRequest request) {
        return ApiResponse.success(conversations.send(id,
                request == null ? null : request.providerId(),
                request == null ? null : request.message()));
    }

    @PostMapping("/{id}/import")
    public ApiResponse<Map<String, String>> importLegacy(@PathVariable String id,
                                                           @RequestBody ImportRequest request) {
        conversations.importLegacy(id, request == null ? null : request.messages());
        return ApiResponse.success(Map.of("status", "IMPORTED"));
    }

    @GetMapping("/{id}/actions/{actionId}/status")
    public ApiResponse<Map<String, String>> actionStatus(@PathVariable String id,
                                                           @PathVariable String actionId) {
        return ApiResponse.success(Map.of("status", conversations.actionStatus(id, actionId)));
    }

    @PostMapping("/{id}/manual-drafts")
    public ApiResponse<AgentConversationMessage> attachManualDraft(@PathVariable String id,
                                                                     @RequestBody CreateSnapshotDraftResponse draft) {
        return ApiResponse.success(conversations.attachManualDraft(id, draft));
    }

    @PostMapping("/{id}/actions/{actionId}/record")
    public ApiResponse<AgentConversationMessage> recordActionOutcome(@PathVariable String id,
                                                                      @PathVariable String actionId) {
        return ApiResponse.success(conversations.recordActionOutcome(id, actionId));
    }
}
