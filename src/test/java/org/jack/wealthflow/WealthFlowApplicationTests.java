package org.jack.wealthflow;

import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.AssetCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class WealthFlowApplicationTests {

    @Autowired
    private AssetCategoryMapper assetCategoryMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldFindAllAssetCategories() {
        List<AssetCategory> categories = assetCategoryMapper.findAll();

        assertNotNull(categories);
    }

}