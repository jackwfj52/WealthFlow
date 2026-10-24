package org.jack.wealthflow.news;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/financial-news")
public class FinancialNewsController {
    private final FinancialNewsService news;

    @PostMapping("/query")
    public ApiResponse<FinancialNewsDtos.Result> query(@RequestBody JsonNode args) {
        return ApiResponse.success(news.query(args));
    }
}
