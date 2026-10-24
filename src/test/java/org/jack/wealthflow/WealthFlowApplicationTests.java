package org.jack.wealthflow;

import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.AssetCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.Files;
import java.io.IOException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class WealthFlowApplicationTests {

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry properties) throws IOException {
        var file = Files.createTempFile("wealthflow-context-test-", ".db");
        file.toFile().deleteOnExit();
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + file);
    }

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
