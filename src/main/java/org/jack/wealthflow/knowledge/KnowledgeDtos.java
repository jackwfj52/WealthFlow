package org.jack.wealthflow.knowledge;

import java.util.List;

public final class KnowledgeDtos {
    private KnowledgeDtos() {}
    public record Document(String id, String title, String type, String format, String body,
                           List<String> tags, String status, int version, String createdAt,
                           String updatedAt, String sourceName, int chunkCount, int indexedCount,
                           int pendingCount, int failedCount, List<Chunk> chunks) {}
    public record Chunk(String id, String section, String content) {}
    public record Write(String title, String type, String format, String body, List<String> tags,
                        String status, Integer expectedVersion, String sourceName) {}
    public record Status(String status, Integer expectedVersion) {}
    public record Page(List<Document> items, int total, int page, int pageSize) {}
    public record Version(int version, String title, String body, String format, List<String> tags,
                          String status, String createdAt) {}
    public record Imports(List<Write> documents) {}
    public record ImportResult(List<Document> created, List<String> skipped) {}
    public record Search(String query, String type, Integer limit, Boolean includeArchived) {}
    public record Source(String id, String documentId, String title, String type, int version,
                         String updatedAt, String section, String excerpt, double score, String status) {}
    public record SearchResult(List<Source> results, String mode, String warning) {}
    public record IndexStatus(int total, int indexed, int pending, int failed, boolean processing,
                              String model, boolean enabled, String warning) {}
}
