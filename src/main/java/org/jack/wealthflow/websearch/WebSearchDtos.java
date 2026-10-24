package org.jack.wealthflow.websearch;

import java.util.List;

public final class WebSearchDtos {
    private WebSearchDtos() {}
    public record Source(String id, String title, String snippet, String url, String siteName,
                         String publishedAt, String retrievedAt) {}
    public record Result(String provider, String query, String timeRange, String retrievedAt,
                         String coverage, List<Source> sources) {
        public Result { sources = sources == null ? List.of() : List.copyOf(sources); }
    }
}
