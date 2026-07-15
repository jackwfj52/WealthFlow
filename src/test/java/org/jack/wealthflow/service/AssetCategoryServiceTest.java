package org.jack.wealthflow.service;

import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.AssetCategory;
import org.jack.wealthflow.service.impl.AssetCategoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetCategoryServiceTest {

    @Mock
    private AssetCategoryMapper assetCategoryMapper;

    @InjectMocks
    private AssetCategoryServiceImpl assetCategoryService;

    @Test
    void shouldFindAllCategories() {
        AssetCategory category = new AssetCategory();
        category.setId(1L);
        category.setName("股票");

        when(assetCategoryMapper.findAll())
                .thenReturn(List.of(category));

        List<AssetCategory> result =
                assetCategoryService.findAll();

        assertEquals(1, result.size());
        assertEquals("股票", result.get(0).getName());

        verify(assetCategoryMapper).findAll();
    }

    @Test
    void shouldInsertCategory() {
        AssetCategory category = new AssetCategory();
        category.setName(" 基金 ");

        when(assetCategoryMapper.findByName("基金"))
                .thenReturn(null);
        when(assetCategoryMapper.insert(category))
                .thenReturn(1);

        assetCategoryService.insert(category);

        assertEquals("基金", category.getName());
        assertNotNull(category.getCreatedDate());

        verify(assetCategoryMapper).insert(category);
    }

    @Test
    void shouldRejectDuplicateCategory() {
        AssetCategory existing = new AssetCategory();
        existing.setId(1L);
        existing.setName("股票");

        AssetCategory category = new AssetCategory();
        category.setName("股票");

        when(assetCategoryMapper.findByName("股票"))
                .thenReturn(existing);

        assertThrows(
                IllegalStateException.class,
                () -> assetCategoryService.insert(category)
        );

        verify(assetCategoryMapper, never())
                .insert(any(AssetCategory.class));
    }

    @Test
    void shouldDeleteUnusedCategory() {
        when(assetCategoryMapper.findById(1L))
                .thenReturn(new AssetCategory());

        when(assetCategoryMapper.countSnapshotsByCategoryId(1L))
                .thenReturn(0);

        when(assetCategoryMapper.deleteById(1L))
                .thenReturn(1);

        assetCategoryService.deleteById(1L);

        verify(assetCategoryMapper).deleteById(1L);
    }

    @Test
    void shouldRejectDeletingCategoryInUse() {
        when(assetCategoryMapper.findById(1L))
                .thenReturn(new AssetCategory());

        when(assetCategoryMapper.countSnapshotsByCategoryId(1L))
                .thenReturn(2);

        assertThrows(
                IllegalStateException.class,
                () -> assetCategoryService.deleteById(1L)
        );

        verify(assetCategoryMapper, never())
                .deleteById(anyLong());
    }
}