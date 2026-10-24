package org.jack.wealthflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.ApiResponse;
import org.jack.wealthflow.dto.PortfolioContext;
import org.jack.wealthflow.service.PortfolioQueryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/portfolio")
public class PortfolioQueryController {
    private final PortfolioQueryService portfolio;
    @PostMapping("/query")
    public ApiResponse<PortfolioContext> query(@RequestBody JsonNode args) {
        return ApiResponse.success(portfolio.query(args));
    }
}
