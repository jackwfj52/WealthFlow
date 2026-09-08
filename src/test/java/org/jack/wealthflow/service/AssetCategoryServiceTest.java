package org.jack.wealthflow.service;

import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.AssetCategory;
import org.jack.wealthflow.service.impl.AssetCategoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetCategoryService.insert(category)
        );

        assertEquals(ErrorCode.CATEGORY_NAME_EXISTS, exception.getErrorCode());
        assertEquals("名称已存在", exception.getMessage());

        verify(assetCategoryMapper, never())
                .insert(any(AssetCategory.class));
    }

    @Test
    void shouldAutoAssignColorWhenNotProvided() {
        AssetCategory category = new AssetCategory();
        category.setName("基金");

        when(assetCategoryMapper.findAll())
                .thenReturn(List.of());
        when(assetCategoryMapper.findByName("基金"))
                .thenReturn(null);
        when(assetCategoryMapper.insert(category))
                .thenReturn(1);

        assetCategoryService.insert(category);

        assertEquals("#1677ff", category.getColor());
        verify(assetCategoryMapper).insert(category);
    }

    @Test
    void shouldRejectInvalidColor() {
        AssetCategory category = new AssetCategory();
        category.setName("基金");
        category.setColor("red");

        when(assetCategoryMapper.findByName("基金"))
                .thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetCategoryService.insert(category)
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
        assertEquals("颜色格式无效，需为 #RRGGBB 格式", exception.getMessage());

        verify(assetCategoryMapper, never())
                .insert(any(AssetCategory.class));
    }

    @Test
    void shouldPreserveColorWhenUpdateColorNotProvided() {
        AssetCategory existing = new AssetCategory();
        existing.setId(1L);
        existing.setName("股票");
        existing.setColor("#f5222d");
        existing.setCreatedDate(LocalDate.of(2026, 1, 1));

        AssetCategory category = new AssetCategory();
        category.setId(1L);
        category.setName("股票");

        when(assetCategoryMapper.findById(1L))
                .thenReturn(existing);
        when(assetCategoryMapper.findByName("股票"))
                .thenReturn(existing);
        when(assetCategoryMapper.update(category))
                .thenReturn(1);

        assetCategoryService.update(category);

        assertEquals("#f5222d", category.getColor());
        assertEquals(LocalDate.of(2026, 1, 1), category.getCreatedDate());

        verify(assetCategoryMapper).update(category);
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

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetCategoryService.deleteById(1L)
        );

        assertEquals(ErrorCode.CATEGORY_HAS_SNAPSHOTS, exception.getErrorCode());
        assertEquals("资产分类下存在快照，无法删除", exception.getMessage());

        verify(assetCategoryMapper, never())
                .deleteById(anyLong());
    }
}
