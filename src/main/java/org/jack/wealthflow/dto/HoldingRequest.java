package org.jack.wealthflow.dto;

import java.math.BigDecimal;
import java.util.List;

public record HoldingRequest(List<Item> items) {
    public record Item(String name, BigDecimal percentage, String sector) {
        public Item(String name, BigDecimal percentage) { this(name, percentage, null); }
    }
}
