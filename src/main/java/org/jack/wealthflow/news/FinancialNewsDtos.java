package org.jack.wealthflow.news;

import java.util.List;

public final class FinancialNewsDtos {
    private FinancialNewsDtos() {}
    public record Article(String id, String title, String summary, String publisher,
                          String publishedAt, String retrievedAt, String url) {}
    public record Result(String provider, String query, int days, String retrievedAt, String timeZone,
                         String coverage, int scannedCount, int matchedCount, int skippedInvalidCount,
                         List<Article> articles) {
        public Result { articles = articles == null ? List.of() : List.copyOf(articles); }
    }
}
