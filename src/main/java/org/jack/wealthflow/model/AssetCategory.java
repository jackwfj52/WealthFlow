package org.jack.wealthflow.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AssetCategory {
    private Long id;
    private String name;
    private LocalDate createdDate;
}
