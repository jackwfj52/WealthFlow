package org.jack.wealthflow.dto;

import java.math.BigDecimal;

public record HoldingResponse(String id, String categoryId, String name, BigDecimal percentage) {}
