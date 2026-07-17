package org.jack.wealthflow.dto;

import org.jack.wealthflow.model.AssetCategory;

public record CategoryResponse(String id, String name, String createdAt) {
    public static CategoryResponse from(AssetCategory category) {
        return new CategoryResponse(
                String.valueOf(category.getId()),
                category.getName(),
                category.getCreatedDate() == null
                        ? null
                        : category.getCreatedDate().toString()
        );
    }
}