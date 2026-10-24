package org.jack.wealthflow.websearch;

public interface WebSearchGateway {
    WebSearchDtos.Result search(String query, String timeRange, int limit);
}
