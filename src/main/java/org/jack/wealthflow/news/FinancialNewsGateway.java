package org.jack.wealthflow.news;

public interface FinancialNewsGateway {
    FinancialNewsDtos.Result fetch(String query, int days, int limit);
}
